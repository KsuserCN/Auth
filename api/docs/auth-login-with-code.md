# 验证码登录接口

## 基本信息
- 方法：POST
- 路径：/auth/login-with-code
- 需要认证：否
- 请求类型：application/json

## 注册流程
1. 用户填写邮箱
2. 前端调用 `/auth/send-code` 发送登录验证码（type=login）
3. 用户收到邮箱验证码
4. 用户填写验证码
5. 前端调用此接口提交邮箱和验证码完成登录

## 请求体
```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

## 字段说明
- email: 邮箱，不能为空，必须与发送验证码时的邮箱相同
- code: 邮箱验证码，6位数字

## 请求示例
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","code":"123456"}' \
  http://localhost:8000/auth/login-with-code
```

## 成功响应
- HTTP Status：200
- Cookie：设置 refreshToken（HttpOnly）

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

## 失败响应
### 1) 邮箱为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "邮箱不能为空"
}
```

### 2) 验证码为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码不能为空"
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

### 6) 验证码错误
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码错误（1/5）"
}
```

### 7) 验证码已过期
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码已过期，请重新获取（1/5）"
}
```

### 8) 验证码错误次数过多
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "验证码错误次数过多，该邮箱已被锁定1小时"
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

## 安全说明
- 验证码有效期为 10 分钟，仅可使用一次
- 验证码错误超过 5 次将锁定邮箱 1 小时
- 发送验证码和验证验证码必须来自同一 IP 地址
- RefreshToken 通过 HttpOnly Cookie 返回，7 天过期
- AccessToken 有效期为 15 分钟
- 所有POST请求必须使用 `Content-Type: application/json` 请求头

## 获取 AccessToken
- 通过 Set-Cookie 响应头获取 refreshToken
- 使用 AccessToken 访问受保护的 API：`Authorization: Bearer {accessToken}`
- AccessToken 过期后，使用 refreshToken 调用 `/auth/refresh` 获取新的 AccessToken
