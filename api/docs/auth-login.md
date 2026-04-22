# 登录接口

## 基本信息
- 方法：POST
- 路径：/auth/login
- 需要认证：否
- 请求类型：application/json

## 速率限制
- **邮箱限制**：每分钟 1 次，每小时 14 次
- **IP限制**：每分钟 3 次，每小时 14 次
- **重要**：登录成功后会自动清除该邮箱和IP的所有速率限制（包括验证码和登录限制）
- **隔离性**：登录接口和验证码发送接口的限制计数器是独立的，互不影响

## 请求参数（JSON）
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| email | string | 是 | 邮箱 |
| password | string | 是 | 密码 |

## 请求示例
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

## 成功响应
- HTTP Status：200
- Set-Cookie：refreshToken（HttpOnly，7天过期）

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9..."
  }
}
```

**说明**：
- `accessToken` 用于调用需要认证的接口，通过 `Authorization: Bearer <accessToken>` 传递，有效期 15 分钟
- `refreshToken` 自动保存在 HttpOnly Cookie 中，用于刷新 AccessToken，有效期 7 天
- 本地开发（app.debug=true）时 Cookie 为非 Secure；生产环境会自动设置 Secure

## 失败响应
### 1) 参数缺失或为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "邮箱不能为空"
}
```

```json
{
  "code": 400,
  "msg": "密码不能为空"
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

### 3) 邮箱或密码错误
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "邮箱或密码错误"
}
```

### 4) 请求过于频繁
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "登录请求过于频繁，请稍后再试"
}
```

**触发条件**：
- 该邮箱在1分钟内请求超过1次
- 该IP在1分钟内请求超过3次
- 该邮箱或IP在1小时内请求超过14次

### 5) 请求类型错误
- HTTP Status：415

```json
{
  "code": 415,
  "msg": "不支持的请求类型: text/plain。请使用 Content-Type: application/json"
}
```

## 调用示例（curl）
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"password123"}' \
  http://localhost:8000/auth/login
```
