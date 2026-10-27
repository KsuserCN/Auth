package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.TotpEnrollmentProofService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/totp/enrollment")
public class TotpEnrollmentVerificationController {
    private final TotpEnrollmentProofService proofs;
    public TotpEnrollmentVerificationController(TotpEnrollmentProofService proofs) { this.proofs = proofs; }

    @PostMapping("/verify")
    public ApiResponse<TotpEnrollmentProofService.EnrollmentProof> verify(@RequestBody TotpEnrollmentVerifyRequest input,
        Authentication authentication, HttpServletRequest request) {
        return new ApiResponse<>(200, ApiResultCode.AUTH_131, proofs.verify(input, authentication, request));
    }

    @PostMapping("/passkey/options")
    public ApiResponse<PasskeyAuthenticationOptionsResponse> passkeyOptions(Authentication authentication,
                                                                         HttpServletRequest request) {
        return new ApiResponse<>(200, ApiResultCode.APPLE_004, proofs.passkeyOptions(authentication, request));
    }

    @PostMapping("/passkey/verify")
    public ApiResponse<TotpEnrollmentProofService.EnrollmentProof> verifyPasskey(
        @RequestBody TotpEnrollmentPasskeyVerifyRequest input, Authentication authentication, HttpServletRequest request) {
        return new ApiResponse<>(200, ApiResultCode.AUTH_131, proofs.verifyPasskey(input, authentication, request));
    }

    @ExceptionHandler(TotpEnrollmentProofService.EnrollmentFailure.class)
    public ResponseEntity<ApiResponse<Void>> failure(TotpEnrollmentProofService.EnrollmentFailure failure) {
        return ResponseEntity.status(failure.status()).body(new ApiResponse<>(failure.status().value(), failure.resultCode()));
    }
}
