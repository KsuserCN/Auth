# 验证码系统设计约束

## 核心原则
本文档定义了邮箱验证码系统的强制性设计约束。**任何新增的验证码功能或相关扩展都必须遵守这些约束。**

## 必须包含的四个核心功能

### 1. 邮箱校验（Email Validation）
**目的**：防止用户使用错误邮箱或邮箱转换攻击

**实现要求**：
- 发送验证码时：记录邮箱至 Redis `verification:email:{email}`，有效期与验证码相同（10分钟）
- 验证验证码时：从 Redis 获取存储的邮箱，与请求中的邮箱进行字符串相等比较
- 不匹配时：返回错误代码 5，错误信息包括当前错误计数

**错误码**：5 - 邮箱不匹配
```json
{
  "code": 400,
  "msg": "邮箱不匹配（X/5）"
}
```

**Redis Key 模式**：
```
verification:email:{email} = "{email}"
```

---

### 2. IP 校验（IP Validation）
**目的**：防止验证码被转发、共享或在不同设备间使用

**实现要求**：
- 发送验证码时：
  - 调用 `RateLimitService.getClientIp(request)` 获取客户端真实 IP
  - 记录 IP 至 Redis `verification:ip:{email}`，有效期与验证码相同（10分钟）
- 验证验证码时：
  - 获取当前请求的客户端 IP
  - 从 Redis 获取存储的 IP，与当前 IP 进行字符串相等比较
  - 不匹配时：返回错误代码 6，错误信息包括当前错误计数

**错误码**：6 - IP 不匹配
```json
{
  "code": 400,
  "msg": "发送验证码的设备与当前设备不匹配（X/5）"
}
```

**IP 获取优先级**（已在 RateLimitService 中实现）：
1. X-Forwarded-For（代理/负载均衡器）
2. X-Real-IP（代理）
3. request.getRemoteAddr()（直连）

**Redis Key 模式**：
```
verification:ip:{email} = "{ip}"
```

---

### 3. 频率限制（Rate Limiting）
**目的**：防止暴力攻击和滥用

**实现要求**：
- **分钟限制**：同一邮箱每分钟最多 1 次；同一 IP 每分钟最多 3 次
- **小时限制**：同一邮箱或 IP 每小时最多 14 次
- 使用 `RateLimitService.isEmailAllowed()` / `RateLimitService.isIpAllowed()` 进行检查
- 检查失败时，同时检查分钟和小时限制，返回相应错误消息

**HTTP 状态码**：429 Too Many Requests

**错误消息格式**：
```json
{
  "code": 429,
  "msg": "发送过于频繁，请1分钟后再试"  // 分钟限制
}
```
或
```json
{
  "code": 429,
  "msg": "发送次数过多，每小时最多发送14次"  // 小时限制
}
```

**Redis Key 模式**：
```
ratelimit:minute:{identifier}    // TTL: 1分钟
ratelimit:hour:{identifier}      // TTL: 1小时
```

**identifier** 可以是：
- 邮箱地址（用于邮箱限制）
- IP 地址（用于 IP 限制）

---

### 4. 验证码生命周期（Lifecycle Management）
**目的**：确保验证码的安全性、及时性和完整性

**实现要求**：

#### 生成
- 使用 `VerificationCodeService.generateCode()` 生成 6 位随机数字
- 确保使用加密随机数生成器（SecureRandom）

#### 存储
- 使用 Redis 存储，不持久化到数据库
- **验证码**：`verification:code:{email}` 
- **已发送标记**：`verification:sent:{email}`
- **邮箱记录**：`verification:email:{email}`
- **IP 记录**：`verification:ip:{email}`
- **错误计数**：`verification:error:{email}`
- 所有 key **必须设置 10 分钟 TTL**（Redis 自动过期）

#### 有效期
- **10 分钟**：从发送时开始计时
- 过期后自动删除，无需额外清理逻辑
- 过期检查：如果 Redis 中不存在，则视为过期

#### 一次性使用
- 验证成功后，立即删除所有相关 key：
  ```
  DEL verification:code:{email}
  DEL verification:sent:{email}
  DEL verification:email:{email}
  DEL verification:ip:{email}
  DEL verification:error:{email}
  ```
- 使用已验证的验证码再次验证，返回错误代码 1（验证码已过期）

#### 错误处理
- 验证码过期：返回错误代码 1
- 验证码未发送：返回错误代码 4
- 验证码错误：返回错误代码 2
- 邮箱被锁定：返回错误代码 3
- 邮箱不匹配：返回错误代码 5
- IP 不匹配：返回错误代码 6

#### 错误计数与锁定
- 每次验证失败（代码 1、2、5、6）都 +1 错误计数
- 错误计数达到 5 次时，锁定邮箱：
  ```
  verification:lock:{email} = "locked"
  TTL: 1 小时
  ```
- 锁定期间返回错误代码 3（邮箱被锁定）
- 锁定时自动清理所有验证相关 key

---

## Redis Key 完整清单

| Key | 用途 | TTL | 说明 |
|-----|------|-----|------|
| `verification:code:{email}` | 验证码值 | 10分钟 | 6位数字 |
| `verification:sent:{email}` | 已发送标记 | 10分钟 | 值为"1" |
| `verification:email:{email}` | 邮箱记录 | 10分钟 | 验证邮箱一致性 |
| `verification:ip:{email}` | IP记录 | 10分钟 | 验证IP一致性 |
| `verification:error:{email}` | 错误计数 | 10分钟 | 0-4（5时锁定） |
| `verification:lock:{email}` | 锁定标记 | 1小时 | 值为"locked" |
| `ratelimit:minute:{identifier}` | 分钟计数 | 1分钟 | 邮箱最多1次，IP最多3次 |
| `ratelimit:hour:{identifier}` | 小时计数 | 1小时 | 最多14次 |

---

## 错误代码映射表

| 代码 | 含义 | HTTP状态 | 是否计入错误计数 |
|------|------|---------|-------------------|
| 0 | 验证成功 | 200 | 否 |
| 1 | 验证码已过期 | 400 | 是 |
| 2 | 验证码错误 | 400 | 是 |
| 3 | 邮箱被锁定 | 429 | 否 |
| 4 | 未发送验证码 | 400 | 否 |
| 5 | 邮箱不匹配 | 400 | 是 |
| 6 | IP 不匹配 | 400 | 是 |

---

## 支持的验证码类型

当前支持的验证码类型在 `SendCodeRequest.type` 中定义：

- **register**：用于用户注册
  - 检查：邮箱是否已被注册（409 Conflict）
  - Redis Key：`verification:code:{email}` （不区分类型）
  
- **login**：用于验证码登录
  - 检查：邮箱是否存在（400 Bad Request）
  - Redis Key：`verification:code:{email}` （不区分类型）

- **change-email**：用于更改邮箱
  - 检查：新邮箱是否已被使用（409 Conflict）
  - Redis Key：`verification:code:{email}` （不区分类型）

- **sensitive-verification**（新增）：用于敏感操作邮箱验证
  - 特点：单独存储，与其他类型隔离，不与登录验证码混淆
  - Redis Key：`verification:code:{email}:sensitive-verification` （区分类型）
  - 使用方式：在 `/auth/verify-sensitive` 中通过 `verifyCodeForType()` 方法验证
  - 关键好处：
    - 敏感操作验证码独立有效期，不被登录验证码失效影响
    - 增强安全性，防止不同场景验证码混淆
    - 支持用户同时接收登录和敏感操作验证码，互不干扰

### 验证码类型隔离机制

为了确保不同用途的验证码互不干扰，**sensitive-verification** 类型采用独立存储：

**普通类型（register、login、change-email）**：
```
verification:code:{email} = "123456"
verification:sent:{email} = "1"
verification:email:{email} = "{email}"
verification:ip:{email} = "{ip}"
```

**敏感操作类型（sensitive-verification）**：
```
verification:code:{email}:sensitive-verification = "123456"
verification:sent:{email}:sensitive-verification = "1"
verification:email:{email}:sensitive-verification = "{email}"
verification:ip:{email}:sensitive-verification = "{ip}"
```

**验证方法**：
- 普通类型使用 `verifyCode(email, code, clientIp)`
- 敏感操作类型使用 `verifyCodeForType(email, code, clientIp, "sensitive-verification")`

### 扩展验证码类型的要求
如果未来需要添加新的验证码类型（如密码重置、身份验证等），**必须**：
1. 在 `SendCodeRequest.type` 中添加新类型值
2. 在 send-code 接口中添加该类型的前置校验逻辑
3. 根据是否需要隔离，选择使用 `saveCode()` 或 `saveCodeWithType()` 方法
4. 在相应业务接口中使用 `verifyCode()` 或 `verifyCodeForType()` 进行验证
```
3. **必须保留上述四个核心功能**（邮箱校验、IP校验、频率限制、生命周期）
4. 在 `AuthController` 对应的验证接口中：
   - 调用 `VerificationCodeService.verifyCode(email, code, clientIp)`
   - 处理所有 6 个返回代码（0-6）
   - 返回对应的错误消息

---

## 实现清单

### VerificationCodeService
- [x] `generateCode()` - 生成6位数字验证码
- [x] `saveCode(email, code, clientIp)` - 保存验证码和相关信息
- [x] `verifyCode(email, code, clientIp)` - 验证验证码（返回0-6）
- [x] `isLocked(email)` - 检查邮箱是否被锁定
- [x] `getErrorCount(email)` - 获取错误计数

### RateLimitService
- [x] `isAllowed(identifier)` - 检查频率限制
- [x] `recordRequest(identifier)` - 记录请求
- [x] `getClientIp(request)` - 获取客户端IP
- [x] `getRemainingMinuteRequests(identifier)` - 获取剩余分钟配额
- [x] `getRemainingHourRequests(identifier)` - 获取剩余小时配额

### AuthController 接口
- [x] `/auth/send-code` - 发送验证码（支持 register/login/change-email）
- [x] `/auth/register` - 注册（验证邮箱、IP、验证码）
- [x] `/auth/login-with-code` - 验证码登录（验证邮箱、IP、验证码）
- [x] `/auth/change-email` - 更改邮箱（验证邮箱、IP、验证码）

---

## 安全指南

1. **不要绕过任何核心功能** - 即使为了兼容性也不行
2. **始终验证 IP 一致性** - 防止跨设备验证码滥用
3. **始终验证邮箱一致性** - 防止邮箱转换攻击
4. **始终遵守频率限制** - 防止暴力破解
5. **不要存储验证码日志** - 防止信息泄露
6. **不要跳过错误计数** - 确保锁定机制生效
7. **使用 HTTPS** - 在生产环境中强制使用（需在配置中设置 `cookie.setSecure(true)`）

---

## 版本历史

| 日期 | 版本 | 描述 |
|------|------|------|
| 2026-02-01 | 1.0 | 初始版本，定义四个核心功能和完整的验证码系统设计 |
| 2026-02-01 | 1.1 | 添加 change-email 验证码类型支持 |
