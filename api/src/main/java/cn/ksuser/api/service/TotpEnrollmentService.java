package cn.ksuser.api.service;

import cn.ksuser.api.dto.TotpRegistrationOptionsResponse;
import cn.ksuser.api.entity.TotpEnrollment;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.TotpEnrollmentRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.repository.UserSessionRepository;
import cn.ksuser.api.repository.UserTotpRepository;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.result.BusinessArgumentException;
import cn.ksuser.api.util.EncryptionUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns the transaction from independent authorization to one confirmed TOTP credential. */
@Service
public class TotpEnrollmentService {
    public static final long ENROLLMENT_TTL_SECONDS = 600;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final DefaultRedisScript<Long> RESERVE_CONFIRM_ATTEMPT = new DefaultRedisScript<>("""
        local count = tonumber(redis.call('GET', KEYS[1]) or '0')
        if count >= 30 then return 0 end
        count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], 3600) end
        return 1
        """, Long.class);
    private final TotpEnrollmentProofService proofs;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final UserTotpRepository configs;
    private final TotpEnrollmentRepository enrollments;
    private final TotpService totp;
    private final EncryptionUtil encryption;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public TotpEnrollmentService(TotpEnrollmentProofService proofs, UserRepository users,
            UserSessionRepository sessions, UserTotpRepository configs, TotpEnrollmentRepository enrollments,
            TotpService totp, EncryptionUtil encryption, StringRedisTemplate redis,
            PlatformTransactionManager transactionManager) {
        this(proofs, users, sessions, configs, enrollments, totp, encryption, redis,
            transactionManager, Clock.systemDefaultZone());
    }

    TotpEnrollmentService(TotpEnrollmentProofService proofs, UserRepository users,
            UserSessionRepository sessions, UserTotpRepository configs, TotpEnrollmentRepository enrollments,
            TotpService totp, EncryptionUtil encryption, StringRedisTemplate redis,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.proofs = proofs; this.users = users; this.sessions = sessions; this.configs = configs;
        this.enrollments = enrollments; this.totp = totp; this.encryption = encryption;
        this.redis = redis; this.transactions = new TransactionTemplate(transactionManager); this.clock = clock;
    }

    public TotpRegistrationOptionsResponse start(Authentication authentication, HttpServletRequest request, String proofId) {
        Actor context = snapshot(proofs.requireContext(authentication, request));
        if (!proofs.consume(proofId, context.uuid(), context.sessionId(), context.sessionVersion())) {
            throw failure(HttpStatus.FORBIDDEN, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED);
        }
        // Consuming the proof precedes the database transaction. A failed write requires a new proof.
        return transactions.execute(status -> {
            User user = lockedUser(context);
            lockedSession(context, user);
            if (configs.existsByUserIdAndIsEnabledTrue(user.getId())) throw new BusinessArgumentException(ApiResultCode.TOTP_001);
            LocalDateTime now = LocalDateTime.now(clock);
            for (TotpEnrollment previous : enrollments.findByUserIdAndCompletedAtIsNullAndCancelledAtIsNull(user.getId())) {
                previous.setCancelledAt(now); previous.erasePendingSecrets();
            }
            // Legacy unconfirmed configuration never constitutes a new enrollment authorization.
            configs.findByUserId(user.getId()).ifPresent(config -> {
                config.clearPendingSecret(); configs.save(config);
            });
            byte[] key = encryption.getMasterKey();
            String account = user.getEmail() == null || user.getEmail().isBlank() ? user.getUsername() : user.getEmail();
            Map<String, Object> generated = totp.generateTotpSecret(user.getId(), account, key);
            String[] recoveryCodes = (String[]) generated.get("recoveryCodes");
            TotpEnrollment enrollment = new TotpEnrollment();
            byte[] identifier = new byte[24]; random.nextBytes(identifier);
            enrollment.setId(Base64.getUrlEncoder().withoutPadding().encodeToString(identifier));
            enrollment.setUserId(user.getId()); enrollment.setSessionId(context.sessionId());
            enrollment.setSessionVersion(context.sessionVersion());
            enrollment.setPendingSecretCiphertext(Base64.getDecoder().decode((String) generated.get("encryptedSecret")));
            enrollment.setRecoveryCodesCiphertext(totp.encryptEnrollmentRecoveryCodes(recoveryCodes, key));
            enrollment.setCreatedAt(now); enrollment.setExpiresAt(now.plusSeconds(ENROLLMENT_TTL_SECONDS));
            enrollments.saveAndFlush(enrollment);
            TotpRegistrationOptionsResponse response = new TotpRegistrationOptionsResponse(
                (String) generated.get("secret"), (String) generated.get("qrCodeUrl"), recoveryCodes);
            response.setEnrollmentId(enrollment.getId()); response.setExpiresInSeconds(ENROLLMENT_TTL_SECONDS);
            return response;
        });
    }

    public boolean confirm(Authentication authentication, HttpServletRequest request, String enrollmentId, String code) {
        Actor context = snapshot(proofs.requireContext(authentication, request));
        if (enrollmentId == null || !enrollmentId.matches("[A-Za-z0-9_-]{32}")) return false;
        ConfirmResult result = transactions.execute(status -> {
            User user = lockedUser(context);
            lockedSession(context, user);
            TotpEnrollment enrollment = enrollments.findForUpdate(enrollmentId, user.getId()).orElse(null);
            if (enrollment == null || !Objects.equals(enrollment.getSessionId(), context.sessionId())
                || !Objects.equals(enrollment.getSessionVersion(), context.sessionVersion())
                || enrollment.getCompletedAt() != null || enrollment.getCancelledAt() != null) return ConfirmResult.INVALID;
            LocalDateTime now = LocalDateTime.now(clock);
            if (!enrollment.getExpiresAt().isAfter(now)) {
                enrollment.setCancelledAt(now); enrollment.erasePendingSecrets(); return ConfirmResult.EXPIRED;
            }
            if (enrollment.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                enrollment.setCancelledAt(now); enrollment.erasePendingSecrets(); return ConfirmResult.EXHAUSTED;
            }
            if (configs.existsByUserIdAndIsEnabledTrue(user.getId())) return ConfirmResult.INVALID;
            reserveConfirmationAttempt(user);
            byte[] key = encryption.getMasterKey();
            String candidate = code == null ? "" : code.trim();
            Long matched = totp.matchEnrollmentStep(enrollment.getPendingSecretCiphertext(), candidate, key,
                clock.instant().getEpochSecond() / 30);
            if (matched == null) {
                enrollment.setFailedAttempts(enrollment.getFailedAttempts() + 1);
                if (enrollment.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                    enrollment.setCancelledAt(now); enrollment.erasePendingSecrets();
                    return ConfirmResult.EXHAUSTED;
                }
                // Return rather than throw, so the failed-attempt count commits.
                return ConfirmResult.INVALID;
            }
            String[] recoveryCodes = totp.decryptEnrollmentRecoveryCodes(enrollment.getRecoveryCodesCiphertext(), key);
            totp.activateEnrollment(user.getId(), enrollment.getPendingSecretCiphertext(), recoveryCodes, matched, key);
            enrollment.setCompletedAt(now); enrollment.erasePendingSecrets();
            enrollments.saveAndFlush(enrollment);
            return ConfirmResult.SUCCESS;
        });
        // These failures must be raised only after the cancellation/attempt transaction committed.
        if (result == ConfirmResult.EXPIRED) throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.TOTP_006);
        if (result == ConfirmResult.EXHAUSTED) throw failure(HttpStatus.TOO_MANY_REQUESTS, ApiResultCode.RATE_LIMITED);
        return result == ConfirmResult.SUCCESS;
    }

    private User lockedUser(Actor context) {
        User user = users.findByUuidForUpdate(context.uuid())
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED));
        if (!Objects.equals(user.getId(), context.userId())) {
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        }
        return user;
    }

    private void lockedSession(Actor context, User user) {
        UserSession current = sessions.findActiveSessionByIdForUpdate(context.sessionId(), LocalDateTime.now(clock))
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED));
        if (!Objects.equals(current.getUser().getId(), user.getId())
            || !Objects.equals(current.getSessionVersion(), context.sessionVersion())) {
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        }
    }

    private void reserveConfirmationAttempt(User user) {
        try {
            Long reserved = redis.execute(RESERVE_CONFIRM_ATTEMPT,
                List.of("auth:totp:enrollment:confirm:v1:" + user.getUuid()));
            if (reserved == null) throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
            if (reserved != 1) throw failure(HttpStatus.TOO_MANY_REQUESTS, ApiResultCode.RATE_LIMITED);
        } catch (DataAccessException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        }
    }

    private enum ConfirmResult { SUCCESS, INVALID, EXPIRED, EXHAUSTED }
    private record Actor(String uuid, Long userId, Long sessionId, Integer sessionVersion) {}
    private static Actor snapshot(TotpEnrollmentProofService.Context context) {
        return new Actor(context.user().getUuid(), context.user().getId(), context.session().getId(),
            context.session().getSessionVersion());
    }

    private static TotpEnrollmentProofService.EnrollmentFailure failure(HttpStatus status, ApiResultCode code) {
        return new TotpEnrollmentProofService.EnrollmentFailure(status, code);
    }

    @Scheduled(fixedDelayString = "${app.totp.enrollment-cleanup-delay-ms:3600000}")
    public void removeExpiredEnrollments() {
        transactions.executeWithoutResult(status -> enrollments.deleteByExpiresAtBefore(LocalDateTime.now(clock).minusDays(1)));
    }
}
