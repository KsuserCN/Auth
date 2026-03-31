# TOTP 前端适配文档（仅最新版协议）

## 目标

本项目已切换为仅支持最新版 TOTP/MFA 协议，不再兼容旧请求格式。前端必须按本文档适配。

## 核心规则

1. 恢复码格式：8 位大写字母（A-Z），例如 `ABCDEFGH`。
2. `registration-verify` 必须回传 `registration-options` 返回的 `recoveryCodes`。
3. MFA 登录时：
   - 使用 TOTP：提交 `code`（6 位数字）
   - 使用恢复码：提交 `recoveryCode`（8 位大写字母）
4. 不再支持把 8 位字母恢复码放进 `code` 字段。

## 一、TOTP 注册流程

### 1. 获取注册选项

接口：`POST /auth/totp/registration-options`

示例响应：

```json
{
  "code": 200,
  "msg": "获取 TOTP 注册选项成功",
  "data": {
    "secret": "BASE32SECRET",
    "qrCodeUrl": "otpauth://...",
    "recoveryCodes": [
      "ABCDEFGH",
      "IJKLMNOP",
      "QRSTUVWX",
      "YZABCDEF",
      "GHIJKLMN",
      "OPQRSTUV",
      "WXYZABCD",
      "EFGHIJKL",
      "MNOPQRST",
      "UVWXYZAB"
    ]
  }
}
```

前端处理要求：

1. 保存 `recoveryCodes` 到当前注册流程状态（内存或页面状态）。
2. 引导用户下载/复制/截图保存恢复码。
3. 进入验证码确认步骤。

### 2. 确认注册

接口：`POST /auth/totp/registration-verify`

请求体（必须包含 recoveryCodes）：

```json
{
  "code": "123456",
  "recoveryCodes": [
    "ABCDEFGH",
    "IJKLMNOP",
    "QRSTUVWX",
    "YZABCDEF",
    "GHIJKLMN",
    "OPQRSTUV",
    "WXYZABCD",
    "EFGHIJKL",
    "MNOPQRST",
    "UVWXYZAB"
  ]
}
```

注意：

1. `code` 是 6 位数字 TOTP。
2. `recoveryCodes` 必须是第一步接口返回的同一组恢复码，不可缺失。
3. 若不传 `recoveryCodes`，后端会返回：
   - `恢复码不能为空，请使用 registration-options 返回的 recoveryCodes`

## 二、MFA 登录流程

### 1. 一因子登录后拿到 challengeId

接口可能是：`/auth/login`、`/auth/login-with-code` 等。

201 响应示例：

```json
{
  "code": 201,
  "msg": "需要 MFA 验证",
  "data": {
    "challengeId": "uuid-xxx",
    "methods": ["totp", "passkey"]
  }
}
```

### 2. 提交 TOTP 或恢复码

接口：`POST /auth/totp/mfa-verify`

方式 A：TOTP

```json
{
  "challengeId": "uuid-xxx",
  "code": "123456"
}
```

方式 B：恢复码

```json
{
  "challengeId": "uuid-xxx",
  "recoveryCode": "ABCDEFGH"
}
```

严格要求：

1. 恢复码必须走 `recoveryCode` 字段。
2. `code` 字段仅用于 6 位数字 TOTP。
3. 不支持 `code=ABCDEFGH` 这种旧写法。

## 三、前端校验建议

### 1. 输入校验正则

```ts
const isTotpCode = (v: string) => /^\d{6}$/.test(v);
const isRecoveryCode = (v: string) => /^[A-Z]{8}$/.test(v);
```

### 2. 提交前规范化

```ts
const recoveryCode = input.trim().toUpperCase();
```

### 3. 组装请求体

```ts
function buildMfaPayload(challengeId: string, mode: 'totp' | 'recovery', input: string) {
  if (mode === 'totp') {
    return { challengeId, code: input.trim() };
  }
  return { challengeId, recoveryCode: input.trim().toUpperCase() };
}
```

## 四、错误码与提示文案

1. `challengeId 和 code 或 recoveryCode 不能为空`
   - 提示：请填写动态码或恢复码。
2. `TOTP/恢复码校验失败，剩余尝试次数：x`
   - 提示：验证码或恢复码错误，请重试。
3. `TOTP/恢复码校验失败次数过多，请重新登录`
   - 提示：尝试次数过多，请重新走登录流程。
4. `恢复码不能为空，请使用 registration-options 返回的 recoveryCodes`
   - 提示：前端需要修复注册确认请求体，附带 recoveryCodes。

## 五、改造清单（必须完成）

1. 注册流程状态中增加 `recoveryCodes` 字段并跨步骤保存。
2. `registration-verify` 请求体补齐 `recoveryCodes`。
3. MFA 页面区分两种输入模式：
   - TOTP（6位数字）
   - 恢复码（8位大写字母）
4. `mfa-verify` 提交时按模式选择字段：`code` 或 `recoveryCode`。
5. 删除旧兼容逻辑：不再把 8 位字母写进 `code`。

## 六、最小联调用例

1. 注册成功用例
   1. 调 `registration-options` 拿到 `recoveryCodes`
   2. 调 `registration-verify` 传 `code + recoveryCodes`
   3. 返回 200
2. 注册失败用例
   1. 调 `registration-verify` 只传 `code`
   2. 返回 400，提示恢复码不能为空
3. 登录恢复码成功用例
   1. 调 `mfa-verify` 传 `challengeId + recoveryCode`
   2. 返回 200
4. 登录恢复码失败用例
   1. 调 `mfa-verify` 传 `challengeId + code=ABCDEFGH`
   2. 返回校验失败（这是预期）
