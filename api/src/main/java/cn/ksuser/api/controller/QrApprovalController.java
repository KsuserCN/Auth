package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.QrApprovalProofService;
import cn.ksuser.api.service.QrChallengeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Protected QR approval needs an independently verified credential, even for an existing session. */
@RestController
@RequestMapping("/auth/qr/approval")
public class QrApprovalController {
    private final QrApprovalProofService proofs;
    public QrApprovalController(QrApprovalProofService proofs) { this.proofs = proofs; }

    @PostMapping("/verify")
    public ApiResponse<QrChallengeService.ApprovalProof> verify(@RequestBody QrApprovalVerifyRequest body,
                                                               Authentication authentication,
                                                               HttpServletRequest request) {
        return new ApiResponse<>(200, ApiResultCode.AUTH_131, proofs.verify(body, authentication, request));
    }

    @PostMapping("/passkey/options")
    public ApiResponse<PasskeyAuthenticationOptionsResponse> passkeyOptions(
        @RequestBody QrApprovalPasskeyOptionsRequest body, Authentication authentication, HttpServletRequest request) {
        return new ApiResponse<>(200, ApiResultCode.APPLE_004,
            proofs.passkeyOptions(body == null ? null : body.approveCode(), authentication, request));
    }

    @PostMapping("/passkey/verify")
    public ApiResponse<QrChallengeService.ApprovalProof> passkeyVerify(@RequestBody QrApprovalPasskeyVerifyRequest body,
                                                                     Authentication authentication,
                                                                     HttpServletRequest request) {
        return new ApiResponse<>(200, ApiResultCode.AUTH_131, proofs.verifyPasskey(body, authentication, request));
    }

    @ExceptionHandler(QrApprovalProofService.ApprovalFailure.class)
    public ResponseEntity<ApiResponse<Void>> failure(QrApprovalProofService.ApprovalFailure failure) {
        return ResponseEntity.status(failure.status())
            .body(new ApiResponse<>(failure.status().value(), failure.resultCode()));
    }
}
