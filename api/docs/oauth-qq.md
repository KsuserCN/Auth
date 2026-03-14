# QQ OAuth 接口总览

## 回调接口拆分
旧接口 `POST /oauth/qq/callback` 已拆分为以下三个接口：

1. 登录回调（无需登录态）
- 路径：`POST /oauth/qq/callback/login`
- 文档：`docs/oauth-qq-callback-login.md`

2. 绑定回调（需要 AccessToken）
- 路径：`POST /oauth/qq/callback/bind`
- 文档：`docs/oauth-qq-callback-bind.md`

3. 解绑接口（需要 AccessToken）
- 路径：`POST /oauth/qq/callback/unbind`
- 文档：`docs/oauth-qq-callback-unbind.md`
- 说明：不再要求 QQ OAuth 回调参数，改为本地解绑（仅校验敏感操作验证）

## state 规范
登录/绑定回调接口要求 `state` 为三段：

`校验参数;操作类型;prd/dev`

- `操作类型` 仅支持：`login`、`bind`、`unbind`
- 登录/绑定接口会校验 `state` 中的操作类型必须与当前接口匹配

## 其他 QQ 相关接口
- 解绑（非 OAuth 回调方式）：`POST /oauth/qq/unbind`
- 绑定已有账号：`POST /oauth/qq/bind-existing`
- 注册并绑定：`POST /oauth/qq/register-bind`

## 第三方登录状态接口
- 路径：`GET /oauth/accounts/status`
- 文档：`docs/oauth-accounts-status.md`
- 说明：返回当前用户在 `wechat/qq/microsoft/github` 的绑定状态与上次登录时间
