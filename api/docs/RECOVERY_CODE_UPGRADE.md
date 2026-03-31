# 恢复码升级指南

## 概述

恢复码已升级为 **8 位英文字母（A-Z）** 格式，并支持在 MFA 登录时作为 TOTP 的备用验证方式。

| 属性 | 旧版本 | 新版本 |
|------|--------|--------|
| 长度 | 8 位 | 8 位 |
| 字符 | 纯数字 (0-9) | 纯字母 (A-Z) |
| 登录 MFA 支持 | ❌ 否 | ✅ 是 |
| 全部消耗后 | - | 自动删除 TOTP |

---

## 恢复码流程

### 1. 获取恢复码（注册步骤）

**API**: `POST /auth/totp/registration-options`

#### 请求
```bash
curl -X POST http://localhost:8080/auth/totp/registration-options \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json"
```

#### 响应 (200 OK)
```json
{
  "code": 200,
  "message": "获取 TOTP 注册选项成功",
  "data": {
    "secret": "JBSWY3DPEBLW64TMMQ======",
    "qrCodeUrl": "otpauth://totp/Ksuser%20CAS:user@example.com?secret=JBSWY3DPEBLW64TMMQ%3D%3D%3D%3D%3D%3D&issuer=Ksuser%20CAS",
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

**重要提示**：
- 返回的 **recoveryCodes** 是 10 个恢复码
- 每个恢复码由 **8 个大写英文字母** 组成
- **务必妥善保管这 10 个恢复码**，可以截图或下载保存
- 这些恢复码仅在当前注册过程中有效

### 2. 确认 TOTP 注册

**API**: `POST /auth/totp/registration-verify`

#### 请求
```bash
curl -X POST http://localhost:8080/auth/totp/registration-verify \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "123456"
  }'
```

- `code`: 从 Google Authenticator 等应用中复制的 **6 位数字**

#### 响应 (200 OK)
```json
{
  "code": 200,
  "message": "TOTP 注册成功"
}
```

**注意**：注册成功后，系统会自动生成一组新的 10 个恢复码并保存到数据库。客户端需要再次调用获取恢复码接口来获取这个最终版本。

---

## MFA 登录时使用恢复码

### 登录流程 - 使用 TOTP 码或恢复码

**第一步**：用户名密码登录 → 返回 `201 + challengeId`

**第二步**：选择二次验证方式

**API**: `POST /auth/totp/mfa-verify`

#### 方式 1: TOTP 动态码（优先）

```json
{
  "challengeId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "code": "123456"
}
```

- `code`: 6 位数字（来自 Google Authenticator 等）

#### 方式 2: 恢复码（备用）

```json
{
  "challengeId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "recoveryCode": "ABCDEFGH"
}
```

- `recoveryCode`: 8 位英文字母
- **登录提示**：可以显示"如果无法获取动态码，可以输入恢复码"

#### 响应 (200 OK) - 验证成功
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJ..."
  }
}
```

#### 响应 (400 Bad Request) - 验证失败
```json
{
  "code": 400,
  "message": "TOTP/恢复码校验失败，剩余尝试次数：2"
}
```

---

## 恢复码的特殊行为

### 1. 恢复码使用后自动失效

- 每个恢复码只能使用 **一次**
- 使用后自动被标记为已使用
- 再次尝试使用同一个恢复码会失败

### 2. 全部恢复码消耗后自动删除 TOTP

- 如果用户用完了全部 10 个恢复码
- 系统会自动删除该用户的 TOTP 配置（包括所有剩余恢复码）
- 用户需要重新启用 TOTP 来恢复二因素认证

**提示**：建议在前端提醒用户：
- "您已使用了最后一个恢复码，TOTP 已被禁用"
- "请重新设置 TOTP 以继续使用二因素认证"

### 3. 获取未使用的恢复码列表

**API**: `GET /auth/totp/recovery-codes`

```bash
curl -X GET http://localhost:8080/auth/totp/recovery-codes \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json"
```

#### 响应 (200 OK)
```json
{
  "code": 200,
  "message": "获取回复码成功",
  "data": [
    "IJKLMNOP",
    "WXYZABCD",
    "EFGHIJKL"
  ]
}
```

- 列表中只包含 **未使用的恢复码**
- 如果全部已使用或 TOTP 已被删除，返回空数组

---

## 前端适配检查清单

### 注册 TOTP 时

- [ ] 调用 `/auth/totp/registration-options` 获取恢复码
- [ ] 显示 QR 码给用户扫描
- [ ] **显示恢复码列表并提示用户妥善保管**
  - 可以提供下载、截图、打印等选项
  - 建议格式：竖排列表，每行一个恢复码
- [ ] 待用户扫描 QR 码并输入 6 位验证码
- [ ] 调用 `/auth/totp/registration-verify` 完成注册
- [ ] 再次获取恢复码（可选，确认最终版本）

### 登录 MFA 时

- [ ] 用户名密码登录成功后获得 `challengeId`
- [ ] 显示两个输入选项：
  - **输入 TOTP 码**（默认，6 位数字）
  - **或者输入恢复码**（备用，8 位字母，需要切换输入模式）
- [ ] 输入验证后调用 `/auth/totp/mfa-verify`
  - 若提交 TOTP 码：`{"challengeId": "...", "code": "123456"}`
  - 若提交恢复码：`{"challengeId": "...", "recoveryCode": "ABCDEFGH"}`
- [ ] 处理响应
  - 200：登录成功，获取 accessToken
  - 400：验证失败，显示剩余尝试次数
  - 特殊：如果响应提示恢复码已全部用完，提示用户需重新启用 TOTP

### UX 建议

#### 输入界面示例

```
[第二步] 二次验证
━━━━━━━━━━━━━━━━━━━
📱 输入 TOTP 验证码
┌─────────────┐
│ _ _ _ _ _ _ │  (6 位数字)
└─────────────┘
━━━━━━━━━━━━━━━━━━━

或者

🔑 使用恢复码
┌─────────────────────┐
│ _ _ _ _ _ _ _ _     │  (8 位字母)
└─────────────────────┘
💡 恢复码由大写字母组成，
   如 ABCDEFGH
```

#### 恢复码显示示例

```
✅ TOTP 设置成功！

请妥善保管以下 10 个恢复码：

 1. ABCDEFGH
 2. IJKLMNOP
 3. QRSTUVWX
 4. YZABCDEF
 5. GHIJKLMN
 6. OPQRSTUV
 7. WXYZABCD
 8. EFGHIJKL
 9. MNOPQRST
10. UVWXYZAB

💾 [下载] [截图] [打印]

⚠️ 重点：
- 如果丢失了 TOTP 设备（如手机），可以使用这些恢复码来登录
- 每个恢复码只能使用一次
- 建议打印或存储在安全的地方
```

---

## 常见问题 (FAQ)

### Q: 恢复码中是否包括数字？
**A**: 不包括。新的恢复码 **仅包含大写英文字母 A-Z**，不包含数字。

### Q: 如果用完所有 10 个恢复码会怎样？
**A**: 系统会自动删除该用户的 TOTP 配置。用户需要重新设置 TOTP 来恢复二因素认证。

### Q: 恢复码可以在任何地方使用吗？
**A**: 恢复码只能在登录时的 MFA 验证阶段使用（`/auth/totp/mfa-verify`）。在其他场景（如敏感操作验证）的 `/auth/totp/verify` 接口也支持恢复码。

### Q: 恢复码无效是什么原因？
**A**: 可能的原因：
1. 恢复码已经使用过（每个恢复码只能用一次）
2. 恢复码已过期（TOTP 被禁用后恢复码也删除了）
3. 恢复码输入有误（需要区分大小写，且为 8 位字母）

### Q: 如何查看还剩多少恢复码？
**A**: 调用 `GET /auth/totp/recovery-codes` 会返回未使用的恢复码列表。

### Q: 支持重新生成恢复码吗？
**A**: 是的，可以调用 `POST /auth/totp/recovery-codes/regenerate` 获取新的一组 10 个恢复码。

---

## 迁移建议

### 对现有用户的影响

- **新设置的 TOTP**：使用新格式（8 位字母恢复码）
- **已存在的 TOTP**：继续使用旧格式（8 位数字恢复码）
  - 建议在用户界面中提示用户重新生成恢复码以获得新格式

### 前端兼容性

```javascript
// ✅ 检查恢复码格式的方法
const isNewFormat = (code) => /^[A-Z]{8}$/.test(code);
const isOldFormat = (code) => /^\d{8}$/.test(code);

// 使用示例
if (isNewFormat(code)) {
  // 新格式：纯字母
  submit({ recoveryCode: code });
} else if (isOldFormat(code)) {
  // 旧格式：纯数字（如果后端仍支持）
  submit({ recoveryCode: code });
}
```

---

## API 变更摘要

| 接口 | 变更 | 影响 |
|------|------|------|
| `POST /auth/totp/registration-options` | 返回的恢复码为 8 位字母 | ✅ 需适配 UI 显示 |
| `POST /auth/totp/registration-verify` | 无变更 | ✅ 无需改动 |
| `POST /auth/totp/mfa-verify` | 新增 `recoveryCode` 字段 | ✅ 需支持恢复码登录 |
| `POST /auth/totp/verify` | 已支持 `recoveryCode` | ✅ 无需改动 |
| `GET /auth/totp/recovery-codes` | 返回 8 位字母恢复码 | ✅ 无需改动 |
| `POST /auth/totp/recovery-codes/regenerate` | 返回 8 位字母恢复码 | ✅ 无需改动 |

---

## 技术细节

### 恢复码存储

- **数据库存储**：SHA-256 哈希 + AES-GCM 加密密文
- **数据库字段**：
  - `code_hash`：8 位字母的 SHA-256 哈希（32 字节二进制）
  - `code_ciphertext`：原文恢复码的 AES-GCM 密文
  - `used_at`：使用时间（NULL 表示未使用）

### 验证过程

```
1. 用户输入恢复码 "ABCDEFGH"
   ↓
2. 系统计算 SHA-256("ABCDEFGH")
   ↓
3. 查询数据库找到对应的 code_hash → 找到记录
   ↓
4. 检查 used_at 是否为 NULL → 未使用
   ↓
5. 更新 used_at = 当前时间 → 标记为已使用
   ↓
6. 检查是否所有恢复码都已使用 → 全部用完则删除 TOTP
   ↓
7. 验证成功 ✅
```

---

## 支持与反馈

- 如有问题，请参考后端日志中的错误信息
- 对于用户反馈，可以提示重新生成恢复码或禁用/重新启用 TOTP
