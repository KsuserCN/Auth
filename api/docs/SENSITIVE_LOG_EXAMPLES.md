# 敏感日志集成示例

本文档提供在现有Controller中集成敏感日志记录的具体示例。

## 示例1：在AuthController中集成登录日志

```java
package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.SensitiveLogUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private SensitiveLogUtil sensitiveLogUtil;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, 
                                     HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        User user = null;
        
        try {
            // 执行注册逻辑
            user = userService.register(request);
            
            // 记录成功日志
            sensitiveLogUtil.logRegister(httpRequest, user.getId(), true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "Registration successful", null));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logRegister(httpRequest, user != null ? user.getId() : null, 
                                        false, e.getMessage(), startTime);
            throw e;
        }
    }

    /**
     * 密码登录
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, 
                                  HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        Long userId = null;
        
        try {
            // 执行登录逻辑
            LoginResponse response = userService.login(request);
            userId = response.getUserId();
            
            // 记录成功日志 - 注意登录方式为 "PASSWORD"
            sensitiveLogUtil.logLogin(httpRequest, userId, "PASSWORD", true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "Login successful", response));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logLogin(httpRequest, userId, "PASSWORD", false, e.getMessage(), startTime);
            throw e;
        }
    }

    /**
     * 邮箱验证码登录
     */
    @PostMapping("/login-with-code")
    public ResponseEntity<?> loginWithCode(@RequestBody LoginWithCodeRequest request, 
                                          HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        Long userId = null;
        
        try {
            // 执行验证码登录逻辑
            LoginResponse response = userService.loginWithCode(request);
            userId = response.getUserId();
            
            // 记录成功日志 - 注意登录方式为 "EMAIL_CODE"
            sensitiveLogUtil.logLogin(httpRequest, userId, "EMAIL_CODE", true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "Login successful", response));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logLogin(httpRequest, userId, "EMAIL_CODE", false, e.getMessage(), startTime);
            throw e;
        }
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestHeader("Authorization") String authHeader,
                                           @RequestBody ChangePasswordRequest request,
                                           HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        User user = jwtUtil.validateAccessTokenAndGetUser(authHeader);
        
        if (user == null) {
            sensitiveLogUtil.logChangePassword(httpRequest, null, false, "Invalid token", startTime);
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>("error", "Invalid or expired token", null));
        }
        
        try {
            // 执行修改密码逻辑
            userService.changePassword(user, request);
            
            // 记录成功日志
            sensitiveLogUtil.logChangePassword(httpRequest, user.getId(), true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "Password changed successfully", null));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logChangePassword(httpRequest, user.getId(), false, e.getMessage(), startTime);
            throw e;
        }
    }

    /**
     * 修改邮箱
     */
    @PostMapping("/change-email")
    public ResponseEntity<?> changeEmail(@RequestHeader("Authorization") String authHeader,
                                        @RequestBody ChangeEmailRequest request,
                                        HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        User user = jwtUtil.validateAccessTokenAndGetUser(authHeader);
        
        if (user == null) {
            sensitiveLogUtil.logChangeEmail(httpRequest, null, false, "Invalid token", startTime);
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>("error", "Invalid or expired token", null));
        }
        
        try {
            // 执行修改邮箱逻辑
            userService.changeEmail(user, request);
            
            // 记录成功日志
            sensitiveLogUtil.logChangeEmail(httpRequest, user.getId(), true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "Email changed successfully", null));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logChangeEmail(httpRequest, user.getId(), false, e.getMessage(), startTime);
            throw e;
        }
    }
}
```

## 示例2：在PasskeyController中集成日志

```java
package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.service.PasskeyService;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/passkey")
public class PasskeyController {

    @Autowired
    private PasskeyService passkeyService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private SensitiveLogUtil sensitiveLogUtil;

    /**
     * Passkey登录验证
     */
    @PostMapping("/authentication/verify")
    public ResponseEntity<?> verifyAuthentication(@RequestBody PasskeyAuthenticationVerifyRequest request,
                                                  HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        Long userId = null;
        
        try {
            // 执行Passkey验证和登录
            LoginResponse response = passkeyService.verifyAuthentication(request);
            userId = response.getUserId();
            
            // 检查是否需要MFA
            boolean requiresMfa = response.getRequiresMfa() != null && response.getRequiresMfa();
            String loginMethod = requiresMfa ? "PASSKEY_MFA" : "PASSKEY";
            
            // 记录成功日志
            sensitiveLogUtil.logLogin(httpRequest, userId, loginMethod, true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "Authentication successful", response));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logLogin(httpRequest, userId, "PASSKEY", false, e.getMessage(), startTime);
            throw e;
        }
    }

    /**
     * 注册新的Passkey
     */
    @PostMapping("/registration/verify")
    public ResponseEntity<?> verifyRegistration(@RequestHeader("Authorization") String authHeader,
                                               @RequestBody PasskeyRegistrationVerifyRequest request,
                                               HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        User user = jwtUtil.validateAccessTokenAndGetUser(authHeader);
        
        if (user == null) {
            sensitiveLogUtil.logAddPasskey(httpRequest, null, false, "Invalid token", startTime);
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>("error", "Invalid or expired token", null));
        }
        
        try {
            // 执行Passkey注册逻辑
            passkeyService.verifyRegistration(user, request);
            
            // 记录成功日志
            sensitiveLogUtil.logAddPasskey(httpRequest, user.getId(), true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "Passkey registered successfully", null));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logAddPasskey(httpRequest, user.getId(), false, e.getMessage(), startTime);
            throw e;
        }
    }

    /**
     * 删除Passkey
     */
    @DeleteMapping("/{passkeyId}")
    public ResponseEntity<?> deletePasskey(@RequestHeader("Authorization") String authHeader,
                                          @PathVariable Long passkeyId,
                                          HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        User user = jwtUtil.validateAccessTokenAndGetUser(authHeader);
        
        if (user == null) {
            sensitiveLogUtil.logDeletePasskey(httpRequest, null, false, "Invalid token", startTime);
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>("error", "Invalid or expired token", null));
        }
        
        try {
            // 执行删除Passkey逻辑
            passkeyService.deletePasskey(user, passkeyId);
            
            // 记录成功日志
            sensitiveLogUtil.logDeletePasskey(httpRequest, user.getId(), true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "Passkey deleted successfully", null));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logDeletePasskey(httpRequest, user.getId(), false, e.getMessage(), startTime);
            throw e;
        }
    }
}
```

## 示例3：在TotpController中集成日志

```java
package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.service.TotpService;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/totp")
public class TotpController {

    @Autowired
    private TotpService totpService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private SensitiveLogUtil sensitiveLogUtil;

    /**
     * 确认并启用TOTP
     */
    @PostMapping("/registration/verify")
    public ResponseEntity<?> verifyRegistration(@RequestHeader("Authorization") String authHeader,
                                               @RequestBody TotpVerifyRequest request,
                                               HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        User user = jwtUtil.validateAccessTokenAndGetUser(authHeader);
        
        if (user == null) {
            sensitiveLogUtil.logEnableTotp(httpRequest, null, false, "Invalid token", startTime);
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>("error", "Invalid or expired token", null));
        }
        
        try {
            // 执行TOTP启用逻辑
            TotpVerifyResponse response = totpService.verifyRegistration(user, request);
            
            // 记录成功日志
            sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "TOTP enabled successfully", response));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), false, e.getMessage(), startTime);
            throw e;
        }
    }

    /**
     * 禁用TOTP
     */
    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestHeader("Authorization") String authHeader,
                                    HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        User user = jwtUtil.validateAccessTokenAndGetUser(authHeader);
        
        if (user == null) {
            sensitiveLogUtil.logDisableTotp(httpRequest, null, false, "Invalid token", startTime);
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>("error", "Invalid or expired token", null));
        }
        
        try {
            // 执行TOTP禁用逻辑
            totpService.disable(user);
            
            // 记录成功日志
            sensitiveLogUtil.logDisableTotp(httpRequest, user.getId(), true, null, startTime);
            
            return ResponseEntity.ok(new ApiResponse<>("success", "TOTP disabled successfully", null));
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logDisableTotp(httpRequest, user.getId(), false, e.getMessage(), startTime);
            throw e;
        }
    }
}
```

## 示例4：在敏感操作验证中集成日志

```java
package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.service.SensitiveOperationService;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class SensitiveOperationController {

    @Autowired
    private SensitiveOperationService sensitiveOperationService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private SensitiveLogUtil sensitiveLogUtil;

    /**
     * 验证敏感操作（密码验证）
     */
    @PostMapping("/verify-sensitive")
    public ResponseEntity<?> verifySensitive(@RequestHeader("Authorization") String authHeader,
                                            @RequestBody VerifySensitiveRequest request,
                                            HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        User user = jwtUtil.validateAccessTokenAndGetUser(authHeader);
        
        if (user == null) {
            sensitiveLogUtil.logSensitiveVerify(httpRequest, null, false, "Invalid token", startTime);
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>("error", "Invalid or expired token", null));
        }
        
        try {
            // 执行敏感操作验证
            boolean verified = sensitiveOperationService.verifyPassword(user, request.getPassword());
            
            if (verified) {
                // 记录成功日志
                sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), true, null, startTime);
                
                // 生成验证token
                String verifyToken = sensitiveOperationService.generateVerifyToken(user);
                return ResponseEntity.ok(new ApiResponse<>("success", "Verification successful", 
                                                          new VerifyResponse(verifyToken)));
            } else {
                // 记录失败日志
                sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), false, 
                                                    "Invalid password", startTime);
                return ResponseEntity.status(401)
                        .body(new ApiResponse<>("error", "Invalid password", null));
            }
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), false, 
                                               e.getMessage(), startTime);
            throw e;
        }
    }

    /**
     * Passkey敏感操作验证
     */
    @PostMapping("/passkey/sensitive-verification/verify")
    public ResponseEntity<?> verifyPasskeySensitive(@RequestHeader("Authorization") String authHeader,
                                                   @RequestBody PasskeySensitiveVerifyRequest request,
                                                   HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        User user = jwtUtil.validateAccessTokenAndGetUser(authHeader);
        
        if (user == null) {
            sensitiveLogUtil.logSensitiveVerify(httpRequest, null, false, "Invalid token", startTime);
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>("error", "Invalid or expired token", null));
        }
        
        try {
            // 执行Passkey敏感操作验证
            boolean verified = sensitiveOperationService.verifyPasskey(user, request);
            
            if (verified) {
                // 记录成功日志
                sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), true, null, startTime);
                
                String verifyToken = sensitiveOperationService.generateVerifyToken(user);
                return ResponseEntity.ok(new ApiResponse<>("success", "Verification successful", 
                                                          new VerifyResponse(verifyToken)));
            } else {
                // 记录失败日志
                sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), false, 
                                                    "Passkey verification failed", startTime);
                return ResponseEntity.status(401)
                        .body(new ApiResponse<>("error", "Verification failed", null));
            }
        } catch (Exception e) {
            // 记录失败日志
            sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), false, 
                                               e.getMessage(), startTime);
            throw e;
        }
    }
}
```

## 关键要点

1. **开始时间记录**：在方法开始时立即记录 `long startTime = System.currentTimeMillis();`

2. **用户ID处理**：确保在catch块中也能访问到userId，可能需要在try块外声明

3. **登录方式**：根据实际登录方式设置正确的loginMethod参数
   - 密码登录：`PASSWORD`
   - 验证码登录：`EMAIL_CODE`
   - Passkey登录：`PASSKEY`
   - Passkey+MFA：`PASSKEY_MFA`

4. **异常处理**：在catch块中记录失败日志，包含异常信息

5. **Token验证失败**：当token无效时，也要记录日志（userId可能为null）

6. **HttpServletRequest**：确保方法参数中包含HttpServletRequest，用于获取IP和User-Agent

7. **失败原因**：简洁明了，避免包含敏感信息（如具体的密码值）
