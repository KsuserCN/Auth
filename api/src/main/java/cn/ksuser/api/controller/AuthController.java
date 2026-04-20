package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.entity.UserSettings;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.repository.UserSettingsRepository;
import cn.ksuser.api.service.TokenBlacklistService;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.EncryptionUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ksuser.api.service.MfaService;
import cn.ksuser.api.dto.MfaChallengeResponse;
import cn.ksuser.api.dto.MfaTotpVerifyRequest;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final long AVATAR_MAX_SIZE_BYTES = 3L * 1024 * 1024;
    private static final String AVATAR_STORAGE_DIR = "static/avatars";
    private static final String QR_TEXT_PREFIX = "KSUSER-AUTH-QR:";

    private final UserService userService;
    private final UserSessionService userSessionService;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;
    private final RateLimitService rateLimitService;
    private final SensitiveOperationService sensitiveOperationService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SecurityValidator securityValidator;
    private final AppProperties appProperties;
    private final PasskeyService passkeyService;
    private final UserSettingsRepository userSettingsRepository;
    private final TotpService totpService;
    private final EncryptionUtil encryptionUtil;
    private final MfaService mfaService;
    private final SensitiveLogUtil sensitiveLogUtil;
    private final SessionTransferService sessionTransferService;
    private final AccountRecoveryService accountRecoveryService;
    private final QrChallengeService qrChallengeService;

    public AuthController(UserService userService, UserSessionService userSessionService, JwtUtil jwtUtil,
                          EmailService emailService, VerificationCodeService verificationCodeService,
                          RateLimitService rateLimitService, SensitiveOperationService sensitiveOperationService,
                          TokenBlacklistService tokenBlacklistService, SecurityValidator securityValidator,
                          AppProperties appProperties, PasskeyService passkeyService,
                          TotpService totpService, EncryptionUtil encryptionUtil,
                          UserSettingsRepository userSettingsRepository, MfaService mfaService,
                          SensitiveLogUtil sensitiveLogUtil,
                          SessionTransferService sessionTransferService,
                          AccountRecoveryService accountRecoveryService,
                          QrChallengeService qrChallengeService) {
        this.userService = userService;
        this.userSessionService = userSessionService;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.verificationCodeService = verificationCodeService;
        this.rateLimitService = rateLimitService;
        this.sensitiveOperationService = sensitiveOperationService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.securityValidator = securityValidator;
        this.appProperties = appProperties;
        this.passkeyService = passkeyService;
        this.totpService = totpService;
        this.encryptionUtil = encryptionUtil;
        this.userSettingsRepository = userSettingsRepository;
        this.mfaService = mfaService;
        this.sensitiveLogUtil = sensitiveLogUtil;
        this.sessionTransferService = sessionTransferService;
        this.accountRecoveryService = accountRecoveryService;
        this.qrChallengeService = qrChallengeService;
    }

    /**
     * 健康检查/初始化接口（用于获取 CSRF Token）
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Void>> health(HttpServletRequest request, HttpServletResponse response) {
        writeCsrfToken(request, response);
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "服务正常"));
    }

    @GetMapping("/csrf-token")
    public ResponseEntity<ApiResponse<Map<String, String>>> csrfToken(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String token = writeCsrfToken(request, response);
        return ResponseEntity.ok(new ApiResponse<>(200, "获取成功", Map.of("csrfToken", token == null ? "" : token)));
    }

    /**
     * 检查用户名是否存在
     * @param username 用户名
     * @return ApiResponse
     */
    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<CheckUsernameResponse>> checkUsername(@RequestParam String username) {
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "用户名不能为空"));
        }

        boolean exists = userService.findByUsername(username).isPresent();
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, exists ? "用户名已存在" : "用户名可用",
                new CheckUsernameResponse(exists)));
    }

    /**
     * 发送验证码（用于注册、登录、更改邮箱或敏感操作验证）
     * @param request HttpServletRequest
     * @param sendCodeRequest 发送验证码请求
     * @return ApiResponse
     */
    @PostMapping("/send-code")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(HttpServletRequest request,
                                                                    @RequestBody SendCodeRequest sendCodeRequest,
                                                                    Authentication authentication) {
        String email = sendCodeRequest.getEmail();
        String type = sendCodeRequest.getType(); // "register"、"login"、"change-email" 或 "sensitive-verification"

        // 参数校验
        if (type == null || type.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "类型不能为空"));
        }
        if (!("register".equals(type) || "login".equals(type) || "change-email".equals(type) || "sensitive-verification".equals(type))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "类型只能是 register、login、change-email 或 sensitive-verification"));
        }

        // 敏感操作验证码：使用当前登录用户绑定邮箱（不从请求体读取）
        if ("sensitive-verification".equals(type)) {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "未登录"));
            }
            String uuid = authentication.getPrincipal().toString();
            User user = userService.findByUuid(uuid).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "用户不存在"));
            }
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "当前用户未绑定邮箱"));
            }
            email = user.getEmail();
        } else {
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "邮箱不能为空"));
            }
            if (!securityValidator.isValidEmail(email)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "邮箱格式不正确"));
            }
        }

        // 注册验证码：不检查邮箱是否已注册，在用户提交验证码后再检查
        
        // 登录验证码：不检查邮箱是否存在，在用户提交验证码后再检查
        
        // 更改邮箱验证码：检查邮箱是否已被使用
        if ("change-email".equals(type) && userService.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "邮箱已被使用"));
        }

        // 检查邮箱是否被锁定
        if (verificationCodeService.isLocked(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
        }

        // 获取IP
        String clientIp = rateLimitService.getClientIp(request);

        // 获取 User-Agent
        String userAgent = rateLimitService.getClientUserAgent(request);

        // 注册功能锁定检查（按 IP/UA）
        if ("register".equals(type) && rateLimitService.isRegisterLocked(clientIp, userAgent)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, "注册过于频繁，请稍后再试"));
        }

        // 检查IP限流（3次/分钟，14次/小时）
        if (!rateLimitService.isIpAllowed(clientIp)) {
            int remainingMinute = rateLimitService.getRemainingMinuteRequestsForIp(clientIp);
            if (remainingMinute == 0) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "发送过于频繁，请1分钟后再试"));
            } else {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "发送次数过多，每小时最多发送14次"));
            }
        }

        // 检查邮箱限流（1次/分钟，14次/小时）
        if (!rateLimitService.isEmailAllowed(email)) {
            int remainingMinute = rateLimitService.getRemainingMinuteRequestsForEmail(email);
            if (remainingMinute == 0) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "发送过于频繁，请1分钟后再试"));
            } else {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "该邮箱发送次数过多，每小时最多发送14次"));
            }
        }

        // 生成并保存验证码
        String code = verificationCodeService.generateCode();
        String action;
        if ("register".equals(type)) {
            action = "注册账号";
        } else if ("login".equals(type)) {
            action = "登录账号";
        } else if ("change-email".equals(type)) {
            action = "更改邮箱";
        } else {
            action = "敏感操作验证";
        }
        
        // 为敏感操作验证使用单独的 type 存储
        if ("sensitive-verification".equals(type)) {
            verificationCodeService.saveCodeWithType(email, code, clientIp, type);
        } else {
            verificationCodeService.saveCode(email, code, clientIp);
        }

        // 发送邮件
        try {
            emailService.sendVerificationCode(email, code, action);
        } catch (MessagingException | UnsupportedEncodingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "邮件发送失败，请稍后重试"));
        }

        // 记录限流
        rateLimitService.recordIpRequest(clientIp);
        rateLimitService.recordEmailRequest(email);

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "验证码已发送"));
    }

    /**
     * 注册接口
     * @param registerRequest 注册请求
     * @param request HttpServletRequest
     * @return ApiResponse
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@RequestBody RegisterRequest registerRequest,
                                                                    HttpServletRequest request,
                                                                    HttpServletResponse response) {
        long startTime = System.currentTimeMillis();
        String username = registerRequest.getUsername();
        String email = registerRequest.getEmail();
        String password = registerRequest.getPassword();
        String code = registerRequest.getCode();
        String clientIp = rateLimitService.getClientIp(request);

        String userAgent = rateLimitService.getClientUserAgent(request);

        // 注册功能锁定检查（按 IP/UA）
        if (rateLimitService.isRegisterLocked(clientIp, userAgent)) {
            sensitiveLogUtil.logRegister(request, null, false, "Registration locked", startTime);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, "注册过于频繁，请稍后再试"));
        }

        // 参数校验
        if (username == null || username.trim().isEmpty()) {
            sensitiveLogUtil.logRegister(request, null, false, "Empty username", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "用户名不能为空"));
        }
        if (email == null || email.trim().isEmpty()) {
            sensitiveLogUtil.logRegister(request, null, false, "Empty email", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不能为空"));
        }
        if (!securityValidator.isValidEmail(email)) {
            sensitiveLogUtil.logRegister(request, null, false, "Invalid email format", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱格式不正确"));
        }
        if (password == null || password.trim().isEmpty()) {
            sensitiveLogUtil.logRegister(request, null, false, "Empty password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码不能为空"));
        }
        if (code == null || code.trim().isEmpty()) {
            sensitiveLogUtil.logRegister(request, null, false, "Empty verification code", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证码不能为空"));
        }

        // 使用 SecurityValidator 进行安全验证
        if (!securityValidator.isValidUsername(username)) {
            sensitiveLogUtil.logRegister(request, null, false, "Invalid username format", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "用户名格式不正确（3-20字符，仅字母数字下划线连字符）"));
        }

        if (!securityValidator.isValidEmail(email)) {
            sensitiveLogUtil.logRegister(request, null, false, "Invalid email format", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱格式不正确"));
        }

        if (!securityValidator.isValidPasswordLength(password)) {
            sensitiveLogUtil.logRegister(request, null, false, "Invalid password length", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码长度必须在" + appProperties.getPassword().getMinLength() + 
                    "-" + appProperties.getPassword().getMaxLength() + "个字符之间"));
        }

        if (!securityValidator.isStrongPassword(password)) {
            sensitiveLogUtil.logRegister(request, null, false, "Weak password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, buildPasswordRequirementMessage()));
        }

        if (securityValidator.isCommonWeakPassword(password)) {
            sensitiveLogUtil.logRegister(request, null, false, "Common weak password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码过于简单，请使用更复杂的密码"));
        }

        // 检查 SQL 注入
        if (securityValidator.possibleSqlInjection(username) || 
            securityValidator.possibleSqlInjection(email)) {
            sensitiveLogUtil.logRegister(request, null, false, "SQL injection detected", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "输入包含非法字符"));
        }

        // 验证验证码
        int verifyResult = verificationCodeService.verifyCode(email, code, clientIp);
        if (verifyResult != 0) {
            // 0 = 成功，1 = 已过期，2 = 错误，3 = 被锁定，4 = 未发送，5 = 邮箱不匹配，6 = IP不匹配
            if (verifyResult == 3) {
                sensitiveLogUtil.logRegister(request, null, false, "Verification code locked", startTime);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
            } else if (verifyResult == 4) {
                sensitiveLogUtil.logRegister(request, null, false, "No verification code sent", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "请先获取验证码"));
            } else if (verifyResult == 5) {
                int errorCount = verificationCodeService.getErrorCount(email);
                sensitiveLogUtil.logRegister(request, null, false, "Email mismatch", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "邮箱不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 6) {
                int errorCount = verificationCodeService.getErrorCount(email);
                sensitiveLogUtil.logRegister(request, null, false, "Device mismatch", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "发送验证码的设备与当前设备不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 1) {
                // 验证码已过期，但需要检查是否因错误次数导致锁定
                if (verificationCodeService.isLocked(email)) {
                    sensitiveLogUtil.logRegister(request, null, false, "Verification code locked", startTime);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(email);
                sensitiveLogUtil.logRegister(request, null, false, "Verification code expired", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码已过期，请重新获取（" + errorCount + "/5）"));
            } else {
                if (verificationCodeService.isLocked(email)) {
                    sensitiveLogUtil.logRegister(request, null, false, "Verification code locked", startTime);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(email);
                sensitiveLogUtil.logRegister(request, null, false, "Invalid verification code", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码错误（" + errorCount + "/5）"));
            }
        }

        // 验证码验证成功后，检查邮箱是否已注册
        if (userService.findByEmail(email).isPresent()) {
            sensitiveLogUtil.logRegister(request, null, false, "Email already registered", startTime);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "邮箱已被注册"));
        }

        // 执行注册
        RegisterResult result = userService.register(username, email, password);
        if (result.getStatus() == RegisterResult.Status.USERNAME_EXISTS) {
            sensitiveLogUtil.logRegister(request, null, false, "Username already exists", startTime);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "用户名已存在"));
        }
        if (result.getStatus() == RegisterResult.Status.BAD_REQUEST) {
            sensitiveLogUtil.logRegister(request, null, false, "Invalid key parameter", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "key 不支持"));
        }
        if (result.getStatus() == RegisterResult.Status.EMAIL_EXISTS) {
            sensitiveLogUtil.logRegister(request, null, false, "Email already exists", startTime);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "邮箱已存在"));
        }

        // 为新用户初始化默认 settings，避免后续首次设置时出现数据缺失带来的不稳定行为
        getOrCreateUserSettings(result.getUser().getId());

        // 生成 Token
        String refreshToken = jwtUtil.generateRefreshToken(result.getUser().getUuid());

        // 保存会话到数据库
        UserSession session = userSessionService.createSession(result.getUser(), refreshToken, clientIp, userAgent);
        int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
        String accessToken = jwtUtil.generateAccessToken(result.getUser().getUuid(), session.getId(), sessionVersion);

        // 将 RefreshToken 设置到 HttpOnly Cookie (包含 SameSite=Strict CSRF 保护)
        setRefreshTokenCookie(response, refreshToken);

        // 记录注册成功次数（按 IP/UA）
        rateLimitService.recordRegisterSuccess(clientIp, userAgent);

        // 记录注册成功日志
        sensitiveLogUtil.logRegister(request, result.getUser().getId(), true, null, startTime);

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "注册成功", RegisterResponse.fromUserWithToken(result.getUser(), accessToken)));
    }

    /**
     * 验证码登录接口
     * @param loginCodeRequest 验证码登录请求
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @PostMapping("/login-with-code")
    public ResponseEntity<ApiResponse<Object>> loginWithCode(@RequestBody LoginCodeRequest loginCodeRequest,
                                                                     HttpServletRequest request,
                                                                     HttpServletResponse response) {
        long startTime = System.currentTimeMillis();
        String email = loginCodeRequest.getEmail();
        String code = loginCodeRequest.getCode();
        String clientIp = rateLimitService.getClientIp(request);

        // 参数校验
        if (email == null || email.trim().isEmpty()) {
            sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Empty email", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不能为空"));
        }
        if (code == null || code.trim().isEmpty()) {
            sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Empty code", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证码不能为空"));
        }

        // 邮箱格式验证
        if (!securityValidator.isValidEmail(email)) {
            sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Invalid email format", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱格式不正确"));
        }

        // 验证验证码
        int verifyResult = verificationCodeService.verifyCode(email, code, clientIp);
        if (verifyResult != 0) {
            // 0 = 成功，1 = 已过期，2 = 错误，3 = 被锁定，4 = 未发送，5 = 邮箱不匹配，6 = IP不匹配
            if (verifyResult == 3) {
                sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Verification code locked", startTime);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
            } else if (verifyResult == 4) {
                sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "No code sent", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "请先获取验证码"));
            } else if (verifyResult == 5) {
                int errorCount = verificationCodeService.getErrorCount(email);
                sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Email mismatch", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "邮箱不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 6) {
                int errorCount = verificationCodeService.getErrorCount(email);
                sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Device mismatch", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "发送验证码的设备与当前设备不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 1) {
                // 验证码已过期，但需要检查是否因错误次数导致锁定
                if (verificationCodeService.isLocked(email)) {
                    sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Verification code locked", startTime);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(email);
                sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Code expired", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码已过期，请重新获取（" + errorCount + "/5）"));
            } else {
                if (verificationCodeService.isLocked(email)) {
                    sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Verification code locked", startTime);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(email);
                sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "Invalid code", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码错误（" + errorCount + "/5）"));
            }
        }

        // 查找用户
        User user = userService.findByEmail(email).orElse(null);
        if (user == null) {
            sensitiveLogUtil.logLogin(request, null, "EMAIL_CODE", false, "User not found", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "邮箱未注册或验证码错误"));
        }
        // 如果用户启用了 MFA，则进入 MFA 流程（可选 TOTP/Passkey）
        UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
        boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
        List<String> mfaMethods = resolveMfaMethods(user, true);
        if (mfaEnabled && !mfaMethods.isEmpty()) {
            String userAgent = request.getHeader("User-Agent");
            String challengeId = mfaService.createChallenge(user.getId(), clientIp, userAgent,
                    "email", new HashSet<>(mfaMethods));
            // 不创建会话、不返回 token，告知前端需要 TOTP 验证
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "需要 MFA 验证",
                        new MfaChallengeResponse(challengeId, mfaMethods.get(0), mfaMethods)));
        }

        // 生成 Token
        String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());

        // 保存会话到数据库
        String userAgent = request.getHeader("User-Agent");
        UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
        int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
        String accessToken = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

        // 将 RefreshToken 设置到 HttpOnly Cookie (包含 SameSite=Strict CSRF 保护)
        setRefreshTokenCookie(response, refreshToken);

        // 登录成功后清除所有速率限制
        rateLimitService.clearAllLimits(email, clientIp);

        // 记录成功登录日志
        sensitiveLogUtil.logLogin(request, user.getId(), "EMAIL_CODE", true, null, startTime);

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "登录成功", new TokenResponse(accessToken)));
    }

    /**
     * 登录接口
     * @param loginRequest 登录请求
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Object>> login(@RequestBody LoginRequest loginRequest, 
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        long startTime = System.currentTimeMillis();
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();
        String clientIp = rateLimitService.getClientIp(request);

        // 参数校验
        if (email == null || email.trim().isEmpty()) {
            sensitiveLogUtil.logLogin(request, null, "PASSWORD", false, "Empty email", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不能为空"));
        }
        if (password == null || password.trim().isEmpty()) {
            sensitiveLogUtil.logLogin(request, null, "PASSWORD", false, "Empty password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码不能为空"));
        }

        // 速率限制检查
        if (!rateLimitService.isEmailAllowed(email, RateLimitService.TYPE_LOGIN)) {
            sensitiveLogUtil.logLogin(request, null, "PASSWORD", false, "Rate limit exceeded", startTime);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, "登录请求过于频繁，请稍后再试"));
        }
        if (!rateLimitService.isIpAllowed(clientIp, RateLimitService.TYPE_LOGIN)) {
            sensitiveLogUtil.logLogin(request, null, "PASSWORD", false, "Rate limit exceeded", startTime);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, "登录请求过于频繁，请稍后再试"));
        }

        // 记录请求
        rateLimitService.recordEmailRequest(email, RateLimitService.TYPE_LOGIN);
        rateLimitService.recordIpRequest(clientIp, RateLimitService.TYPE_LOGIN);

        // 执行登录
        User user = userService.login(email, password).orElse(null);
        if (user == null) {
            sensitiveLogUtil.logLogin(request, null, "PASSWORD", false, "Invalid email or password", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "邮箱或密码错误"));
        }
        // 如果用户启用了 MFA，则进入 MFA 流程（可选 TOTP/Passkey）
        UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
        boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
        List<String> mfaMethods = resolveMfaMethods(user, true);
        if (mfaEnabled && !mfaMethods.isEmpty()) {
            String userAgent = request.getHeader("User-Agent");
            String challengeId = mfaService.createChallenge(user.getId(), clientIp, userAgent,
                    "password", new HashSet<>(mfaMethods));
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "需要 MFA 验证",
                        new MfaChallengeResponse(challengeId, mfaMethods.get(0), mfaMethods)));
        }

        // 生成 Token
        String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());

        // 保存会话到数据库
        String userAgent = request.getHeader("User-Agent");
        UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
        int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
        String accessToken = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

        // 将 RefreshToken 设置到 HttpOnly Cookie (包含 SameSite=Strict CSRF 保护)
        setRefreshTokenCookie(response, refreshToken);

        // 记录成功登录日志
        sensitiveLogUtil.logLogin(request, user.getId(), "PASSWORD", true, null, startTime);

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "登录成功", new TokenResponse(accessToken)));
    }

    /**
     * 获取当前用户信息
     * @param authentication 认证信息
     * @return ApiResponse
     */
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<UserInfoResponse>> info(@RequestParam(value = "type", required = false, defaultValue = "basic") String type,
                                                              Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        UserSettingsResponse settingsResponse = buildUserSettingsResponse(user.getId());

        UserInfoResponse userInfo;
        if ("details".equalsIgnoreCase(type)) {
            userInfo = new UserInfoResponse(
                user.getUuid(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRealName(),
                user.getGender(),
                user.getBirthDate(),
                user.getRegion(),
                user.getBio(),
                user.getVerificationType(),
                user.getUpdatedAt(),
                settingsResponse
            );
        } else {
            userInfo = new UserInfoResponse(
                user.getUuid(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getVerificationType(),
                settingsResponse
            );
        }

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "获取成功", userInfo));
    }

    /**
     * 更新用户设置
    * @param updateUserSettingRequest 更新请求（支持 bool 与字符串偏好）
     * @param authentication 认证信息
     * @return ApiResponse
     */
    @PostMapping("/update/setting")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> updateSetting(@RequestBody UpdateUserSettingRequest updateUserSettingRequest,
                                                                            Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        if (updateUserSettingRequest == null || updateUserSettingRequest.getField() == null
            || updateUserSettingRequest.getField().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "字段名不能为空"));
        }

        String field = updateUserSettingRequest.getField().trim();

        boolean isBooleanField = "mfa_enabled".equals(field) || "mfaEnabled".equals(field)
                || "detect_unusual_login".equals(field) || "detectUnusualLogin".equals(field)
                || "notify_sensitive_action_email".equals(field) || "notifySensitiveActionEmail".equals(field)
                || "subscribe_news_email".equals(field) || "subscribeNewsEmail".equals(field);
        boolean isPreferenceField = "preferred_mfa_method".equals(field) || "preferredMfaMethod".equals(field)
                || "preferred_sensitive_method".equals(field) || "preferredSensitiveMethod".equals(field);

        if (!isBooleanField && !isPreferenceField) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "不支持的字段名"));
        }

        if (isBooleanField && updateUserSettingRequest.getValue() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "布尔字段 value 不能为空"));
        }

        if (isPreferenceField && (updateUserSettingRequest.getStringValue() == null
                || updateUserSettingRequest.getStringValue().trim().isEmpty())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "偏好字段 stringValue 不能为空"));
        }

        UserSettings settings = getOrCreateUserSettings(user.getId());

        switch (field) {
            case "mfa_enabled":
            case "mfaEnabled":
                settings.setMfaEnabled(updateUserSettingRequest.getValue());
                break;
            case "detect_unusual_login":
            case "detectUnusualLogin":
                settings.setDetectUnusualLogin(updateUserSettingRequest.getValue());
                break;
            case "notify_sensitive_action_email":
            case "notifySensitiveActionEmail":
                settings.setNotifySensitiveActionEmail(updateUserSettingRequest.getValue());
                break;
            case "subscribe_news_email":
            case "subscribeNewsEmail":
                settings.setSubscribeNewsEmail(updateUserSettingRequest.getValue());
                break;
            case "preferred_mfa_method":
            case "preferredMfaMethod": {
                if (!Boolean.TRUE.equals(settings.getMfaEnabled())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "请先启用 MFA 再设置登录MFA优先方式"));
                }
                String preferredMfaMethod = updateUserSettingRequest.getStringValue().trim().toLowerCase(Locale.ROOT);
                if (!("totp".equals(preferredMfaMethod) || "passkey".equals(preferredMfaMethod))) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "登录MFA优先方式只能是 totp 或 passkey"));
                }
                List<String> availableMfaMethods = resolveMfaMethods(user, true);
                if (!availableMfaMethods.contains(preferredMfaMethod)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "当前用户未启用所选 MFA 方式"));
                }
                settings.setPreferredMfaMethod(preferredMfaMethod);
                break;
            }
            case "preferred_sensitive_method":
            case "preferredSensitiveMethod": {
                String preferredSensitiveMethod = normalizeSensitiveMethod(updateUserSettingRequest.getStringValue());
                if (preferredSensitiveMethod == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "敏感验证优先方式只能是 password、email-code、passkey 或 totp"));
                }
                List<String> availableSensitiveMethods = resolveSensitiveMethods(user);
                if (!availableSensitiveMethods.contains(preferredSensitiveMethod)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "当前用户不可用所选敏感验证方式"));
                }
                settings.setPreferredSensitiveMethod(preferredSensitiveMethod);
                break;
            }
            default:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "不支持的字段名"));
        }

        UserSettings saved = userSettingsRepository.save(settings);
        UserSettingsResponse response = new UserSettingsResponse(
            Boolean.TRUE.equals(saved.getMfaEnabled()),
            Boolean.TRUE.equals(saved.getDetectUnusualLogin()),
            Boolean.TRUE.equals(saved.getNotifySensitiveActionEmail()),
            Boolean.TRUE.equals(saved.getSubscribeNewsEmail()),
            normalizeMfaPreference(saved.getPreferredMfaMethod()),
            normalizeSensitiveMethod(saved.getPreferredSensitiveMethod())
        );

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "更新成功", response));
    }

    private UserSettingsResponse buildUserSettingsResponse(Long userId) {
        return userSettingsRepository.findByUserId(userId)
            .map(settings -> new UserSettingsResponse(
                Boolean.TRUE.equals(settings.getMfaEnabled()),
                Boolean.TRUE.equals(settings.getDetectUnusualLogin()),
                Boolean.TRUE.equals(settings.getNotifySensitiveActionEmail()),
                Boolean.TRUE.equals(settings.getSubscribeNewsEmail()),
                normalizeMfaPreference(settings.getPreferredMfaMethod()),
                normalizeSensitiveMethod(settings.getPreferredSensitiveMethod())
            ))
            .orElseGet(() -> new UserSettingsResponse(false, true, true, false, "totp", "password"));
    }

    private UserSettings getOrCreateUserSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserSettings settings = new UserSettings();
                settings.setUserId(userId);
                settings.setMfaEnabled(false);
                settings.setDetectUnusualLogin(true);
                settings.setNotifySensitiveActionEmail(true);
                settings.setSubscribeNewsEmail(false);
                settings.setPreferredMfaMethod("totp");
                settings.setPreferredSensitiveMethod("password");
                return userSettingsRepository.save(settings);
            });
    }

    /**
     * 刷新 AccessToken
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(HttpServletRequest request, HttpServletResponse response) {
        // 从 Cookie 中获取 RefreshToken
        String oldRefreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    oldRefreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (oldRefreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken不存在"));
        }

        // 检查旧RefreshToken是否已在黑名单中
        if (tokenBlacklistService.isBlacklisted(oldRefreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken已失效"));
        }

        // 验证 Token 类型是否为 refresh
        String tokenType = jwtUtil.getTokenType(oldRefreshToken);
        if (tokenType == null || !tokenType.equals("refresh")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "Token类型无效，应为RefreshToken"));
        }

        // 从 Token 中获取 uuid
        String uuid = jwtUtil.getUuidFromToken(oldRefreshToken);
        if (uuid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken无效或已过期"));
        }

        // 查找用户
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken无效或已过期"));
        }

        // 验证 RefreshToken
        UserSession session = userSessionService.verifyRefreshToken(user, oldRefreshToken).orElse(null);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken无效或已过期"));
        }

        String clientIp = rateLimitService.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        userSessionService.updateSessionActivity(session, clientIp, userAgent);

        // 生成新的 RefreshToken
        String newRefreshToken = jwtUtil.generateRefreshToken(uuid);

        // 刷新 sessionVersion，使旧 AccessToken 立即失效
        UserSession updatedSession = userSessionService.bumpSessionVersion(session);
        int newSessionVersion = updatedSession.getSessionVersion() == null ? 0 : updatedSession.getSessionVersion();

        // 更新数据库中的 RefreshToken
        userSessionService.updateRefreshToken(updatedSession, newRefreshToken);

        // 将旧的 RefreshToken 加入黑名单（7天过期）
        tokenBlacklistService.addToBlacklist(oldRefreshToken, Duration.ofDays(7));

        // 生成新的 AccessToken
        String newAccessToken = jwtUtil.generateAccessToken(uuid, updatedSession.getId(), newSessionVersion);

        // 将新的 RefreshToken 设置到 HttpOnly Cookie (包含 SameSite=Strict CSRF 保护)
        setRefreshTokenCookie(response, newRefreshToken);

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "刷新成功", new RefreshResponse(newAccessToken)));
    }

    /**
     * 创建一次性跨端登录票据
     */
    @PostMapping("/session-transfer/create")
    public ResponseEntity<ApiResponse<SessionTransferResponse>> createSessionTransfer(
            @RequestBody(required = false) SessionTransferCreateRequest requestBody,
            Authentication authentication,
            HttpServletRequest request) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        String target = requestBody == null ? null : requestBody.getTarget();
        String purpose = requestBody == null ? null : requestBody.getPurpose();
        String clientIp = rateLimitService.getClientIp(request);
        String userAgent = rateLimitService.getClientUserAgent(request);
        try {
            SessionTransferService.SessionTransferPayload payload =
                sessionTransferService.createTransfer(user.getId(), target, purpose, clientIp, userAgent);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "跨端票据已创建",
                    new SessionTransferResponse(payload.getTransferCode(), SessionTransferService.DEFAULT_TTL_SECONDS)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "跨端票据创建失败"));
        }
    }

    /**
     * 兑换一次性跨端登录票据，目标端会获得自己的完整会话
     */
    @PostMapping("/session-transfer/exchange")
    public ResponseEntity<ApiResponse<TokenResponse>> exchangeSessionTransfer(
            @RequestBody SessionTransferExchangeRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        long startTime = System.currentTimeMillis();

        if (requestBody == null || requestBody.getTransferCode() == null || requestBody.getTransferCode().isBlank()) {
            sensitiveLogUtil.logLogin(request, null, "BRIDGE", false, "empty_transfer_code", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "transferCode 不能为空"));
        }

        if (requestBody.getTarget() == null || requestBody.getTarget().isBlank()) {
            sensitiveLogUtil.logLogin(request, null, "BRIDGE", false, "empty_transfer_target", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "target 不能为空"));
        }

        try {
            SessionTransferService.SessionTransferPayload payload =
                sessionTransferService.consumeTransfer(requestBody.getTransferCode(), requestBody.getTarget());
            String bridgeLoginMethod = resolveBridgeLoginMethod(requestBody.getTarget());
            User user = userService.findById(payload.getUserId()).orElse(null);
            if (user == null) {
                if (payload.shouldAuditAsBridgeLogin()) {
                    sensitiveLogUtil.logLogin(request, null, bridgeLoginMethod, false, "user_not_found", startTime);
                }
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "用户不存在"));
            }

            String clientIp = rateLimitService.getClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            TokenResponse tokenResponse = issueSessionToken(user, clientIp, userAgent, response);
            if (payload.shouldAuditAsBridgeLogin()) {
                sensitiveLogUtil.logLogin(request, user.getId(), bridgeLoginMethod, true, null, startTime);
            }

            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "跨端登录成功", tokenResponse));
        } catch (IllegalArgumentException e) {
            sensitiveLogUtil.logLogin(request, null, "BRIDGE", false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage()));
        } catch (IllegalStateException e) {
            sensitiveLogUtil.logLogin(request, null, "BRIDGE", false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "跨端登录失败"));
        }
    }

    /**
     * 由已登录设备创建一次性账号恢复授权
     */
    @PostMapping("/account-recovery/issue")
    public ResponseEntity<ApiResponse<AccountRecoveryIssueResponse>> issueAccountRecovery(
            Authentication authentication,
            HttpServletRequest request) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        String clientIp = rateLimitService.getClientIp(request);
        if (!sensitiveOperationService.isVerified(uuid, clientIp)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, "请先完成敏感操作验证"));
        }

        Long sponsorSessionId = getCurrentSessionId(request);
        UserSession sponsorSession = sponsorSessionId == null
            ? null
            : userSessionService.findActiveSessionById(sponsorSessionId).orElse(null);
        if (sponsorSession == null || !user.getId().equals(sponsorSession.getUser().getId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "当前登录会话无效，请重新登录后再试"));
        }

        String userAgent = rateLimitService.getClientUserAgent(request);
        try {
            AccountRecoveryService.AccountRecoveryPayload payload =
                accountRecoveryService.createAuthorization(user, sponsorSession.getId(), clientIp, userAgent);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "恢复授权已生成",
                    new AccountRecoveryIssueResponse(
                        payload.getRecoveryCode(),
                        AccountRecoveryService.DEFAULT_TTL_SECONDS,
                        payload.getUsername(),
                        payload.getMaskedEmail(),
                        payload.getSponsorClientName(),
                        payload.getSponsorBrowser(),
                        payload.getSponsorSystem(),
                        payload.getSponsorIpLocation()
                    )));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "恢复授权生成失败"));
        }
    }

    /**
     * 查询账号恢复授权状态
     */
    @GetMapping("/account-recovery/status")
    public ResponseEntity<ApiResponse<AccountRecoveryStatusResponse>> getAccountRecoveryStatus(
            @RequestParam String recoveryCode) {
        if (recoveryCode == null || recoveryCode.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "recoveryCode 不能为空"));
        }

        AccountRecoveryService.AccountRecoveryPayload payload =
            accountRecoveryService.getAuthorization(recoveryCode);
        if (payload == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "恢复授权不存在、已失效或已被使用"));
        }

        User user = userService.findById(payload.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户不存在"));
        }

        if (!isAccountRecoverySponsorSessionActive(payload, user)) {
            return ResponseEntity.status(HttpStatus.GONE)
                .body(new ApiResponse<>(410, "发起恢复授权的设备已退出登录，请重新发起"));
        }

        long remainingSeconds = accountRecoveryService.getRemainingSeconds(recoveryCode);
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "查询成功",
                new AccountRecoveryStatusResponse(
                    remainingSeconds > 0 ? remainingSeconds : 0,
                    payload.getUsername(),
                    payload.getMaskedEmail(),
                    payload.getSponsorClientName(),
                    payload.getSponsorBrowser(),
                    payload.getSponsorSystem(),
                    payload.getSponsorIpLocation()
                )));
    }

    /**
     * 使用已背书的恢复授权完成密码重置并签发新会话
     */
    @PostMapping("/account-recovery/complete")
    public ResponseEntity<ApiResponse<TokenResponse>> completeAccountRecovery(
            @RequestBody AccountRecoveryCompleteRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        long startTime = System.currentTimeMillis();

        if (requestBody == null || requestBody.getRecoveryCode() == null || requestBody.getRecoveryCode().isBlank()) {
            sensitiveLogUtil.logChangePassword(request, null, false, "empty_recovery_code", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "recoveryCode 不能为空"));
        }

        String newPassword = requestBody.getNewPassword();
        if (newPassword == null || newPassword.trim().isEmpty()) {
            sensitiveLogUtil.logChangePassword(request, null, false, "empty_new_password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "新密码不能为空"));
        }

        AccountRecoveryService.AccountRecoveryPayload previewPayload =
            accountRecoveryService.getAuthorization(requestBody.getRecoveryCode());
        if (previewPayload == null) {
            sensitiveLogUtil.logChangePassword(request, null, false, "recovery_authorization_missing", startTime);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "恢复授权不存在、已失效或已被使用"));
        }

        User user = userService.findById(previewPayload.getUserId()).orElse(null);
        if (user == null) {
            sensitiveLogUtil.logChangePassword(request, null, false, "user_not_found", startTime);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "用户不存在"));
        }

        if (!isAccountRecoverySponsorSessionActive(previewPayload, user)) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "sponsor_session_inactive", startTime);
            return ResponseEntity.status(HttpStatus.GONE)
                .body(new ApiResponse<>(410, "发起恢复授权的设备已退出登录，请重新发起"));
        }

        if (!securityValidator.isValidPasswordLength(newPassword)) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "invalid_password_length", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码长度必须在" + appProperties.getPassword().getMinLength() +
                    "-" + appProperties.getPassword().getMaxLength() + "个字符之间"));
        }

        if (!securityValidator.isStrongPassword(newPassword)) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "weak_password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, buildPasswordRequirementMessage()));
        }

        if (securityValidator.isCommonWeakPassword(newPassword)) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "common_weak_password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码过于简单，请使用更复杂的密码"));
        }

        try {
            accountRecoveryService.consumeAuthorization(requestBody.getRecoveryCode());
        } catch (IllegalArgumentException e) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "recovery_authorization_missing", startTime);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, e.getMessage()));
        } catch (IllegalStateException e) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "recovery_authorization_invalid", startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "恢复授权读取失败"));
        }

        userService.updatePassword(user, newPassword);
        userSessionService.revokeAllSessions(user);
        sensitiveOperationService.clearVerification(user.getUuid());

        String clientIp = rateLimitService.getClientIp(request);
        String userAgent = rateLimitService.getClientUserAgent(request);
        TokenResponse tokenResponse = issueSessionToken(user, clientIp, userAgent, response);

        sensitiveLogUtil.logChangePassword(request, user.getId(), true, null, startTime);
        sensitiveLogUtil.logLogin(request, user.getId(), "ACCOUNT_RECOVERY", true, null, startTime);

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "账号恢复成功", tokenResponse));
    }


    @PostMapping("/qr/login/init")
    public ResponseEntity<ApiResponse<QrChallengeInitResponse>> initQrLogin(
            @RequestParam(required = false) String target,
            HttpServletRequest request) {
        String webIp = rateLimitService.getClientIp(request);
        String userAgent = rateLimitService.getClientUserAgent(request);
        String normalizedTarget;
        if (target == null || target.isBlank()) {
            normalizedTarget = SessionTransferService.TARGET_WEB;
        } else {
            normalizedTarget = sessionTransferService.normalizeTarget(target);
            if (normalizedTarget == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "target 只能是 web、desktop 或 mobile"));
            }
        }
        QrChallengeService.QrChallengePayload payload =
            qrChallengeService.createChallenge(
                QrChallengeService.ChallengeType.LOGIN,
                null,
                null,
                normalizedTarget,
                webIp,
                userAgent
            );
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "二维码挑战已创建", toQrInitResponse(payload)));
    }

    @PostMapping("/qr/mfa/init")
    public ResponseEntity<ApiResponse<QrChallengeInitResponse>> initQrMfa(
            @RequestBody QrMfaInitRequest requestBody,
            HttpServletRequest request) {
        if (requestBody == null || requestBody.getMfaChallengeId() == null || requestBody.getMfaChallengeId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "mfaChallengeId 不能为空"));
        }

        String mfaChallengeId = requestBody.getMfaChallengeId().trim();
        String clientIp = rateLimitService.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        Long userId = mfaService.verifyChallenge(mfaChallengeId, clientIp, userAgent);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "mfaChallengeId 无效或已过期"));
        }
        if (!mfaService.isMethodAllowed(mfaChallengeId, "qr")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "当前 challenge 不允许使用扫码验证"));
        }

        QrChallengeService.QrChallengePayload payload =
            qrChallengeService.createChallenge(
                QrChallengeService.ChallengeType.MFA,
                userId,
                mfaChallengeId,
                SessionTransferService.TARGET_WEB,
                clientIp,
                userAgent
            );
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "二维码挑战已创建", toQrInitResponse(payload)));
    }

    @PostMapping("/qr/sensitive/init")
    public ResponseEntity<ApiResponse<QrChallengeInitResponse>> initQrSensitive(
            Authentication authentication,
            HttpServletRequest request) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        String webIp = rateLimitService.getClientIp(request);
        String userAgent = rateLimitService.getClientUserAgent(request);
        QrChallengeService.QrChallengePayload payload = qrChallengeService.createChallenge(
            QrChallengeService.ChallengeType.SENSITIVE,
            user.getId(),
            null,
            SessionTransferService.TARGET_WEB,
            webIp,
            userAgent
        );
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "二维码挑战已创建", toQrInitResponse(payload)));
    }

    @GetMapping("/qr/preview")
    public ResponseEntity<ApiResponse<QrScanPreviewResponse>> getQrScanPreview(
            @RequestParam(required = false) String approveCode,
            @RequestParam(required = false) String transferCode) {
        if ((approveCode == null || approveCode.isBlank()) && (transferCode == null || transferCode.isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "approveCode 或 transferCode 不能为空"));
        }

        if (approveCode != null && !approveCode.isBlank()) {
            QrChallengeService.QrChallengePayload challenge = qrChallengeService.getByApproveCode(approveCode.trim());
            if (challenge == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "二维码挑战不存在或已过期"));
            }
            long expiresInSeconds = qrChallengeService.getRemainingSeconds(challenge.getChallengeId());
            QrScanPreviewResponse response = new QrScanPreviewResponse(
                "approve_" + (challenge.getType() == null ? "login" : challenge.getType()),
                challenge.getClientName(),
                challenge.getBrowser(),
                challenge.getSystem(),
                challenge.getWebIp(),
                challenge.getIpLocation(),
                expiresInSeconds > 0 ? expiresInSeconds : 0L
            );
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "获取成功", response));
        }

        SessionTransferService.SessionTransferPayload payload = sessionTransferService.getTransfer(transferCode.trim());
        if (payload == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "跨端票据不存在或已过期"));
        }
        long expiresInSeconds = sessionTransferService.getRemainingSeconds(transferCode.trim());
        QrScanPreviewResponse response = new QrScanPreviewResponse(
            "transfer",
            payload.getRequesterClientName(),
            payload.getRequesterBrowser(),
            payload.getRequesterSystem(),
            payload.getRequesterIp(),
            payload.getRequesterIpLocation(),
            expiresInSeconds > 0 ? expiresInSeconds : 0L
        );
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "获取成功", response));
    }

    @PostMapping("/qr/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveQrChallenge(
            @RequestBody QrApproveRequest requestBody,
            Authentication authentication,
            HttpServletRequest request) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }
        if (requestBody == null || requestBody.getApproveCode() == null || requestBody.getApproveCode().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "approveCode 不能为空"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        QrChallengeService.QrChallengePayload challenge =
            qrChallengeService.getByApproveCode(requestBody.getApproveCode().trim());
        if (challenge == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "二维码挑战不存在或已过期"));
        }
        if (!QrChallengeService.ChallengeStatus.PENDING.value().equals(challenge.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "二维码挑战状态不可用"));
        }

        QrChallengeService.ChallengeType challengeType = QrChallengeService.ChallengeType.fromValue(challenge.getType());
        if (challengeType == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "二维码挑战类型无效"));
        }

        Map<String, Object> result = new HashMap<>();
        try {
            if (challengeType == QrChallengeService.ChallengeType.LOGIN) {
                UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
                boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
                if (mfaEnabled) {
                    List<String> followUpMethods = resolveMfaMethods(user, true, false);
                    if (followUpMethods.isEmpty()) {
                        qrChallengeService.rejectChallenge(challenge.getChallengeId(), user.getId(),
                            Map.of("reason", "未配置可用的非扫码 MFA 方式"));
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new ApiResponse<>(400, "当前账号未配置可用的非扫码 MFA 方式"));
                    }

                    String mfaChallengeId = mfaService.createChallenge(
                        user.getId(),
                        challenge.getWebIp(),
                        "qr-login",
                        "qr",
                        new HashSet<>(followUpMethods)
                    );
                    result.put("mfaChallengeId", mfaChallengeId);
                    result.put("method", followUpMethods.get(0));
                    result.put("methods", followUpMethods);
                } else {
                    String challengeTarget = sessionTransferService.normalizeTarget(challenge.getTarget());
                    if (challengeTarget == null) {
                        challengeTarget = SessionTransferService.TARGET_WEB;
                    }
                    SessionTransferService.SessionTransferPayload transferPayload = sessionTransferService.createTransfer(
                        user.getId(),
                        challengeTarget,
                        SessionTransferService.PURPOSE_BRIDGE_LOGIN,
                        challenge.getWebIp(),
                        challenge.getUserAgent()
                    );
                    result.put("transferCode", transferPayload.getTransferCode());
                }
            } else if (challengeType == QrChallengeService.ChallengeType.MFA) {
                if (challenge.getRequestedUserId() == null || !challenge.getRequestedUserId().equals(user.getId())) {
                    qrChallengeService.rejectChallenge(challenge.getChallengeId(), user.getId(),
                        Map.of("reason", "二维码挑战用户不匹配"));
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(403, "二维码挑战用户不匹配"));
                }
                if (challenge.getMfaChallengeId() == null || challenge.getMfaChallengeId().isBlank()) {
                    qrChallengeService.rejectChallenge(challenge.getChallengeId(), user.getId(),
                        Map.of("reason", "mfaChallengeId 缺失"));
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "mfaChallengeId 缺失"));
                }

                Long challengeUserId = mfaService.verifyChallenge(
                    challenge.getMfaChallengeId(),
                    challenge.getWebIp(),
                    "qr-mfa"
                );
                if (challengeUserId == null) {
                    qrChallengeService.rejectChallenge(challenge.getChallengeId(), user.getId(),
                        Map.of("reason", "mfaChallengeId 无效或已过期"));
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "mfaChallengeId 无效或已过期"));
                }
                if (!challengeUserId.equals(user.getId())) {
                    qrChallengeService.rejectChallenge(challenge.getChallengeId(), user.getId(),
                        Map.of("reason", "MFA 挑战用户不匹配"));
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(403, "MFA 挑战用户不匹配"));
                }
                if (!mfaService.isMethodAllowed(challenge.getMfaChallengeId(), "qr")) {
                    qrChallengeService.rejectChallenge(challenge.getChallengeId(), user.getId(),
                        Map.of("reason", "当前 challenge 不允许使用扫码验证"));
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "当前 challenge 不允许使用扫码验证"));
                }

                mfaService.consumeChallenge(challenge.getMfaChallengeId());
                String challengeTarget = sessionTransferService.normalizeTarget(challenge.getTarget());
                if (challengeTarget == null) {
                    challengeTarget = SessionTransferService.TARGET_WEB;
                }
                SessionTransferService.SessionTransferPayload transferPayload = sessionTransferService.createTransfer(
                    user.getId(),
                    challengeTarget,
                    SessionTransferService.PURPOSE_BRIDGE_LOGIN,
                    challenge.getWebIp(),
                    challenge.getUserAgent()
                );
                result.put("transferCode", transferPayload.getTransferCode());
                result.put("verified", true);
            } else if (challengeType == QrChallengeService.ChallengeType.SENSITIVE) {
                if (challenge.getRequestedUserId() == null || !challenge.getRequestedUserId().equals(user.getId())) {
                    qrChallengeService.rejectChallenge(challenge.getChallengeId(), user.getId(),
                        Map.of("reason", "二维码挑战用户不匹配"));
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(403, "二维码挑战用户不匹配"));
                }
                if (challenge.getWebIp() == null || challenge.getWebIp().isBlank()) {
                    qrChallengeService.rejectChallenge(challenge.getChallengeId(), user.getId(),
                        Map.of("reason", "二维码挑战缺少 webIp"));
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "二维码挑战无效"));
                }

                sensitiveOperationService.markVerifiedOnce(user.getUuid(), challenge.getWebIp());
                result.put("verified", true);
            }

            qrChallengeService.approveChallenge(challenge.getChallengeId(), user.getId(), result);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "二维码挑战已授权", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "二维码挑战授权失败"));
        }
    }

    @GetMapping("/qr/status")
    public ResponseEntity<ApiResponse<QrChallengeStatusResponse>> getQrChallengeStatus(
            @RequestParam String challengeId,
            @RequestParam String pollToken) {
        if (challengeId == null || challengeId.isBlank() || pollToken == null || pollToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "challengeId 与 pollToken 不能为空"));
        }

        QrChallengeService.QrChallengePayload challenge = qrChallengeService.getByChallengeId(challengeId.trim());
        if (challenge == null) {
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "二维码挑战已过期", new QrChallengeStatusResponse("expired", 0L)));
        }

        if (!pollToken.trim().equals(challenge.getPollToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "pollToken 无效"));
        }

        long expiresInSeconds = qrChallengeService.getRemainingSeconds(challenge.getChallengeId());
        if (expiresInSeconds <= 0) {
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "二维码挑战已过期", new QrChallengeStatusResponse("expired", 0L)));
        }

        String status = challenge.getStatus() == null ? "pending" : challenge.getStatus();
        QrChallengeStatusResponse statusResponse = new QrChallengeStatusResponse(status, expiresInSeconds);

        Map<String, Object> result = challenge.getResult();
        if (result != null) {
            Object transferCode = result.get("transferCode");
            if (transferCode instanceof String value && !value.isBlank()) {
                statusResponse.setTransferCode(value);
            }
            Object mfaChallengeId = result.get("mfaChallengeId");
            if (mfaChallengeId instanceof String value && !value.isBlank()) {
                statusResponse.setMfaChallengeId(value);
            }
            Object method = result.get("method");
            if (method instanceof String value && !value.isBlank()) {
                statusResponse.setMethod(value);
            }
            Object methods = result.get("methods");
            if (methods instanceof List<?> values) {
                List<String> normalizedMethods = values.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(String::valueOf)
                    .toList();
                if (!normalizedMethods.isEmpty()) {
                    statusResponse.setMethods(normalizedMethods);
                }
            }
            Object verified = result.get("verified");
            if (verified instanceof Boolean value) {
                statusResponse.setVerified(value);
            }
        }

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "查询成功", statusResponse));
    }

    /**
     * 获取当前登录设备/在线会话列表
     * @param authentication 认证信息（AccessToken）
     * @param request HttpServletRequest
     * @return ApiResponse
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionInfoResponse>>> getSessions(
            Authentication authentication,
            HttpServletRequest request) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        Long currentSessionId = getCurrentSessionId(request);
        LocalDateTime onlineThreshold = LocalDateTime.now().minusMinutes(10);

        List<SessionInfoResponse> responses = userSessionService.listActiveSessions(user).stream()
            .sorted(Comparator.comparing(UserSession::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserSession::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(session -> new SessionInfoResponse(
                session,
                session.getLastSeenAt() != null && session.getLastSeenAt().isAfter(onlineThreshold),
                currentSessionId != null && currentSessionId.equals(session.getId())
            ))
            .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "查询成功", responses));
    }

    /**
     * 取消指定会话（踢下线）
     * @param sessionId 会话ID
     * @param authentication 认证信息（AccessToken）
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @PostMapping("/sessions/{sessionId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @PathVariable Long sessionId,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        UserSession session = userSessionService.findSessionByIdForUser(sessionId, user).orElse(null);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "会话不存在"));
        }

        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "会话已失效"));
        }

        userSessionService.revokeSession(session);

        Long currentSessionId = getCurrentSessionId(request);
        if (currentSessionId != null && currentSessionId.equals(sessionId)) {
            setRefreshTokenCookie(response, "");
        }

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "会话已取消"));
    }

    /**
     * 验证 TOTP 或恢复码（用于 MFA 登录完成）
     * 前端在收到 201 并带有 challengeId 后，调用此接口完成 TOTP/恢复码校验并下发 token
     */
    @PostMapping("/totp/mfa-verify")
    public ResponseEntity<ApiResponse<Object>> verifyTotpForLogin(
            @RequestBody MfaTotpVerifyRequest requestBody,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (requestBody == null || requestBody.getChallengeId() == null || 
            (requestBody.getCode() == null && requestBody.getRecoveryCode() == null)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "challengeId 和 code 或 recoveryCode 不能为空"));
        }

        String clientIp = rateLimitService.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // ✅ 先验证challenge（不删除）
        Long userId = mfaService.verifyChallenge(requestBody.getChallengeId(), clientIp, userAgent);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "challengeId 无效或已过期"));
        }

        if (!mfaService.isMethodAllowed(requestBody.getChallengeId(), "totp")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "当前 challenge 不允许使用 TOTP"));
        }

        // 查找用户
        User user = userService.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        long startTime = System.currentTimeMillis();
        try {
            String loginMethod = mfaService.getLoginMethod(requestBody.getChallengeId());
            String logMethod = Arrays.asList(loginMethod, "mfa").toString();
            byte[] masterKey = encryptionUtil.getMasterKey();
            boolean ok = false;
            String verifyMethod = "unknown";
            String codeValue = requestBody.getCode() == null ? null : requestBody.getCode().trim();
            String recoveryCodeValue = requestBody.getRecoveryCode() == null ? null : requestBody.getRecoveryCode().trim();

            // 优先尝试 TOTP 码验证
            if (codeValue != null && !codeValue.isEmpty()) {
                ok = totpService.verifyTotpCode(user.getId(), codeValue, masterKey);
                if (ok) {
                    verifyMethod = "totp_code";
                }
            }

            // 如果 TOTP 码验证失败，尝试恢复码验证
            if (!ok && recoveryCodeValue != null && !recoveryCodeValue.isEmpty()) {
                ok = totpService.verifyRecoveryCode(user.getId(), recoveryCodeValue);
                if (ok) {
                    verifyMethod = "recovery_code";
                }
            }

            if (!ok) {
                // ✅ MFA验证失败，记录失败次数
                mfaService.recordFailedAttempt(requestBody.getChallengeId());
                int remaining = mfaService.getRemainingAttempts(requestBody.getChallengeId());
                sensitiveLogUtil.logLogin(httpRequest, user.getId(), logMethod, false, "TOTP/恢复码校验失败", startTime);
                
                if (remaining > 0) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "TOTP/恢复码校验失败，剩余尝试次数：" + remaining));
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "TOTP/恢复码校验失败次数过多，请重新登录"));
                }
            }

            // ✅ 验证通过后，消费并删除challenge
            mfaService.consumeChallenge(requestBody.getChallengeId());

            // 正常创建会话并下发 token
            String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
            UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
            int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
            String accessToken = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

            setRefreshTokenCookie(response, refreshToken);

            // MFA验证成功
            sensitiveLogUtil.logLogin(httpRequest, user.getId(), logMethod, true, verifyMethod, startTime);

            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "登录成功", new TokenResponse(accessToken)));
        } catch (Exception e) {
            String loginMethod = mfaService.getLoginMethod(requestBody.getChallengeId());
            String logMethod = Arrays.asList(loginMethod, "mfa").toString();
            sensitiveLogUtil.logLogin(httpRequest, user.getId(), logMethod, false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "TOTP 验证处理失败"));
        }
    }

    /**
     * 使用 Passkey 完成 MFA 登录
     */
    @PostMapping("/passkey/mfa-verify")
    public ResponseEntity<ApiResponse<Object>> verifyPasskeyForLoginMfa(
            @RequestBody MfaPasskeyVerifyRequest requestBody,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (requestBody == null || requestBody.getMfaChallengeId() == null || requestBody.getMfaChallengeId().isBlank()
                || requestBody.getPasskeyChallengeId() == null || requestBody.getPasskeyChallengeId().isBlank()
                || requestBody.getCredentialRawId() == null || requestBody.getCredentialRawId().isBlank()
                || requestBody.getClientDataJSON() == null || requestBody.getClientDataJSON().isBlank()
                || requestBody.getAuthenticatorData() == null || requestBody.getAuthenticatorData().isBlank()
                || requestBody.getSignature() == null || requestBody.getSignature().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "mfaChallengeId、passkeyChallengeId 及 Passkey 参数不能为空"));
        }

        String clientIp = rateLimitService.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        Long userId = mfaService.verifyChallenge(requestBody.getMfaChallengeId(), clientIp, userAgent);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "mfaChallengeId 无效或已过期"));
        }

        if (!mfaService.isMethodAllowed(requestBody.getMfaChallengeId(), "passkey")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "当前 challenge 不允许使用 Passkey"));
        }

        User user = userService.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        long startTime = System.currentTimeMillis();
        String loginMethod = mfaService.getLoginMethod(requestBody.getMfaChallengeId());
        String logMethod = Arrays.asList(loginMethod, "mfa").toString();

        try {
            PasskeyAuthenticationVerifyRequest passkeyRequest = new PasskeyAuthenticationVerifyRequest(
                    requestBody.getCredentialRawId(),
                    requestBody.getClientDataJSON(),
                    requestBody.getAuthenticatorData(),
                    requestBody.getSignature()
            );

            Long passkeyUserId = passkeyService.verifyAuthenticationAndGetUserId(
                    passkeyRequest,
                    requestBody.getPasskeyChallengeId()
            );

            if (!userId.equals(passkeyUserId)) {
                mfaService.recordFailedAttempt(requestBody.getMfaChallengeId());
                sensitiveLogUtil.logLogin(httpRequest, user.getId(), logMethod, false, "Passkey 与 MFA 用户不匹配", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "Passkey 校验失败"));
            }

            mfaService.consumeChallenge(requestBody.getMfaChallengeId());

            String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
            UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
            int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
            String accessToken = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

            setRefreshTokenCookie(response, refreshToken);
            sensitiveLogUtil.logLogin(httpRequest, user.getId(), logMethod, true, null, startTime);

            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "登录成功", new TokenResponse(accessToken)));
        } catch (IllegalArgumentException e) {
            mfaService.recordFailedAttempt(requestBody.getMfaChallengeId());
            int remaining = mfaService.getRemainingAttempts(requestBody.getMfaChallengeId());
            sensitiveLogUtil.logLogin(httpRequest, user.getId(), logMethod, false, e.getMessage(), startTime);
            if (remaining > 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "Passkey 校验失败，剩余尝试次数：" + remaining));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "Passkey 校验失败次数过多，请重新登录"));
        } catch (Exception e) {
            sensitiveLogUtil.logLogin(httpRequest, user.getId(), logMethod, false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Passkey MFA 验证处理失败"));
        }
    }

    /**
     * 退出登录
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        // 从 Authorization Header 中提取 AccessToken
        String accessToken = null;
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            accessToken = bearerToken.substring(7);
        }

        // 从 Cookie 中获取 RefreshToken
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken不存在"));
        }

        // 从 Token 中获取 uuid
        String uuid = jwtUtil.getUuidFromToken(refreshToken);
        if (uuid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken无效或已过期"));
        }

        // 查找用户
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken无效或已过期"));
        }

        // 验证 RefreshToken 并撤销会话
        UserSession session = userSessionService.verifyRefreshToken(user, refreshToken).orElse(null);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken无效或已过期"));
        }

        userSessionService.revokeSession(session);

        // ✅ 将 AccessToken 和 RefreshToken 加入黑名单
        if (accessToken != null) {
            // AccessToken 有效期：15分钟
            tokenBlacklistService.addToBlacklist(accessToken, Duration.ofMinutes(15));
        }
        // RefreshToken 有效期：7天
        tokenBlacklistService.addToBlacklist(refreshToken, Duration.ofDays(7));

        // 清除敏感操作验证标记
        sensitiveOperationService.clearVerification(uuid);

        // 清除 RefreshToken Cookie
        setRefreshTokenCookie(response, "");

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "退出成功"));
    }

    /**
     * 所有设备退出登录
     * @param authentication 认证信息（AccessToken）
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @PostMapping("/logout/all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(Authentication authentication, HttpServletResponse response) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        // 撤销该用户所有会话
        userSessionService.revokeAllSessions(user);

        // ✅ 吊销用户的所有 Token（7天有效期对应 RefreshToken）
        tokenBlacklistService.revokeAllUserTokens(uuid, 7 * 24 * 60);

        // 清除敏感操作验证标记
        sensitiveOperationService.clearVerification(uuid);

        // 清除 RefreshToken Cookie
        setRefreshTokenCookie(response, "");

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "已从所有设备退出登录"));
    }

    /**
     * 更新用户信息
     * @param updateProfileRequest 更新请求（用户名和头像 URL 都是可选的）
     * @param authentication 认证信息（AccessToken）
     * @return ApiResponse
     */
    @PostMapping("/update/profile")
    public ResponseEntity<ApiResponse<UpdateProfileResponse>> updateProfile(@RequestBody UpdateProfileRequest updateProfileRequest,
                                                                             Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        // 参数校验：一次只能更新一个字段
        String key = updateProfileRequest.getKey();
        String value = updateProfileRequest.getValue();

        if (key == null || key.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "key 不能为空"));
        }
        if (value == null || value.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "value 不能为空"));
        }

        String normalizedKey = key.trim();

        // 用户名格式校验
        if ("username".equalsIgnoreCase(normalizedKey)) {
            if (!securityValidator.isValidUsername(value)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "用户名格式不正确（3-20字符，字母数字下划线连字符或简体中文）"));
            }
            // SQL 注入检查
            if (securityValidator.possibleSqlInjection(value)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "输入包含非法字符"));
            }
        }

        // 出生日期校验（YYYY-MM-DD）
        LocalDate parsedBirthDate = null;
        if ("birthDate".equalsIgnoreCase(normalizedKey)) {
            try {
                parsedBirthDate = LocalDate.parse(value.trim());
            } catch (DateTimeParseException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "birthDate 格式不正确，应为 YYYY-MM-DD"));
            }
        }

        // 执行更新（一次只更新一个字段）
        RegisterResult result = userService.updateProfileSingleField(user, normalizedKey, value.trim(), parsedBirthDate);
        if (result.getStatus() == RegisterResult.Status.USERNAME_EXISTS) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "用户名已存在"));
        }

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "更新成功", UpdateProfileResponse.fromUser(result.getUser())));
    }

    /**
     * 上传头像（保存到 API 本地静态目录）
     * @param file 上传文件
     * @param authentication 认证信息（AccessToken）
     * @return ApiResponse
     */
    @PostMapping(value = "/upload/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UpdateProfileResponse>> uploadAvatar(@RequestPart("file") MultipartFile file,
                                                                            Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "请选择头像文件"));
        }

        if (file.getSize() > AVATAR_MAX_SIZE_BYTES) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "头像文件不能超过 3MB"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "仅支持图片文件"));
        }

        String fileExtension = resolveFileExtension(contentType, file.getOriginalFilename());
        if (fileExtension == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "不支持的图片格式"));
        }

        try {
            Path avatarDir = Paths.get(AVATAR_STORAGE_DIR).toAbsolutePath().normalize();
            Files.createDirectories(avatarDir);

            String filename = UUID.randomUUID().toString().replace("-", "") + fileExtension;
            Path target = avatarDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = getAvatarHostPrefix() + "/static/avatars/" + filename;
            RegisterResult result = userService.updateProfileSingleField(user, "avatarUrl", avatarUrl, null);

            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "头像上传成功", UpdateProfileResponse.fromUser(result.getUser())));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "头像上传失败"));
        }
    }

    private String getAvatarHostPrefix() {
        return appProperties.isDebug() ? "http://localhost:8000" : "https://api.ksuser.cn";
    }

    private String resolveFileExtension(String contentType, String originalFilename) {
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        switch (normalizedType) {
            case "image/jpeg":
            case "image/jpg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/webp":
                return ".webp";
            case "image/gif":
                return ".gif";
            default:
                break;
        }

        if (originalFilename == null) {
            return null;
        }
        int index = originalFilename.lastIndexOf('.');
        if (index < 0 || index == originalFilename.length() - 1) {
            return null;
        }

        String extension = originalFilename.substring(index).toLowerCase(Locale.ROOT);
        if (".jpg".equals(extension) || ".jpeg".equals(extension) || ".png".equals(extension)
            || ".webp".equals(extension) || ".gif".equals(extension)) {
            return ".jpeg".equals(extension) ? ".jpg" : extension;
        }
        return null;
    }

    /**
     * 敏感操作验证接口
     * @param verifySensitiveOperationRequest 验证请求
     * @param authentication 认证信息（AccessToken）
     * @param request HttpServletRequest
     * @return ApiResponse
     */
    @PostMapping("/verify-sensitive")
    public ResponseEntity<ApiResponse<Void>> verifySensitiveOperation(@RequestBody VerifySensitiveOperationRequest verifySensitiveOperationRequest,
                                                                       Authentication authentication,
                                                                       HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        
        if (authentication == null || authentication.getPrincipal() == null) {
            sensitiveLogUtil.logSensitiveVerify(request, null, false, "not_authenticated", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            sensitiveLogUtil.logSensitiveVerify(request, null, false, "user_not_found", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        String method = verifySensitiveOperationRequest.getMethod();

        // 参数校验
        if (method == null || method.trim().isEmpty()) {
            sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "empty_verification_method", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证方式不能为空"));
        }
        if (!("password".equals(method) || "email-code".equals(method) || "totp".equals(method) || "passkey".equals(method))) {
            sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "invalid_verification_method", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse<>(400, "验证方式只能是 password、email-code、passkey 或 totp"));
        }

        String clientIp = rateLimitService.getClientIp(request);

        // 密码验证
        if ("password".equals(method)) {
            String password = verifySensitiveOperationRequest.getPassword();
            if (password == null || password.trim().isEmpty()) {
                sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "empty_password", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "密码不能为空"));
            }

            if (!userService.verifyPassword(password, user.getPasswordHash())) {
                sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "invalid_password", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "密码错误"));
            }

            // 验证成功，标记用户已验证
            sensitiveOperationService.markVerified(uuid, clientIp);
            verificationCodeService.clearLockAndError(user.getEmail());
            sensitiveLogUtil.logSensitiveVerify(request, user.getId(), true, null, startTime);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "验证成功，有效期15分钟"));
        }

        // 邮箱验证码验证
        if ("email-code".equals(method)) {
            String code = verifySensitiveOperationRequest.getCode();
            if (code == null || code.trim().isEmpty()) {
                sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "empty_verification_code", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码不能为空"));
            }

            String email = user.getEmail();
            // 使用 "sensitive-verification" 类型的验证码进行验证
            int verifyResult = verificationCodeService.verifyCodeForType(email, code, clientIp, "sensitive-verification");
            
            if (verifyResult != 0) {
                // 0 = 成功，1 = 已过期，2 = 错误，3 = 被锁定，4 = 未发送，5 = 邮箱不匹配，6 = IP不匹配
                if (verifyResult == 3) {
                    sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "verification_code_locked", startTime);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                } else if (verifyResult == 4) {
                    sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "verification_code_not_sent", startTime);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "请先获取验证码"));
                } else if (verifyResult == 5) {
                    int errorCount = verificationCodeService.getErrorCount(email);
                    sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "email_mismatch", startTime);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "邮箱不匹配（" + errorCount + "/5）"));
                } else if (verifyResult == 6) {
                    int errorCount = verificationCodeService.getErrorCount(email);
                    sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "ip_mismatch", startTime);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "发送验证码的设备与当前设备不匹配（" + errorCount + "/5）"));
                } else if (verifyResult == 1) {
                    if (verificationCodeService.isLocked(email)) {
                        sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "verification_code_locked", startTime);
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                    }
                    int errorCount = verificationCodeService.getErrorCount(email);
                    sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "verification_code_expired", startTime);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "验证码已过期，请重新获取（" + errorCount + "/5）"));
                } else {
                    if (verificationCodeService.isLocked(email)) {
                        sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "verification_code_locked", startTime);
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                    }
                    int errorCount = verificationCodeService.getErrorCount(email);
                    sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "verification_code_invalid", startTime);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "验证码错误（" + errorCount + "/5）"));
                }
            }

            // 验证成功，标记用户已验证
            sensitiveOperationService.markVerified(uuid, clientIp);
            verificationCodeService.clearLockAndError(email);
            sensitiveLogUtil.logSensitiveVerify(request, user.getId(), true, null, startTime);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "验证成功，有效期15分钟"));
        }

        // TOTP 验证
        if ("totp".equals(method)) {
            String code = verifySensitiveOperationRequest.getCode();
            if (code == null || code.trim().isEmpty()) {
                sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "empty_verification_code", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码不能为空"));
            }

            // 检查是否已启用 TOTP
            if (!totpService.isTotpEnabled(user.getId())) {
                sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "totp_not_enabled", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "用户未启用 TOTP"));
            }

            try {
                byte[] masterKey = encryptionUtil.getMasterKey();
                boolean ok = totpService.verifyTotpCode(user.getId(), code, masterKey);
                if (!ok) {
                    sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "verification_code_invalid_or_expired", startTime);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "验证码错误或已过期"));
                }
            } catch (Exception e) {
                sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, e.getMessage(), startTime);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(500, "TOTP 验证失败：" + e.getMessage()));
            }

            // 验证成功，标记用户已验证
            sensitiveOperationService.markVerified(uuid, clientIp);
            sensitiveLogUtil.logSensitiveVerify(request, user.getId(), true, null, startTime);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "验证成功，有效期15分钟"));
        }

        if ("passkey".equals(method)) {
            sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "passkey_requires_webauthn_challenge", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "Passkey 验证请使用 /auth/passkey/sensitive-verification-options 与 /auth/passkey/sensitive-verification-verify"));
        }

        sensitiveLogUtil.logSensitiveVerify(request, user.getId(), false, "unknown_verification_method", startTime);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse<>(400, "未知的验证方式"));
    }

    /**
     * 更改邮箱接口
     * @param changeEmailRequest 更改邮箱请求
     * @param authentication 认证信息（AccessToken）
     * @param request HttpServletRequest
     * @return ApiResponse
     */
    @PostMapping("/update/email")
    public ResponseEntity<ApiResponse<ChangeEmailResponse>> changeEmail(@RequestBody ChangeEmailRequest changeEmailRequest,
                                                                         Authentication authentication,
                                                                         HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        
        if (authentication == null || authentication.getPrincipal() == null) {
            sensitiveLogUtil.logChangeEmail(request, null, false, "not_authenticated", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            sensitiveLogUtil.logChangeEmail(request, null, false, "user_not_found", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已完成敏感操作验证
        if (!sensitiveOperationService.isVerified(uuid, clientIp)) {
            sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "sensitive_verification_not_completed", startTime);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, "请先完成敏感操作验证"));
        }

        // 参数校验
        String newEmail = changeEmailRequest.getNewEmail();
        String code = changeEmailRequest.getCode();

        if (newEmail == null || newEmail.trim().isEmpty()) {
            sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "empty_email", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "新邮箱不能为空"));
        }
        if (!securityValidator.isValidEmail(newEmail)) {
            sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "invalid_email_format", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱格式不正确"));
        }
        if (code == null || code.trim().isEmpty()) {
            sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "empty_verification_code", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证码不能为空"));
        }

        // 检查新邮箱是否已被使用
        if (userService.findByEmail(newEmail).isPresent()) {
            sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "email_already_exists", startTime);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "邮箱已被使用"));
        }

        // 验证新邮箱的验证码
        int verifyResult = verificationCodeService.verifyCode(newEmail, code, clientIp);
        if (verifyResult != 0) {
            // 0 = 成功，1 = 已过期，2 = 错误，3 = 被锁定，4 = 未发送，5 = 邮箱不匹配，6 = IP不匹配
            if (verifyResult == 3) {
                sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "verification_code_locked", startTime);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
            } else if (verifyResult == 4) {
                sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "verification_code_not_sent", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "请先获取验证码"));
            } else if (verifyResult == 5) {
                int errorCount = verificationCodeService.getErrorCount(newEmail);
                sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "email_mismatch", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "邮箱不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 6) {
                int errorCount = verificationCodeService.getErrorCount(newEmail);
                sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "ip_mismatch", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "发送验证码的设备与当前设备不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 1) {
                if (verificationCodeService.isLocked(newEmail)) {
                    sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "verification_code_locked", startTime);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(newEmail);
                sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "verification_code_expired", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码已过期，请重新获取（" + errorCount + "/5）"));
            } else {
                if (verificationCodeService.isLocked(newEmail)) {
                    sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "verification_code_locked", startTime);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(newEmail);
                sensitiveLogUtil.logChangeEmail(request, user.getId(), false, "verification_code_invalid", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码错误（" + errorCount + "/5）"));
            }
        }

        // 更新邮箱
        user.setEmail(newEmail);
        User updatedUser = userService.save(user);
        sensitiveLogUtil.logChangeEmail(request, updatedUser.getId(), true, null, startTime);
        
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "邮箱更新成功", ChangeEmailResponse.fromUser(updatedUser)));
    }

    /**
     * 检查敏感操作验证状态
     * @param authentication 认证信息（AccessToken）
     * @param request HttpServletRequest
     * @return ApiResponse
     */
    @GetMapping("/check-sensitive-verification")
    public ResponseEntity<ApiResponse<SensitiveVerificationStatusResponse>> checkSensitiveVerification(Authentication authentication,
                                                                                                         HttpServletRequest request) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已验证
        boolean isVerified = sensitiveOperationService.isVerified(uuid, clientIp);
        
        // 获取剩余时间
        long remainingSeconds = sensitiveOperationService.getRemainingTime(uuid);
        
        List<String> sensitiveMethods = resolveSensitiveMethods(user);
        String preferredSensitiveMethod = resolvePreferredSensitiveMethod(user, sensitiveMethods);

        SensitiveVerificationStatusResponse response = new SensitiveVerificationStatusResponse(
            isVerified,
            remainingSeconds > 0 ? remainingSeconds : 0,
            preferredSensitiveMethod,
            sensitiveMethods
        );

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "查询成功", response));
    }

    /**
     * 注销账号
     * @param deleteAccountRequest 注销请求
     * @param authentication 认证信息（AccessToken）
     * @param request HttpServletRequest
     * @return ApiResponse
     */
    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<String>> deleteAccount(@RequestBody DeleteAccountRequest deleteAccountRequest,
                                                              Authentication authentication,
                                                              HttpServletRequest request) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已完成敏感操作验证
        if (!sensitiveOperationService.isVerified(uuid, clientIp)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, "请先完成敏感操作验证"));
        }

        // 参数校验
        String confirmText = deleteAccountRequest.getConfirmText();
        if (confirmText == null || !confirmText.equals("我真的不想要我的号辣")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "请输入正确的确认文本"));
        }

        // 删除用户账号
        userService.deleteUser(user);

        // 删除该用户的所有会话
        userSessionService.deleteAllSessionsByUser(user);

        // 清除该用户的敏感操作验证状态
        sensitiveOperationService.clearVerification(uuid);

        // 将当前 AccessToken 加入黑名单
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            // AccessToken 有效期：15分钟
            tokenBlacklistService.addToBlacklist(accessToken, Duration.ofMinutes(15));
        }

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "账号已注销"));
    }

    /**
     * 修改密码
     * @param changePasswordRequest 修改密码请求
     * @param authentication 认证信息（AccessToken）
     * @param request HttpServletRequest
     * @return ApiResponse
     */
    @PostMapping("/update/password")
    public ResponseEntity<ApiResponse<String>> changePassword(@RequestBody ChangePasswordRequest changePasswordRequest,
                                                               Authentication authentication,
                                                               HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        if (authentication == null || authentication.getPrincipal() == null) {
            sensitiveLogUtil.logChangePassword(request, null, false, "Not authenticated", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            sensitiveLogUtil.logChangePassword(request, null, false, "User not found", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        String clientIp = rateLimitService.getClientIp(request);

        // 检查是否已完成敏感操作验证
        if (!sensitiveOperationService.isVerified(uuid, clientIp)) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "Sensitive verification not completed", startTime);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, "请先完成敏感操作验证"));
        }

        // 参数校验
        String newPassword = changePasswordRequest.getNewPassword();

        if (newPassword == null || newPassword.trim().isEmpty()) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "Empty new password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "新密码不能为空"));
        }

        if (!securityValidator.isValidPasswordLength(newPassword)) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "Invalid password length", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码长度必须在" + appProperties.getPassword().getMinLength() + 
                    "-" + appProperties.getPassword().getMaxLength() + "个字符之间"));
        }

        if (!securityValidator.isStrongPassword(newPassword)) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "Weak password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, buildPasswordRequirementMessage()));
        }

        if (securityValidator.isCommonWeakPassword(newPassword)) {
            sensitiveLogUtil.logChangePassword(request, user.getId(), false, "Common weak password", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码过于简单，请使用更复杂的密码"));
        }

        // 更新密码
        userService.updatePassword(user, newPassword);

        // 清除敏感操作验证状态
        sensitiveOperationService.clearVerification(uuid);

        // 记录成功日志
        sensitiveLogUtil.logChangePassword(request, user.getId(), true, null, startTime);
        
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "密码修改成功"));
    }

    /**
     * 根据配置动态生成密码要求消息
     * @return 密码要求描述
     */
    private String buildPasswordRequirementMessage() {
        AppProperties.Password pwdConfig = appProperties.getPassword();
        List<String> requirements = new ArrayList<>();
        
        if (pwdConfig.isRequireUppercase()) {
            requirements.add("大写字母");
        }
        if (pwdConfig.isRequireLowercase()) {
            requirements.add("小写字母");
        }
        if (pwdConfig.isRequireDigits()) {
            requirements.add("数字");
        }
        if (pwdConfig.isRequireSpecialChars()) {
            requirements.add("特殊字符");
        }
        
        if (requirements.isEmpty()) {
            return "密码强度不足";
        }
        
        return "密码强度不足：需包含" + String.join("、", requirements);
    }

    /**
     * 设置 RefreshToken Cookie (包含 SameSite=Strict CSRF 保护)
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        boolean hasToken = token != null && !token.isEmpty();
        org.springframework.http.ResponseCookie responseCookie = org.springframework.http.ResponseCookie
            .from("refreshToken", hasToken ? token : "")
            .httpOnly(true)
            .secure(!appProperties.isDebug())
            .path("/")
            .maxAge(hasToken ? 604800 : 0)
            .sameSite("Strict")
            .build();
        response.addHeader("Set-Cookie", responseCookie.toString());
    }

    private String getAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String writeCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        org.springframework.security.web.csrf.CsrfToken csrf =
            (org.springframework.security.web.csrf.CsrfToken) request.getAttribute(
                org.springframework.security.web.csrf.CsrfToken.class.getName());
        if (csrf == null) {
            return null;
        }

        String token = csrf.getToken();
        response.addHeader("X-CSRF-TOKEN", token);
        return token;
    }

    private Long getCurrentSessionId(HttpServletRequest request) {
        String accessToken = getAccessToken(request);
        if (accessToken == null) {
            return null;
        }
        return jwtUtil.getSessionId(accessToken);
    }

    private TokenResponse issueSessionToken(User user, String clientIp, String userAgent, HttpServletResponse response) {
        String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
        UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
        int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
        String accessToken = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);
        setRefreshTokenCookie(response, refreshToken);
        return new TokenResponse(accessToken);
    }

    private boolean isAccountRecoverySponsorSessionActive(
            AccountRecoveryService.AccountRecoveryPayload payload,
            User user) {
        if (payload == null || payload.getSponsorSessionId() == null || user == null || user.getId() == null) {
            return false;
        }

        UserSession sponsorSession = userSessionService.findActiveSessionById(payload.getSponsorSessionId()).orElse(null);
        return sponsorSession != null && user.getId().equals(sponsorSession.getUser().getId());
    }

    private String resolveBridgeLoginMethod(String target) {
        if (target == null || target.isBlank()) {
            return "BRIDGE";
        }

        return switch (target.trim().toLowerCase(Locale.ROOT)) {
            case "web" -> "BRIDGE_FROM_DESKTOP";
            case "desktop" -> "BRIDGE_FROM_WEB";
            case "mobile" -> "BRIDGE_TO_MOBILE";
            default -> "BRIDGE";
        };
    }


    private QrChallengeInitResponse toQrInitResponse(QrChallengeService.QrChallengePayload payload) {
        long expiresInSeconds = qrChallengeService.getRemainingSeconds(payload.getChallengeId());
        long ttl = expiresInSeconds > 0 ? expiresInSeconds : QrChallengeService.DEFAULT_TTL_SECONDS;
        return new QrChallengeInitResponse(
            payload.getChallengeId(),
            payload.getPollToken(),
            QR_TEXT_PREFIX + payload.getApproveCode(),
            ttl
        );
    }

    // ==================== Passkey (WebAuthn) 端点 ====================

    /**
     * 生成 Passkey 注册选项
     * @param request 注册选项请求
     * @param authentication 认证信息（AccessToken）
     * @return ApiResponse
     */
    @PostMapping("/passkey/registration-options")
    public ResponseEntity<ApiResponse<PasskeyRegistrationOptionsResponse>> generatePasskeyRegistrationOptions(
            @RequestBody PasskeyRegistrationOptionsRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        // ========== 检查是否已通过敏感操作验证 ==========
        String clientIp = rateLimitService.getClientIp(httpRequest);
        if (!sensitiveOperationService.isVerified(uuid, clientIp)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, "需要先通过敏感操作验证"));
        }

        // ========== 生成注册选项 ==========
        try {
            PasskeyRegistrationOptionsResponse options = passkeyService.generateRegistrationOptions(
                    user,
                    request != null ? request.getAuthenticatorType() : null
            );
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "生成注册选项成功", options));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "生成注册选项失败：" + e.getMessage()));
        }
    }

    /**
     * 验证 Passkey 注册
     * @param request 注册验证请求
     * @param authentication 认证信息（AccessToken）
     * @return ApiResponse
     */
    @PostMapping("/passkey/registration-verify")
    public ResponseEntity<ApiResponse<PasskeyRegistrationVerifyResponse>> verifyPasskeyRegistration(
            @RequestBody PasskeyRegistrationVerifyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        
        if (authentication == null || authentication.getPrincipal() == null) {
            sensitiveLogUtil.logAddPasskey(httpRequest, null, false, "not_authenticated", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            sensitiveLogUtil.logAddPasskey(httpRequest, null, false, "user_not_found", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        try {
            cn.ksuser.api.entity.UserPasskey passkey = passkeyService.verifyRegistration(user, request);
            sensitiveLogUtil.logAddPasskey(httpRequest, user.getId(), true, null, startTime);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "Passkey 注册成功", PasskeyRegistrationVerifyResponse.fromUserPasskey(passkey)));
        } catch (IllegalArgumentException e) {
            sensitiveLogUtil.logAddPasskey(httpRequest, user.getId(), false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage()));
        } catch (Exception e) {
            sensitiveLogUtil.logAddPasskey(httpRequest, user.getId(), false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Passkey 注册失败：" + e.getMessage()));
        }
    }

    /**
     * 生成 Passkey 认证选项
     * @return ApiResponse
     */
    @PostMapping("/passkey/authentication-options")
    public ResponseEntity<ApiResponse<PasskeyAuthenticationOptionsResponse>> generatePasskeyAuthenticationOptions() {
        try {
            PasskeyAuthenticationOptionsResponse options = passkeyService.generateAuthenticationOptions();
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "生成认证选项成功", options));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "生成认证选项失败：" + e.getMessage()));
        }
    }

    /**
     * 验证 Passkey 认证（登录）
     * @param request 认证验证请求
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @PostMapping("/passkey/authentication-verify")
    public ResponseEntity<ApiResponse<Object>> verifyPasskeyAuthentication(
            @RequestBody PasskeyAuthenticationVerifyRequest request,
            @RequestParam(required = false) String challengeId,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        long startTime = System.currentTimeMillis();
        try {
            // 检查 challengeId 是否传递
            if (challengeId == null || challengeId.trim().isEmpty()) {
                sensitiveLogUtil.logLogin(httpRequest, null, "PASSKEY", false, "empty_challenge_id", startTime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "challengeId 不能为空"));
            }
            
            // 验证 Passkey 并获取用户 ID
            Long userId = passkeyService.verifyAuthenticationAndGetUserId(request, challengeId);
            
            // 通过用户 ID 从数据库查询用户
            User user = userService.findById(userId).orElse(null);
            if (user == null) {
                sensitiveLogUtil.logLogin(httpRequest, null, "PASSKEY", false, "user_not_found", startTime);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "Passkey 验证失败"));
            }

            // Passkey 作为一因子登录时，允许 TOTP/二维码等非 Passkey 二因子
            UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
            boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
            String clientIp = rateLimitService.getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            if (mfaEnabled) {
                List<String> mfaMethods = resolveMfaMethods(user, false, true);
                if (!mfaMethods.isEmpty()) {
                    String challenge = mfaService.createChallenge(user.getId(), clientIp, userAgent,
                            "passkey", new HashSet<>(mfaMethods));
                    return ResponseEntity.status(HttpStatus.CREATED)
                        .body(new ApiResponse<>(201, "需要 MFA 验证",
                                new MfaChallengeResponse(challenge, mfaMethods.get(0), mfaMethods)));
                }
            }

            // 生成 Token
            String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
            UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
            int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
            String accessToken = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

            // 设置 RefreshToken Cookie
            setRefreshTokenCookie(response, refreshToken);

            sensitiveLogUtil.logLogin(httpRequest, user.getId(), "PASSKEY", true, null, startTime);

            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "登录成功", new TokenResponse(accessToken)));
        } catch (IllegalArgumentException e) {
            sensitiveLogUtil.logLogin(httpRequest, null, "PASSKEY", false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage()));
        } catch (Exception e) {
            sensitiveLogUtil.logLogin(httpRequest, null, "PASSKEY", false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Passkey 认证失败：" + e.getMessage()));
        }
    }

    /**
     * 生成敏感操作验证选项（Passkey）
     * @param authentication 认证信息（AccessToken）
     * @return ApiResponse
     */
    @PostMapping("/passkey/sensitive-verification-options")
    public ResponseEntity<ApiResponse<PasskeyAuthenticationOptionsResponse>> generateSensitiveVerificationOptions(
            Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        try {
            PasskeyAuthenticationOptionsResponse options = passkeyService.generateSensitiveVerificationOptions();
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "生成敏感操作验证选项成功", options));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "生成验证选项失败：" + e.getMessage()));
        }
    }

    /**
     * 验证敏感操作（Passkey）
     * @param request 认证验证请求
     * @param authentication 认证信息（AccessToken）
     * @param httpRequest HttpServletRequest
     * @return ApiResponse
     */
    @PostMapping("/passkey/sensitive-verification-verify")
    public ResponseEntity<ApiResponse<Void>> verifySensitiveOperationWithPasskey(
            @RequestBody PasskeyAuthenticationVerifyRequest request,
            @RequestParam(required = false) String challengeId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        
        // 检查 challengeId 是否传递
        if (challengeId == null || challengeId.trim().isEmpty()) {
            sensitiveLogUtil.logSensitiveVerify(httpRequest, null, false, "empty_challenge_id", startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "challengeId 不能为空"));
        }
        
        if (authentication == null || authentication.getPrincipal() == null) {
            sensitiveLogUtil.logSensitiveVerify(httpRequest, null, false, "not_authenticated", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            sensitiveLogUtil.logSensitiveVerify(httpRequest, null, false, "user_not_found", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        try {
            passkeyService.verifySensitiveOperation(user, request, challengeId);
            
            String clientIp = rateLimitService.getClientIp(httpRequest);
            sensitiveOperationService.markVerified(uuid, clientIp);
            sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), true, null, startTime);
            
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "验证成功，有效期15分钟"));
        } catch (IllegalArgumentException e) {
            sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage()));
        } catch (Exception e) {
            sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "敏感操作验证失败：" + e.getMessage()));
        }
    }

    /**
     * 获取用户 Passkey 列表
     * @param authentication 认证信息（AccessToken）
     * @return ApiResponse
     */
    @GetMapping("/passkey/list")
    public ResponseEntity<ApiResponse<PasskeyListResponse>> getUserPasskeys(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        try {
            List<PasskeyListResponse.PasskeyInfo> passkeys = passkeyService.getUserPasskeys(user.getId());
            PasskeyListResponse response = new PasskeyListResponse(passkeys);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "获取成功", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "获取 Passkey 列表失败：" + e.getMessage()));
        }
    }

    /**
     * 删除 Passkey
     * @param passkeyId Passkey ID
     * @param authentication 认证信息（AccessToken）
     * @return ApiResponse
     */
    @DeleteMapping("/passkey/{passkeyId}")
    public ResponseEntity<ApiResponse<Void>> deletePasskey(
            @PathVariable Long passkeyId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        
        if (authentication == null || authentication.getPrincipal() == null) {
            sensitiveLogUtil.logDeletePasskey(httpRequest, null, false, "not_authenticated", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            sensitiveLogUtil.logDeletePasskey(httpRequest, null, false, "user_not_found", startTime);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        try {
            passkeyService.deletePasskey(passkeyId, user.getId());
            sensitiveLogUtil.logDeletePasskey(httpRequest, user.getId(), true, null, startTime);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "Passkey 删除成功"));
        } catch (IllegalArgumentException e) {
            sensitiveLogUtil.logDeletePasskey(httpRequest, user.getId(), false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage()));
        } catch (Exception e) {
            sensitiveLogUtil.logDeletePasskey(httpRequest, user.getId(), false, e.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "删除失败：" + e.getMessage()));
        }
    }

    /**
     * 重命名 Passkey
     * @param passkeyId Passkey ID
     * @param request 重命名请求
     * @param authentication 认证信息（AccessToken）
     * @return ApiResponse
     */
    @PutMapping("/passkey/{passkeyId}/rename")
    public ResponseEntity<ApiResponse<Void>> renamePasskey(
            @PathVariable Long passkeyId,
            @RequestBody PasskeyRenameRequest request,
            Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "未登录"));
        }

        String uuid = authentication.getPrincipal().toString();
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "用户不存在"));
        }

        try {
            passkeyService.renamePasskey(passkeyId, user.getId(), request.getNewName());
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "Passkey 重命名成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "重命名失败：" + e.getMessage()));
        }
    }

    private List<String> resolveMfaMethods(User user, boolean passkeyAllowed) {
        return resolveMfaMethods(user, passkeyAllowed, true);
    }

    private List<String> resolveMfaMethods(User user, boolean passkeyAllowed, boolean qrAllowed) {
        List<String> methods = new ArrayList<>();
        if (totpService.isTotpEnabled(user.getId())) {
            methods.add("totp");
        }
        if (passkeyAllowed && !passkeyService.getUserPasskeys(user.getId()).isEmpty()) {
            methods.add("passkey");
        }
        if (qrAllowed) {
            methods.add("qr");
        }

        String preferredMfaMethod = userSettingsRepository.findByUserId(user.getId())
            .map(UserSettings::getPreferredMfaMethod)
            .orElse("totp");
        String normalizedPreferred = normalizeMfaPreference(preferredMfaMethod);
        methods.sort((a, b) -> Integer.compare(
            methodPriority(a, normalizedPreferred),
            methodPriority(b, normalizedPreferred)
        ));
        return methods;
    }

    private List<String> resolveSensitiveMethods(User user) {
        List<String> methods = new ArrayList<>();
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            methods.add("password");
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            methods.add("email-code");
        }
        if (!passkeyService.getUserPasskeys(user.getId()).isEmpty()) {
            methods.add("passkey");
        }
        if (totpService.isTotpEnabled(user.getId())) {
            methods.add("totp");
        }

        String preferredSensitiveMethod = userSettingsRepository.findByUserId(user.getId())
            .map(UserSettings::getPreferredSensitiveMethod)
            .orElse("password");
        methods.sort((a, b) -> Integer.compare(
            methodPriority(a, normalizeSensitiveMethod(preferredSensitiveMethod)),
            methodPriority(b, normalizeSensitiveMethod(preferredSensitiveMethod))
        ));
        return methods;
    }

    private String resolvePreferredSensitiveMethod(User user, List<String> methods) {
        if (methods == null || methods.isEmpty()) {
            return null;
        }
        String preferredSensitiveMethod = userSettingsRepository.findByUserId(user.getId())
            .map(UserSettings::getPreferredSensitiveMethod)
            .orElse("password");
        String normalized = normalizeSensitiveMethod(preferredSensitiveMethod);
        if (normalized != null && methods.contains(normalized)) {
            return normalized;
        }
        return methods.get(0);
    }

    private String normalizeMfaPreference(String raw) {
        if (raw == null) {
            return "totp";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return ("totp".equals(normalized) || "passkey".equals(normalized) || "qr".equals(normalized)) ? normalized : "totp";
    }

    private String normalizeSensitiveMethod(String raw) {
        if (raw == null) {
            return "password";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("email_code".equals(normalized) || "emailcode".equals(normalized) || "emailcode".equals(normalized.replace("-", ""))) {
            return "email-code";
        }
        if ("password".equals(normalized)
                || "email-code".equals(normalized)
                || "passkey".equals(normalized)
                || "totp".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private int methodPriority(String method, String preferred) {
        if (method == null) {
            return Integer.MAX_VALUE;
        }
        if (preferred != null && method.equals(preferred)) {
            return 0;
        }
        return switch (method) {
            case "totp" -> 1;
            case "passkey" -> 2;
            case "qr" -> 3;
            case "password" -> 4;
            case "email-code" -> 5;
            default -> 10;
        };
    }
}
