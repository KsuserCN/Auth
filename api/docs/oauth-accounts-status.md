# 第三方登录状态查询接口

## 基本信息
- 方法：GET
- 路径：`/oauth/accounts/status`
- 需要认证：是（AccessToken）
- 请求类型：无请求体

## 用途
用于返回当前登录用户在系统中各第三方登录方式的绑定情况，便于前端在安全设置页展示「已绑定/未绑定」及「上次登录时间」。

## 请求头
```http
Authorization: Bearer <accessToken>
```

## 请求示例
```bash
curl -X GET \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/oauth/accounts/status
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "查询成功",
  "data": [
    {
      "provider": "wechat",
      "bound": false,
      "lastLoginAt": null
    },
    {
      "provider": "qq",
      "bound": true,
      "lastLoginAt": "2026-03-12T15:42:11"
    },
    {
      "provider": "microsoft",
      "bound": false,
      "lastLoginAt": null
    },
    {
      "provider": "github",
      "bound": false,
      "lastLoginAt": null
    }
  ]
}
```

## 返回字段说明
| 字段 | 类型 | 说明 |
|---|---|---|
| provider | string | 第三方提供方标识，固定为 `wechat` / `qq` / `microsoft` / `github` |
| bound | boolean | 是否已绑定该第三方账号（`is_enabled=true` 视为已绑定） |
| lastLoginAt | string\|null | 该第三方账号最近一次用于登录的时间（ISO-8601），未登录过则为 `null` |

## 失败响应
### 1) 未登录或 Token 失效（HTTP 401）
```json
{
  "code": 401,
  "msg": "未登录或Token已过期"
}
```

### 2) 认证用户不存在（HTTP 401）
```json
{
  "code": 401,
  "msg": "用户不存在"
}
```
