# TOTP 完整使用指南

## 目录

1. [概述](#概述)
2. [功能特性](#功能特性)
3. [系统架构](#系统架构)
4. [数据库设计](#数据库设计)
5. [API 接口](#api-接口)
6. [使用流程](#使用流程)
7. [前端集成](#前端集成)
8. [后端实现](#后端实现)
9. [安全考虑](#安全考虑)
10. [常见问题](#常见问题)

## 概述

TOTP（Time-based One-Time Password，基于时间的一次性密码）是一种双因素认证方案。用户可以在身份验证器应用（如 Google Authenticator、Microsoft Authenticator、Authy 等）中添加账户，然后在登录或敏感操作时输入实时生成的 6 位数字码。

### 为什么需要 TOTP？

- **增强安全性**：即使密码被泄露，攻击者仍然无法登录（没有 TOTP 码）
- **防止账户被盗**：用户可以在身份验证器应用中控制 TOTP 码
- **易于使用**：用户可以扫描二维码快速添加账户

## 功能特性

### ✅ 核心功能

- **TOTP 生成和验证**：基于 RFC 6238 标准
- **二维码支持**：用户可以扫描二维码快速添加账户
- **回复码支持**：用户丢失设备时可用回复码登录
- **灵活的启用/禁用**：用户可以随时启用或禁用 TOTP
- **时间容错**：支持 ±30 秒的时间误差

### 🔒 安全特性

- **密钥哈希存储**：密钥不以明文形式存储
- **回复码一次性使用**：每个回复码只能使用一次
- **密码验证**：禁用 TOTP 需要验证密码
- **安全显示**：回复码列表只显示部分字符

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端应用                               │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  用户界面 → TOTP 设置 → 验证 → 登录流程                        │
│                                                               │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP/HTTPS
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     Spring Boot API 服务器                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐      ┌──────────────────┐             │
│  │ TotpController   │      │ TotpService      │             │
│  └────────┬─────────┘      └────────┬─────────┘             │
│           │                         │                        │
│           └──────────┬──────────────┘                        │
│                      ▼                                        │
│           ┌──────────────────────┐                           │
│           │ Repository 层         │                           │
│           │ (数据库操作)          │                           │
│           └──────────┬───────────┘                           │
│                      │                                        │
└──────────────────────┼──────────────────────────────────────┘
                       │
                       ▼
            ┌─────────────────────┐
            │      MySQL 数据库    │
            ├─────────────────────┤
            │ user_totp           │
            │ totp_recovery_codes │
            └─────────────────────┘
```

## 数据库设计

### user_totp 表

```sql
CREATE TABLE user_totp (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL UNIQUE,
  secret_key VARCHAR(255) NOT NULL,
  secret_key_hash VARCHAR(255) NOT NULL,
  is_enabled TINYINT(1) NOT NULL DEFAULT 0,
  backup_verification_code VARCHAR(255) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_totp_user (user_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

**关键字段**：
- `secret_key`：Base32 编码的 TOTP 密钥
- `secret_key_hash`：密钥的哈希值
- `is_enabled`：TOTP 是否启用

### totp_recovery_codes 表

```sql
CREATE TABLE totp_recovery_codes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  code_hash VARCHAR(255) NOT NULL,
  is_used TINYINT(1) NOT NULL DEFAULT 0,
  used_at DATETIME DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  KEY idx_recovery_codes_user_unused (user_id, is_used, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

**关键字段**：
- `code_hash`：回复码的哈希值（不存储明文）
- `is_used`：是否已使用
- `used_at`：使用时间

## API 接口

### 接口列表

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /auth/totp/registration-options | 获取 TOTP 注册选项 |
| POST | /auth/totp/registration-verify | 确认 TOTP 注册 |
| POST | /auth/totp/verify | 验证 TOTP 码 |
| GET | /auth/totp/status | 获取 TOTP 状态 |
| GET | /auth/totp/recovery-codes | 获取回复码列表 |
| POST | /auth/totp/recovery-codes/regenerate | 重新生成回复码 |
| POST | /auth/totp/disable | 禁用 TOTP |

详细文档见 [TOTP.md](totp.md)

## 使用流程

### 流程 1：启用 TOTP

```
用户 → 点击"启用 TOTP"
  ↓
系统 → 调用 POST /auth/totp/registration-options
  ↓
系统 → 返回密钥、二维码 URL、回复码
  ↓
用户 → 扫描二维码或手动输入密钥到身份验证器应用
  ↓
用户 → 输入身份验证器应用中的 6 位码
  ↓
系统 → 调用 POST /auth/totp/registration-verify
  ↓
系统 → 验证码有效 → TOTP 启用成功
  ↓
用户 → 保存回复码到安全位置
```

### 流程 2：登录时使用 TOTP

```
用户 → 输入用户名和密码
  ↓
系统 → 验证用户名和密码
  ↓
系统 → 检查用户是否启用了 TOTP
  ↓
系统 → 提示用户输入 TOTP 码
  ↓
用户 → 输入身份验证器应用中的 6 位码
  ↓
系统 → 调用 POST /auth/totp/verify
  ↓
系统 → 验证码有效 → 发放访问令牌
  ↓
用户 → 登录成功
```

### 流程 3：使用回复码

```
用户 → 丢失 TOTP 设备
  ↓
用户 → 登录时选择"使用回复码"
  ↓
用户 → 输入保存的回复码
  ↓
系统 → 调用 POST /auth/totp/verify (recoveryCode)
  ↓
系统 → 验证回复码有效 → 标记为已使用 → 发放访问令牌
  ↓
用户 → 登录成功
  ↓
用户 → 禁用旧的 TOTP，启用新的 TOTP
```

## 前端集成

### 1. 安装依赖

```javascript
// 用于显示二维码
npm install qrcode
```

### 2. 启用 TOTP 组件

```javascript
import QRCode from 'qrcode';

async function enableTotp() {
  // 1. 获取注册选项
  const optionsResponse = await fetch('/auth/totp/registration-options', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    }
  });
  
  const options = await optionsResponse.json();
  const { secret, qrCodeUrl, recoveryCodes } = options.data;
  
  // 2. 显示二维码
  QRCode.toCanvas(
    document.getElementById('qrcode'),
    qrCodeUrl,
    error => { if (error) console.error(error); }
  );
  
  // 3. 显示密钥和回复码
  document.getElementById('secret').textContent = secret;
  document.getElementById('recovery-codes').innerHTML = 
    recoveryCodes.map(code => `<p>${code}</p>`).join('');
  
  // 4. 等待用户输入验证码
  const code = await promptUserForCode();
  
  // 5. 确认 TOTP 注册
  const verifyResponse = await fetch('/auth/totp/registration-verify', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ code: code })
  });
  
  if (verifyResponse.status === 200) {
    alert('TOTP 启用成功！');
  }
}
```

### 3. TOTP 验证组件

```javascript
async function verifyTotp() {
  const code = document.getElementById('totp-code').value;
  
  const response = await fetch('/auth/totp/verify', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ code: code })
  });
  
  if (response.status === 200) {
    // 验证成功
    finishLogin();
  } else {
    // 验证失败，提示用户使用回复码
    showRecoveryCodeOption();
  }
}

async function verifyWithRecoveryCode() {
  const code = document.getElementById('recovery-code').value;
  
  const response = await fetch('/auth/totp/verify', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ recoveryCode: code })
  });
  
  if (response.status === 200) {
    // 验证成功
    finishLogin();
  }
}
```

## 后端实现

### 核心代码

#### TotpService.java

```java
@Service
public class TotpService {
    // 生成 TOTP 密钥
    public Map<String, Object> generateTotpSecret(Long userId) {
        // 生成 32 字节的随机数据
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        
        // 使用 Base32 编码
        Base32 base32 = new Base32();
        String secretKey = base32.encodeToString(randomBytes).replaceAll("=", "");
        
        // 生成 10 个回复码
        String[] recoveryCodes = generateRecoveryCodes(10);
        
        // 生成二维码 URL
        String qrCodeUrl = String.format(
            "otpauth://totp/KSUser:user%d?secret=%s&issuer=KSUser",
            userId, secretKey
        );
        
        return Map.of(
            "secret", secretKey,
            "qrCodeUrl", qrCodeUrl,
            "recoveryCodes", recoveryCodes
        );
    }
    
    // 验证 TOTP 码
    public boolean verifyTotpCode(Long userId, String code) {
        Optional<UserTotp> userTotpOpt = userTotpRepository.findByUserId(userId);
        if (userTotpOpt.isEmpty() || !userTotpOpt.get().getIsEnabled()) {
            return false;
        }
        
        // 验证码有效性（容差 ±30 秒）
        long currentTimeInterval = System.currentTimeMillis() / 1000 / 30;
        for (int i = -1; i <= 1; i++) {
            String generatedCode = generateTotpCode(
                userTotpOpt.get().getSecretKey(),
                currentTimeInterval + i
            );
            if (generatedCode.equals(code)) {
                return true;
            }
        }
        
        return false;
    }
    
    // 确认 TOTP 注册
    @Transactional
    public boolean confirmTotpRegistration(Long userId, String secretKey, String code, String[] recoveryCodes) {
        if (!verifyTotpCode(secretKey, code)) {
            return false;
        }
        
        // 删除旧的 TOTP 配置
        userTotpRepository.deleteByUserId(userId);
        
        // 保存新的 TOTP 配置
        UserTotp userTotp = new UserTotp(
            userId,
            secretKey,
            passwordEncoder.encode(secretKey)
        );
        userTotp.setIsEnabled(true);
        userTotpRepository.save(userTotp);
        
        // 保存回复码
        for (String code : recoveryCodes) {
            recoveryCodeRepository.save(new TotpRecoveryCode(
                userId,
                passwordEncoder.encode(code)
            ));
        }
        
        return true;
    }
}
```

#### TotpController.java

```java
@RestController
@RequestMapping("/auth/totp")
public class TotpController {
    // 获取 TOTP 注册选项
    @PostMapping("/registration-options")
    public ResponseEntity<ApiResponse<TotpRegistrationOptionsResponse>> getTotpRegistrationOptions(
            Authentication authentication) {
        User user = getCurrentUser(authentication);
        Map<String, Object> secretData = totpService.generateTotpSecret(user.getId());
        
        return ResponseEntity.ok(new ApiResponse<>(200, "获取成功",
            new TotpRegistrationOptionsResponse(
                (String) secretData.get("secret"),
                (String) secretData.get("qrCodeUrl"),
                (String[]) secretData.get("recoveryCodes")
            )));
    }
    
    // 验证 TOTP 码
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<TotpVerifyResponse>> verifyTotp(
            Authentication authentication,
            @RequestBody TotpVerifyRequest request) {
        User user = getCurrentUser(authentication);
        
        // 尝试验证 TOTP 码
        if (request.getCode() != null && !request.getCode().isEmpty()) {
            if (totpService.verifyTotpCode(user.getId(), request.getCode())) {
                return ResponseEntity.ok(new ApiResponse<>(200, "验证成功",
                    new TotpVerifyResponse(true, "验证成功")));
            }
        }
        
        // 尝试验证回复码
        if (request.getRecoveryCode() != null && !request.getRecoveryCode().isEmpty()) {
            if (totpService.verifyRecoveryCode(user.getId(), request.getRecoveryCode())) {
                return ResponseEntity.ok(new ApiResponse<>(200, "验证成功",
                    new TotpVerifyResponse(true, "验证成功")));
            }
        }
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiResponse<>(401, "验证失败",
                new TotpVerifyResponse(false, "验证失败")));
    }
}
```

## 安全考虑

### 1. 密钥安全

- ✅ 密钥使用 Base32 编码存储
- ✅ 密钥哈希值用于验证完整性
- ✅ 不在日志中显示密钥
- ✅ 使用 HTTPS 传输

### 2. 验证安全

- ✅ TOTP 码只能使用一次
- ✅ 支持时间误差容忍（防止时间不同步）
- ✅ 实施速率限制（防止暴力破解）
- ✅ 记录验证失败的尝试

### 3. 回复码安全

- ✅ 回复码存储为哈希值
- ✅ 每个回复码只能使用一次
- ✅ 使用后自动标记为已使用
- ✅ 列表显示时只显示部分字符

### 4. 操作安全

- ✅ 禁用 TOTP 需要验证密码
- ✅ 重要操作记录审计日志
- ✅ 支持撤销操作

## 常见问题

### Q1: 用户丢失了 TOTP 设备怎么办？

A: 用户可以使用保存的回复码中的任一码登录。登录后，用户应立即禁用旧的 TOTP 并重新启用新的 TOTP。

### Q2: 回复码用完了怎么办？

A: 用户可以调用"重新生成回复码"接口生成新的回复码。旧的回复码将被删除。

### Q3: TOTP 码过期了怎么办？

A: TOTP 码每 30 秒更新一次。用户可以等待下一个码生成或使用回复码。

### Q4: 可以在多个设备上使用 TOTP 吗？

A: 可以。在启用 TOTP 时获得的密钥可以在多个身份验证器应用中添加，这样可以在多个设备上生成相同的码。

### Q5: TOTP 会自动失效吗？

A: 不会。TOTP 一旦启用，就会一直保持启用状态，直到用户主动禁用。

### Q6: 如果忘记了 TOTP 密钥怎么办？

A: 无法恢复。用户需要禁用当前 TOTP，然后重新启用新的 TOTP。

### Q7: 什么是"时间容错"？

A: TOTP 验证支持前后各 30 秒的时间误差。这样即使用户设备的时间与服务器时间相差不超过 1 分钟，TOTP 验证仍然有效。

## 参考资源

- [RFC 6238 - TOTP](https://tools.ietf.org/html/rfc6238)
- [Google Authenticator](https://support.google.com/accounts/answer/1066447)
- [Authy](https://authy.com/)
- [Microsoft Authenticator](https://www.microsoft.com/en-us/account/authenticator)

## 相关文档

- [TOTP API 文档](totp.md)
- [TOTP 注册选项接口](totp-registration-options.md)
- [TOTP 验证接口](totp-verify.md)
- [TOTP 注册确认接口](totp-registration-verify.md)
- [TOTP 状态接口](totp-status.md)
- [TOTP 禁用接口](totp-disable.md)
- [TOTP 回复码重新生成接口](totp-recovery-codes-regenerate.md)
- [TOTP 回复码列表接口](totp-recovery-codes.md)
- [Postman 测试集合](postman/TOTP-API.json)
