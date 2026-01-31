package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.service.UserService;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final UserSessionService userSessionService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, UserSessionService userSessionService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.userSessionService = userSessionService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 注册接口
     * @param registerRequest 注册请求
     * @return ApiResponse
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@RequestBody RegisterRequest registerRequest) {
        String username = registerRequest.getUsername();
        String email = registerRequest.getEmail();
        String password = registerRequest.getPassword();

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
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String accessToken = jwtUtil.generateAccessToken(user.getUuid(), tokenVersion);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());

        // 保存会话到数据库
        userSessionService.createSession(user, refreshToken);

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
        if (!userSessionService.verifyRefreshToken(user, refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, "RefreshToken无效或已过期"));
        }

        // 刷新 tokenVersion，使旧 AccessToken 立即失效
        User updatedUser = userService.bumpTokenVersion(user);
        int newTokenVersion = updatedUser.getTokenVersion() == null ? 0 : updatedUser.getTokenVersion();

        // 生成新的 AccessToken
        String newAccessToken = jwtUtil.generateAccessToken(uuid, newTokenVersion);

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "刷新成功", new RefreshResponse(newAccessToken)));
    }
}
