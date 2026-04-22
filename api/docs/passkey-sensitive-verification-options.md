# Passkey 获取敏感操作验证选项接口

## 基本信息
- 方法：POST
- 路径：/auth/passkey/sensitive-verification-options
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json

## 用途
此接口用于生成 Passkey 敏感操作验证所需的 WebAuthn 认证选项。用户需要先登录，并在执行敏感操作（如修改密码、修改邮箱、删除账户等）前完成身份验证。

## 安全说明
- 生成的 challenge 只能使用一次，有效期 10 分钟
- 敏感操作强制要求 User Verification（PIN 或生物识别），比普通登录更严格
- 后续验证时会使用存储的公钥进行完整的签名验证
- 会检查 User Verified 标志必须为真（这是敏感操作与普通认证的主要区别）
- 会验证 Passkey 属于当前用户

## 敏感操作说明
需要 Passkey 验证的敏感操作包括：
- 修改密码（/auth/update/password）
- 修改邮箱（/auth/update/email）
- 删除账户（待实现）
- 其他敏感操作

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
无需请求体（空 JSON 对象 `{}` 或不传递）

## 请求示例
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  http://localhost:8000/auth/passkey/sensitive-verification-options
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "message": "生成敏感操作验证选项成功",
  "data": {
    "challengeId": "uuid-string",
    "challenge": "base64编码的challenge",
    "timeout": "300000",
    "rpId": "localhost",
    "userVerification": "required"
  }
}
```

## 响应字段说明
- challenge: 随机生成的 challenge，用于防止重放攻击，有效期 10 分钟
- timeout: 用户交互超时时间（毫秒）
- rpId: Relying Party ID（不包含协议和端口）
- userVerification: 用户验证要求（敏感操作固定为 "required"，强制要求用户验证）

## 失败响应

### 1) 未登录
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录"
}
```

### 2) 用户未注册 Passkey
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "用户未注册 Passkey"
}
```

### 3) 内部错误
- HTTP Status：500

```json
{
  "code": 500,
  "msg": "生成验证选项失败"
}
```

## 前端集成示例

```javascript
// 1. 获取验证选项
const response = await fetch('/auth/passkey/sensitive-verification-options', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`
  }
});

const result = await response.json();
const options = result.data;

// 2. 解析选项
const credentialRequestOptions = {
  publicKey: {
    challenge: Uint8Array.from(atob(options.challenge), c => c.charCodeAt(0)),
    timeout: parseInt(options.timeout),
    rpId: options.rpId,
    userVerification: options.userVerification
  }
};

// 3. 进行认证
const assertion = await navigator.credentials.get(credentialRequestOptions);

// 4. 继续下一步：提交验证结果
// 参见：passkey-sensitive-verification-verify.md
```

## 注意事项
1. 必须先登录才能调用此接口
2. 用户必须已注册至少一个 Passkey
3. Challenge 有效期为 10 分钟，过期后需要重新获取
4. 敏感操作验证强制要求用户验证（userVerification: required）
5. 验证成功后，有效期为 15 分钟，在此期间可以执行敏感操作

## 验证有效期
- 验证成功后，系统会在 Redis 中存储验证状态
- 有效期为 15 分钟
- 执行敏感操作时会自动检查验证状态
- 验证状态在成功执行敏感操作后会自动清除

## 相关接口
- [完成敏感操作验证](passkey-sensitive-verification-verify.md)
- [修改密码](auth-change-password.md)
- [修改邮箱](auth-change-email.md)
- [密码方式验证](auth-verify-sensitive.md)
