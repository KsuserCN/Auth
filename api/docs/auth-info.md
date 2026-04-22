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
      "subscribeNewsEmail": false,
      "preferredMfaMethod": "totp",
      "preferredSensitiveMethod": "password"
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
      "subscribeNewsEmail": false,
      "preferredMfaMethod": "totp",
      "preferredSensitiveMethod": "password"
    }
  }
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
用于更新用户设置项（支持布尔开关与枚举偏好）。

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
### 布尔字段更新
```json
{
  "field": "mfa_enabled",
  "value": true
}
```

### 偏好字段更新
```json
{
  "field": "preferred_mfa_method",
  "stringValue": "passkey"
}
```

## 字段说明
- field：设置字段名（支持 snake_case 或 camelCase）
  - mfa_enabled / mfaEnabled
  - detect_unusual_login / detectUnusualLogin
  - notify_sensitive_action_email / notifySensitiveActionEmail
  - subscribe_news_email / subscribeNewsEmail
  - preferred_mfa_method / preferredMfaMethod
  - preferred_sensitive_method / preferredSensitiveMethod
- value：布尔值（仅布尔字段使用）
- stringValue：字符串（仅偏好字段使用）
  - preferred_mfa_method：totp / passkey
  - preferred_sensitive_method：password / email-code / passkey / totp

## 规则说明
- 设置 preferred_mfa_method 前，必须先开启 mfaEnabled=true。
- 偏好只是“默认跳转方式”，不是强制限制；前端仍可让用户点击“选择其他验证方式”。
- 选择偏好时会校验当前用户是否具备该验证能力（例如未绑定 Passkey 时不能设为 passkey）。

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
    "subscribeNewsEmail": false,
    "preferredMfaMethod": "passkey",
    "preferredSensitiveMethod": "password"
  }
}
```

## 常见失败响应
- `400 字段名不能为空`
- `400 布尔字段 value 不能为空`
- `400 偏好字段 stringValue 不能为空`
- `400 不支持的字段名`
- `400 请先启用 MFA 再设置登录MFA优先方式`
- `400 当前用户未启用所选 MFA 方式`
- `400 当前用户不可用所选敏感验证方式`
- `401 未登录`
- `401 用户不存在`
