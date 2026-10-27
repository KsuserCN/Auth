package cn.ksuser.api.service;

import cn.ksuser.api.dto.PasskeyAuthenticationOptionsResponse;
import cn.ksuser.api.dto.QrApprovalPasskeyVerifyRequest;
import cn.ksuser.api.dto.QrApprovalVerifyRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.util.EncryptionUtil;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Issues QR-specific proofs only after a new, server-verified credential assertion. */
@Service
public class QrApprovalProofService {
    private static final String PREFIX = "auth:qr:approval:v2:";
    private static final int MAX_ATTEMPTS = 5;
    private static final DefaultRedisScript<Long> RESERVE_ATTEMPT = new DefaultRedisScript<>("""
        for i = 1, 3 do
            local count = tonumber(redis.call('GET', KEYS[i]) or '0')
            if count >= tonumber(ARGV[i]) then return 0 end
        end
        for i = 1, 3 do
            local count = redis.call('INCR', KEYS[i])
            if count == 1 then redis.call('EXPIRE', KEYS[i], ARGV[i + 3]) end
        end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME_PASSKEY_BINDING = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
        redis.call('DEL', KEYS[1])
        return 1
        """, Long.class);

    private final QrChallengeService challenges;
    private final UserService users;
    private final UserSessionService sessions;
    private final JwtUtil jwt;
    private final TotpService totp;
    private final EncryptionUtil encryption;
    private final PasskeyService passkeys;
    private final VerificationCodeService codes;
    private final RateLimitService rateLimit;
    private final StringRedisTemplate redis;
    private final SensitiveLogUtil logs;

    public QrApprovalProofService(QrChallengeService challenges, UserService users, UserSessionService sessions,
                                  JwtUtil jwt, TotpService totp, EncryptionUtil encryption, PasskeyService passkeys,
                                  VerificationCodeService codes, RateLimitService rateLimit,
                                  StringRedisTemplate redis, SensitiveLogUtil logs) {
        this.challenges = challenges; this.users = users; this.sessions = sessions; this.jwt = jwt;
        this.totp = totp; this.encryption = encryption; this.passkeys = passkeys; this.codes = codes;
        this.rateLimit = rateLimit; this.redis = redis; this.logs = logs;
    }

    public QrChallengeService.ApprovalProof verify(QrApprovalVerifyRequest input, Authentication authentication,
                                                  HttpServletRequest request) {
        if (input == null) throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.BAD_REQUEST);
        Context context = context(authentication, request);
        QrChallengeService.QrChallengePayload challenge = challenge(input.approveCode(), context.user());
        String method = input.method() == null ? "" : input.method().trim().toLowerCase(Locale.ROOT);
        if ("totp".equals(method) && input.recoveryCode() != null && !input.recoveryCode().isBlank()) {
            method = "recovery_code";
        }
        if ("recovery-code".equals(method)) method = "recovery_code";
        if (!List.of("password", "email-code", "totp", "recovery_code").contains(method)) {
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_129);
        }
        if (challenge.getConsoleVerificationRequestId() != null && "email-code".equals(method)) {
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.CONSOLE_FLOW_INVALID);
        }
        validateInput(method, input);
        reserveAttempt(context, challenge, false);
        long start = System.currentTimeMillis();
        boolean verified = switch (method) {
            case "password" -> context.user().getHasPassword()
                && users.verifyPassword(input.password(), context.user().getPasswordHash());
            case "email-code" -> context.user().getEmail() != null && !context.user().getEmail().isBlank()
                && codes.verifyCodeForType(context.user().getEmail(), input.code().trim(),
                    rateLimit.getClientIp(request), "sensitive-verification") == 0;
            case "totp" -> totp.isTotpEnabled(context.user().getId())
                && totp.verifyTotpCode(context.user().getId(), input.code().trim(), encryption.getMasterKey());
            case "recovery_code" -> totp.isTotpEnabled(context.user().getId())
                && totp.verifyRecoveryCode(context.user().getId(), input.recoveryCode().trim().toUpperCase(Locale.ROOT));
            default -> false;
        };
        logs.logSensitiveVerify(request, context.user().getId(), method, verified,
            verified ? null : "qr_approval_credential_invalid", start);
        if (!verified) throw failure(HttpStatus.BAD_REQUEST,
            "password".equals(method) ? ApiResultCode.AUTH_130 : ApiResultCode.AUTH_VERIFICATION_CODE_INVALID);
        // Re-read after potentially expensive credential verification; revoked sessions cannot mint proofs.
        context = context(authentication, request);
        return challenges.issueApprovalProof(challenge.getChallengeId(), context.user().getId(),
            context.session().getId(), context.session().getSessionVersion(), method);
    }

    public PasskeyAuthenticationOptionsResponse passkeyOptions(String approveCode, Authentication authentication,
                                                               HttpServletRequest request) {
        Context context = context(authentication, request);
        QrChallengeService.QrChallengePayload challenge = challenge(approveCode, context.user());
        reserveAttempt(context, challenge, true);
        try {
            PasskeyAuthenticationOptionsResponse options = passkeys.generateSensitiveVerificationOptions();
            if (options == null || options.getChallengeId() == null || options.getChallengeId().isBlank()) {
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
            }
            redis.opsForValue().set(passkeyKey(options.getChallengeId()), binding(context, challenge),
                Duration.ofSeconds(remaining(challenge)));
            return options;
        } catch (ApprovalFailure ex) {
            throw ex;
        } catch (Exception ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        }
    }

    public QrChallengeService.ApprovalProof verifyPasskey(QrApprovalPasskeyVerifyRequest input,
                                                        Authentication authentication, HttpServletRequest request) {
        if (input == null || input.passkeyChallengeId() == null || input.passkeyChallengeId().isBlank()
            || input.passkeyChallengeId().length() > 128 || input.credential() == null) {
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.BAD_REQUEST);
        }
        Context context = context(authentication, request);
        QrChallengeService.QrChallengePayload challenge = challenge(input.approveCode(), context.user());
        reserveAttempt(context, challenge, false);
        try {
            Long consumed = redis.execute(CONSUME_PASSKEY_BINDING,
                List.of(passkeyKey(input.passkeyChallengeId())), binding(context, challenge));
            if (!Long.valueOf(1).equals(consumed)) throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_152);
        } catch (DataAccessException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        }
        long start = System.currentTimeMillis();
        try {
            passkeys.verifySensitiveOperation(context.user(), input.credential(), input.passkeyChallengeId());
        } catch (Exception ex) {
            logs.logSensitiveVerify(request, context.user().getId(), "passkey", false,
                "qr_approval_credential_invalid", start);
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_152);
        }
        logs.logSensitiveVerify(request, context.user().getId(), "passkey", true, null, start);
        context = context(authentication, request);
        return challenges.issueApprovalProof(challenge.getChallengeId(), context.user().getId(),
            context.session().getId(), context.session().getSessionVersion(), "passkey");
    }

    private Context context(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken || request == null) {
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        }
        String token = header.substring(7);
        String uuid = authentication.getName();
        Long sid = jwt.getSessionId(token);
        Integer version = jwt.getSessionVersion(token);
        if (sid == null || version == null || !jwt.isTokenValid(token) || !"access".equals(jwt.getTokenType(token))
            || !uuid.equals(jwt.getUuidFromToken(token))) {
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        }
        UserSession session = sessions.findActiveSessionById(sid)
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED));
        if (session.getUser() == null || !uuid.equals(session.getUser().getUuid())
            || !version.equals(session.getSessionVersion())) {
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        }
        User user = users.findByUuid(uuid)
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED));
        return new Context(user, session);
    }

    private QrChallengeService.QrChallengePayload challenge(String approveCode, User user) {
        if (approveCode == null || approveCode.isBlank() || approveCode.length() > 128) {
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_088);
        }
        QrChallengeService.QrChallengePayload challenge = challenges.getByApproveCode(approveCode.trim());
        if (challenge == null || !Integer.valueOf(2).equals(challenge.getSchemaVersion())
            || !QrChallengeService.ChallengeStatus.PENDING.value().equals(challenge.getStatus())
            || challenge.getExpiresAt() == null || challenge.getExpiresAt() <= Instant.now().getEpochSecond()
            || !(QrChallengeService.ChallengeType.SENSITIVE.value().equals(challenge.getType())
                || QrChallengeService.ChallengeType.RECOVERY.value().equals(challenge.getType()))) {
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_086);
        }
        if (challenge.getRequestedUserId() != null && !Objects.equals(user.getId(), challenge.getRequestedUserId())) {
            throw failure(HttpStatus.FORBIDDEN, ApiResultCode.AUTH_092);
        }
        return challenge;
    }

    private void reserveAttempt(Context context, QrChallengeService.QrChallengePayload challenge, boolean options) {
        String bucket = options ? "options:" : "verify:";
        String account = digest(context.user().getUuid());
        int limit = options ? 10 : MAX_ATTEMPTS;
        try {
            Long allowed = redis.execute(RESERVE_ATTEMPT, List.of(
                PREFIX + bucket + "minute:" + account,
                PREFIX + bucket + "hour:" + account,
                PREFIX + bucket + "challenge:" + challenge.getChallengeId() + ":" + account),
                Integer.toString(limit), Integer.toString(options ? 60 : 30), Integer.toString(limit),
                "60", "3600", Long.toString(remaining(challenge)));
            if (allowed == null) throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
            if (allowed != 1) throw failure(HttpStatus.TOO_MANY_REQUESTS, ApiResultCode.RATE_LIMITED);
        } catch (DataAccessException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        }
    }

    private static long remaining(QrChallengeService.QrChallengePayload challenge) {
        long remaining = challenge.getExpiresAt() - Instant.now().getEpochSecond();
        if (remaining <= 0) throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_086);
        return Math.min(remaining, QrChallengeService.DEFAULT_TTL_SECONDS);
    }

    private static void validateInput(String method, QrApprovalVerifyRequest input) {
        if ("password".equals(method)) {
            if (input.password() == null || input.password().isBlank() || input.password().length() > 256) {
                throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_017);
            }
        } else if ("recovery_code".equals(method)) {
            if (input.recoveryCode() == null || !input.recoveryCode().trim().matches("(?i)^[A-Z]{8}$")) {
                throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_VERIFICATION_CODE_INVALID);
            }
        } else if (input.code() == null || !input.code().trim().matches("^[0-9]{6}$")) {
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_VERIFICATION_CODE_INVALID);
        }
    }

    private static String passkeyKey(String challengeId) { return PREFIX + "passkey:" + challengeId; }
    private static String binding(Context context, QrChallengeService.QrChallengePayload challenge) {
        return digest(context.user().getUuid()) + ":" + context.session().getId() + ":"
            + context.session().getSessionVersion() + ":" + challenge.getChallengeId();
    }
    private static String digest(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    private record Context(User user, UserSession session) { }
    private static ApprovalFailure failure(HttpStatus status, ApiResultCode code) { return new ApprovalFailure(status, code); }
    public static final class ApprovalFailure extends RuntimeException {
        private final HttpStatus status;
        private final ApiResultCode resultCode;
        public ApprovalFailure(HttpStatus status, ApiResultCode resultCode) {
            super(resultCode.name()); this.status = status; this.resultCode = resultCode;
        }
        public HttpStatus status() { return status; }
        public ApiResultCode resultCode() { return resultCode; }
    }
}
