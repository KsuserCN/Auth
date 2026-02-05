# Passkey 完成注册接口

## 基本信息
- 方法：POST
- 路径：/auth/passkey/registration-verify
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json

## 用途
此接口用于提交 WebAuthn 凭证，完成 Passkey 注册。需要先调用 `/auth/passkey/registration-options` 获取注册选项。

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
// 接上一步：passkey-registration-options.md

// 3. 创建凭证
const credential = await navigator.credentials.create(credentialCreationOptions);

// 4. 提交凭证进行验证
const verifyResponse = await fetch('/auth/passkey/registration-verify', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`
  },
  body: JSON.stringify({
    credentialRawId: btoa(String.fromCharCode.apply(null, new Uint8Array(credential.rawId))),
    clientDataJSON: btoa(String.fromCharCode.apply(null, new Uint8Array(credential.response.clientDataJSON))),
    attestationObject: btoa(String.fromCharCode.apply(null, new Uint8Array(credential.response.attestationObject))),
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
