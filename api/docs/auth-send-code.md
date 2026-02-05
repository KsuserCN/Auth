# 发送验证码接口

## 基本信息
- 方法：POST
- 路径：/auth/send-code
- 需要认证：否（仅 sensitive-verification 需要 AccessToken）
- 请求类型：application/json

## 请求体
```json
{
  "email": "user@example.com",
  "type": "register"
}
```

## 字段说明
- email: 邮箱。除 sensitive-verification 外必填；sensitive-verification 将自动使用当前登录用户绑定邮箱
- type: 验证码类型，只能是 "register"、"login"、"change-email" 或 "sensitive-verification"
  - register: 用于注册账号，**不检查邮箱是否已注册**（为了保护邮箱隐私）
  - login: 用于验证码登录，**不检查邮箱是否存在**（为了保护邮箱隐私）
  - change-email: 用于更改邮箱，检查邮箱是否已被使用
  - sensitive-verification: 用于敏感操作邮箱验证（与 login 类型隔离，单独存储）

## 请求示例
```bash
# 注册验证码
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","type":"register"}' \
  http://localhost:8000/auth/send-code

# 登录验证码
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","type":"login"}' \
  http://localhost:8000/auth/send-code

# 敏感操作邮箱验证码（新增，使用当前登录用户绑定邮箱）
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"type":"sensitive-verification"}' \
  http://localhost:8000/auth/send-code

# 更改邮箱验证码
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"newemail@example.com","type":"change-email"}' \
  http://localhost:8000/auth/send-code
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "验证码已发送"
}
```

## 限流规则
- 同一邮箱：每分钟最多发送1次
- 同一 IP：每分钟最多发送3次
- 同一IP/邮箱：每小时最多发送14次
- 验证码错误超过5次：该邮箱锁定1小时，期间无法发送验证码

## 验证码规则
- 格式：6位数字
- 有效期：10分钟
- 仅可使用一次
- 发送和验证必须来自同一IP地址

## 失败响应
### 1) 邮箱为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "邮箱不能为空"
}
```

### 2) 邮箱格式不正确
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "邮箱格式不正确"
}
```

### 3) 类型为空或无效
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "类型不能为空"
}
```

或

```json
{
  "code": 400,
  "msg": "类型只能是 register、login 或 change-email"
}
```

### 4) 邮箱已被注册（仅注册验证码）
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "邮箱已被注册"
}
```

### 4.1) 邮箱已被使用（仅更改邮箱验证码）
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "邮箱已被使用"
}
```

### 5) 邮箱被锁定
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "验证码错误次数过多，该邮箱已被锁定1小时"
}
```

### 6) 发送过于频繁（分钟限制）
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "发送过于频繁，请1分钟后再试"
}
```

### 7) 发送次数过多（小时限制）
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "发送次数过多，每小时最多发送14次"
}
```

或

```json
{
  "code": 429,
  "msg": "该邮箱发送次数过多，每小时最多发送14次"
}
```

### 8) 邮件发送失败
- HTTP Status：500

```json
{
  "code": 500,
  "msg": "邮件发送失败，请稍后重试"
}
```

### 9) 请求类型错误
- HTTP Status：415

```json
{
  "code": 415,
  "msg": "不支持的请求类型: text/plain。请使用 Content-Type: application/json"
}
```

## 注意事项
- 所有POST请求必须使用 `Content-Type: application/json` 请求头
- 不支持其他内容类型（如 text/plain、application/x-www-form-urlencoded等）
- 请求体必须是有效的JSON格式
