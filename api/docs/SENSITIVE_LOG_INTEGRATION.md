# 敏感操作日志集成指南

本文档说明如何在现有的业务代码中集成敏感操作日志记录功能。

## 功能概述

敏感操作日志功能会自动记录以下信息：
- 用户ID
- 操作类型（注册、登录、修改密码等）
- 登录方式（仅登录操作）
- IP地址及属地
- User-Agent及解析后的浏览器和设备信息
- 操作结果（成功/失败）
- 失败原因
- 操作耗时
- 风险评分
- 是否触发各种锁定机制

## 使用方法

### 1. 注入依赖

在需要记录日志的Service或Controller中注入 `SensitiveLogUtil`：

```java
@Autowired
private SensitiveLogUtil sensitiveLogUtil;
```

### 2. 记录日志

#### 2.1 记录注册操作

```java
@PostMapping("/register")
public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
    long startTime = System.currentTimeMillis();
    
    try {
        // 执行注册逻辑
        User user = userService.register(request);
        
        // 记录成功日志
        sensitiveLogUtil.logRegister(httpRequest, user.getId(), true, null, startTime);
        
        return ResponseEntity.ok(new ApiResponse<>("success", "Registration successful", null));
    } catch (Exception e) {
        // 记录失败日志
        sensitiveLogUtil.logRegister(httpRequest, null, false, e.getMessage(), startTime);
        throw e;
    }
}
```

#### 2.2 记录登录操作

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    long startTime = System.currentTimeMillis();
    
    try {
        // 执行登录逻辑
        LoginResponse response = authService.login(request);
        
        // 记录成功日志（注意指定登录方式）
        sensitiveLogUtil.logLogin(httpRequest, response.getUserId(), "PASSWORD", true, null, startTime);
        
        return ResponseEntity.ok(new ApiResponse<>("success", "Login successful", response));
    } catch (Exception e) {
        // 记录失败日志
        sensitiveLogUtil.logLogin(httpRequest, null, "PASSWORD", false, e.getMessage(), startTime);
        throw e;
    }
}
```

登录方式常量：
- `PASSWORD` - 密码登录
- `EMAIL_CODE` - 邮箱验证码登录
- `PASSKEY` - Passkey登录
- `PASSKEY_MFA` - Passkey + MFA登录

#### 2.3 记录敏感操作认证

```java
@PostMapping("/verify-sensitive")
public ResponseEntity<?> verifySensitive(@RequestBody VerifyRequest request, HttpServletRequest httpRequest) {
    long startTime = System.currentTimeMillis();
    
    try {
        User user = getCurrentUser();
        // 执行验证逻辑
        boolean verified = sensitiveOperationService.verify(user, request);
        
        if (verified) {
            sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), true, null, startTime);
            return ResponseEntity.ok(new ApiResponse<>("success", "Verification successful", null));
        } else {
            sensitiveLogUtil.logSensitiveVerify(httpRequest, user.getId(), false, "Invalid credentials", startTime);
            return ResponseEntity.status(401).body(new ApiResponse<>("error", "Invalid credentials", null));
        }
    } catch (Exception e) {
        User user = getCurrentUser();
        sensitiveLogUtil.logSensitiveVerify(httpRequest, user != null ? user.getId() : null, false, e.getMessage(), startTime);
        throw e;
    }
}
```

#### 2.4 记录修改密码操作

```java
@PostMapping("/change-password")
public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
    long startTime = System.currentTimeMillis();
    User user = getCurrentUser();
    
    try {
        // 执行修改密码逻辑
        userService.changePassword(user, request);
        
        sensitiveLogUtil.logChangePassword(httpRequest, user.getId(), true, null, startTime);
        
        return ResponseEntity.ok(new ApiResponse<>("success", "Password changed successfully", null));
    } catch (Exception e) {
        sensitiveLogUtil.logChangePassword(httpRequest, user.getId(), false, e.getMessage(), startTime);
        throw e;
    }
}
```

#### 2.5 记录修改邮箱操作

```java
@PostMapping("/change-email")
public ResponseEntity<?> changeEmail(@RequestBody ChangeEmailRequest request, HttpServletRequest httpRequest) {
    long startTime = System.currentTimeMillis();
    User user = getCurrentUser();
    
    try {
        // 执行修改邮箱逻辑
        userService.changeEmail(user, request);
        
        sensitiveLogUtil.logChangeEmail(httpRequest, user.getId(), true, null, startTime);
        
        return ResponseEntity.ok(new ApiResponse<>("success", "Email changed successfully", null));
    } catch (Exception e) {
        sensitiveLogUtil.logChangeEmail(httpRequest, user.getId(), false, e.getMessage(), startTime);
        throw e;
    }
}
```

#### 2.6 记录新增Passkey操作

```java
@PostMapping("/passkey/register")
public ResponseEntity<?> registerPasskey(@RequestBody PasskeyRegisterRequest request, HttpServletRequest httpRequest) {
    long startTime = System.currentTimeMillis();
    User user = getCurrentUser();
    
    try {
        // 执行新增Passkey逻辑
        passkeyService.registerPasskey(user, request);
        
        sensitiveLogUtil.logAddPasskey(httpRequest, user.getId(), true, null, startTime);
        
        return ResponseEntity.ok(new ApiResponse<>("success", "Passkey added successfully", null));
    } catch (Exception e) {
        sensitiveLogUtil.logAddPasskey(httpRequest, user.getId(), false, e.getMessage(), startTime);
        throw e;
    }
}
```

#### 2.7 记录删除Passkey操作

```java
@DeleteMapping("/passkey/{id}")
public ResponseEntity<?> deletePasskey(@PathVariable Long id, HttpServletRequest httpRequest) {
    long startTime = System.currentTimeMillis();
    User user = getCurrentUser();
    
    try {
        // 执行删除Passkey逻辑
        passkeyService.deletePasskey(user, id);
        
        sensitiveLogUtil.logDeletePasskey(httpRequest, user.getId(), true, null, startTime);
        
        return ResponseEntity.ok(new ApiResponse<>("success", "Passkey deleted successfully", null));
    } catch (Exception e) {
        sensitiveLogUtil.logDeletePasskey(httpRequest, user.getId(), false, e.getMessage(), startTime);
        throw e;
    }
}
```

#### 2.8 记录启用TOTP操作

```java
@PostMapping("/totp/enable")
public ResponseEntity<?> enableTotp(@RequestBody EnableTotpRequest request, HttpServletRequest httpRequest) {
    long startTime = System.currentTimeMillis();
    User user = getCurrentUser();
    
    try {
        // 执行启用TOTP逻辑
        totpService.enableTotp(user, request);
        
        sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), true, null, startTime);
        
        return ResponseEntity.ok(new ApiResponse<>("success", "TOTP enabled successfully", null));
    } catch (Exception e) {
        sensitiveLogUtil.logEnableTotp(httpRequest, user.getId(), false, e.getMessage(), startTime);
        throw e;
    }
}
```

#### 2.9 记录禁用TOTP操作

```java
@PostMapping("/totp/disable")
public ResponseEntity<?> disableTotp(HttpServletRequest httpRequest) {
    long startTime = System.currentTimeMillis();
    User user = getCurrentUser();
    
    try {
        // 执行禁用TOTP逻辑
        totpService.disableTotp(user);
        
        sensitiveLogUtil.logDisableTotp(httpRequest, user.getId(), true, null, startTime);
        
        return ResponseEntity.ok(new ApiResponse<>("success", "TOTP disabled successfully", null));
    } catch (Exception e) {
        sensitiveLogUtil.logDisableTotp(httpRequest, user.getId(), false, e.getMessage(), startTime);
        throw e;
    }
}
```

## 最佳实践

### 1. 始终记录开始时间

在方法开始时立即记录开始时间，以便准确计算操作耗时：

```java
long startTime = System.currentTimeMillis();
```

### 2. 在finally块中记录（可选）

如果需要确保日志一定被记录，可以使用try-finally：

```java
long startTime = System.currentTimeMillis();
boolean success = false;
String errorMessage = null;

try {
    // 执行业务逻辑
    doSomething();
    success = true;
} catch (Exception e) {
    errorMessage = e.getMessage();
    throw e;
} finally {
    sensitiveLogUtil.logChangePassword(httpRequest, userId, success, errorMessage, startTime);
}
```

### 3. 异步记录

默认情况下，日志记录是异步的，不会阻塞主业务流程。如果需要同步记录，可以使用：

```java
sensitiveLogUtil.logSync(httpRequest, userId, "OPERATION_TYPE", null, 
                         UserSensitiveLog.OperationResult.SUCCESS, null, startTime);
```

### 4. 失败原因

失败原因应该简洁明了，避免包含敏感信息：

```java
// ✅ 好的失败原因
"Invalid password"
"Email already exists"
"Passkey verification failed"

// ❌ 不好的失败原因（包含敏感信息）
"Password 'abc123' does not match"
"User john@example.com already exists"
```

### 5. 获取HttpServletRequest

在Controller中直接作为方法参数注入：

```java
@PostMapping("/some-endpoint")
public ResponseEntity<?> someMethod(@RequestBody SomeRequest request, 
                                   HttpServletRequest httpRequest) {
    // 使用 httpRequest
}
```

在Service中，可以通过 `RequestContextHolder` 获取：

```java
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
```

## 数据库表结构

敏感操作日志存储在 `user_sensitive_logs` 表中，表结构见 `sql/init.sql`。

## API接口

用户可以通过以下接口查询自己的敏感操作日志：

```
GET /auth/sensitive-logs
```

详细的API文档见 `docs/sensitive-logs.md`。

## 注意事项

1. 日志记录是异步的，失败不会影响主业务流程
2. IP属地信息依赖第三方API，可能会有延迟或失败
3. User-Agent解析使用ua-parser库，支持大多数常见浏览器和设备
4. 日志记录会自动处理NULL值和异常情况
5. 不要在日志中记录密码、token等敏感信息
6. 操作耗时计算包括日志记录本身的时间（异步记录时影响很小）

## 依赖

确保 `build.gradle` 中包含以下依赖：

```gradle
// User-Agent parser for device and browser detection
implementation 'com.github.ua-parser:uap-java:1.6.1'
```

## 配置

确保主应用类启用了异步支持：

```java
@SpringBootApplication
@EnableAsync
public class ApiApplication {
    // ...
}
```
