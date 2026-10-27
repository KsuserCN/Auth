package cn.ksuser.api.controller;

import cn.ksuser.api.result.ApiResultCode;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.service.TotpService;
import cn.ksuser.api.service.TotpEnrollmentService;
import cn.ksuser.api.service.TotpEnrollmentProofService;
import cn.ksuser.api.service.UserService;
import cn.ksuser.api.service.SensitiveOperationService;
import cn.ksuser.api.service.RateLimitService;
import cn.ksuser.api.util.EncryptionUtil;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveSessionContext;
import cn.ksuser.api.util.SensitiveLogUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;

/**
 * TOTP（Time-based One-Time Password）控制器
 * 用于双因素认证的管理和验证
 */
@RestController
@RequestMapping("/auth/totp")
public class TotpController {

    private final TotpService totpService;
    private final TotpEnrollmentService enrollments;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final EncryptionUtil encryptionUtil;
    private final SensitiveOperationService sensitiveOperationService;
    private final RateLimitService rateLimitService;
    private final SensitiveLogUtil sensitiveLogUtil;

    public TotpController(TotpService totpService, UserService userService,
                          JwtUtil jwtUtil,
                          EncryptionUtil encryptionUtil,
                          SensitiveOperationService sensitiveOperationService,
                          RateLimitService rateLimitService,
                          SensitiveLogUtil sensitiveLogUtil,
                          TotpEnrollmentService enrollments) {
        this.totpService = totpService;
        this.enrollments = enrollments;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.encryptionUtil = encryptionUtil;
        this.sensitiveOperationService = sensitiveOperationService;
        this.rateLimitService = rateLimitService;
        this.sensitiveLogUtil = sensitiveLogUtil;
    }

    /** A fresh independent proof starts one session-bound enrollment; it does not enable TOTP. */
    @PostMapping("/registration-options")
    public ResponseEntity<ApiResponse<TotpRegistrationOptionsResponse>> getTotpRegistrationOptions(
            Authentication authentication,
            @RequestBody(required = false) TotpRegistrationStartRequest request,
            HttpServletRequest httpRequest) {
        if (request == null || request.proofId() == null || request.proofId().isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED));
        }
        TotpRegistrationOptionsResponse options = enrollments.start(authentication, httpRequest, request.proofId());
        return ResponseEntity.ok(new ApiResponse<>(200, ApiResultCode.TOTP_002, options));
    }

    /** Only the server-held enrollment can supply the secret and recovery codes being committed. */
    @PostMapping("/registration-verify")
    public ResponseEntity<ApiResponse<String>> confirmTotpRegistration(
            Authentication authentication, @RequestBody TotpRegistrationConfirmRequest request,
            HttpServletRequest httpRequest) {
        if (request == null || request.getEnrollmentId() == null || request.getEnrollmentId().isBlank()
            || request.getCode() == null || !request.getCode().trim().matches("^[0-9]{6}$")) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, ApiResultCode.TOTP_005));
        }
        long startTime = System.currentTimeMillis();
        Long userId = authentication == null ? null : userService.findByUuid(authentication.getName()).map(User::getId).orElse(null);
        boolean success;
        try {
            success = enrollments.confirm(authentication, httpRequest, request.getEnrollmentId(), request.getCode().trim());
        } catch (RuntimeException failure) {
            sensitiveLogUtil.logEnableTotp(httpRequest, userId, false, "enrollment_confirmation_failed", startTime);
            throw failure;
        }
        sensitiveLogUtil.logEnableTotp(httpRequest, userId, success, success ? null : "enrollment_code_invalid", startTime);
        return success
            ? ResponseEntity.ok(new ApiResponse<>(200, ApiResultCode.TOTP_007))
            : ResponseEntity.badRequest().body(new ApiResponse<>(400, ApiResultCode.TOTP_008));
    }

    @ExceptionHandler(TotpEnrollmentProofService.EnrollmentFailure.class)
    public ResponseEntity<ApiResponse<Void>> enrollmentFailure(TotpEnrollmentProofService.EnrollmentFailure failure) {
        return ResponseEntity.status(failure.status())
            .body(new ApiResponse<>(failure.status().value(), failure.resultCode()));
    }

    /**
     * 验证 TOTP 码或恢复码
     * 用于登录时或需要二次验证时调用
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<TotpVerifyResponse>> verifyTotp(
            Authentication authentication,
            @RequestBody TotpVerifyRequest request) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, ApiResultCode.UNAUTHENTICATED));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, ApiResultCode.AUTH_005));
        }

        User user = userOpt.get();

        try {
            byte[] masterKey = encryptionUtil.getMasterKey();
            
            // 先尝试使用 TOTP 码验证
            if (request.getCode() != null && !request.getCode().isEmpty()) {
                if (totpService.verifyTotpCode(user.getId(), request.getCode(), masterKey)) {
                    return ResponseEntity.status(HttpStatus.OK)
                        .body(new ApiResponse<>(200, ApiResultCode.TOTP_010, new TotpVerifyResponse(true)));
                }
            }

            // 尝试使用恢复码验证
            if (request.getRecoveryCode() != null && !request.getRecoveryCode().isEmpty()) {
                if (totpService.verifyRecoveryCode(user.getId(), request.getRecoveryCode())) {
                    return ResponseEntity.status(HttpStatus.OK)
                        .body(new ApiResponse<>(200, ApiResultCode.TOTP_011, new TotpVerifyResponse(true)));
                }
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, ApiResultCode.TOTP_012, new TotpVerifyResponse(false)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, ApiResultCode.TOTP_012));
        }
    }

    /**
     * 获取 TOTP 状态
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<TotpStatusResponse>> getTotpStatus(
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, ApiResultCode.UNAUTHENTICATED));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, ApiResultCode.AUTH_005));
        }

        User user = userOpt.get();
        Map<String, Object> status = totpService.getTotpStatus(user.getId());
        
        Number recoveryCodesCount = (Number) status.get("recoveryCodesCount");
        TotpStatusResponse response = new TotpStatusResponse(
            (Boolean) status.get("enabled"),
            recoveryCodesCount == null ? 0L : recoveryCodesCount.longValue()
        );

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, ApiResultCode.TOTP_013, response));
    }

    /**
     * 禁用 TOTP
     * 需要先完成敏感操作验证
     */
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disableTotp(
            Authentication authentication,
            HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        
        if (authentication == null) {
            sensitiveLogUtil.logDisableTotp(request, null, false, "not_authenticated", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, ApiResultCode.UNAUTHENTICATED));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            sensitiveLogUtil.logDisableTotp(request, null, false, "user_not_found", startTime);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, ApiResultCode.AUTH_005));
        }

        User user = userOpt.get();
        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已完成敏感操作验证
        if (!SensitiveSessionContext.from(request, jwtUtil).isVerified(sensitiveOperationService, userUuid, clientIp, "login-options")) {
            sensitiveLogUtil.logDisableTotp(request, user.getId(), false, "sensitive_verification_not_completed", startTime);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED));
        }

        if (!SensitiveSessionContext.from(request, jwtUtil).consume(sensitiveOperationService, userUuid, clientIp, "login-options")) {
            sensitiveLogUtil.logDisableTotp(request, user.getId(), false, "sensitive_verification_not_completed", startTime);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED));
        }

        boolean success = totpService.disableTotp(user.getId());
        
        if (success) {
            sensitiveLogUtil.logDisableTotp(request, user.getId(), true, null, startTime);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, ApiResultCode.TOTP_014));
        } else {
            sensitiveLogUtil.logDisableTotp(request, user.getId(), false, "totp_not_enabled", startTime);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, ApiResultCode.AUTH_133));
        }
    }

    /**
     * 重新生成恢复码
     * 仅当用户已启用 TOTP 时可用
     * 需要先完成敏感操作验证
     */
    @PostMapping("/recovery-codes/regenerate")
    public ResponseEntity<ApiResponse<String[]>> regenerateRecoveryCodes(
            Authentication authentication,
            HttpServletRequest request) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, ApiResultCode.UNAUTHENTICATED));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, ApiResultCode.AUTH_005));
        }

        User user = userOpt.get();
        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已完成敏感操作验证
        if (!SensitiveSessionContext.from(request, jwtUtil).isVerified(sensitiveOperationService, userUuid, clientIp, "login-options")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED));
        }

        // 检查用户是否启用了 TOTP
        if (!totpService.isTotpEnabled(user.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, ApiResultCode.AUTH_133));
        }

        try {
            byte[] masterKey = encryptionUtil.getMasterKey();
            if (!SensitiveSessionContext.from(request, jwtUtil).consume(sensitiveOperationService, userUuid, clientIp, "login-options")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(403, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED));
            }
            String[] newCodes = totpService.regenerateRecoveryCodes(user.getId(), masterKey);
            
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, ApiResultCode.TOTP_015, newCodes));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, ApiResultCode.TOTP_016));
        }
    }

    /**
     * 获取恢复码列表
     * 仅返回未使用的恢复码的哈希前缀（不返回完整恢复码）
     * 需要先完成敏感操作验证
     */
    @GetMapping("/recovery-codes")
    public ResponseEntity<ApiResponse<java.util.List<String>>> getRecoveryCodes(
            Authentication authentication,
            HttpServletRequest request) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, ApiResultCode.UNAUTHENTICATED));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, ApiResultCode.AUTH_005));
        }

        User user = userOpt.get();
        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已完成敏感操作验证
        if (!SensitiveSessionContext.from(request, jwtUtil).isVerified(sensitiveOperationService, userUuid, clientIp, "login-options")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED));
        }
        
        byte[] masterKey = encryptionUtil.getMasterKey();
        if (!SensitiveSessionContext.from(request, jwtUtil).consume(sensitiveOperationService, userUuid, clientIp, "login-options")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED));
        }
        java.util.List<String> codes = totpService.getRecoveryCodes(user.getId(), masterKey);
        
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, ApiResultCode.TOTP_017, codes));
    }
}
