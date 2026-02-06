# 敏感操作验证接口

## 基本信息
- 方法：POST
- 路径：/auth/verify-sensitive
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json

## 用途
此接口用于验证用户身份，以便执行敏感操作（如更改邮箱、修改密码等）。验证成功后，用户在15分钟内可以执行敏感操作，且验证结果只在同一设备（IP）有效。

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
```json
{
  "method": "password",
  "password": "userPassword123"
}
```

或

```json
{
  "method": "email-code",
  "code": "123456"
}
```

或

```json
{
  "method": "totp",
  "code": "123456"
}
```

## 字段说明
- method: 验证方式，只能是 "password"、"email-code" 或 "totp"
  - password: 使用密码验证
  - email-code: 使用当前邮箱的验证码验证
  - totp: 使用 TOTP 动态验证码验证
- password: 用户密码（当 method=password 时必填）
- code: 验证码（当 method=email-code 或 totp 时必填）

## 验证方式说明

### 1. 密码验证
使用用户的登录密码进行验证。

**请求示例**：
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"password","password":"myPassword123"}' \
  http://localhost:8000/auth/verify-sensitive
```

### 2. 邮箱验证码验证
使用当前邮箱接收的验证码进行验证。**此方式使用单独的验证码类型 "sensitive-verification"**，与登录验证码隔离存储。

**流程**：
1. 先调用 `/auth/send-code` 发送验证码到当前邮箱（type=**sensitive-verification**，无需传 email，需携带 AccessToken）
2. 用户收到验证码后，调用此接口进行验证

**请求示例**：
```bash
# 第一步：发送敏感操作验证码（使用 sensitive-verification 类型）
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"type":"sensitive-verification"}' \
  http://localhost:8000/auth/send-code

# 第二步：验证验证码
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"email-code","code":"123456"}' \
  http://localhost:8000/auth/verify-sensitive
```

### 3. TOTP 验证
使用已启用 TOTP 的动态验证码进行验证。

**请求示例**：
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"totp","code":"123456"}' \
  http://localhost:8000/auth/verify-sensitive
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "验证成功，有效期15分钟"
}
```

## 失败响应

### 1) 未登录
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录"
}
```

### 2) 用户不存在
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "用户不存在"
}
```

### 3) 验证方式为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证方式不能为空"
}
```

### 4) 验证方式不合法
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证方式只能是 password、email-code 或 totp"
}
```

### 5) 密码为空（method=password时）
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "密码不能为空"
}
```

### 6) 密码错误（method=password时）
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "密码错误"
}
```

### 7) 验证码为空（method=email-code时）
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码不能为空"
}
```

### 8) 验证码错误（method=email-code时）
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码错误（X/5）"
}
```

### 9) 验证码已过期（method=email-code时）
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码已过期，请重新获取（X/5）"
}
```

### 10) 未发送验证码（method=email-code时）
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "请先获取验证码"
}
```

### 11) 邮箱不匹配（method=email-code时）
- HTTP Status：400
### 12) 未启用 TOTP（method=totp时）
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "用户未启用 TOTP"
}
```

### 13) TOTP 验证失败（method=totp时）
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码错误或已过期"
}
```

```json
{
  "code": 400,
  "msg": "邮箱不匹配（X/5）"
}
```

### 12) IP不匹配（method=email-code时）
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "发送验证码的设备与当前设备不匹配（X/5）"
}
```

### 13) 邮箱被锁定（method=email-code时）
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "验证码错误次数过多，该邮箱已被锁定1小时"
}
```

### 14) 请求类型错误
- HTTP Status：415

```json
{
  "code": 415,
  "msg": "不支持的请求类型: text/plain。请使用 Content-Type: application/json"
}
```

## 验证结果说明

### 有效期
- 验证成功后，标记在 Redis 中保存 **15 分钟**
- 15 分钟内可以执行敏感操作，无需重复验证

### 设备绑定
- 验证结果与设备（IP 地址）绑定
- 只能在验证时的设备上使用，不能跨设备使用
- 如果从其他设备执行敏感操作，需要重新验证

### 自动过期
- Redis TTL 自动管理，15分钟后自动过期
- 无需手动清除验证标记

## 使用流程

### 密码验证流程
```
1. 用户点击"更改邮箱"等敏感操作按钮
2. 系统要求进行身份验证
3. 用户选择"密码验证"
4. 前端调用 /auth/verify-sensitive 提交密码
5. 验证成功，15分钟内可执行敏感操作
```

### 邮箱验证码流程
```
1. 用户点击"更改邮箱"等敏感操作按钮
2. 系统要求进行身份验证
3. 用户选择"邮箱验证码验证"
4. 前端调用 /auth/send-code 发送验证码（type=login）
5. 用户收到验证码
6. 前端调用 /auth/verify-sensitive 提交验证码
7. 验证成功，15分钟内可执行敏感操作
```

## 安全说明
- 验证码验证遵循所有验证码系统约束（邮箱校验、IP校验、频率限制、生命周期）
- 密码验证使用 Argon2id 算法进行比对
- 验证结果绑定设备（IP），防止跨设备滥用
- AccessToken 过期后需要重新登录
- 所有POST请求必须使用 `Content-Type: application/json` 请求头

## 适用场景
此验证机制适用于所有敏感操作，包括但不限于：
- 更改邮箱
- 修改密码
- 删除账号
- 绑定/解绑第三方账号
- 修改安全设置

## 注意事项
- 验证成功后的15分钟内，在同一设备上执行敏感操作无需重复验证
- 如果从其他设备执行敏感操作，会返回 403 错误，需要重新验证
- 如果验证已过期（超过15分钟），需要重新验证
- 建议在执行敏感操作前先检查验证状态，避免用户填写完表单后才发现需要重新验证
