# TOTP 功能使用说明

## 功能概述

已在登录选项页面添加了完整的 TOTP (基于时间的一次性密码) 双因素认证功能，包括：

1. **启用 TOTP**：通过扫描二维码或手动输入密钥
2. **敏感操作验证**：使用 TOTP 验证码或回复码
3. **查看回复码**：查看未使用的回复码（部分显示）
4. **重新生成回复码**：生成新的回复码
5. **禁用 TOTP**：关闭双因素认证

## 新增的 API 接口

在 `src/api/auth.ts` 中添加了以下接口：

### 1. 获取 TOTP 状态

```typescript
getTotpStatus(): Promise<TotpStatusResponse>
```

### 2. 获取 TOTP 注册选项

```typescript
getTotpRegistrationOptions(): Promise<TotpRegistrationOptionsResponse>
```

### 3. 确认 TOTP 注册

```typescript
verifyTotpRegistration(data: TotpRegistrationVerifyRequest): Promise<void>
```

### 4. TOTP 验证

```typescript
verifyTotp(data: TotpVerifyRequest): Promise<TotpVerifyResponse>
```

### 5. 获取回复码列表

```typescript
getRecoveryCodes(): Promise<string[]>
```

### 6. 重新生成回复码

```typescript
regenerateRecoveryCodes(): Promise<string[]>
```

### 7. 禁用 TOTP

```typescript
disableTotp(): Promise<void>
```

## 界面功能

### 1. TOTP 状态显示

- 显示 TOTP 是否已启用
- 显示剩余回复码数量
- 当回复码少于 3 个时，显示警告标签

### 2. 启用 TOTP 流程

1. 点击"启用 TOTP"按钮
2. 扫描二维码或手动输入密钥到身份验证器应用（如 Google Authenticator）
3. 输入身份验证器显示的 6 位验证码
4. 保存 10 个回复码（可复制或下载）
5. 确认启用

### 3. 敏感操作验证

- 支持使用 TOTP 验证码（6 位数字）
- 支持使用回复码（8 位数字）
- 验证码每 30 秒更新一次

### 4. 回复码管理

- **查看回复码**：查看未使用的回复码（出于安全考虑，只显示部分字符）
- **重新生成回复码**：删除所有旧回复码，生成 10 个新回复码
- **保存选项**：可以复制到剪贴板或下载为文本文件

### 5. 禁用 TOTP

- 禁用后将失去双因素认证保护
- 需要确认操作

## 使用的技术

- **QRCode 库**：用于生成二维码
- **Element Plus**：UI 组件库
- **Vue 3 Composition API**：状态管理和逻辑处理

## 安全注意事项

1. **回复码保管**：回复码应保存在安全的地方，每个码只能使用一次
2. **回复码警告**：当回复码少于 3 个时，建议立即重新生成
3. **完整显示**：只有在首次生成或重新生成时，才会显示完整的回复码
4. **敏感操作**：禁用 TOTP 等操作需要确认

## 依赖包

已添加的依赖：

- `qrcode`: ^1.5.4
- `@types/qrcode`: 类型定义（开发依赖）

## 后端 API 要求

所有 API 接口应遵循后端文档规范：

- `/auth/totp/status` - 获取状态
- `/auth/totp/registration-options` - 获取注册选项
- `/auth/totp/registration-verify` - 确认注册
- `/auth/totp/verify` - 验证
- `/auth/totp/recovery-codes` - 获取回复码
- `/auth/totp/recovery-codes/regenerate` - 重新生成回复码
- `/auth/totp/disable` - 禁用

## 常见问题

### Q: 为什么查看回复码时只显示部分字符？

A: 出于安全考虑，防止屏幕被拍照时泄露完整的回复码。

### Q: 回复码用完了怎么办？

A: 可以点击"重新生成回复码"按钮生成新的回复码。

### Q: 如何测试 TOTP 功能？

A: 可以使用以下身份验证器应用：

- Google Authenticator
- Microsoft Authenticator
- Authy
- FreeOTP

## 页面路径

TOTP 功能位于：

- 路径：`/login-options`
- 组件：`src/views/LoginOptionsView.vue`
- API：`src/api/auth.ts`
