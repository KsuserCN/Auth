package cn.ksuser.api.controller;

import cn.ksuser.api.result.ApiResultCode;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserTotp;
import cn.ksuser.api.repository.UserTotpRepository;
import cn.ksuser.api.service.TotpService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

/**
 * TOTP（Time-based One-Time Password）控制器
 * 用于双因素认证的管理和验证
 */
@RestController
@RequestMapping("/auth/totp")
public class TotpController {

    private final TotpService totpService;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final UserTotpRepository userTotpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionUtil encryptionUtil;
    private final SensitiveOperationService sensitiveOperationService;
    private final RateLimitService rateLimitService;
    private final SensitiveLogUtil sensitiveLogUtil;

    public TotpController(TotpService totpService, UserService userService,
                          UserTotpRepository userTotpRepository,
                          PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                          EncryptionUtil encryptionUtil,
                          SensitiveOperationService sensitiveOperationService,
                          RateLimitService rateLimitService,
                          SensitiveLogUtil sensitiveLogUtil) {
        this.totpService = totpService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userTotpRepository = userTotpRepository;
        this.passwordEncoder = passwordEncoder;
        this.encryptionUtil = encryptionUtil;
        this.sensitiveOperationService = sensitiveOperationService;
        this.rateLimitService = rateLimitService;
        this.sensitiveLogUtil = sensitiveLogUtil;
    }

    /**
     * 获取 TOTP 注册选项
     * 第一步：生成秘钥和恢复码，并生成 QR 码
     * 秘钥会临时存储在数据库的 pending_secret_ciphertext 字段，有效期 10 分钟
     */
    @PostMapping("/registration-options")
    public ResponseEntity<ApiResponse<TotpRegistrationOptionsResponse>> getTotpRegistrationOptions(
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
        
        // 检查用户是否已启用 TOTP
        if (totpService.isTotpEnabled(user.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, ApiResultCode.TOTP_001));
        }

        try {
            byte[] masterKey = encryptionUtil.getMasterKey();
            
            // 删除旧的待确认 TOTP（如果存在）
            Optional<UserTotp> existingOpt = userTotpRepository.findByUserId(user.getId());
            if (existingOpt.isPresent()) {
                UserTotp existing = existingOpt.get();
                if (existing.getIsEnabled()) {
                    // 已启用，不允许重新注册
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, ApiResultCode.TOTP_001));
                }
                // 未启用但存在，删除旧的待确认配置
                userTotpRepository.delete(existing);
            }
            
            // 生成新的秘钥和恢复码（优先显示绑定邮箱）
            String accountName = user.getEmail() != null && !user.getEmail().isBlank()
                ? user.getEmail()
                : user.getUsername();
            Map<String, Object> secretData = totpService.generateTotpSecret(user.getId(), accountName, masterKey);
            
            // 创建新的 UserTotp 记录，临时存储待确认密钥
            byte[] encryptedSecret = java.util.Base64.getDecoder()
                .decode((String) secretData.get("encryptedSecret"));
            
            UserTotp userTotp = new UserTotp();
            userTotp.setUserId(user.getId());
            userTotp.setPendingSecretCiphertext(encryptedSecret);
            userTotp.setPendingExpiresAt(LocalDateTime.now().plusMinutes(10)); // 10 分钟有效期
            userTotp.setIsEnabled(false);
            userTotpRepository.save(userTotp);
            
            TotpRegistrationOptionsResponse response = new TotpRegistrationOptionsResponse(
                (String) secretData.get("secret"),
                (String) secretData.get("qrCodeUrl"),
                (String[]) secretData.get("recoveryCodes")
            );

            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, ApiResultCode.TOTP_002, response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, ApiResultCode.TOTP_003));
        }
    }

    /**
     * 确认 TOTP 注册
     * 第二步：用户使用 Google Authenticator 等应用生成的 6 位码来确认注册
     * 此操作会：
     * 1. 验证码是否正确
     * 2. 将待确认秘钥移至正式秘钥
     * 3. 保存恢复码
     * 4. 标记 TOTP 为已启用
     */
    @PostMapping("/registration-verify")
    public ResponseEntity<ApiResponse<String>> confirmTotpRegistration(
            Authentication authentication,
            @RequestBody TotpRegistrationConfirmRequest request,
            HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        
        if (authentication == null) {
            sensitiveLogUtil.logEnableTotp(httpRequest, null, false, "not_authenticated", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, ApiResultCode.UNAUTHENTICATED));
        }

        if (request.getCode() == null || request.getCode().isEmpty()) {
            sensitiveLogUtil.logEnableTotp(httpRequest, null, false, "empty_verification_code", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, ApiResultCode.AUTH_018));
        }

        if (request.getRecoveryCodes() == null || request.getRecoveryCodes().length == 0) {
            sensitiveLogUtil.logEnableTotp(httpRequest, null, false, "empty_recovery_codes", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, ApiResultCode.TOTP_004));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            sensitiveLogUtil.logEnableTotp(httpRequest, null, false, "user_not_found", startTime);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, ApiResultCode.AUTH_005));
        }

        User user = userOpt.get();
        
        // 检查用户是否已启用 TOTP
        if (totpService.isTotpEnabled(user.getId())) {
            sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), false, "totp_already_enabled", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, ApiResultCode.TOTP_001));
        }

        try {
            byte[] masterKey = encryptionUtil.getMasterKey();
            
            // 从数据库获取待确认的秘钥和恢复码列表
            Optional<UserTotp> userTotpOpt = userTotpRepository.findByUserId(user.getId());
            if (userTotpOpt.isEmpty()) {
                sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), false, "totp_not_initialized", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, ApiResultCode.TOTP_005));
            }

            UserTotp userTotp = userTotpOpt.get();
            
            // 检查是否过期
            if (userTotp.isPendingSecretExpired()) {
                userTotpRepository.delete(userTotp);
                sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), false, "pending_secret_expired", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, ApiResultCode.TOTP_006));
            }

            // 使用 registration-options 返回给客户端的同一组恢复码进行确认保存
            
            // 确认注册
            boolean success = totpService.confirmTotpRegistration(
                user.getId(), 
                request.getCode(), 
                request.getRecoveryCodes(),
                masterKey
            );

            if (success) {
                sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), true, null, startTime);
                return ResponseEntity.status(HttpStatus.OK)
                    .body(new ApiResponse<>(200, ApiResultCode.TOTP_007));
            } else {
                sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), false, "invalid_or_expired_verification_code", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, ApiResultCode.TOTP_008));
            }
        } catch (Exception e) {
            sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, ApiResultCode.TOTP_009));
        }
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
