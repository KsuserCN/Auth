# 项目安全审计报告

## 📋 执行摘要

该 Spring Boot 认证系统已实现了许多安全防护措施，但仍存在需要改进的地方。本报告列出了发现的所有安全问题，按严重性分类。

---

## 🔴 高危问题

### 1. **JWT 密钥硬编码在配置文件中**
**位置**: [application.properties](../src/main/resources/application.properties#L22)
**问题**:
```properties
jwt.secret=${JWT_SECRET:ksuser-very-secret-key-2026-abc}
```
默认密钥 `ksuser-very-secret-key-2026-abc` 是硬编码的、公开的、长度不足（只有 28 字节）。

**风险**: 
- 任何人都可以伪造 JWT Token，完全绕过认证
- 密钥长度只有 28 字节，而 HMAC-SHA256 需要至少 32 字节

**修复建议**:
```properties
# 移除硬编码默认值，强制在生产环境设置
jwt.secret=${JWT_SECRET}
# 或使用更强的默认值
jwt.secret=${JWT_SECRET:xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx}
```

---

### 2. **CORS 在生产环境配置不当**
**位置**: [CorsConfig.java](../src/main/java/cn/ksuser/api/config/CorsConfig.java#L34)
**问题**:
```java
if (appProperties.isDebug()) {
    config.addAllowedOrigin("http://localhost:5173");
} else {
    config.addAllowedOrigin("https://auth.ksuser.cn");
}
```
- 只允许单个特定域名，但未进行硬编码验证
- 如果配置环境变量不对，会导致安全问题
- `allowCredentials=true` 与 CORS 结合有安全隐患

**风险**:
- CORS 配置错误可导致跨域攻击
- 如果域名变更，容易配置错误

**修复建议**:
```java
// 使用配置属性明确指定允许的源
private final List<String> allowedOrigins;

public CorsFilter corsFilter() {
    config.setAllowedOrigins(allowedOrigins); // 从配置读取
    
    // allowCredentials=true 时，不能使用通配符
    // 当前实现正确，但需要确保 allowedOrigins 被正确配置
}
```

在 `application.properties` 中添加:
```properties
app.cors.allowed-origins=https://auth.ksuser.cn,https://www.ksuser.cn
```

---

### 3. **调试模式跳过安全 Header**
**位置**: [SecurityHeadersConfig.java](../src/main/java/cn/ksuser/api/config/SecurityHeadersConfig.java#L39-L42)
**问题**:
```java
if (appProperties.isDebug()) {
    filterChain.doFilter(request, response);
    return; // 完全跳过安全 Header
}
```

**风险**:
- 调试模式仅限本地开发，但如果不小心在生产启用，会移除所有安全 Header
- 包括 HSTS、CSP、X-Frame-Options 等关键防护

**修复建议**:
```java
// 不要完全跳过，只是在调试模式下使用较弱的设置
// 例如放宽 CSP，但保留其他 Header
if (!appProperties.isDebug()) {
    // 应用完整的安全 Header
    response.setHeader("Strict-Transport-Security", 
        "max-age=31536000; includeSubDomains; preload");
    // ...
}
```

---

### 4. **数据库 DDL 自动更新**
**位置**: [application.properties](../src/main/resources/application.properties#L12)
**问题**:
```properties
spring.jpa.hibernate.ddl-auto=update
```

**风险**:
- 生产环境中自动修改数据库结构
- 可能导致意外的数据丢失或性能问题
- 容易引发数据库中断

**修复建议**:
```properties
# 开发环境
spring.jpa.hibernate.ddl-auto=update

# 生产环境
spring.jpa.hibernate.ddl-auto=validate
```

在配置中按环境区分:
```yaml
# application-prod.properties
spring.jpa.hibernate.ddl-auto=validate
```

---

## 🟠 中危问题

### 5. **SQL 注入防护不足**
**位置**: [SecurityValidator.java](../src/main/java/cn/ksuser/api/security/SecurityValidator.java#L138-L200)
**问题**:
```java
public boolean possibleSqlInjection(String input) {
    // 使用黑名单模式检测
    String[] sqlKeywords = {"SELECT", "INSERT", "UPDATE", ...};
    for (String keyword : sqlKeywords) {
        if (upperInput.contains(keyword)) {
            return true;
        }
    }
}
```

**风险**:
- 黑名单方式容易被绕过（例如：`SEL/**/ECT`、`UNION/**/ALL`）
- 关键词检测过于严格，可能误伤合法输入（用户名中可能包含"select"）
- 即使检测到注入，仍在数据库查询前进行，不是根本解决方案

**修复建议**:
```java
// 方案1: 使用 JPA 参数化查询（已在使用）- 最佳实践
// userRepository.findByUsername(username); // ✓ 正确，已参数化

// 方案2: 如果需要客户端验证，使用严格的白名单
public boolean isValidUsername(String username) {
    // 只允许字母、数字、下划线、连字符，长度3-50
    return username != null && username.matches("^[a-zA-Z0-9_-]{3,50}$");
}

// 方案3: 移除不必要的关键词检查，依赖参数化查询
```

**现状评估**: 项目已使用 JPA Repository 进行参数化查询，SQL 注入风险已大幅降低。建议移除不必要的关键词黑名单检查。

---

### 6. **密码存储配置缺陷**
**位置**: [PasswordConfig.java](../src/main/java/cn/ksuser/api/config/PasswordConfig.java)
**问题**:
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
}
```

**风险**:
- 使用默认配置，未对 Argon2 参数进行优化
- 没有配置成本参数（cost）、内存参数（memory）等

**修复建议**:
```java
@Bean
public PasswordEncoder passwordEncoder() {
    // 配置更强的 Argon2 参数
    return new Argon2PasswordEncoder(
        16,      // salt length
        32,      // hash length
        1,       // parallelism
        60000,   // memory (60MB) - 高于默认的 19MB
        3        // iterations - 高于默认的 2
    );
}
```

---

### 7. **敏感操作缺少日志审计**
**位置**: [AuthController.java](../src/main/java/cn/ksuser/api/controller/AuthController.java)
**问题**: 项目缺少对以下敏感操作的日志记录:
- 登录/登出成功/失败
- 密码更改
- 邮箱更改
- 权限变更
- API 错误

**风险**:
- 无法追溯安全事件
- 难以检测攻击行为
- 不符合审计要求

**修复建议**:
```java
// 添加审计日志
private static final Logger auditLogger = LoggerFactory.getLogger("audit");

@PostMapping("/login")
public ResponseEntity<ApiResponse<LoginResponse>> login(...) {
    try {
        // ... 登录逻辑
        User user = userService.findByEmail(email).orElse(null);
        auditLogger.info("LOGIN_SUCCESS user={} ip={}", user.getUuid(), clientIp);
    } catch (Exception e) {
        auditLogger.warn("LOGIN_FAILED email={} ip={} reason={}", email, clientIp, e.getMessage());
    }
}
```

在 `application.properties` 中添加审计日志配置:
```properties
logging.level.audit=INFO
logging.file.name=logs/audit.log
```

---

### 8. **令牌刷新缺少版本控制**
**位置**: [JwtUtil.java](../src/main/java/cn/ksuser/api/util/JwtUtil.java#L34-L45)
**问题**: 
- Access Token 中包含 sessionVersion，但 Refresh Token 不包含
- 刷新时无法验证会话版本的一致性

**风险**:
- 如果会话版本更新（例如修改密码后），旧的 Refresh Token 仍可能被重用
- 攻击者可能使用旧令牌进行重放攻击

**修复建议**:
```java
public String generateRefreshToken(String uuid, int sessionVersion) {
    return Jwts.builder()
            .subject(uuid)
            .claim("type", "refresh")
            .claim("sv", sessionVersion)  // 添加会话版本
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
            .signWith(getSigningKey())
            .compact();
}

// 在刷新时验证
public boolean validateRefreshTokenVersion(String token, int expectedVersion) {
    Claims claims = parseToken(token);
    return claims != null && expectedVersion == ((Number) claims.get("sv")).intValue();
}
```

---

## 🟡 低危问题

### 9. **缺少请求速率限制的全局拦截**
**位置**: [AuthController.java](../src/main/java/cn/ksuser/api/controller/AuthController.java)
**问题**: 
- 速率限制仅在部分端点实现
- `/auth/check-username` 端点无速率限制，可用于用户枚举攻击

**风险**:
- 攻击者可以通过穷举方式发现有效的用户名/邮箱
- 无法防护其他尚未实现限流的新端点

**修复建议**:
```java
// 1. 在 RateLimitService 中为 check-username 添加限流
if (!rateLimitService.isIpAllowed(clientIp)) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(new ApiResponse<>(429, "请求过于频繁"));
}

// 2. 创建全局速率限制拦截器
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String clientIp = getClientIp(request);
        if (!rateLimitService.isGlobalIpAllowed(clientIp)) {
            throw new TooManyRequestsException();
        }
        return true;
    }
}
```

---

### 10. **密码最小长度过短**
**位置**: [application.properties](../src/main/resources/application.properties#L57)
**问题**:
```properties
app.password.min-length=6
```

**风险**:
- 6 字符密码强度不足，容易被破解
- NIST 建议至少 8-12 字符

**修复建议**:
```properties
app.password.min-length=12
```

并更新验证逻辑确保用户了解密码要求。

---

### 11. **缺少 IP 白名单/黑名单管理**
**位置**: [RateLimitService.java](../src/main/java/cn/ksuser/api/service/RateLimitService.java)
**问题**: 
- 无法针对特定 IP 的恶意行为进行快速响应
- 无法在安全事件后快速阻止攻击者 IP

**修复建议**:
```java
@Service
public class IpBlockingService {
    private final StringRedisTemplate redisTemplate;
    
    public void blockIp(String ip, Duration duration, String reason) {
        String key = "ip:blocked:" + ip;
        redisTemplate.opsForValue().set(key, reason, duration);
        auditLogger.warn("IP_BLOCKED ip={} reason={}", ip, reason);
    }
    
    public boolean isBlocked(String ip) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("ip:blocked:" + ip));
    }
}
```

---

### 12. **缺少设备指纹识别**
**位置**: 整个项目
**问题**: 
- 同一个刷新令牌可以从不同设备/浏览器使用
- 无法检测异常登录地点

**风险**:
- 令牌泄露后，攻击者可以从任何地方使用
- 用户无法发现帐户被盗用

**修复建议**:
```java
// 在会话中存储设备指纹
public class UserSession {
    private String deviceFingerprint; // User-Agent hash
    private String clientIp;
    
    public static String generateFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String acceptLang = request.getHeader("Accept-Language");
        return hash(userAgent + "|" + acceptLang);
    }
}

// 登录时验证
if (!session.getDeviceFingerprint().equals(currentFingerprint)) {
    // 可疑登录，发送邮件通知用户
}
```

---

### 13. **缺少 API 速率限制精细化控制**
**位置**: [RateLimitService.java](../src/main/java/cn/ksuser/api/service/RateLimitService.java#L29-L46)
**问题**:
```properties
# 全局限制，无法针对不同端点进行不同配置
app.rate-limit.email-per-minute=1
app.rate-limit.ip-per-minute=3
```

**修复建议**:
```properties
# 按端点配置
app.rate-limit.endpoints.send-code.per-minute=1
app.rate-limit.endpoints.check-username.per-minute=10
app.rate-limit.endpoints.login.per-minute=5
```

---

### 14. **Cookie 安全标志配置缺陷**
**位置**: [application.properties](../src/main/resources/application.properties#L64-L67)
**问题**:
```properties
server.servlet.session.cookie.secure=false  # 开发环境导致
server.servlet.session.cookie.same-site=lax  # 应该是 strict
```

**风险**:
- `secure=false` 即使在生产环境也可能被误用
- `same-site=lax` 对某些 CSRF 攻击的防护不足

**修复建议**:
```properties
# 开发环境 (application-dev.properties)
server.servlet.session.cookie.secure=false
server.servlet.session.cookie.same-site=lax

# 生产环境 (application-prod.properties)
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.same-site=strict
```

---

### 15. **缺少异常活动监测**
**位置**: [GlobalExceptionHandler.java](../src/main/java/cn/ksuser/api/exception/GlobalExceptionHandler.java)
**问题**: 
- 异常处理中无法识别和监测异常活动
- 如大量 401 错误可能表示破解攻击

**修复建议**:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private final AnomalyDetectionService anomalyDetectionService;
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        anomalyDetectionService.recordException(clientIp, ex.getClass().getSimpleName());
        
        // 如果同一 IP 短时间内产生过多异常，触发告警
        if (anomalyDetectionService.isAnomalous(clientIp)) {
            auditLogger.warn("ANOMALY_DETECTED ip={}", clientIp);
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiResponse<>(500, "服务器错误"));
    }
}
```

---

## 🟢 已实现的安全措施（优点）

### ✅ 正面的安全实践

1. **使用参数化查询（JPA）**
   - 有效防止 SQL 注入
   
2. **密码加密（Argon2）**
   - 使用现代密码哈希算法
   
3. **JWT Token 管理**
   - 实现了 Access Token 和 Refresh Token 分离
   - 会话版本控制
   - Token 黑名单机制
   
4. **验证码防护**
   - 错误次数限制和账户锁定
   - 验证码过期机制
   
5. **速率限制**
   - 针对邮箱和 IP 的限流
   - 分钟级和小时级限制
   
6. **安全 Header**
   - X-Content-Type-Options、X-Frame-Options
   - CSP、HSTS、Referrer-Policy 等
   
7. **CORS 配置**
   - 明确指定允许的源（未使用通配符）
   
8. **敏感操作验证**
   - 邮箱变更需要验证
   - 密码变更需要验证

---

## 📊 优先级修复计划

### 🔥 立即修复（第一阶段）
1. JWT 密钥强化 - JWT_SECRET 必须从环境变量读取
2. 数据库 DDL 配置按环境分离
3. 添加审计日志记录

### ⚡ 紧急修复（第二阶段）
4. 安全 Header 调试模式改进
5. 为 Refresh Token 添加会话版本
6. 为 check-username 添加速率限制

### 📋 后续优化（第三阶段）
7. 增强密码安全配置
8. 实现设备指纹识别
9. 添加异常活动监测
10. 完善 IP 黑名单功能

---

## 🔐 安全检查清单

- [ ] 移除 JWT 密钥硬编码默认值
- [ ] 为不同环境分离 DDL 自动更新配置
- [ ] 添加审计日志记录关键操作
- [ ] 为 Refresh Token 添加会话版本验证
- [ ] 为所有端点添加速率限制
- [ ] 增强 Argon2 密码编码器配置
- [ ] 在生产环境启用所有安全 Header
- [ ] 移除不必要的 SQL 注入关键词黑名单检查
- [ ] 添加异常活动监测
- [ ] 实现设备指纹识别机制
- [ ] 编写安全测试用例
- [ ] 定期进行安全审计

---

## 📚 参考资源

- [OWASP Top 10 2021](https://owasp.org/Top10/)
- [NIST 密码指南](https://pages.nist.gov/800-63-3/sp800-63b.html)
- [Spring Security 最佳实践](https://spring.io/projects/spring-security)
- [JWT 安全最佳实践](https://tools.ietf.org/html/rfc8949)

---

**报告生成日期**: 2026-02-03
**审计范围**: Spring Boot 认证 API 系统
**风险等级分布**: 🔴 4 个高危 | 🟠 4 个中危 | 🟡 7 个低危
