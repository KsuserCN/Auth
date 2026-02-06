package cn.ksuser.api.controller;

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
    private final UserService userService;
    private final UserTotpRepository userTotpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionUtil encryptionUtil;
    private final SensitiveOperationService sensitiveOperationService;
    private final RateLimitService rateLimitService;

    public TotpController(TotpService totpService, UserService userService,
                          UserTotpRepository userTotpRepository,
                          PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                          EncryptionUtil encryptionUtil,
                          SensitiveOperationService sensitiveOperationService,
                          RateLimitService rateLimitService) {
        this.totpService = totpService;
        this.userService = userService;
        this.userTotpRepository = userTotpRepository;
        this.passwordEncoder = passwordEncoder;
        this.encryptionUtil = encryptionUtil;
        this.sensitiveOperationService = sensitiveOperationService;
        this.rateLimitService = rateLimitService;
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
                .body(new ApiResponse<>(401, "未认证"));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户不存在"));
        }

        User user = userOpt.get();
        
        // 检查用户是否已启用 TOTP
        if (totpService.isTotpEnabled(user.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "用户已启用 TOTP"));
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
                        .body(new ApiResponse<>(400, "用户已启用 TOTP"));
                }
                // 未启用但存在，删除旧的待确认配置
                userTotpRepository.delete(existing);
            }
            
            // 生成新的秘钥和恢复码
            Map<String, Object> secretData = totpService.generateTotpSecret(user.getId(), masterKey);
            
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
                .body(new ApiResponse<>(200, "获取 TOTP 注册选项成功", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "生成 TOTP 秘钥失败：" + e.getMessage()));
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
            @RequestBody TotpRegistrationConfirmRequest request) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未认证"));
        }

        if (request.getCode() == null || request.getCode().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证码不能为空"));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户不存在"));
        }

        User user = userOpt.get();
        
        // 检查用户是否已启用 TOTP
        if (totpService.isTotpEnabled(user.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "用户已启用 TOTP"));
        }

        try {
            byte[] masterKey = encryptionUtil.getMasterKey();
            
            // 从数据库获取待确认的秘钥和恢复码列表
            Optional<UserTotp> userTotpOpt = userTotpRepository.findByUserId(user.getId());
            if (userTotpOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "TOTP 未初始化，请先调用 registration-options"));
            }

            UserTotp userTotp = userTotpOpt.get();
            
            // 检查是否过期
            if (userTotp.isPendingSecretExpired()) {
                userTotpRepository.delete(userTotp);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "待确认秘钥已过期，请重新生成"));
            }

            // 需要恢复码列表用于确认
            // 注：恢复码应该在 registration-options 时已返回给客户端
            // 这里简化处理，假设客户端已保存
            // 实际应该通过 Redis 临时存储恢复码
            
            // 临时生成恢复码（生产环境应从 Redis 或请求中获取）
            java.security.SecureRandom random = new java.security.SecureRandom();
            String[] recoveryCodes = new String[10];
            for (int i = 0; i < 10; i++) {
                StringBuilder code = new StringBuilder();
                for (int j = 0; j < 8; j++) {
                    code.append(random.nextInt(10));
                }
                recoveryCodes[i] = code.toString();
            }
            
            // 确认注册
            boolean success = totpService.confirmTotpRegistration(
                user.getId(), 
                request.getCode(), 
                recoveryCodes,
                masterKey
            );

            if (success) {
                return ResponseEntity.status(HttpStatus.OK)
                    .body(new ApiResponse<>(200, "TOTP 注册成功"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码无效或已过期"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "TOTP 注册失败：" + e.getMessage()));
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
                .body(new ApiResponse<>(401, "未认证"));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户不存在"));
        }

        User user = userOpt.get();

        try {
            byte[] masterKey = encryptionUtil.getMasterKey();
            
            // 先尝试使用 TOTP 码验证
            if (request.getCode() != null && !request.getCode().isEmpty()) {
                if (totpService.verifyTotpCode(user.getId(), request.getCode(), masterKey)) {
                    return ResponseEntity.status(HttpStatus.OK)
                        .body(new ApiResponse<>(200, "TOTP 验证成功",
                            new TotpVerifyResponse(true, "验证成功")));
                }
            }

            // 尝试使用恢复码验证
            if (request.getRecoveryCode() != null && !request.getRecoveryCode().isEmpty()) {
                if (totpService.verifyRecoveryCode(user.getId(), request.getRecoveryCode())) {
                    return ResponseEntity.status(HttpStatus.OK)
                        .body(new ApiResponse<>(200, "使用恢复码验证成功",
                            new TotpVerifyResponse(true, "使用恢复码验证成功")));
                }
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "验证失败",
                    new TotpVerifyResponse(false, "TOTP 码或恢复码无效")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "验证失败：" + e.getMessage()));
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
                .body(new ApiResponse<>(401, "未认证"));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户不存在"));
        }

        User user = userOpt.get();
        Map<String, Object> status = totpService.getTotpStatus(user.getId());
        
        Number recoveryCodesCount = (Number) status.get("recoveryCodesCount");
        TotpStatusResponse response = new TotpStatusResponse(
            (Boolean) status.get("enabled"),
            recoveryCodesCount == null ? 0L : recoveryCodesCount.longValue()
        );

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "获取 TOTP 状态成功", response));
    }

    /**
     * 禁用 TOTP
     * 需要先完成敏感操作验证
     */
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disableTotp(
            Authentication authentication,
            HttpServletRequest request) {
        
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未认证"));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户不存在"));
        }

        User user = userOpt.get();
        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已完成敏感操作验证
        if (!sensitiveOperationService.isVerified(userUuid, clientIp)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, "请先完成敏感操作验证"));
        }

        boolean success = totpService.disableTotp(user.getId());
        
        if (success) {
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "TOTP 禁用成功"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户未启用 TOTP"));
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
                .body(new ApiResponse<>(401, "未认证"));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户不存在"));
        }

        User user = userOpt.get();
        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已完成敏感操作验证
        if (!sensitiveOperationService.isVerified(userUuid, clientIp)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, "请先完成敏感操作验证"));
        }

        // 检查用户是否启用了 TOTP
        if (!totpService.isTotpEnabled(user.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "用户未启用 TOTP"));
        }

        try {
            byte[] masterKey = encryptionUtil.getMasterKey();
            String[] newCodes = totpService.regenerateRecoveryCodes(user.getId(), masterKey);
            
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "恢复码已重新生成", newCodes));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "重新生成恢复码失败：" + e.getMessage()));
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
                .body(new ApiResponse<>(401, "未认证"));
        }

        String userUuid = (String) authentication.getPrincipal();
        Optional<User> userOpt = userService.findByUuid(userUuid);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户不存在"));
        }

        User user = userOpt.get();
        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已完成敏感操作验证
        if (!sensitiveOperationService.isVerified(userUuid, clientIp)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, "请先完成敏感操作验证"));
        }
        
        byte[] masterKey = encryptionUtil.getMasterKey();
        java.util.List<String> codes = totpService.getRecoveryCodes(user.getId(), masterKey);
        
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "获取回复码成功", codes));
    }
}
