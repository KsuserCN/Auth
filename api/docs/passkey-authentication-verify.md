# Passkey 完成认证接口

## 基本信息
- 方法：POST
- 路径：/auth/passkey/authentication-verify
- 需要认证：否
- 请求类型：application/json
- 查询参数：challengeId（必填）

## 用途
此接口用于提交 WebAuthn 认证结果，完成 Passkey 登录。需要先调用 `/auth/passkey/authentication-options` 获取认证选项。

## ⚠️ 编码要求

**所有二进制数据必须使用 Base64URL 编码（RFC 4648 Section 5）**

- `credentialRawId`: ✅ 必须是 Base64URL（不含 `+`, `/`, `=`）
- `clientDataJSON`: ✅ 必须是 Base64URL
- `authenticatorData`: ✅ 必须是 Base64URL
- `signature`: ✅ 必须是 Base64URL

**常见错误：** 如果使用标准 Base64 编码（包含 `+`, `/`, `=`），后端会返回 "Input data does not match expected form" 错误。

**解决方案：** 参考 [前端集成指南](./PASSKEY_FRONTEND_INTEGRATION.md) 中的 `arrayBufferToBase64URL()` 函数。

## 安全说明
生产级的完整验证流程（使用 webauthn4j 库）：
1. ✅ Challenge 验证和一次性使用（防重放）
2. ✅ 从数据库加载存储的公钥（COSE_Key）
3. ✅ 使用公钥进行完整的签名验证（最关键）⭐
4. ✅ ClientDataJSON 验证（type/challenge/origin）
5. ✅ AuthenticatorData 验证（RP ID Hash/flags）
6. ✅ Sign Count 检查（防克隆）
7. ✅ User Present 标志验证
8. ✅ 更新 Sign Count 到数据库（防重放和克隆）

## 查询参数
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| challengeId | string | 是 | 从认证选项响应中获取的 challenge ID |

## 请求体
```json
{
  "credentialRawId": "base64编码的credential ID",
  "clientDataJSON": "base64编码的ClientDataJSON",
  "authenticatorData": "base64编码的authenticatorData",
  "signature": "base64编码的签名"
}
```

## 字段说明
- credentialRawId: 凭证 ID，通过 base64 编码的二进制数据
- clientDataJSON: 客户端数据 JSON，包含 challenge、origin 等信息
- authenticatorData: 认证器数据，包含 RP ID hash、flags、sign count 等
- signature: 认证器对认证器数据和客户端数据的签名

## 请求示例
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "credentialRawId":"base64string...",
    "clientDataJSON":"base64string...",
    "authenticatorData":"base64string...",
    "signature":"base64string..."
  }' \
  'http://localhost:8000/auth/passkey/authentication-verify?challengeId=xxx'
```

## 成功响应
- HTTP Status：200
- Set-Cookie：refreshToken（HttpOnly，7天过期）

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

## 响应字段说明
- accessToken: 访问令牌，有效期 15 分钟，用于调用需要认证的接口
- refreshToken: 刷新令牌（自动设置到 HttpOnly Cookie），有效期 7 天，用于刷新 AccessToken

## 失败响应

### 1) Challenge ID 缺失
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "Challenge ID 不能为空"
}
```

### 2) Challenge 过期或不存在
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "Challenge 已过期或不存在"
}
```

### 3) Origin 不匹配
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "Origin 不匹配"
}
```

### 4) Passkey 验证失败
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "Passkey 验证失败"
}
```

可能的原因：
- Credential ID 不存在
- 签名验证失败
- Sign count 异常（可能的克隆攻击）
- RP ID hash 不匹配

### 5) 内部错误
- HTTP Status：500

```json
{
  "code": 500,
  "msg": "认证失败"
}
```

## 前端集成示例

```javascript
// Base64URL 编码函数（必须使用，不能用 btoa）
function arrayBufferToBase64URL(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/\+/g, '-')    // 替换 + 为 -
    .replace(/\//g, '_')    // 替换 / 为 _
    .replace(/=/g, '');     // 删除 padding
}

// 接上一步：passkey-authentication-options.md

// 4. 进行认证
const assertion = await navigator.credentials.get(credentialRequestOptions);

// 5. 提取 challengeId（从之前保存的 challenge 中提取，或从后端返回的数据中获取）
const challengeId = extractChallengeId(options.challenge);

// 6. 提交认证结果（使用 Base64URL 编码）
const verifyResponse = await fetch(`/auth/passkey/authentication-verify?challengeId=${challengeId}`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    credentialRawId: arrayBufferToBase64URL(assertion.rawId),                           // ✅ Base64URL
    clientDataJSON: arrayBufferToBase64URL(assertion.response.clientDataJSON),          // ✅ Base64URL
    authenticatorData: arrayBufferToBase64URL(assertion.response.authenticatorData),    // ✅ Base64URL
    signature: arrayBufferToBase64URL(assertion.response.signature)                     // ✅ Base64URL
  })
});

const result = await verifyResponse.json();
if (result.code === 200) {
  // 登录成功，保存 accessToken
  localStorage.setItem('accessToken', result.data.accessToken);
  // refreshToken 已自动保存在 HttpOnly Cookie 中
  console.log('登录成功');
}
```

## 注意事项
1. 必须在获取认证选项后的 10 分钟内完成认证
2. Challenge 仅能使用一次，即使验证失败也需要重新获取
3. Origin 必须与配置的 origin 一致
4. 认证成功后会自动更新 Passkey 的 lastUsedAt 和 signCount
5. 如果 signCount 异常（小于上次的值），可能存在克隆攻击，验证会失败

## 安全性
- Challenge 防重放：每个 challenge 仅能使用一次
- Origin 验证：确保请求来自合法的前端
- 签名验证：验证认证器的签名（当前为简化实现，生产环境应使用完整验证）
- Sign Count 检查：防止认证器克隆
- RP ID 验证：确保认证器属于当前服务

## Cookie 安全性
- HttpOnly：RefreshToken 仅通过 HttpOnly Cookie 传递，防止 XSS 攻击
- Secure：生产环境（app.debug=false）自动启用 Secure，仅 HTTPS 传输
- SameSite：设置为 Lax，防止 CSRF 攻击

## 相关接口
- [获取认证选项](passkey-authentication-options.md)
- [刷新 AccessToken](auth-refresh.md)
- [获取用户信息](auth-info.md)
