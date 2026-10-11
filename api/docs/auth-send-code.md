# 发送验证码接口

## 基本信息
- 方法：POST
- 路径：/auth/send-code
- 需要认证：否（仅 sensitive-verification 需要 AccessToken）
- 请求类型：application/json

## 验证码邮件语言

可发送 `Accept-Language: zh-Hans` 或 `Accept-Language: en`，选择简体中文或英文验证码邮件。Web、Desktop 和 iOS 每次发送时使用当前生效的应用语言：手动选择优先，跟随模式根据设备或浏览器首选语言解析。

服务端按 `q` 权重选择首个有效具体语言，所有中文变体（包括 `zh-TW`、`zh-Hant`）使用简体中文，其他语言使用英语；忽略权重为零和通配符。请求头缺失、格式错误或无可用具体语言时沿用中文默认，以兼容旧客户端。请求体无需增加语言字段。

权重遵循 [HTTP q 值格式](https://www.rfc-editor.org/rfc/rfc9110.html#section-12.4.2)：范围为 0 到 1，最多三位小数。带正号、缺少整数部分或过多小数位等非法值同样回退中文。

该设置覆盖注册、登录、更改邮箱和敏感操作验证码的主题及正文，仅影响邮件展示，不改变验证码、收件人、认证、有效期、IP 校验或限流规则。敏感操作提醒等其他邮件保持现有行为。

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -H "Accept-Language: en" \
  -d '{"email":"user@example.com","type":"login"}' \
  http://localhost:8000/auth/send-code
```

将语言头改为 `Accept-Language: zh-Hans` 可发送中文邮件。

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

## 注册锁定规则（仅注册验证码）
- 同一 IP 或同一 UA 在**当天**注册成功达到 2 次：锁定 10 分钟
- 同一 IP 或同一 UA 在**当天**注册成功达到 3 次：锁定 1 小时
- 同一 IP 或同一 UA 在**当天**注册成功达到 4 次及以上：锁定 1 天
- 锁定期间注册验证码不允许发送

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

### 5.1) 注册功能被锁定（仅注册验证码）
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "注册过于频繁，请稍后再试"
}
```
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
