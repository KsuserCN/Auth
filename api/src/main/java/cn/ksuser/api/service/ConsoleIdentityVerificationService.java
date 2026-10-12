package cn.ksuser.api.service;

import cn.ksuser.api.dto.PasskeyAuthenticationOptionsResponse;
import cn.ksuser.api.dto.PasskeyAuthenticationVerifyRequest;
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

    public ConsoleIdentityVerificationService(ConsoleSessionService sessions, UserRepository users,
                                              UserPasskeyRepository passkeys, UserService userService,
                                              TotpService totp, PasskeyService passkeyService,
                                              EncryptionUtil encryption) {
        this.sessions = sessions;
        this.users = users;
        this.passkeys = passkeys;
        this.userService = userService;
        this.totp = totp;
        this.passkeyService = passkeyService;
        this.encryption = encryption;
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
}
