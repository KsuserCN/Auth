# 注册接口

## 基本信息
- 方法：POST
- 路径：/auth/register
- 需要认证：否
- 请求类型：application/json

## 注册流程
1. 前端调用 `/auth/check-username` 检查用户名是否可用
2. 用户填写密码（跟更改密码的动态获取强度一致）、重复密码（前端校验）、邮箱
3. 前端调用 `/auth/send-code` 发送邮箱验证码（type=register）
   - **注意**：此步骤不检查邮箱是否已注册，无论邮箱是否已注册都会发送验证码
   - 这样可以保护已注册邮箱的隐私（避免邮箱枚举攻击）
4. 用户收到邮箱验证码（如果邮箱未注册）
5. 用户填写验证码
6. 前端调用此接口提交用户名、密码、邮箱、验证码完成注册
   - 如果邮箱已注册，会在此步骤返回错误

## 密码策略（从rapplication.properties配置）
- **长度**: 6-66个字符
- **必须包含**: 大写字母、小写字母、数字
- **特殊字符**: 可选（当前配置为false）
- **弱密码检查**: 禁止使用常见弱密码（password、123456等）

## 请求体
```json
{
  "username": "test",
  "email": "test@example.com",
  "password": "password123",
  "code": "123456"
}
```

## 字段说明
- username: 用户名，不能为空，3-20个字符，仅字母数字下划线连字符
- email: 邮箱，不能为空，必须符合邮箱格式
- password: 密码，6-66个字符，必须包含大写字母、小写字母和数字
- code: 邮箱验证码，6位数字

## 请求示例
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"password123","code":"123456"}' \
  http://localhost:8000/auth/register
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "注册成功",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "test",
    "email": "test@example.com",
    "avatarUrl": null,
    "createdAt": "2026-01-31T17:00:00"
  }
}
```

## 失败响应
### 1) 参数为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "用户名不能为空"
}
```

或

```json
{
  "code": 400,
  "msg": "邮箱不能为空"
}
```

或

```json
{
  "code": 400,
  "msg": "密码不能为空"
}
```

或

```json
{
  "code": 400,
  "msg": "验证码不能为空"
}
```

### 2) 密码长度不符
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "密码长度必须在6-66个字符之间"
}
```

### 3) 密码强度不足
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "密码强度不足：需包含大写字母、小写字母和数字"
}
```

### 4) 密码过于简单
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "密码过于简单，请使用更复杂的密码"
}
```

### 3) 未发送验证码
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "请先获取验证码"
}
```

### 4) 邮箱不匹配
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "邮箱不匹配（1/5）"
}
```

### 5) IP地址不匹配
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "发送验证码的设备与当前设备不匹配（1/5）"
}
```

### 6) 验证码错误或已过期
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码错误（1/5）"
}
```

或

```json
{
  "code": 400,
  "msg": "验证码已过期，请重新获取（1/5）"
}
```

### 7) 验证码错误次数过多
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "验证码错误次数过多，该邮箱已被锁定1小时"
}
```

### 8) 邮箱已被注册
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "邮箱已被注册"
}
```

**说明**：为了保护邮箱隐私，在发送验证码时不检查邮箱是否已注册，仅在验证码验证成功后才返回此错误。

### 9) 用户名已存在
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "用户名已存在"
}
```

### 10) 请求类型错误
- HTTP Status：415

```json
{
  "code": 415,
  "msg": "不支持的请求类型: text/plain。请使用 Content-Type: application/json"
}
```

## 安全说明
- 密码使用 Argon2id 算法哈希后存储
- 验证码错误超过5次将锁定邮箱1小时
- 邮箱被锁定期间，无法发送验证码和验证，1小时后自动解锁
- 邮箱验证码10分钟内有效，仅可使用一次
- 发送验证码和验证验证码必须来自同一IP地址
- 所有POST请求必须使用 `Content-Type: application/json` 请求头
