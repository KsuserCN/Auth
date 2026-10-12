package cn.ksuser.api.controller;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.PasskeyAuthenticationOptionsResponse;
import cn.ksuser.api.dto.PasskeyAuthenticationVerifyRequest;
import cn.ksuser.api.dto.QrChallengeInitResponse;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.ConsoleIdentityVerificationService;
import cn.ksuser.api.service.ConsoleSessionService;
import cn.ksuser.api.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/console/verification")
public class ConsoleIdentityVerificationController {
    private final ConsoleIdentityVerificationService verification;
    private final RateLimitService rateLimit;

    public ConsoleIdentityVerificationController(ConsoleIdentityVerificationService verification, RateLimitService rateLimit) {
        this.verification = verification;
        this.rateLimit = rateLimit;
    }

    @GetMapping("/context")
    public ApiResponse<ConsoleIdentityVerificationService.VerificationContext> context(
        @RequestParam("request_id") String requestId, Authentication authentication) {
        return success(verification.context(principal(authentication), requestId));
    }

    @PostMapping("/password")
    public ApiResponse<ConsoleSessionService.SessionView> password(@RequestBody PasswordRequest body,
                                                                   Authentication authentication) {
        return success(verification.verifyPassword(principal(authentication), body.requestId(), body.password()));
    }

    @PostMapping("/totp")
    public ApiResponse<ConsoleSessionService.SessionView> totp(@RequestBody TotpRequest body,
                                                               Authentication authentication) {
        return success(verification.verifyTotp(principal(authentication), body.requestId(), body.code(), body.recoveryCode()));
    }

    @PostMapping("/passkey/options")
    public ApiResponse<PasskeyAuthenticationOptionsResponse> passkeyOptions(@RequestBody VerificationRequest body,
                                                                            Authentication authentication) {
        return success(verification.passkeyOptions(principal(authentication), body.requestId()));
    }

    @PostMapping("/passkey/verify")
    public ApiResponse<ConsoleSessionService.SessionView> passkeyVerify(@RequestBody PasskeyRequest body,
                                                                        Authentication authentication) {
        return success(verification.verifyPasskey(principal(authentication), body.requestId(), body.challengeId(),
            body.credential()));
    }

    @PostMapping("/qr/init")
    public ApiResponse<QrChallengeInitResponse> qrInit(@RequestBody VerificationRequest body,
                                                        Authentication authentication, HttpServletRequest request) {
        return success(verification.startQrVerification(principal(authentication), body.requestId(),
            rateLimit.getClientIp(request), rateLimit.getClientUserAgent(request)));
    }

    @GetMapping("/qr/status")
    public ApiResponse<ConsoleIdentityVerificationService.QrVerificationStatus> qrStatus(
        @RequestParam("request_id") String requestId, @RequestParam String challengeId,
        @RequestParam String pollToken, Authentication authentication) {
        return success(verification.pollQrVerification(principal(authentication), requestId, challengeId, pollToken));
    }

    private static ConsoleSessionService.ConsolePrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ConsoleSessionService.ConsolePrincipal value)) {
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", ApiResultCode.CONSOLE_SESSION_INVALID);
        }
        return value;
    }

    private static <T> ApiResponse<T> success(T value) {
        return new ApiResponse<>(200, ApiResultCode.REQUEST_SUCCESS, value);
    }

    public record VerificationRequest(String requestId) {}
    public record PasswordRequest(String requestId, String password) {}
    public record TotpRequest(String requestId, String code, String recoveryCode) {}
    public record PasskeyRequest(String requestId, String challengeId, PasskeyAuthenticationVerifyRequest credential) {}
}
