# Passkey 验证敏感操作接口

## 基本信息
- 方法：POST
- 路径：/auth/passkey/sensitive-verification-verify
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json
- 查询参数：challengeId（必填）

## 用途
此接口用于提交 WebAuthn 认证结果，完成敏感操作验证。需要先调用 `/auth/passkey/sensitive-verification-options` 获取验证选项。验证成功后，用户可以在 15 分钟内执行敏感操作。

## ⚠️ 编码要求

**所有二进制数据必须使用 Base64URL 编码（RFC 4648 Section 5）**

- `credentialRawId`: ✅ 必须是 Base64URL（不含 `+`, `/`, `=`）
- `clientDataJSON`: ✅ 必须是 Base64URL
- `authenticatorData`: ✅ 必须是 Base64URL
- `signature`: ✅ 必须是 Base64URL

**常见错误：** 如果使用标准 Base64 编码（包含 `+`, `/`, `=`），后端会返回 "Input data does not match expected form" 错误。

**解决方案：** 参考 [前端集成指南](./PASSKEY_FRONTEND_INTEGRATION.md) 中的 `arrayBufferToBase64URL()` 函数。

## 安全说明
生产级的完整验证流程（使用 webauthn4j 库，比普通认证更严格）：
1. ✅ Challenge 验证和一次性使用（防重放）
2. ✅ 从数据库加载存储的公钥（COSE_Key）
3. ✅ 使用公钥进行完整的签名验证（最关键）⭐
4. ✅ ClientDataJSON 验证（type/challenge/origin）
5. ✅ AuthenticatorData 验证（RP ID Hash/flags）
6. ✅ Sign Count 检查（防克隆）
7. ✅ User Present 标志验证
8. ✅ **User Verified 标志强制检查（必须=1）** - 需要 PIN 或生物识别
9. ✅ Passkey 用户归属验证（防跨用户攻击）
10. ✅ 更新 Sign Count 到数据库

## 查询参数
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| challengeId | string | 是 | 从敏感操作验证选项响应中获取的 challenge ID |

## 请求头
```
Authorization: Bearer <accessToken>
```

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
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "credentialRawId":"base64string...",
    "clientDataJSON":"base64string...",
    "authenticatorData":"base64string...",
    "signature":"base64string..."
  }' \
  'http://localhost:8000/auth/passkey/sensitive-verification-verify?challengeId=xxx'
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "message": "验证成功，有效期15分钟"
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

### 4) Passkey 不属于当前用户
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "Passkey 不属于当前用户"
}
```

### 5) Passkey 验证失败
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

### 6) 内部错误
- HTTP Status：500

```json
{
  "code": 500,
  "msg": "验证失败"
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

// 接上一步：passkey-sensitive-verification-options.md

// 3. 进行认证
const assertion = await navigator.credentials.get(credentialRequestOptions);

// 4. 提交验证结果（使用 Base64URL 编码）
const verifyResponse = await fetch('/auth/passkey/sensitive-verification-verify', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`
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
  console.log('验证成功，现在可以执行敏感操作');
  // 在接下来的 15 分钟内可以调用敏感操作接口
}
```

## 完整流程示例（修改密码）

```javascript
// Base64URL 编码/解码函数
function arrayBufferToBase64URL(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
}

function base64URLToArrayBuffer(base64url) {
  let base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
  const padding = (4 - (base64.length % 4)) % 4;
  base64 += '='.repeat(padding);
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

// 步骤1：获取敏感操作验证选项
const optionsResponse = await fetch('/auth/passkey/sensitive-verification-options', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }
});
const optionsData = await optionsResponse.json();

// 步骤2：使用 Passkey 进行验证
const credentialRequestOptions = {
  publicKey: {
    challenge: base64URLToArrayBuffer(optionsData.data.challenge),  // ✅ Base64URL 解码
    timeout: parseInt(optionsData.data.timeout),
    rpId: optionsData.data.rpId,
    userVerification: optionsData.data.userVerification
  }
};

const assertion = await navigator.credentials.get(credentialRequestOptions);

// 步骤3：提交验证结果（使用 Base64URL 编码）
const verifyResponse = await fetch('/auth/passkey/sensitive-verification-verify', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    credentialRawId: arrayBufferToBase64URL(assertion.rawId),                           // ✅ Base64URL
    clientDataJSON: arrayBufferToBase64URL(assertion.response.clientDataJSON),          // ✅ Base64URL
    authenticatorData: arrayBufferToBase64URL(assertion.response.authenticatorData),    // ✅ Base64URL
    signature: arrayBufferToBase64URL(assertion.response.signature)                     // ✅ Base64URL
  })
});

// 步骤4：验证成功后，执行敏感操作（如修改密码）
if (verifyResponse.ok) {
  const changePasswordResponse = await fetch('/auth/update/password', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      newPassword: 'NewPassword123'
    })
  });
  
  const changeResult = await changePasswordResponse.json();
  if (changeResult.code === 200) {
    console.log('密码修改成功');
  }
}
```

## 注意事项
1. 必须在获取验证选项后的 10 分钟内完成验证
2. Challenge 仅能使用一次，即使验证失败也需要重新获取
3. 验证成功后，有效期为 15 分钟
4. 执行敏感操作后，验证状态会自动清除，需要重新验证才能执行下一个敏感操作
5. Passkey 必须属于当前登录用户

## 验证状态管理
- 验证状态存储在 Redis 中，key 为 `sensitive_verification:userId`
- 验证成功后，状态有效期为 15 分钟
- 执行敏感操作时会自动检查验证状态
- 敏感操作执行成功后会清除验证状态

## 安全性
- Challenge 防重放：每个 challenge 仅能使用一次
- Origin 验证：确保请求来自合法的前端
- 用户归属检查：验证 Passkey 是否属于当前用户
- 签名验证：验证认证器的签名
- Sign Count 检查：防止认证器克隆
- 时效性控制：验证状态仅 15 分钟有效

## 相关接口
- [获取敏感操作验证选项](passkey-sensitive-verification-options.md)
- [修改密码](auth-change-password.md)
- [修改邮箱](auth-change-email.md)
- [密码方式验证](auth-verify-sensitive.md)
- [邮箱验证码方式验证](auth-check-sensitive-verification.md)
