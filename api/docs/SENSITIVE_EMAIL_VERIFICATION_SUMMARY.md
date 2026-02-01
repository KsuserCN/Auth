# 敏感操作邮箱验证 - 快速参考

## 核心改动总结

### 1. 验证码类型隔离
- **新增 type**：`"sensitive-verification"` - 用于敏感操作邮箱验证
- **独立存储**：与登录验证码完全隔离，互不干扰
- **验证方法**：使用 `verifyCodeForType()` 而不是普通的 `verifyCode()`

### 2. 代码层面改动

#### AuthController.java
```java
// 发送验证码时
if ("sensitive-verification".equals(type)) {
    verificationCodeService.saveCodeWithType(email, code, clientIp, type);
} else {
    verificationCodeService.saveCode(email, code, clientIp);
}

// 验证时
int verifyResult = verificationCodeService.verifyCodeForType(
    email, code, clientIp, "sensitive-verification"
);
```

#### VerificationCodeService.java
- 新增 `saveCodeWithType()` 方法
- 新增 `verifyCodeForType()` 方法
- Redis Key 格式区分类型：`verification:code:{email}:sensitive-verification`

---

## 完整流程（邮箱验证）

### 1. 发送敏感操作验证码
```bash
POST /auth/send-code
{
  "email": "user@example.com",
  "type": "sensitive-verification"
}
```

### 2. 进行敏感操作验证
```bash
POST /auth/verify-sensitive
Authorization: Bearer {accessToken}
{
  "method": "email-code",
  "code": "123456"
}
```

### 3. 检查验证状态
```bash
GET /auth/check-sensitive-verification
Authorization: Bearer {accessToken}
```

### 4. 发送新邮箱验证码
```bash
POST /auth/send-code
{
  "email": "new-email@example.com",
  "type": "change-email"
}
```

### 5. 完成邮箱更改
```bash
POST /auth/update/email
Authorization: Bearer {accessToken}
{
  "newEmail": "new-email@example.com",
  "code": "654321"
}
```

---

## 与密码验证方式的对比

| 方面 | 邮箱验证 | 密码验证 |
|------|---------|---------|
| 方式 | `method: "email-code"` | `method: "password"` |
| 验证码类型 | `sensitive-verification` | - |
| 需要邮箱访问 | 是 | 否 |
| 需要记住密码 | 否 | 是 |
| 发送流程 | POST /auth/send-code | - |
| 适用场景 | 忘记密码/额外安全 | 快速验证 |

---

## 安全特性

### 验证码隔离
- ✅ 敏感操作验证码独立存储
- ✅ 不与登录验证码冲突
- ✅ 不同场景可同时有效

### 设备绑定
- ✅ 验证码与发送设备 IP 绑定
- ✅ 验证必须来自同一 IP
- ✅ 跨设备时需重新验证

### 时间限制
- ✅ 验证码有效期：10 分钟
- ✅ 敏感操作有效期：15 分钟
- ✅ 错误次数限制：5 次后锁定 1 小时

---

## 错误处理

### 常见错误与解决

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| 验证码已过期 | 10 分钟内未验证 | 重新发送验证码 |
| IP 不匹配 | 不同设备 | 从同一设备验证 |
| 验证过期 | 15 分钟未完成操作 | 重新进行敏感操作验证 |
| 邮箱已使用 | 新邮箱已被注册 | 使用其他邮箱 |
| 邮箱被锁定 | 错误 5 次 | 等待 1 小时 |

---

## 完整 Postman 测试

详见：[POSTMAN_SENSITIVE_EMAIL_VERIFICATION.md](POSTMAN_SENSITIVE_EMAIL_VERIFICATION.md)

此文档包含：
- 详细的 7 步测试流程
- 可直接导入的 Postman 集合 JSON
- 环境变量设置指南
- 完整的错误处理说明
- 前端集成示例

---

## 相关文档

- [发送验证码接口](auth-send-code.md) - type 参数已更新
- [敏感操作验证接口](auth-verify-sensitive.md) - 邮箱验证方式已更新
- [检查验证状态接口](auth-check-sensitive-verification.md)
- [更改邮箱接口](auth-change-email.md)
- [验证码系统设计](VERIFICATION_CODE_DESIGN.md) - 新增类型隔离说明

---

## 编译状态

✅ **BUILD SUCCESSFUL** - 所有代码已编译成功
- AuthController.java ✅
- VerificationCodeService.java ✅
- 所有相关 DTO 类 ✅

---

## 下一步建议

1. **测试**：使用提供的 Postman 流程进行完整测试
2. **集成**：在前端实现邮箱验证流程
3. **监控**：监控验证码发送和错误情况
4. **文档**：向用户说明邮箱验证的安全性

---

## 技术架构

```
发送验证码 (sensitive-verification)
    ↓
VerificationCodeService.saveCodeWithType()
    ↓
Redis: verification:code:{email}:sensitive-verification
    ↓
进行敏感操作验证
    ↓
VerificationCodeService.verifyCodeForType()
    ↓
验证成功 → SensitiveOperationService.markVerified()
    ↓
Redis: sensitive:verified:{uuid}
    ↓
检查验证状态
    ↓
SensitiveOperationService.isVerified()
    ↓
验证有效且 IP 一致 → 允许敏感操作
```

---

## 常见问题

**Q: 为什么要隔离敏感操作验证码？**
A: 防止用户在登录验证码过期后无法进行敏感操作，提高用户体验和安全性。

**Q: 敏感操作验证码和登录验证码可以同时接收吗？**
A: 可以。它们独立存储，用户可以同时有效，互不干扰。

**Q: 如果 15 分钟内没完成敏感操作会怎样？**
A: 验证过期，需要重新进行敏感操作验证才能继续操作。

**Q: 支持哪些敏感操作？**
A: 目前支持邮箱更改。可以通过 SensitiveOperationService 扩展到密码修改、账号删除等。
