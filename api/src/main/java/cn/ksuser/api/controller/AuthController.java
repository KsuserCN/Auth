package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.TokenBlacklistService;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.JwtUtil;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

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

    public AuthController(UserService userService, UserSessionService userSessionService, JwtUtil jwtUtil,
                          EmailService emailService, VerificationCodeService verificationCodeService,
                          RateLimitService rateLimitService, SensitiveOperationService sensitiveOperationService,
                          TokenBlacklistService tokenBlacklistService, SecurityValidator securityValidator,
                          AppProperties appProperties) {
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
    }

    /**
     * 健康检查/初始化接口（用于获取 CSRF Token）
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Void>> health(HttpServletRequest request, HttpServletResponse response) {
        // 触发 CSRF Token 生成与下发
        org.springframework.security.web.csrf.CsrfToken csrf = 
            (org.springframework.security.web.csrf.CsrfToken) request.getAttribute(
                org.springframework.security.web.csrf.CsrfToken.class.getName());
        if (csrf != null) {
            // Token 会通过 Set-Cookie 自动下发
            csrf.getToken();
        }
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "服务正常"));
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

        // 注册验证码：检查邮箱是否已注册
        if ("register".equals(type) && userService.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "邮箱已被注册"));
        }

        // 登录验证码：检查邮箱是否存在
        if ("login".equals(type) && userService.findByEmail(email).isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不存在"));
        }

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
                                                                    HttpServletRequest request) {
        String username = registerRequest.getUsername();
        String email = registerRequest.getEmail();
        String password = registerRequest.getPassword();
        String code = registerRequest.getCode();
        String clientIp = rateLimitService.getClientIp(request);

        // 参数校验
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "用户名不能为空"));
        }
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不能为空"));
        }
        if (!securityValidator.isValidEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱格式不正确"));
        }
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码不能为空"));
        }
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证码不能为空"));
        }

        // 使用 SecurityValidator 进行安全验证
        if (!securityValidator.isValidUsername(username)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "用户名格式不正确（3-20字符，仅字母数字下划线连字符）"));
        }

        if (!securityValidator.isValidEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱格式不正确"));
        }

        if (!securityValidator.isValidPasswordLength(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码长度必须在" + appProperties.getPassword().getMinLength() + 
                    "-" + appProperties.getPassword().getMaxLength() + "个字符之间"));
        }

        if (!securityValidator.isStrongPassword(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, buildPasswordRequirementMessage()));
        }

        if (securityValidator.isCommonWeakPassword(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码过于简单，请使用更复杂的密码"));
        }

        // 检查 SQL 注入
        if (securityValidator.possibleSqlInjection(username) || 
            securityValidator.possibleSqlInjection(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "输入包含非法字符"));
        }

        // 验证验证码
        int verifyResult = verificationCodeService.verifyCode(email, code, clientIp);
        if (verifyResult != 0) {
            // 0 = 成功，1 = 已过期，2 = 错误，3 = 被锁定，4 = 未发送，5 = 邮箱不匹配，6 = IP不匹配
            if (verifyResult == 3) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
            } else if (verifyResult == 4) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "请先获取验证码"));
            } else if (verifyResult == 5) {
                int errorCount = verificationCodeService.getErrorCount(email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "邮箱不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 6) {
                int errorCount = verificationCodeService.getErrorCount(email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "发送验证码的设备与当前设备不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 1) {
                // 验证码已过期，但需要检查是否因错误次数导致锁定
                if (verificationCodeService.isLocked(email)) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码已过期，请重新获取（" + errorCount + "/5）"));
            } else {
                if (verificationCodeService.isLocked(email)) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码错误（" + errorCount + "/5）"));
            }
        }

        // 执行注册
        RegisterResult result = userService.register(username, email, password);
        if (result.getStatus() == RegisterResult.Status.USERNAME_EXISTS) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "用户名已存在"));
        }
        if (result.getStatus() == RegisterResult.Status.BAD_REQUEST) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "key 不支持"));
        }
        if (result.getStatus() == RegisterResult.Status.EMAIL_EXISTS) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "邮箱已存在"));
        }

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "注册成功", RegisterResponse.fromUser(result.getUser())));
    }

    /**
     * 验证码登录接口
     * @param loginCodeRequest 验证码登录请求
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @return ApiResponse
     */
    @PostMapping("/login-with-code")
    public ResponseEntity<ApiResponse<TokenResponse>> loginWithCode(@RequestBody LoginCodeRequest loginCodeRequest,
                                                                     HttpServletRequest request,
                                                                     HttpServletResponse response) {
        String email = loginCodeRequest.getEmail();
        String code = loginCodeRequest.getCode();
        String clientIp = rateLimitService.getClientIp(request);

        // 参数校验
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不能为空"));
        }
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证码不能为空"));
        }

        // 邮箱格式验证
        if (!securityValidator.isValidEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱格式不正确"));
        }

        // 验证验证码
        int verifyResult = verificationCodeService.verifyCode(email, code, clientIp);
        if (verifyResult != 0) {
            // 0 = 成功，1 = 已过期，2 = 错误，3 = 被锁定，4 = 未发送，5 = 邮箱不匹配，6 = IP不匹配
            if (verifyResult == 3) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
            } else if (verifyResult == 4) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "请先获取验证码"));
            } else if (verifyResult == 5) {
                int errorCount = verificationCodeService.getErrorCount(email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "邮箱不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 6) {
                int errorCount = verificationCodeService.getErrorCount(email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "发送验证码的设备与当前设备不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 1) {
                // 验证码已过期，但需要检查是否因错误次数导致锁定
                if (verificationCodeService.isLocked(email)) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码已过期，请重新获取（" + errorCount + "/5）"));
            } else {
                if (verificationCodeService.isLocked(email)) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码错误（" + errorCount + "/5）"));
            }
        }

        // 查找用户
        User user = userService.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "邮箱或验证码错误"));
        }

        // 生成 Token
        String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());

        // 保存会话到数据库
        UserSession session = userSessionService.createSession(user, refreshToken);
        int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
        String accessToken = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

        // 将 RefreshToken 设置到 HttpOnly Cookie (包含 SameSite=Strict CSRF 保护)
        setRefreshTokenCookie(response, refreshToken);

        // 登录成功后清除所有速率限制
        rateLimitService.clearAllLimits(email, clientIp);

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
    public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody LoginRequest loginRequest, 
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();
        String clientIp = rateLimitService.getClientIp(request);

        // 参数校验
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不能为空"));
        }
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码不能为空"));
        }

        // 速率限制检查
        if (!rateLimitService.isEmailAllowed(email, RateLimitService.TYPE_LOGIN)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, "登录请求过于频繁，请稍后再试"));
        }
        if (!rateLimitService.isIpAllowed(clientIp, RateLimitService.TYPE_LOGIN)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, "登录请求过于频繁，请稍后再试"));
        }

        // 记录请求
        rateLimitService.recordEmailRequest(email, RateLimitService.TYPE_LOGIN);
        rateLimitService.recordIpRequest(clientIp, RateLimitService.TYPE_LOGIN);

        // 执行登录
        User user = userService.login(email, password).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "邮箱或密码错误"));
        }

        // 生成 Token
        String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());

        // 保存会话到数据库
        UserSession session = userSessionService.createSession(user, refreshToken);
        int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
        String accessToken = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

        // 将 RefreshToken 设置到 HttpOnly Cookie (包含 SameSite=Strict CSRF 保护)
        setRefreshTokenCookie(response, refreshToken);

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
                user.getUpdatedAt()
            );
        } else {
            userInfo = new UserInfoResponse(
                user.getUuid(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl()
            );
        }

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "获取成功", userInfo));
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

        String method = verifySensitiveOperationRequest.getMethod();

        // 参数校验
        if (method == null || method.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证方式不能为空"));
        }
        if (!("password".equals(method) || "email-code".equals(method))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证方式只能是 password 或 email-code"));
        }

        String clientIp = rateLimitService.getClientIp(request);

        // 密码验证
        if ("password".equals(method)) {
            String password = verifySensitiveOperationRequest.getPassword();
            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "密码不能为空"));
            }

            if (!userService.verifyPassword(password, user.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "密码错误"));
            }

            // 验证成功，标记用户已验证
            sensitiveOperationService.markVerified(uuid, clientIp);
            verificationCodeService.clearLockAndError(user.getEmail());
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "验证成功，有效期15分钟"));
        }

        // 邮箱验证码验证
        if ("email-code".equals(method)) {
            String code = verifySensitiveOperationRequest.getCode();
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码不能为空"));
            }

            String email = user.getEmail();
            // 使用 "sensitive-verification" 类型的验证码进行验证
            int verifyResult = verificationCodeService.verifyCodeForType(email, code, clientIp, "sensitive-verification");
            
            if (verifyResult != 0) {
                // 0 = 成功，1 = 已过期，2 = 错误，3 = 被锁定，4 = 未发送，5 = 邮箱不匹配，6 = IP不匹配
                if (verifyResult == 3) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                } else if (verifyResult == 4) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "请先获取验证码"));
                } else if (verifyResult == 5) {
                    int errorCount = verificationCodeService.getErrorCount(email);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "邮箱不匹配（" + errorCount + "/5）"));
                } else if (verifyResult == 6) {
                    int errorCount = verificationCodeService.getErrorCount(email);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "发送验证码的设备与当前设备不匹配（" + errorCount + "/5）"));
                } else if (verifyResult == 1) {
                    if (verificationCodeService.isLocked(email)) {
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                    }
                    int errorCount = verificationCodeService.getErrorCount(email);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "验证码已过期，请重新获取（" + errorCount + "/5）"));
                } else {
                    if (verificationCodeService.isLocked(email)) {
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                    }
                    int errorCount = verificationCodeService.getErrorCount(email);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "验证码错误（" + errorCount + "/5）"));
                }
            }

            // 验证成功，标记用户已验证
            sensitiveOperationService.markVerified(uuid, clientIp);
            verificationCodeService.clearLockAndError(email);
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "验证成功，有效期15分钟"));
        }

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
        String newEmail = changeEmailRequest.getNewEmail();
        String code = changeEmailRequest.getCode();

        if (newEmail == null || newEmail.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "新邮箱不能为空"));
        }
        if (!securityValidator.isValidEmail(newEmail)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱格式不正确"));
        }
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证码不能为空"));
        }

        // 检查新邮箱是否已被使用
        if (userService.findByEmail(newEmail).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "邮箱已被使用"));
        }

        // 验证新邮箱的验证码
        int verifyResult = verificationCodeService.verifyCode(newEmail, code, clientIp);
        if (verifyResult != 0) {
            // 0 = 成功，1 = 已过期，2 = 错误，3 = 被锁定，4 = 未发送，5 = 邮箱不匹配，6 = IP不匹配
            if (verifyResult == 3) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
            } else if (verifyResult == 4) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "请先获取验证码"));
            } else if (verifyResult == 5) {
                int errorCount = verificationCodeService.getErrorCount(newEmail);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "邮箱不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 6) {
                int errorCount = verificationCodeService.getErrorCount(newEmail);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "发送验证码的设备与当前设备不匹配（" + errorCount + "/5）"));
            } else if (verifyResult == 1) {
                if (verificationCodeService.isLocked(newEmail)) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(newEmail);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码已过期，请重新获取（" + errorCount + "/5）"));
            } else {
                if (verificationCodeService.isLocked(newEmail)) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
                }
                int errorCount = verificationCodeService.getErrorCount(newEmail);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "验证码错误（" + errorCount + "/5）"));
            }
        }

        // 更新邮箱
        user.setEmail(newEmail);
        User updatedUser = userService.save(user);
        
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
        
        SensitiveVerificationStatusResponse response = new SensitiveVerificationStatusResponse(
            isVerified, 
            remainingSeconds > 0 ? remainingSeconds : 0
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
        if (confirmText == null || !confirmText.equals("DELETE")) {
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
            tokenBlacklistService.addToBlacklist(accessToken);
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
        String newPassword = changePasswordRequest.getNewPassword();

        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "新密码不能为空"));
        }

        if (!securityValidator.isValidPasswordLength(newPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码长度必须在" + appProperties.getPassword().getMinLength() + 
                    "-" + appProperties.getPassword().getMaxLength() + "个字符之间"));
        }

        if (!securityValidator.isStrongPassword(newPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, buildPasswordRequirementMessage()));
        }

        if (securityValidator.isCommonWeakPassword(newPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码过于简单，请使用更复杂的密码"));
        }

        // 更新密码
        userService.updatePassword(user, newPassword);

        // 清除敏感操作验证状态
        sensitiveOperationService.clearVerification(uuid);
        
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
        Cookie cookie = new Cookie("refreshToken", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(!appProperties.isDebug());
        cookie.setPath("/");
        if (token != null && !token.isEmpty()) {
            cookie.setMaxAge(604800); // 7天
        } else {
            cookie.setMaxAge(0); // 清除 cookie
        }
        response.addCookie(cookie);
        
        // 添加 SameSite=Strict 属性用于 CSRF 保护
        // 由于 javax.servlet.http.Cookie 不直接支持 SameSite，通过 Set-Cookie 响应头设置
        String sameSiteValue = "SameSite=Strict";
        String setCookieHeader = String.format("refreshToken=%s; Path=/; HttpOnly; %s%s; Max-Age=%d",
            token != null ? token : "",
            cookie.getSecure() ? "Secure; " : "",
            sameSiteValue,
            cookie.getMaxAge()
        );
        response.addHeader("Set-Cookie", setCookieHeader);
    }
}
