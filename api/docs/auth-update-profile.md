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
  "key": "username",
  "value": "newusername"
}
```

## 字段说明
- key: 要更新的字段（必填，只能填写以下之一：username / avatarUrl / realName / gender / birthDate / region / bio）
- value: 新值（必填）

### 允许更新的字段与规则
- username: 3-20 个字符，仅字母数字下划线连字符或简体中文
- avatarUrl: 任意长度 URL 字符串
- realName: 真实姓名
- gender: male / female / secret
- birthDate: 出生日期（格式：YYYY-MM-DD）
- region: 地区
- bio: 个人简介（最多200字）

> 注意：一次只能更新一个字段

## 请求示例

### 更新用户名
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"key":"username","value":"newname"}' \
  http://localhost:8000/auth/update/profile
```

### 更新头像URL
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"key":"avatarUrl","value":"https://example.com/avatar.jpg"}' \
  http://localhost:8000/auth/update/profile
```

### 更新扩展资料
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"key":"realName","value":"张三"}' \
  http://localhost:8000/auth/update/profile

curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"key":"gender","value":"male"}' \
  http://localhost:8000/auth/update/profile

curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"key":"birthDate","value":"1999-01-01"}' \
  http://localhost:8000/auth/update/profile

curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"key":"region","value":"Beijing"}' \
  http://localhost:8000/auth/update/profile

curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"key":"bio","value":"这里是个人简介"}' \
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
    "avatarUrl": "https://example.com/avatar.jpg",
    "realName": "张三",
    "gender": "male",
    "birthDate": "1999-01-01",
    "region": "Beijing",
    "bio": "这里是个人简介",
    "updatedAt": "2026-02-03T12:00:00"
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

### 3) 参数为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "key 不能为空"
}
```

```json
{
  "code": 400,
  "msg": "value 不能为空"
}
```

### 4) 用户名格式不正确
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "用户名格式不正确（3-20字符，字母数字下划线连字符或简体中文）"
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
### 6) key 不支持
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "key 不支持"
}
```
### 7) 请求类型错误
- HTTP Status：415

```json
{
  "code": 415,
  "msg": "不支持的请求类型: text/plain。请使用 Content-Type: application/json"
}
```

### 8) token 无效或已过期
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "访问被拒绝"
}
```

## 注意事项
- 所有POST请求必须使用 `Content-Type: application/json` 请求头
- 一次只能更新一个字段
- key/value 都不能为空
- value 不能是空字符串 ""
- AccessToken 有效期为 15 分钟，过期后需要使用 refreshToken 调用 `/auth/refresh` 获取新的 AccessToken
- 更新用户名后，新的用户名必须在系统中唯一，不能与其他用户重复

## 使用流程
1. 用户登录获取 AccessToken
2. 调用此接口提交新的用户名和/或头像URL
3. 接口返回更新后的用户信息
4. 如果更新了用户名，后续登录时需要使用新的用户名或邮箱

## 字段约束

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| key | string | 是 | 要更新的字段名 |
| value | string | 是 | 字段的新值 |

允许的 key：username / avatarUrl / realName / gender / birthDate / region / bio
