# MFA 偏好前端适配指南

## 目标
新增用户偏好后，前端需要做到两件事：
1. 登录 MFA 流程默认跳到用户偏好方式。
2. 敏感验证弹窗默认打开用户偏好方式。

同时保留“选择其他验证方式”，允许用户手动切换。

## 相关接口
- `GET /auth/info`
- `POST /auth/update/setting`
- `GET /auth/check-sensitive-verification`
- 登录接口（如 `/auth/login`、`/auth/login-with-code`、OAuth callback）

## 一、设置页如何读写偏好

### 1) 读取当前偏好
调用 `GET /auth/info`，从 `data.settings` 读取：
- `preferredMfaMethod`: `totp | passkey`
- `preferredSensitiveMethod`: `password | email-code | passkey | totp`

### 2) 更新登录 MFA 偏好
请求：
```json
{
  "field": "preferred_mfa_method",
  "stringValue": "passkey"
}
```

注意：
- 用户必须已开启 `mfaEnabled=true`。
- 后端会校验该方式当前可用（例如未绑定 Passkey 会报错）。

### 3) 更新敏感验证偏好
请求：
```json
{
  "field": "preferred_sensitive_method",
  "stringValue": "totp"
}
```

注意：
- 后端会校验该方式当前可用（例如未启用 TOTP 会报错）。

## 二、登录 MFA 流程适配

### 后端返回示例
当登录第一因素通过但需要 MFA 时：
```json
{
  "code": 201,
  "msg": "需要 MFA 验证",
  "data": {
    "challengeId": "xxx",
    "method": "passkey",
    "methods": ["passkey", "totp"]
  }
}
```

### 前端处理规则
1. 默认使用 `method` 作为初始验证方式。
2. 展示 `methods` 全部选项，支持“选择其他验证方式”。
3. 用户切换后，按选中的方式继续提交验证。

说明：`method` 已按用户偏好排序；`methods` 保留全部可用方式。

## 三、敏感操作验证适配

### 1) 打开敏感操作前先查状态
调用 `GET /auth/check-sensitive-verification`：
```json
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "verified": false,
    "remainingSeconds": 0,
    "preferredMethod": "passkey",
    "methods": ["passkey", "password", "email-code", "totp"]
  }
}
```

### 2) 前端处理规则
1. `verified=true` 且 `remainingSeconds>0`：直接进入敏感操作页面。
2. 否则弹出验证框，默认选中 `preferredMethod`。
3. 验证框中列出 `methods`，允许“选择其他验证方式”。

### 3) 各方式调用路径
- `password` / `email-code` / `totp`：`POST /auth/verify-sensitive`
- `passkey`：
  - `POST /auth/passkey/sensitive-verification-options`
  - `POST /auth/passkey/sensitive-verification-verify`

## 四、推荐交互细节
- 若后端返回“当前用户不可用所选验证方式”，前端刷新 `GET /auth/info` 或 `GET /auth/check-sensitive-verification` 重新渲染可选项。
- 偏好设置页可把不可用选项置灰并展示原因（未启用 TOTP、未绑定 Passkey、未绑定邮箱、未设置密码）。
- 保持切换入口常驻，避免用户因默认方式不可用而卡死流程。
