# 更新用户信息接口

## 基本信息
- 方法：POST
- 路径：/auth/update/profile
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
```json
{
  "username": "newusername",
  "avatarUrl": "https://example.com/avatar.jpg"
}
```

## 字段说明
- username: 新用户名（可选，1-50个字符）
- avatarUrl: 新头像URL（可选，任意长度）
- 注意：至少需要提供以上字段中的一个，不能都为空或空字符串

## 请求示例

### 更新用户名
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"username":"newname"}' \
  http://localhost:8000/auth/update/profile
```

### 更新头像URL
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"avatarUrl":"https://example.com/avatar.jpg"}' \
  http://localhost:8000/auth/update/profile
```

### 同时更新用户名和头像
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"username":"newname","avatarUrl":"https://example.com/avatar.jpg"}' \
  http://localhost:8000/auth/update/profile
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "更新成功",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "newname",
    "email": "user@example.com",
    "avatarUrl": "https://example.com/avatar.jpg"
  }
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

### 3) 没有提供任何更新字段
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "至少需要提供用户名或头像 URL 中的一个"
}
```

### 4) 用户名长度不合法
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "用户名长度必须在 1-50 个字符之间"
}
```

### 5) 用户名已存在
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "用户名已存在"
}
```

### 6) 请求类型错误
- HTTP Status：415

```json
{
  "code": 415,
  "msg": "不支持的请求类型: text/plain。请使用 Content-Type: application/json"
}
```

### 7) token 无效或已过期
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "访问被拒绝"
}
```

## 注意事项
- 所有POST请求必须使用 `Content-Type: application/json` 请求头
- 至少需要提供 username 或 avatarUrl 中的一个字段
- 如果只想更新其中一个字段，可以只提供那个字段，另一个字段可以省略或设置为 null
- username 和 avatarUrl 都不能是空字符串 ""，如果不更新可以不提供该字段
- AccessToken 有效期为 15 分钟，过期后需要使用 refreshToken 调用 `/auth/refresh` 获取新的 AccessToken
- 更新用户名后，新的用户名必须在系统中唯一，不能与其他用户重复

## 使用流程
1. 用户登录获取 AccessToken
2. 调用此接口提交新的用户名和/或头像URL
3. 接口返回更新后的用户信息
4. 如果更新了用户名，后续登录时需要使用新的用户名或邮箱

## 字段约束

| 字段 | 类型 | 长度 | 必填 | 说明 |
|------|------|------|------|------|
| username | string | 1-50 | 否* | 新用户名，必须唯一 |
| avatarUrl | string | 0-255 | 否* | 新头像URL，通常是图片链接 |

*至少填一个
