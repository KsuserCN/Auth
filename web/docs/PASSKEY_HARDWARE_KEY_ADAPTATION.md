# Passkey 实体安全密钥适配说明

## 背景

当前前端在添加 Passkey 时固定使用自动选择模式：

- `auto`: 自动选择（由浏览器决定）

并且前端在调用浏览器 WebAuthn 注册 API 前，会主动忽略 `authenticatorSelection.authenticatorAttachment`，以确保不会被后端值覆盖。

为了让实体密钥可用，后端在生成 WebAuthn 参数时仍需保证与自动模式兼容，并在登录/敏感验证返回 `allowCredentials`。

## 前端请求变更

接口：`POST /auth/passkey/registration-options`

请求体字段：

```json
{
  "passkeyName": "My YubiKey",
  "authenticatorType": "auto"
}
```

说明：

- 当前前端固定传 `auto`
- 后端建议在字段缺省时也按 `auto` 处理，保证向后兼容

## 后端适配要求

### 1. 处理 `authenticatorType`

当前前端固定 `auto`，后端建议行为：

- `auto` -> 不设置 `authenticatorAttachment`（或置空）

可选兼容（便于未来扩展）：

- `platform` -> `authenticatorAttachment = "platform"`
- `cross-platform` -> `authenticatorAttachment = "cross-platform"`

### 2. 返回的注册选项结构

后端返回字段 `authenticatorSelection`（当前前端已直接透传）中应包含：

```json
{
  "authenticatorSelection": {
    "authenticatorAttachment": null,
    "residentKey": "preferred",
    "userVerification": "preferred"
  }
}
```

其中：

- `authenticatorAttachment` 在 `auto` 模式下可不返回或置空
- `residentKey`、`userVerification` 可保留现有策略

### 3. 登录与敏感操作必须返回 `allowCredentials`

接口：

- `POST /auth/passkey/authentication-options`
- `POST /auth/passkey/sensitive-verification-options`

建议在返回中包含：

```json
{
  "challengeId": "...",
  "challenge": "...",
  "rpId": "example.com",
  "timeout": "60000",
  "userVerification": "preferred",
  "allowCredentials": "[{\"id\":\"base64urlCredentialId\",\"type\":\"public-key\",\"transports\":[\"usb\",\"nfc\",\"ble\"]}]"
}
```

说明：

- 前端已支持解析 `allowCredentials` 并将每个 `id` 从 Base64URL 转为 ArrayBuffer 后传给 `navigator.credentials.get`
- 对于非 discoverable credential（尤其常见于实体密钥），`allowCredentials` 是关键字段
- `transports` 建议按真实能力返回（如 `usb`/`nfc`/`ble`/`hybrid`），可提升浏览器引导准确性

### 4. 兼容与回退策略（推荐）

为降低失败率，建议：

- 继续采用 `auto` 策略，不强制绑定某类认证器
- 当用户设备未插入/未靠近实体密钥时，保持标准 WebAuthn 错误码，前端据此提示用户插入或触碰密钥

### 5. 存储 transports（已在当前前端上送）

前端在注册验证时会上送：

- `transports`: 例如 `usb,nfc,ble,internal,hybrid`

建议后端持久化该字段，便于在设备管理页展示“本设备/USB/NFC/蓝牙”等标签。

## 验证清单

1. 抓包确认 `registration-options` 请求体包含 `"authenticatorType":"auto"`
2. `registration-verify` 成功后，设备列表中的 `transports` 可见 `usb/nfc/ble/internal/hybrid` 之一
3. 登录流程中 `authentication-options` 返回 `allowCredentials` 后，可用实体密钥完成认证
4. 敏感操作流程中 `sensitive-verification-options` 返回 `allowCredentials` 后，可用实体密钥完成认证

## 兼容性提示

- Safari / iOS 对部分外接密钥能力受系统版本影响
- 企业内网环境需确保 RP ID、HTTPS、域名配置满足 WebAuthn 要求
- 对于不支持外接密钥的环境，`auto` 是最稳妥默认值
