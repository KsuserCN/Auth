# Passkey 获取认证选项接口

## 基本信息
- 方法：POST
- 路径：/auth/passkey/authentication-options
- 需要认证：否
- 请求类型：application/json

## 用途
此接口用于生成 Passkey 登录所需的 WebAuthn 认证选项。用户无需登录即可获取认证选项，这是 Passkey 登录流程的第一步。

## 安全说明
- 生成的 challenge 只能使用一次，有效期 10 分钟
- 后续认证时会使用存储的公钥进行完整的签名验证
- 会检查 Sign Count，防止克隆的 Passkey
- Origin 和 RP ID 必须匹配，防止跨域攻击
- User Presence 标志必须为真

## 请求体
无需请求体（空 JSON 对象 `{}` 或不传递）

## 请求示例
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  http://localhost:8000/auth/passkey/authentication-options
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "message": "生成认证选项成功",
  "data": {
    "challengeId": "uuid-string",
    "challenge": "base64编码的challenge",
    "timeout": "300000",
    "rpId": "localhost",
    "userVerification": "preferred"
  }
}
```

## 响应字段说明
- **challengeId**: challenge 的孤一标识符（**待比必须保存，下一步验证时需要**）
- challenge: 随机生成的 challenge，用于防止重放攻击，有效期 10 分钟
- timeout: 用户交互超时时间（毫秒）
- rpId: Relying Party ID（不包含协议和端口）
- userVerification: 用户验证要求（required/preferred/discouraged）

## 失败响应

### 1) 内部错误
- HTTP Status：500

```json
{
  "code": 500,
  "msg": "生成认证选项失败"
}
```

## 前端集成示例

```javascript
// 1. 获取认证选项
const response = await fetch('/auth/passkey/authentication-options', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  }
});

const result = await response.json();
const options = result.data;
const challengeId = options.challengeId; // 保存 challengeId，用于后续验证

// 2. 解析 WebAuthn 选项
const credentialRequestOptions = {
  publicKey: {
    challenge: Uint8Array.from(atob(options.challenge), c => c.charCodeAt(0)),
    timeout: parseInt(options.timeout),
    rpId: options.rpId,
    userVerification: options.userVerification
  }
};

// 3. 进行 Passkey 认证
const assertion = await navigator.credentials.get(credentialRequestOptions);

// 4. 提交认证结果（需要传递 challengeId）
// 参见：passkey-authentication-verify.md
await fetch('/auth/passkey/authentication-verify?challengeId=' + challengeId, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    credentialRawId: btoa(String.fromCharCode(...new Uint8Array(assertion.id))),
    clientDataJSON: btoa(String.fromCharCode(...new Uint8Array(assertion.response.clientDataJSON))),
    authenticatorData: btoa(String.fromCharCode(...new Uint8Array(assertion.response.authenticatorData))),
    signature: btoa(String.fromCharCode(...new Uint8Array(assertion.response.signature)))
  })
});
```

## 注意事项
1. 此接口无需登录即可调用
2. Challenge 有效期为 10 分钟，过期后需要重新获取
3. 前端需要保存 challenge 的原始值，用于后续验证
4. 前端需要检查浏览器是否支持 WebAuthn API（window.PublicKeyCredential）
5. 建议在认证前检查用户是否有已注册的 Passkey（可通过 userVerification 配置）

## WebAuthn 浏览器支持
Passkey 功能需要浏览器支持 WebAuthn API：
- Chrome 67+
- Firefox 60+
- Safari 13+
- Edge 18+

检查方法：
```javascript
if (window.PublicKeyCredential === undefined) {
  alert('此浏览器不支持 Passkey 登录');
}
```

## 相关接口
- [完成 Passkey 认证](passkey-authentication-verify.md)
- [密码登录](auth-login.md)
- [验证码登录](auth-login-with-code.md)
