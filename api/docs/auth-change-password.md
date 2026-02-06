# 修改密码接口

## 基本信息
- 方法：POST
- 路径：/auth/update/password
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json
- 前置要求：必须先完成敏感操作验证（/auth/verify-sensitive）

## 用途
此接口用于修改用户的登录密码。这是一个敏感操作，需要先通过身份验证。

## 密码策略（从rapplication.properties配置）
- **长度**: 6-66个字符
- **必须包含**: 大写字母、小写字母、数字
- **特殊字符**: 可选（当前配置为false）
- **弱密码检查**: 禁止使用常见弱密码（password、123456等）

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
```json
{
  "newPassword": "newPassword123"
}
```

## 字段说明
- newPassword: 新密码，不能为空，6-66个字符，必须包含大写字母、小写字母和数字

## 完整操作流程

### 第一步：敏感操作验证
用户必须先完成身份验证，可以选择以下任一方式：

#### 方式1：密码验证
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"password","password":"oldPassword123"}' \
  http://localhost:8000/auth/verify-sensitive
```

#### 方式2：邮箱验证码验证
```bash
# 1. 发送验证码到当前绑定邮箱（需要已登录）
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"type":"sensitive-verification"}' \
  http://localhost:8000/auth/send-code

# 2. 提交验证码
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"email-code","code":"123456"}' \
  http://localhost:8000/auth/verify-sensitive
```

#### 方式3：TOTP 验证
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"totp","code":"123456"}' \
  http://localhost:8000/auth/verify-sensitive
```

### 第二步：提交新密码
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"newPassword123"}' \
  http://localhost:8000/auth/update/password
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "密码修改成功"
}
```

**注意**：
- 密码修改成功后，敏感操作验证状态会自动清除
- 现有的登录会话（AccessToken 和 RefreshToken）仍然有效
- 如需强制所有设备下线，请调用 `/auth/logout/all` 接口

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

### 3) 未完成敏感操作验证
- HTTP Status：403

```json
{
  "code": 403,
  "msg": "请先完成敏感操作验证"
}
```

**解决方法**：先调用 `/auth/verify-sensitive` 完成身份验证

### 4) 验证已过期或在其他设备
- HTTP Status：403

```json
{
  "code": 403,
  "msg": "请先完成敏感操作验证"
}
```

**原因**：
- 距离上次验证已超过15分钟
- 当前设备（IP）与验证时的设备不同

**解决方法**：重新调用 `/auth/verify-sensitive` 进行验证

### 5) 新密码为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "新密码不能为空"
}
```

### 6) 密码长度不符
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "密码长度必须在6-66个字符之间"
}
```

### 7) 密码强度不足
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "密码强度不足：需包含大写字母、小写字母和数字"
}
```

### 8) 密码过于简单
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "密码过于简单，请使用更复杂的密码"
}
```

## 安全特性

### 1. 双重验证
修改密码需要先完成敏感操作验证，可以选择：
- 使用旧密码验证
- 使用邮箱验证码验证

### 2. 验证有效期
敏感操作验证状态仅在以下情况下有效：
- **时间限制**：验证后15分钟内有效
- **设备限制**：只能在验证时使用的同一IP地址操作
- **一次性使用**：密码修改成功后，验证状态会自动清除

### 3. 密码加密
- 使用 Argon2 算法加密存储密码
- 明文密码不会被保存或记录

### 4. 会话保持
- 密码修改后，现有登录会话继续有效
- 如需强制退出所有设备，请使用 `/auth/logout-all`

## 使用示例

### 场景1：使用旧密码验证后修改密码

```bash
# 1. 使用旧密码验证
curl -X POST \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{"method":"password","password":"oldPassword123"}' \
  http://localhost:8000/auth/verify-sensitive

# 响应：
# {
#   "code": 200,
#   "msg": "验证成功，有效期15分钟"
# }

# 2. 修改密码（必须在15分钟内完成）
curl -X POST \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"newPassword456"}' \
  http://localhost:8000/auth/update/password

# 响应：
# {
#   "code": 200,
#   "msg": "密码修改成功"
# }
```

### 场景2：使用邮箱验证码修改密码

```bash
# 1. 发送敏感操作验证码到当前绑定邮箱
curl -X POST \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{"type":"sensitive-verification"}' \
  http://localhost:8000/auth/send-code

# 响应：
# {
#   "code": 200,
#   "msg": "验证码已发送"
# }

# 2. 使用验证码验证身份
curl -X POST \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{"method":"email-code","code":"123456"}' \
  http://localhost:8000/auth/verify-sensitive

# 响应：
# {
#   "code": 200,
#   "msg": "验证成功，有效期15分钟"
# }

# 3. 修改密码
curl -X POST \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"newPassword456"}' \
  http://localhost:8000/auth/update/password

# 响应：
# {
#   "code": 200,
#   "msg": "密码修改成功"
# }
```

## 常见问题

### Q1: 修改密码后需要重新登录吗？
A: 不需要。修改密码后，现有的登录会话继续有效。如果你希望强制所有设备退出登录，请使用 `/auth/logout-all` 接口。

### Q2: 如果忘记旧密码怎么办？
A: 可以使用邮箱验证码方式完成敏感操作验证，然后修改密码。

### Q3: 敏感操作验证后，最多可以修改几次密码？
A: 每次敏感操作验证只能使用一次。密码修改成功后，验证状态会自动清除。如需再次修改，需要重新验证。

### Q4: 新密码有什么要求？
A: 新密码必须至少6位，建议使用字母、数字和特殊字符的组合以提高安全性。

### Q5: 在A设备验证，可以在B设备修改密码吗？
A: 不可以。敏感操作验证绑定了IP地址，必须在同一设备（IP）上完成后续操作。

## 相关接口
- [敏感操作验证](/docs/auth-verify-sensitive.md)
- [发送验证码](/docs/auth-send-code.md)
- [检查验证状态](/docs/auth-check-sensitive-verification.md)
- [全设备退出登录](/docs/auth-logout-all.md)
