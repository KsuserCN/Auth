# 获取第三方 OAuth 授权上下文

- 路径：`GET /oauth2/authorize/context`
- 作用：在授权确认页加载应用信息，预校验客户端、回调、scope、state 和 PKCE 参数
- 认证：无需登录，但真正授权前前端仍需确保用户已登录

## 查询参数

- `client_id`：必填，应用 `AppID`
- `redirect_uri`：必填，必须与应用登记的任一回调地址完全一致；应用可登记多个地址，使用英文分号 `;` 分隔
- `response_type`：必填，当前固定 `code`
- `scope`：可选，仅支持 `profile email`；省略时使用应用登记的全部范围
- `state`：`public` 必填，非空且最多 512 字符；`confidential` 为兼容旧客户端可省略，所有新接入都应生成随机 state 并在回调校验
- `code_challenge`：`public` 必填，`BASE64URL(SHA256(code_verifier))`，不含 `=`，恰好 43 字符
- `code_challenge_method`：使用 PKCE 时必填，固定 `S256`，不支持 `plain`

第三方注册支持 `public` 和 `confidential`，创建后不可切换；历史应用及创建时省略 `clientType` 的应用仍是 `confidential`。public 不生成密钥，适用于原生 App；confidential 的密钥只保存在受控后端。confidential 可以继续不使用 PKCE，但只要提供 challenge 或 method 中任一项，就必须提供完整合法的 S256 参数，兑换时也必须验证。

`/oauth2` 当前提供 OAuth 授权码和 UserInfo，不支持 `openid` scope、ID Token 或 OIDC Discovery。官方应用使用独立的 [`/official` OIDC 协议](official-app-integration.md)。原生 App-to-App 授权见 [原生应用授权 API](native-app-authorization.md)。

## 成功示例

`subjectName` 为应用归属账号的认证实体（真实姓名 / 企业名称），`subjectVerificationType` 为 `personal`、`enterprise`、`admin` 或 `none`。实体未补录时名称为 `null`，不会回退为可自行编辑的用户名或个人资料姓名。扫码授权上下文使用相同字段，见 [认证实体维护](user-verification-entity.md)。

没有关联到有效归属账号时，`subjectVerificationType` 也为 `null`；已有认证账号但名称尚未补录时，仍返回该账号的认证类型。

```bash
curl 'http://localhost:8000/oauth2/authorize/context?client_id=ksapp_xxx&redirect_uri=https%3A%2F%2Fclient.example%2Fksuser%2Fcallback&response_type=code&scope=profile%20email&state=demo-random-state&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256'
```

```json
{
  "code": 200,
  "errorCode": "APPLE_004",
  "data": {
    "clientId": "ksapp_xxx",
    "appName": "OAuth Demo",
    "contactInfo": "test@example.com",
    "redirectUri": "https://client.example/ksuser/callback",
    "requestedScopes": ["profile", "email"],
    "alreadyAuthorized": false,
    "existingGrantMode": "PERSISTENT",
    "existingGrantExpiresAt": null,
    "subjectName": null,
    "subjectVerificationType": null
  }
}
```
