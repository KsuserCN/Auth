# Passkey 完成注册接口

## 基本信息
- 方法：POST
- 路径：/auth/passkey/registration-verify
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json

## 用途
此接口用于提交 WebAuthn 凭证，完成 Passkey 注册。需要先调用 `/auth/passkey/registration-options` 获取注册选项。

## ⚠️ 编码要求

**所有二进制数据必须使用 Base64URL 编码（RFC 4648 Section 5）**

- `credentialRawId`: ✅ 必须是 Base64URL（不含 `+`, `/`, `=`）
- `attestationObject`: ✅ 必须是 Base64URL
- `clientDataJSON`: ✅ 必须是 Base64URL

**常见错误：** 如果使用标准 Base64 编码（包含 `+`, `/`, `=`），后端会返回 "Input data does not match expected form" 错误。

**解决方案：** 参考 [前端集成指南](./PASSKEY_FRONTEND_INTEGRATION.md) 中的 `arrayBufferToBase64URL()` 函数。

## 安全说明
生产级的完整验证流程（使用 webauthn4j 库）：
1. ✅ Challenge 验证和一次性使用（防重放）
2. ✅ Attestation 完整性验证（包括签名验证）
3. ✅ ClientDataJSON 验证（type/challenge/origin）
4. ✅ RP ID Hash 验证（防冒充）
5. ✅ Flags 验证（UP=1, AT=1）
6. ✅ 提取 credentialPublicKey（COSE_Key）
7. ✅ Credential ID 唯一性检查
8. ✅ 安全存储公钥，用于后续认证时的签名验证

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
```json
{
  "credentialRawId": "base64编码的credential ID",
  "clientDataJSON": "base64编码的ClientDataJSON",
  "attestationObject": "base64编码的attestationObject",
  "passkeyName": "My Security Key",
  "transports": "usb,nfc,ble"
}
```

## 字段说明
- credentialRawId: 凭证 ID，通过 base64 编码的二进制数据
- clientDataJSON: 客户端数据 JSON，包含 challenge、origin 等信息
- attestationObject: 证明对象，包含认证器数据和证明声明
- passkeyName: Passkey 名称（可选，如果未提供则使用注册选项中的名称）
- transports: 认证器支持的传输方式，多个值用逗号分隔（如："usb,nfc,ble,internal"）

## 请求示例
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "credentialRawId":"base64string...",
    "clientDataJSON":"base64string...",
    "attestationObject":"base64string...",
    "passkeyName":"My Security Key",
    "transports":"usb,nfc"
  }' \
  http://localhost:8000/auth/passkey/registration-verify
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "message": "Passkey 注册成功",
  "data": {
    "passkeyId": 1,
    "passkeyName": "My Security Key",
    "createdAt": "2026-02-05T10:30:00"
  }
}
```

## 响应字段说明
- passkeyId: 新创建的 Passkey ID，用于后续管理操作
- passkeyName: Passkey 名称
- createdAt: 创建时间

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

### 4) Credential 已存在
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "此 Passkey 已注册"
}
```

### 5) 注册失败
- HTTP Status：500

```json
{
  "code": 500,
  "msg": "Passkey 注册失败"
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

// 接上一步：passkey-registration-options.md

// 3. 创建凭证
const credential = await navigator.credentials.create(credentialCreationOptions);

// 4. 提交凭证进行验证（使用 Base64URL 编码）
const verifyResponse = await fetch('/auth/passkey/registration-verify', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`
  },
  body: JSON.stringify({
    credentialRawId: arrayBufferToBase64URL(credential.rawId),                          // ✅ Base64URL
    clientDataJSON: arrayBufferToBase64URL(credential.response.clientDataJSON),         // ✅ Base64URL
    attestationObject: arrayBufferToBase64URL(credential.response.attestationObject),   // ✅ Base64URL
    passkeyName: 'My Security Key',
    transports: credential.response.getTransports().join(',')
  })
});

const result = await verifyResponse.json();
if (result.code === 200) {
  console.log('Passkey 注册成功:', result.data.passkeyId);
}
```

## 注意事项
1. 必须在获取注册选项后的 10 分钟内完成注册
2. 每个 credentialId 只能注册一次
3. Origin 必须与配置的 origin 一致
4. Challenge 仅能使用一次，即使验证失败也需要重新获取
5. transports 字段帮助前端识别认证器类型，建议填写

## 安全性
- Challenge 防重放：每个 challenge 仅能使用一次
- Origin 验证：确保请求来自合法的前端
- 凭证唯一性：防止重复注册
- 认证器数据验证：检查 RP ID hash、flags 等

## 相关接口
- [获取注册选项](passkey-registration-options.md)
- [获取 Passkey 列表](passkey-list.md)
- [删除 Passkey](passkey-delete.md)
