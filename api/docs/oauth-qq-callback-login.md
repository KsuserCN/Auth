# QQ OAuth 登录回调接口

## 基本信息
- 方法：POST
- 路径：`/oauth/qq/callback/login`
- 需要认证：否
- 请求类型：`application/json`

## 用途
用于 QQ 登录流程。前端从 QQ 回调页拿到 `code` 后，将 `code/redirectUri/state` 发送到本接口。

## state 规范
- 格式：`校验参数;操作类型;prd/dev`
- 本接口要求：`操作类型=login`
- 示例：`5501171622cef638d3851ad5a2e8ebc1;login;dev`

## 请求参数（JSON）
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | 是 | QQ 返回的一次性授权码 |
| redirectUri | string | 是 | 前端回调地址，必须在后端白名单中 |
| state | string | 是 | 状态参数，必须符合上面的格式 |

## 请求示例
```json
{
  "code": "AUTH_CODE_FROM_QQ",
  "redirectUri": "https://auth.ksuser.cn/oauth/qq/callback",
  "state": "5501171622cef638d3851ad5a2e8ebc1;login;prd"
}
```

## 成功响应
### 1) 已绑定且登录成功（HTTP 200）
```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "accessToken": "...",
    "user": {},
    "operationType": "login",
    "env": "prd"
  }
}
```

### 2) 需要 TOTP 二次验证（HTTP 201）
```json
{
  "code": 201,
  "msg": "需要 TOTP 验证",
  "data": {
    "challengeId": "...",
    "method": "totp",
    "operationType": "login",
    "env": "prd"
  }
}
```

### 3) 未绑定（HTTP 202）
```json
{
  "code": 202,
  "msg": "未绑定，需要注册或绑定",
  "data": {
    "needBind": true,
    "openid": "...",
    "operationType": "login",
    "env": "prd",
    "message": "未绑定，请使用绑定或注册接口完成账号关联"
  }
}
```

## 失败响应
### 1) 参数错误（HTTP 400）
- `code 不能为空`
- `redirectUri 不能为空`
- `state 不能为空`
- `state 格式不正确，需为: 校验参数;操作类型;prd/dev`
- `state 操作类型与当前接口不匹配`
- `state 环境标识不支持，仅支持 prd/dev`
- `不允许的 redirectUri`
- `QQ token 错误: ...`
- `QQ me 错误: ...`

### 2) 请求频繁（HTTP 429）
- `请求过于频繁`
- `登录处理中，请勿重复提交`

### 3) 上游异常（HTTP 502）
- `QQ token 接口无响应`
- `QQ me 接口无响应`
- `未从 QQ 返回 access_token`
- `未从 QQ 返回 openid`

### 4) 服务异常（HTTP 500）
- `内部错误`
