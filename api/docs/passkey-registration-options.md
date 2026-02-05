# Passkey 获取注册选项接口

## 基本信息
- 方法：POST
- 路径：/auth/passkey/registration-options
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json

## 用途
此接口用于生成 Passkey 注册所需的 WebAuthn 选项。用户需要先登录才能注册 Passkey。

## 安全说明
- 生成的 challenge 只能使用一次，有效期 10 分钟
- 注册时会进行完整的 Attestation 验证（包括签名验证）
- 提取并安全存储公钥（COSE_Key 格式），用于后续认证时的签名验证
- Credential ID 会进行唯一性检查，防止重复注册
- 所有操作都记录到数据库，便于审计

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
```json
{
  "passkeyName": "My Security Key"
}
```

## 字段说明
- passkeyName: Passkey 名称，用于用户识别，建议使用设备名称（如"iPhone"、"MacBook Pro"、"YubiKey"等）

## 请求示例
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"passkeyName":"My Security Key"}' \
  http://localhost:8000/auth/passkey/registration-options
```

## 成功响应
- HTTP Status：200

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

## 响应字段说明
- challenge: 随机生成的 challenge，用于防止重放攻击，有效期 10 分钟
- rp: Relying Party 信息（服务提供方），包含名称和 ID
- user: 用户信息，包含 userId、邮箱和用户名
- pubKeyCredParams: 支持的公钥算法列表
- timeout: 用户交互超时时间（毫秒）
- attestation: 证明等级（none/indirect/direct）
- authenticatorSelection: 认证器选择条件

## 失败响应

### 1) 未登录
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录"
}
```

### 2) Passkey 名称为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "Passkey 名称不能为空"
}
```

## 前端集成示例

```javascript
// 1. 获取注册选项
const response = await fetch('/auth/passkey/registration-options', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`
  },
  body: JSON.stringify({
    passkeyName: 'My Security Key'
  })
});

const result = await response.json();
const options = result.data;

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

// 4. 继续下一步：提交凭证进行验证
// 参见：passkey-registration-verify.md
```

## 注意事项
1. challenge 有效期为 10 分钟，过期后需要重新获取
2. 每个用户可以注册多个 Passkey
3. 建议使用有意义的 Passkey 名称，方便用户管理多个认证器
4. 前端需要检查浏览器是否支持 WebAuthn API（window.PublicKeyCredential）

## 相关接口
- [完成 Passkey 注册](passkey-registration-verify.md)
- [获取 Passkey 列表](passkey-list.md)
