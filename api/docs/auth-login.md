# 登录接口

## 基本信息
- 方法：POST
- 路径：/auth/login
- Content-Type：application/json

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

### 2) 邮箱或密码错误
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "邮箱或密码错误"
}
```

## 调用示例（curl）
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"password123"}' \
  http://localhost:8000/auth/login
```
