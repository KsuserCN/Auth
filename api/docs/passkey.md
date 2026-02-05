# Passkey (WebAuthn) API 文档

## ⚠️ 重要：前端集成要求

**所有二进制数据必须使用 Base64URL 编码（RFC 4648 Section 5）发送给后端。**

### 快速说明

- ✅ **正确**：使用 Base64URL（字符集：`A-Za-z0-9-_`，无 padding）
- ❌ **错误**：使用标准 Base64（字符集：`A-Za-z0-9+/`，有 `=` padding）

**示例对比：**
```
Base64URL:    __uxKQbLE8aYZ4GIWRmJq9eorb4     ✅ 正确
标准 Base64:  //uxKQbLE8aYZ4GIWRmJq9eorb4=    ❌ 错误（会导致验证失败）
```

**完整集成指南：** 请参考 [Passkey 前端集成指南](./PASSKEY_FRONTEND_INTEGRATION.md)

---

## 概述

Passkey 是基于 WebAuthn 标准的现代身份验证方式，支持以下场景：
- **注册 Passkey**：用户可以注册和管理多个 Passkey
- **Passkey 登录**：用户可以使用 Passkey 直接登录
- **敏感操作验证**：用户可以使用 Passkey 验证敏感操作（如删除账户、修改密码等）

## Passkey 注册流程

### 1. 获取注册选项
**请求：**
```
POST /auth/passkey/registration-options
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "passkeyName": "My Security Key"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "生成注册选项成功",
  "data": {
    "challenge": "base64编码的challenge",
    "rp": "{\"name\":\"KSUser Auth API\",\"id\":\"localhost\"}",
    "user": "{\"id\":\"base64编码的userId\",\"name\":\"user@example.com\",\"displayName\":\"username\"}",
    "pubKeyCredParams": "[{\"type\":\"public-key\",\"alg\":-7}]",
    "timeout": "300000",
    "attestation": "none",
    "authenticatorSelection": "{\"authenticatorAttachment\":\"platform\",\"residentKey\":\"preferred\",\"userVerification\":\"preferred\"}"
  }
}
```

### 2. 完成 Passkey 注册
**请求：**
```
POST /auth/passkey/registration-verify
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "credentialRawId": "base64编码的credential ID",
  "clientDataJSON": "base64编码的ClientDataJSON",
  "attestationObject": "base64编码的attestationObject",
  "passkeyName": "My Security Key",
  "transports": "usb,nfc,ble"
}
```

**响应：**
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

## Passkey 登录流程

### 1. 获取认证选项
**请求：**
```
POST /auth/passkey/authentication-options
Content-Type: application/json
```

**响应：**
```json
{
  "code": 200,
  "message": "生成认证选项成功",
  "data": {
    "challenge": "base64编码的challenge",
    "timeout": "300000",
    "rpId": "localhost",
    "userVerification": "preferred"
  }
}
```

### 2. 完成 Passkey 认证
**请求：**
```
POST /auth/passkey/authentication-verify?challengeId={challengeId}
Content-Type: application/json

{
  "credentialRawId": "base64编码的credential ID",
  "clientDataJSON": "base64编码的ClientDataJSON",
  "authenticatorData": "base64编码的authenticatorData",
  "signature": "base64编码的签名"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

> **注意：** 此时 RefreshToken 会自动设置到 HttpOnly Cookie 中

## 敏感操作验证（Passkey）

### 1. 获取敏感操作验证选项
**请求：**
```
POST /auth/passkey/sensitive-verification-options
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**响应：**
```json
{
  "code": 200,
  "message": "生成敏感操作验证选项成功",
  "data": {
    "challenge": "base64编码的challenge",
    "timeout": "300000",
    "rpId": "localhost",
    "userVerification": "required"
  }
}
```

### 2. 验证敏感操作
**请求：**
```
POST /auth/passkey/sensitive-verification-verify
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "credentialRawId": "base64编码的credential ID",
  "clientDataJSON": "base64编码的ClientDataJSON",
  "authenticatorData": "base64编码的authenticatorData",
  "signature": "base64编码的签名"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "验证成功，有效期15分钟"
}
```

> **注意：** 验证成功后，用户可以在接下来的 15 分钟内执行敏感操作（修改邮箱、修改密码、删除账户等）

## Passkey 管理接口

### 获取 Passkey 列表
**请求：**
```
GET /auth/passkey/list
Authorization: Bearer {accessToken}
```

**响应：**
```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "passkeys": [
      {
        "id": 1,
        "name": "My Security Key",
        "transports": "usb,nfc",
        "lastUsedAt": "2026-02-05T10:30:00",
        "createdAt": "2026-02-05T09:00:00"
      }
    ]
  }
}
```

### 删除 Passkey
**请求：**
```
DELETE /auth/passkey/{passkeyId}
Authorization: Bearer {accessToken}
```

**响应：**
```json
{
  "code": 200,
  "message": "Passkey 删除成功"
}
```

## 配置说明

在 `application.properties` 中可以配置 Passkey 相关参数：

```properties
# Passkey (WebAuthn) 配置
app.passkey.rp-name=KSUser Auth API
app.passkey.rp-id=localhost
app.passkey.origin=http://localhost:5173
app.passkey.attestation=none
app.passkey.user-verification=preferred
app.passkey.resident-key=preferred
app.passkey.timeout=300000
```

### 参数说明

- **rp-name**：Relying Party 名称，显示给用户
- **rp-id**：Relying Party ID，WebAuthn 验证时使用（不包含协议和端口）
- **origin**：当前应用的 Origin，根据 `app.debug` 自动切换：
  - `debug=true`: `http://localhost:5173`
  - `debug=false`: `https://auth.ksuser.cn`
- **attestation**：证明等级，可选值：`none`、`indirect`、`direct`
- **user-verification**：用户验证要求，可选值：`required`、`preferred`、`discouraged`
- **resident-key**：凭证存储方式，可选值：`required`、`preferred`、`discouraged`
- **timeout**：用户交互超时时间（毫秒）

## 前端集成指南

### 1. 检查 WebAuthn 支持
```javascript
if (window.PublicKeyCredential === undefined) {
  console.error('此浏览器不支持 WebAuthn');
}
```

### 2. 获取注册选项并创建凭证
```javascript
// 1. 获取注册选项
const optionsResponse = await fetch('/auth/passkey/registration-options', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`
  },
  body: JSON.stringify({
    passkeyName: 'My Security Key'
  })
});

const optionsData = await optionsResponse.json();
const options = optionsData.data;

// 2. 解析选项
const credentialCreationOptions = {
  publicKey: {
    challenge: Uint8Array.from(atob(options.challenge), c => c.charCodeAt(0)),
    rp: JSON.parse(options.rp),
    user: JSON.parse(options.user),
    pubKeyCredParams: JSON.parse(options.pubKeyCredParams),
    timeout: parseInt(options.timeout),
    attestation: options.attestation,
    authenticatorSelection: JSON.parse(options.authenticatorSelection)
  }
};

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
```

### 3. 获取认证选项并进行认证
```javascript
// 1. 获取认证选项
const optionsResponse = await fetch('/auth/passkey/authentication-options', {
  method: 'POST'
});

const optionsData = await optionsResponse.json();
const options = optionsData.data;

// 2. 获取 challengeId（前端需要保存，以便后续使用）
const challengeId = options.challengeId;

// 3. 解析选项
const credentialRequestOptions = {
  publicKey: {
    challenge: Uint8Array.from(atob(options.challenge), c => c.charCodeAt(0)),
    timeout: parseInt(options.timeout),
    rpId: options.rpId,
    userVerification: options.userVerification
  }
};

// 4. 进行认证
const assertion = await navigator.credentials.get(credentialRequestOptions);

// 5. 提交认证结果
const verifyResponse = await fetch(`/auth/passkey/authentication-verify?challengeId=${challengeId}`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    credentialRawId: btoa(String.fromCharCode.apply(null, new Uint8Array(assertion.rawId))),
    clientDataJSON: btoa(String.fromCharCode.apply(null, new Uint8Array(assertion.response.clientDataJSON))),
    authenticatorData: btoa(String.fromCharCode.apply(null, new Uint8Array(assertion.response.authenticatorData))),
    signature: btoa(String.fromCharCode.apply(null, new Uint8Array(assertion.response.signature)))
  })
});

const result = await verifyResponse.json();
if (result.code === 200) {
  // 登录成功，accessToken 在 result.data.accessToken 中
  localStorage.setItem('accessToken', result.data.accessToken);
}
```

## 错误处理

| HTTP 状态码 | 错误代码 | 错误消息 | 说明 |
|-----------|-------|--------|------|
| 400 | 400 | Challenge 已过期或不存在 | 需要重新获取选项 |
| 400 | 400 | Origin 不匹配 | 前端 Origin 与配置不符 |
| 401 | 401 | 未登录 | 缺少有效的 AccessToken |
| 401 | 401 | Passkey 验证失败 | credential 不存在或无效 |
| 409 | 409 | Passkey 不属于当前用户 | 尝试使用他人的 Passkey |
| 500 | 500 | Passkey 注册失败 | 服务器内部错误 |

## 安全考虑

1. **Challenge 防重放**：每个 challenge 仅可使用一次，过期时间为 10 分钟
2. **原点验证**：验证客户端 Origin 与配置一致
3. **签名验证**：验证认证器签名的有效性
4. **Sign Count 防克隆**：记录并检查 sign count，防止认证器克隆
5. **IP 绑定**（可选）：敏感操作验证时可以绑定客户端 IP

## 常见问题

### Q1: 如何更新前端 Origin？
在 `application.properties` 中修改 `app.passkey.origin`，或设置环境变量 `PASSKEY_ORIGIN`。当 `app.debug=true` 时自动设置为 `http://localhost:5173`，`false` 时为 `https://auth.ksuser.cn`。

### Q2: Passkey 支持跨域吗？
Passkey 的 Origin 必须与认证时的实际 Origin 一致，不支持跨域认证。如需支持多个 Origin，前端应该在对应的 Origin 下进行 Passkey 操作。

### Q3: 如何处理用户没有注册 Passkey 的情况？
获取用户 Passkey 列表，如果为空则提示用户先注册 Passkey，之后才能使用 Passkey 相关功能。

### Q4: 敏感操作验证的 15 分钟有效期是如何计算的？
从验证成功时开始计算，15 分钟后过期。如需再次进行敏感操作，需要重新验证。
