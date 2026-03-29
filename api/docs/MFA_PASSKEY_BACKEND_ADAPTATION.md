# MFA 引入 Passkey 的后端适配说明

## 目标
- 支持 Passkey 作为 MFA 二次验证方式。
- 保持安全策略：当用户第一因子为 Passkey 登录时，MFA 仅允许 TOTP，不允许再次使用 Passkey。

## 关键设计

### 1. MFA Challenge 需要携带策略信息
建议在 challenge 中存储以下字段：
- userId
- 登录来源（loginMethod）：如 password / email / passkey / qq / github / google / microsoft
- allowedMethods：允许的 MFA 方式集合，取值为 `totp`、`passkey`
- 失败次数与过期时间

示例策略：
- 第一因子是 password/email/oauth：`allowedMethods = ["totp", "passkey"]`（按用户能力裁剪）
- 第一因子是 passkey：`allowedMethods = ["totp"]`

### 2. 第一因子接口返回 MFA 入口信息
当需要 MFA 时，返回 `201`：

```json
{
  "code": 201,
  "msg": "需要 MFA 验证",
  "data": {
    "challengeId": "...",
    "method": "totp",
    "methods": ["totp", "passkey"]
  }
}
```

说明：
- `method` 为兼容旧前端保留的默认方式。
- `methods` 是权威字段，用于前端展示可选 MFA 按钮。

### 3. TOTP MFA 验证接口
- 路径：`POST /auth/totp/mfa-verify`
- 验证流程：
1. 校验 challenge 是否有效。
2. 校验 `totp` 是否在 `allowedMethods` 中。
3. 验证 TOTP 成功后消费 challenge。
4. 创建会话并签发 token。

### 4. Passkey MFA 验证接口
- 路径：`POST /auth/passkey/mfa-verify`
- 请求体应包含：
- mfaChallengeId
- passkeyChallengeId
- credentialRawId
- clientDataJSON
- authenticatorData
- signature

验证流程：
1. 校验 `mfaChallengeId` 有效。
2. 校验 `passkey` 是否在 `allowedMethods` 中。
3. 使用 `passkeyChallengeId` 执行 WebAuthn 断言验证。
4. 比对 Passkey 验证得到的 userId 与 MFA challenge 的 userId 必须一致。
5. 成功后消费 challenge 并签发 token。

## 安全要求
- challenge 需短时有效（如 5 分钟）且一次性消费。
- 对 MFA 验证失败计数并限制重试次数。
- 记录登录链路日志（包括一因子类型、MFA 成功/失败）。

## 兼容性
- 未启用 MFA 的用户保持原有登录行为不变（直接返回 200 + token）。
- 保持 `method` 字段兼容旧前端，同时新增 `methods` 供新前端使用。
