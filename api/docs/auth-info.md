# 用户信息接口

## 基本信息
- 方法：GET
- 路径：/auth/info
- 需要认证：是（AccessToken）

## 查询参数
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| type | string | 否 | basic | basic 仅返回基础字段；details 返回全部字段 |

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求示例
```bash
curl -X GET \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  http://localhost:8000/auth/info
```

```bash
curl -X GET \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  "http://localhost:8000/auth/info?type=details"
```

## 成功响应
- HTTP Status：200

### type=basic（默认）
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com",
    "avatarUrl": null,
    "settings": {
      "mfaEnabled": false,
      "detectUnusualLogin": true,
      "notifySensitiveActionEmail": true,
      "subscribeNewsEmail": false
    }
  }
}
```

### type=details
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com",
    "avatarUrl": null,
    "realName": "张三",
    "gender": "male",
    "birthDate": "1999-01-01",
    "region": "Beijing",
    "bio": "这里是个人简介",
    "updatedAt": "2026-02-03T12:00:00",
    "settings": {
      "mfaEnabled": false,
      "detectUnusualLogin": true,
      "notifySensitiveActionEmail": true,
      "subscribeNewsEmail": false
    }
  }
}
```

## 失败响应
### 1) 未登录或 Token 无效
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录"
}
```

---

# 更新用户设置接口

## 基本信息
- 方法：POST
- 路径：/auth/update/setting
- 需要认证：是（AccessToken）
- 请求类型：application/json

## 用途
用于更新用户的设置项（字段名 + bool）。

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
```json
{
  "field": "mfa_enabled",
  "value": true
}
```

## 字段说明
- field: 设置字段名（支持 snake_case 或 camelCase）
  - mfa_enabled / mfaEnabled
  - detect_unusual_login / detectUnusualLogin
  - notify_sensitive_action_email / notifySensitiveActionEmail
  - subscribe_news_email / subscribeNewsEmail
- value: 布尔值（true/false）

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "更新成功",
  "data": {
    "mfaEnabled": true,
    "detectUnusualLogin": true,
    "notifySensitiveActionEmail": true,
    "subscribeNewsEmail": false
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

### 3) 字段名或字段值为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "字段名不能为空"
}
```

```json
{
  "code": 400,
  "msg": "字段值不能为空"
}
```

### 4) 字段名不支持
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "不支持的字段名"
}
```
