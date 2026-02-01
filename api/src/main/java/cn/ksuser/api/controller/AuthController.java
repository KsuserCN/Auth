package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
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

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final UserSessionService userSessionService;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;
    private final RateLimitService rateLimitService;

    public AuthController(UserService userService, UserSessionService userSessionService, JwtUtil jwtUtil,
                          EmailService emailService, VerificationCodeService verificationCodeService,
                          RateLimitService rateLimitService) {
        this.userService = userService;
        this.userSessionService = userSessionService;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.verificationCodeService = verificationCodeService;
        this.rateLimitService = rateLimitService;
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
     * 发送验证码（用于注册或登录）
     * @param request HttpServletRequest
     * @param sendCodeRequest 发送验证码请求
     * @return ApiResponse
     */
    @PostMapping("/send-code")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(HttpServletRequest request,
                                                                    @RequestBody SendCodeRequest sendCodeRequest) {
        String email = sendCodeRequest.getEmail();
        String type = sendCodeRequest.getType(); // "register" 或 "login"

        // 参数校验
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不能为空"));
        }
        if (type == null || type.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "类型不能为空"));
        }
        if (!("register".equals(type) || "login".equals(type))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "类型只能是 register 或 login"));
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

        // 检查邮箱是否被锁定
        if (verificationCodeService.isLocked(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, "验证码错误次数过多，该邮箱已被锁定1小时"));
        }

        // 获取IP
        String clientIp = rateLimitService.getClientIp(request);

        // 检查IP限流
        if (!rateLimitService.isAllowed(clientIp)) {
            int remainingMinute = rateLimitService.getRemainingMinuteRequests(clientIp);
            if (remainingMinute == 0) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "发送过于频繁，请1分钟后再试"));
            } else {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "发送次数过多，每小时最多发送6次"));
            }
        }

        // 检查邮箱限流
        if (!rateLimitService.isAllowed(email)) {
            int remainingMinute = rateLimitService.getRemainingMinuteRequests(email);
            if (remainingMinute == 0) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "发送过于频繁，请1分钟后再试"));
            } else {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(429, "该邮箱发送次数过多，每小时最多发送6次"));
            }
        }

        // 生成并保存验证码
        String code = verificationCodeService.generateCode();
        String action = "register".equals(type) ? "注册账号" : "登录账号";
        verificationCodeService.saveCode(email, code, clientIp);

        // 发送邮件
        try {
            emailService.sendVerificationCode(email, code, action);
        } catch (MessagingException | UnsupportedEncodingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "邮件发送失败，请稍后重试"));
        }

        // 记录限流
        rateLimitService.recordRequest(clientIp);
        rateLimitService.recordRequest(email);

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
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码不能为空"));
        }
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "验证码不能为空"));
        }

        // 密码长度校验
        if (password.length() < 6 || password.length() > 66) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码长度必须在6-66个字符之间"));
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

        // 将 RefreshToken 设置到 HttpOnly Cookie
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 生产环境应该设置为 true
        cookie.setPath("/");
        cookie.setMaxAge(604800); // 7天
        response.addCookie(cookie);

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
    public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();

        // 参数校验
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不能为空"));
        }
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码不能为空"));
        }

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

        // 将 RefreshToken 设置到 HttpOnly Cookie
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 生产环境应该设置为 true
        cookie.setPath("/");
        cookie.setMaxAge(604800); // 7天
        response.addCookie(cookie);

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "登录成功", new TokenResponse(accessToken)));
    }

    /**
     * 获取当前用户信息
     * @param authentication 认证信息
     * @return ApiResponse
     */
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<UserInfoResponse>> info(Authentication authentication) {
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

        UserInfoResponse userInfo = new UserInfoResponse(
            user.getUuid(),
            user.getUsername(),
            user.getEmail(),
            user.getAvatarUrl()
        );

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "获取成功", userInfo));
    }

    /**
     * 刷新 AccessToken
     * @param request HttpServletRequest
     * @return ApiResponse
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(HttpServletRequest request) {
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

        // 验证 RefreshToken
        UserSession session = userSessionService.verifyRefreshToken(user, refreshToken).orElse(null);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken无效或已过期"));
        }

        // 刷新 sessionVersion，使旧 AccessToken 立即失效
        UserSession updatedSession = userSessionService.bumpSessionVersion(session);
        int newSessionVersion = updatedSession.getSessionVersion() == null ? 0 : updatedSession.getSessionVersion();

        // 生成新的 AccessToken
        String newAccessToken = jwtUtil.generateAccessToken(uuid, updatedSession.getId(), newSessionVersion);

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

        // 清除 RefreshToken Cookie
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

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

        // 清除 RefreshToken Cookie
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "已从所有设备退出登录"));
    }
}
