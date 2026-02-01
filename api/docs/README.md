# 身份认证 API 文档

## 概述
本文档描述了完整的身份认证系统 API，包括：
- 用户注册（邮箱验证）
- 密码登录
- 验证码登录
- 会话管理（多设备支持）
- 令牌刷新和撤销

## 文档导航
- [验证码系统设计约束](VERIFICATION_CODE_DESIGN.md) - 验证码系统的核心功能和设计原则
- [请求类型处理指南](REQUEST_TYPE_HANDLING.md) - Content-Type 验证和错误处理说明

## API 端点

### 1. 检查用户名可用性
[GET /auth/check-username](auth-check-username.md)

检查用户名是否已被注册。

### 2. 发送验证码
[POST /auth/send-code](auth-send-code.md)

发送邮箱验证码，用于注册或登录。支持以下功能：
- 自动区分注册和登录场景
- 邮箱校验（注册检查已注册，登录检查存在）
- IP 和邮箱的速率限制（1/分钟，6/小时）
- 错误次数限制（5次后锁定1小时）

### 3. 注册
[POST /auth/register](auth-register.md)

使用邮箱验证码完成用户注册。特性：
- 验证邮箱和IP与发送验证码时一致
- 密码长度 6-66 字符
- 使用 Argon2id 加密密码
- 密钥存储用户 UUID

### 4. 密码登录
[POST /auth/login](auth-login.md)

使用邮箱和密码登录。

### 5. 验证码登录
[POST /auth/login-with-code](auth-login-with-code.md)

使用邮箱验证码登录。验证流程：
- 验证邮箱和IP与发送验证码时一致
- 验证成功后创建会话并返回令牌

### 6. 获取当前用户信息
[GET /auth/info](auth-info.md)

获取当前登录用户的信息。需要认证。

### 7. 更新用户信息
[POST /auth/update/profile](auth-update-profile.md)

更新用户名和/或头像URL。支持：
- 单独更新用户名或头像
- 同时更新用户名和头像
- 用户名唯一性检查
- 需要认证（AccessToken）

### 8. 刷新令牌
[POST /auth/refresh](auth-refresh.md)

使用 RefreshToken 获取新的 AccessToken。支持多设备会话。

### 9. 退出登录（单设备）
[POST /auth/logout](auth-logout.md)

退出当前设备上的登录。

### 10. 退出登录（全设备）
[POST /auth/logout/all](auth-logout-all.md)

从所有设备上退出登录。

## 认证流程

### 注册流程
```
1. GET /auth/check-username?username=xxx
   → 检查用户名是否可用

2. POST /auth/send-code
   → 请求体: {"email": "xxx@xxx.com", "type": "register"}
   → 发送注册验证码到邮箱

3. POST /auth/register
   → 请求体: {"username": "xxx", "email": "xxx@xxx.com", "password": "xxx", "code": "xxxxxx"}
   → 完成注册
```

### 密码登录流程
```
1. POST /auth/login
   → 请求体: {"email": "xxx@xxx.com", "password": "xxx"}
   → 返回 AccessToken
```

### 验证码登录流程
```
1. POST /auth/send-code
   → 请求体: {"email": "xxx@xxx.com", "type": "login"}
   → 发送登录验证码到邮箱

2. POST /auth/login-with-code
   → 请求体: {"email": "xxx@xxx.com", "code": "xxxxxx"}
   → 完成登录
```

## 安全特性

### 密码安全
- 使用 Argon2id 算法加密密码
- 密码长度限制 6-66 字符
- 密码永不返回，仅支持验证

### 令牌安全
- **AccessToken**：15 分钟有效期，用于访问受保护的资源
- **RefreshToken**：7 天有效期，通过 HttpOnly Cookie 返回
- 支持令牌刷新，自动使旧令牌失效
- 支持多设备会话，设备之间独立

### 验证码安全
- 6 位数字验证码，每 10 分钟过期
- 一次性使用，验证成功后自动删除
- 错误计数限制：5 次错误后锁定邮箱 1 小时
- 同一邮箱最多：1 次/分钟，6 次/小时
- 同一 IP 最多：1 次/分钟，6 次/小时

### 会话安全
- 每个会话有唯一 ID 和版本号
- 刷新 AccessToken 时自动递增版本号，使旧令牌立即失效
- 支持从单个设备或所有设备登出
- 每个会话包含加密的 RefreshToken

### 设备和 IP 验证
- 发送验证码和验证验证码必须来自同一 IP 地址
- 防止验证码被其他设备或代理滥用

## 错误处理

### HTTP 状态码
- **200**：请求成功
- **400**：客户端请求错误（参数验证、验证码错误等）
- **401**：未认证或令牌无效
- **403**：无权限访问
- **405**：请求方法不支持
- **409**：资源冲突（用户名/邮箱已存在）
- **415**：不支持的请求类型
- **429**：请求过于频繁或超过限制
- **500**：服务器错误

### 错误响应格式
```json
{
  "code": 400,
  "msg": "错误描述信息"
}
```

### 请求类型错误（415）
当请求的 Content-Type 与接口要求不符时，会返回 415 状态码。

**示例**：发送 text/plain 到需要 application/json 的接口
```bash
curl -X POST \
  -H "Content-Type: text/plain" \
  -d 'invalid data' \
  http://localhost:8000/auth/send-code

# 响应
{
  "code": 415,
  "msg": "不支持的请求类型: text/plain。请使用 Content-Type: application/json"
}
```

### 请求类型要求
- **需要 JSON 请求体的接口**：必须使用 `Content-Type: application/json`
  - POST /auth/send-code
  - POST /auth/register
  - POST /auth/login
  - POST /auth/login-with-code
  
- **无请求体的接口**：不需要 Content-Type 或设置为空
  - POST /auth/refresh（仅使用 Cookie）
  - POST /auth/logout（仅使用 Cookie）
  - POST /auth/logout/all（仅使用 Authorization 头）

## 使用示例

### 注册新用户
```bash
# 1. 检查用户名
curl -X GET http://localhost:8000/auth/check-username?username=john

# 2. 发送验证码
curl -X POST http://localhost:8000/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","type":"register"}'

# 3. 等待邮件，获取验证码（例如：123456）

# 4. 注册
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username":"john",
    "email":"john@example.com",
    "password":"secure123",
    "code":"123456"
  }'
```

### 密码登录
```bash
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"secure123"}'

# 响应包含 AccessToken
# Cookie 中包含 RefreshToken
```

### 验证码登录
```bash
# 1. 发送验证码
curl -X POST http://localhost:8000/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","type":"login"}'

# 2. 使用验证码登录
curl -X POST http://localhost:8000/auth/login-with-code \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","code":"123456"}'
```

### 访问受保护的资源
```bash
curl -X GET http://localhost:8000/auth/info \
  -H "Authorization: Bearer {accessToken}"
```

### 更新用户信息
```bash
# 更新用户名
curl -X POST http://localhost:8000/auth/update/profile \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"username":"newname"}'

# 更新头像URL
curl -X POST http://localhost:8000/auth/update/profile \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"avatarUrl":"https://example.com/avatar.jpg"}'

# 同时更新用户名和头像
curl -X POST http://localhost:8000/auth/update/profile \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"username":"newname","avatarUrl":"https://example.com/avatar.jpg"}'
```

### 刷新令牌
```bash
curl -X POST http://localhost:8000/auth/refresh \
  -b "refreshToken={token}"
```

### 登出
```bash
# 单设备登出
curl -X POST http://localhost:8000/auth/logout \
  -b "refreshToken={token}"

# 全设备登出
curl -X POST http://localhost:8000/auth/logout/all \
  -H "Authorization: Bearer {accessToken}"
```

## 配置说明

### 邮箱配置
```properties
spring.mail.host=smtp.exmail.qq.com
spring.mail.port=465
spring.mail.username=your-email@example.com
spring.mail.password=your-password
```

### JWT 配置
```properties
jwt.secret=your-secret-key
jwt.access-token-expiration=900000  # 15分钟（毫秒）
jwt.refresh-token-expiration=604800000  # 7天（毫秒）
```

### Redis 配置
```properties
spring.redis.host=localhost
spring.redis.port=6379
```
