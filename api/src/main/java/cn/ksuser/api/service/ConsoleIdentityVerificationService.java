package cn.ksuser.api.service;

import cn.ksuser.api.dto.PasskeyAuthenticationOptionsResponse;
import cn.ksuser.api.dto.PasskeyAuthenticationVerifyRequest;
import cn.ksuser.api.dto.QrChallengeInitResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.UserPasskeyRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.result.BusinessArgumentException;
import cn.ksuser.api.service.ConsoleSessionService.ConsolePrincipal;
import cn.ksuser.api.service.ConsoleSessionService.SessionView;
import cn.ksuser.api.util.EncryptionUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Verifies fresh credentials against the independent Console session. */
@Service
public class ConsoleIdentityVerificationService {
    private final ConsoleSessionService sessions;
    private final UserRepository users;
    private final UserPasskeyRepository passkeys;
    private final UserService userService;
    private final TotpService totp;
    private final PasskeyService passkeyService;
    private final EncryptionUtil encryption;
    private final QrChallengeService qrChallenges;

    public ConsoleIdentityVerificationService(ConsoleSessionService sessions, UserRepository users,
                                              UserPasskeyRepository passkeys, UserService userService,
                                              TotpService totp, PasskeyService passkeyService,
                                              EncryptionUtil encryption, QrChallengeService qrChallenges) {
        this.sessions = sessions;
        this.users = users;
        this.passkeys = passkeys;
        this.userService = userService;
        this.totp = totp;
        this.passkeyService = passkeyService;
        this.encryption = encryption;
        this.qrChallenges = qrChallenges;
    }

    public VerificationContext context(ConsolePrincipal principal, String requestId) {
        User user = verificationUser(principal, requestId);
        return new VerificationContext(requestId, user.getHasPassword(), totp.isTotpEnabled(user.getId()),
            !passkeys.findByUserId(user.getId()).isEmpty());
    }

    public SessionView verifyPassword(ConsolePrincipal principal, String requestId, String password) {
        User user = verificationUser(principal, requestId);
        if (!user.getHasPassword()) throw flowFailure();
        if (password == null || password.isBlank()) throw new BusinessArgumentException(ApiResultCode.AUTH_017);
        if (!userService.verifyPassword(password, user.getPasswordHash()))
            throw new BusinessArgumentException(ApiResultCode.AUTH_130);
        return sessions.completeDirectVerification(requestId, principal);
    }

    public SessionView verifyTotp(ConsolePrincipal principal, String requestId, String code, String recoveryCode) {
        User user = verificationUser(principal, requestId);
        if (!totp.isTotpEnabled(user.getId())) throw flowFailure();
        boolean usingRecoveryCode = recoveryCode != null && !recoveryCode.isBlank();
        if (!usingRecoveryCode && (code == null || code.isBlank()))
            throw new BusinessArgumentException(ApiResultCode.AUTH_132);

        boolean verified = usingRecoveryCode
            ? totp.verifyRecoveryCode(user.getId(), recoveryCode.trim().toUpperCase())
            : totp.verifyTotpCode(user.getId(), code.trim(), encryption.getMasterKey());
        if (!verified) {
            throw new BusinessArgumentException(usingRecoveryCode
                ? ApiResultCode.AUTH_RECOVERY_CODE_INVALID : ApiResultCode.AUTH_VERIFICATION_CODE_INVALID);
        }
        return sessions.completeDirectVerification(requestId, principal);
    }

    public PasskeyAuthenticationOptionsResponse passkeyOptions(ConsolePrincipal principal, String requestId) {
        User user = verificationUser(principal, requestId);
        if (passkeys.findByUserId(user.getId()).isEmpty()) throw flowFailure();
        try {
            return passkeyService.generateSensitiveVerificationOptions();
        } catch (Exception ex) {
            throw new BusinessArgumentException(ApiResultCode.AUTH_151, ex);
        }
    }

    public SessionView verifyPasskey(ConsolePrincipal principal, String requestId, String challengeId,
                                     PasskeyAuthenticationVerifyRequest credential) {
        User user = verificationUser(principal, requestId);
        if (passkeys.findByUserId(user.getId()).isEmpty() || challengeId == null || challengeId.isBlank())
            throw flowFailure();
        try {
            passkeyService.verifySensitiveOperation(user, credential, challengeId);
        } catch (BusinessArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessArgumentException(ApiResultCode.AUTH_152, ex);
        }
        return sessions.completeDirectVerification(requestId, principal);
    }

    public QrChallengeInitResponse startQrVerification(ConsolePrincipal principal, String requestId,
                                                       String clientIp, String userAgent) {
        User user = verificationUser(principal, requestId);
        QrChallengeService.QrChallengePayload challenge = qrChallenges.createConsoleSensitiveChallenge(
            user.getId(), requestId, SessionTransferService.TARGET_WEB, clientIp, userAgent);
        long expiresInSeconds = qrChallenges.getRemainingSeconds(challenge.getChallengeId());
        long ttl = expiresInSeconds > 0 ? expiresInSeconds : QrChallengeService.DEFAULT_TTL_SECONDS;
        return new QrChallengeInitResponse(challenge.getChallengeId(), challenge.getPollToken(),
            QrChallengeService.QR_TEXT_PREFIX + challenge.getApproveCode(), ttl);
    }

    public QrVerificationStatus pollQrVerification(ConsolePrincipal principal, String requestId,
                                                  String challengeId, String pollToken) {
        User user = verificationUser(principal, requestId);
        QrChallengeService.QrChallengePayload challenge = qrChallenges.getByChallengeId(challengeId);
        if (challenge == null) return new QrVerificationStatus("expired", 0L, null);

        if (!constantTimeEquals(challenge.getPollToken(), pollToken)
            || !QrChallengeService.ChallengeType.SENSITIVE.value().equals(challenge.getType())
            || !constantTimeEquals(challenge.getConsoleVerificationRequestId(), requestId)
            || challenge.getRequestedUserId() == null
            || !challenge.getRequestedUserId().equals(user.getId())) {
            throw flowFailure();
        }

        long expiresInSeconds = qrChallenges.getRemainingSeconds(challengeId);
        if (expiresInSeconds <= 0) return new QrVerificationStatus("expired", 0L, null);

        String status = challenge.getStatus() == null ? "pending" : challenge.getStatus();
        if (!QrChallengeService.ChallengeStatus.APPROVED.value().equals(status)) {
            return new QrVerificationStatus(status, expiresInSeconds, null);
        }

        Map<String, Object> result = challenge.getResult();
        if (challenge.getApprovedByUserId() == null || !challenge.getApprovedByUserId().equals(user.getId())
            || result == null || !Boolean.TRUE.equals(result.get("verified"))) {
            throw flowFailure();
        }
        SessionView session = sessions.completeDirectVerification(requestId, principal);
        return new QrVerificationStatus("approved", expiresInSeconds, session);
    }

    private static boolean constantTimeEquals(String first, String second) {
        if (first == null || second == null) return false;
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }

    private User verificationUser(ConsolePrincipal principal, String requestId) {
        sessions.validateVerificationRequest(requestId, principal);
        return users.findByUuid(principal.userUuid()).orElseThrow(ConsoleIdentityVerificationService::sessionFailure);
    }

    private static Oauth2Exception flowFailure() {
        return new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.CONSOLE_FLOW_INVALID);
    }

    private static Oauth2Exception sessionFailure() {
        return new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", ApiResultCode.CONSOLE_SESSION_INVALID);
    }

    public record VerificationContext(String requestId, boolean hasPassword, boolean hasTotp, boolean hasPasskey) {}
    public record QrVerificationStatus(String status, long expiresInSeconds, SessionView session) {}
}
