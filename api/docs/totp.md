# TOTP（Time-based One-Time Password）文档

## 概述

TOTP（Time-based One-Time Password，基于时间的一次性密码）是一种双因素认证（2FA）方案。用户可以在身份验证器应用（如 Google Authenticator、Microsoft Authenticator、Authy 等）中添加账户，然后在登录或敏感操作时输入实时生成的 6 位数字码。

### 特性

- **安全**：基于 RFC 6238 标准的 TOTP 算法
- **易用**：支持扫描二维码快速添加
- **容错**：支持时间误差容忍（±30秒）
- **备份**：提供 10 个回复码，用户丢失设备时可用回复码登录
- **灵活**：可随时启用/禁用 TOTP

## 数据库表结构

### user_totp 表

存储用户 TOTP 配置信息。

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**字段说明**：
- `id`：主键 ID
- `user_id`：关联的用户 ID（唯一）
- `secret_key`：TOTP 密钥（Base32 编码），用于生成一次性密码
- `secret_key_hash`：密钥的哈希值，用于验证
- `is_enabled`：TOTP 是否启用（0=禁用，1=启用）
- `backup_verification_code`：备份验证码，在未启用时用于确认
- `created_at`：创建时间
- `updated_at`：更新时间

### totp_recovery_codes 表

存储用户的回复码，用户丢失 TOTP 设备时可用回复码登录。

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**字段说明**：
- `id`：主键 ID
- `user_id`：关联的用户 ID
- `code_hash`：回复码的哈希值（出于安全考虑存储哈希而非明文）
- `is_used`：是否已使用（0=未使用，1=已使用）
- `used_at`：使用时间
- `created_at`：创建时间

## API 接口

### 1. 获取 TOTP 注册选项

**请求**：
```
POST /auth/totp/registration-options
Authorization: Bearer {access_token}
Content-Type: application/json
```

**响应**：
```json
{
  "code": 200,
  "message": "获取 TOTP 注册选项成功",
  "data": {
    "secret": "JBSWY3DPEBLW64TMMQ======",
    "qrCodeUrl": "otpauth://totp/KSUser:user123?secret=JBSWY3DPEBLW64TMMQ======&issuer=KSUser",
    "recoveryCodes": [
      "12345678",
      "87654321",
      "11111111",
      "22222222",
      "33333333",
      "44444444",
      "55555555",
      "66666666",
      "77777777",
      "88888888"
    ]
  }
}
```

**说明**：
- 返回 TOTP 密钥（秘钥）
- 返回二维码 URL，用户可以用身份验证器应用扫描此二维码
- 返回 10 个回复码，用户应该妥善保管这些码

### 2. 确认 TOTP 注册

**请求**：
```
POST /auth/totp/registration-verify
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "code": "123456"
}
```

**响应**：
```json
{
  "code": 200,
  "message": "TOTP 注册成功",
  "data": "TOTP 已启用"
}
```

**说明**：
- 用户使用身份验证器应用中生成的 6 位数字码进行验证
- 验证成功后，TOTP 被启用

### 3. 验证 TOTP 码

**请求**：
```
POST /auth/totp/verify
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "code": "123456"
}
```

或使用回复码：

```
POST /auth/totp/verify
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "recoveryCode": "12345678"
}
```

**响应**：
```json
{
  "code": 200,
  "message": "TOTP 验证成功",
  "data": {
    "success": true,
    "message": "验证成功"
  }
}
```

**错误响应**：
```json
{
  "code": 401,
  "message": "验证失败",
  "data": {
    "success": false,
    "message": "TOTP 码或回复码无效"
  }
}
```

### 4. 获取 TOTP 状态

**请求**：
```
GET /auth/totp/status
Authorization: Bearer {access_token}
```

**响应**：
```json
{
  "code": 200,
  "message": "获取 TOTP 状态成功",
  "data": {
    "enabled": true,
    "recoveryCodesCount": 8
  }
}
```

**说明**：
- `enabled`：TOTP 是否启用
- `recoveryCodesCount`：剩余未使用的回复码数量

### 5. 禁用 TOTP

**请求**：
```
POST /auth/totp/disable
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "password": "user_password"
}
```

**响应**：
```json
{
  "code": 200,
  "message": "TOTP 禁用成功"
}
```

**说明**：
- 需要验证用户密码以禁用 TOTP
- 禁用后，所有回复码也会被删除

### 6. 重新生成回复码

**请求**：
```
POST /auth/totp/recovery-codes/regenerate
Authorization: Bearer {access_token}
```

**响应**：
```json
{
  "code": 200,
  "message": "回复码已重新生成",
  "data": [
    "12345678",
    "87654321",
    "11111111",
    "22222222",
    "33333333",
    "44444444",
    "55555555",
    "66666666",
    "77777777",
    "88888888"
  ]
}
```

**说明**：
- 生成新的回复码
- 旧的回复码将被删除
- 建议在用户消耗了大量回复码时调用此接口

### 7. 获取回复码列表

**请求**：
```
GET /auth/totp/recovery-codes
Authorization: Bearer {access_token}
```

**响应**：
```json
{
  "code": 200,
  "message": "获取回复码成功",
  "data": [
    "123456**",
    "876543**",
    "111111**",
    "222222**",
    "333333**",
    "444444**",
    "555555**"
  ]
}
```

**说明**：
- 返回用户的未使用回复码（出于安全考虑，只显示码的部分信息）

## 使用流程

### 启用 TOTP 流程

1. **用户请求启用 TOTP**
   ```
   POST /auth/totp/registration-options
   ```
   - 服务器生成密钥和回复码
   - 返回二维码 URL

2. **用户扫描二维码**
   - 用户使用身份验证器应用（Google Authenticator、Microsoft Authenticator 等）扫描二维码
   - 身份验证器应用显示当前和下一个 6 位数字码

3. **用户确认 TOTP 注册**
   ```
   POST /auth/totp/registration-verify
   {
     "code": "123456"
   }
   ```
   - 用户输入身份验证器应用中显示的 6 位数字码
   - 服务器验证码有效性
   - TOTP 启用成功

4. **保存回复码**
   - 用户应将回复码保存到安全的位置（如密码管理器）

### 使用 TOTP 登录流程

1. 用户输入用户名和密码
2. 服务器验证用户名和密码
3. 服务器检查用户是否启用了 TOTP
4. 如果启用，提示用户输入 TOTP 码
5. 用户输入身份验证器应用中的 6 位数字码
6. 服务器验证 TOTP 码
7. 验证成功，发放访问令牌

### 使用回复码登录流程

1. 用户输入用户名和密码
2. 服务器验证用户名和密码
3. 服务器检查用户是否启用了 TOTP
4. 如果启用，提示用户输入 TOTP 码
5. 用户选择"使用回复码"选项
6. 用户输入回复码
7. 服务器验证回复码，并标记为已使用
8. 验证成功，发放访问令牌

## 安全考虑

1. **密钥存储**
   - 密钥使用 Base32 编码存储
   - 密钥哈希值用于验证完整性

2. **码验证**
   - 支持 ±30 秒时间误差容忍
   - 防止重放攻击（每个码只能使用一次）

3. **回复码**
   - 回复码存储为哈希值（不存储明文）
   - 每个回复码只能使用一次
   - 使用后自动标记为已使用

4. **禁用 TOTP**
   - 需要验证用户密码
   - 删除所有相关的回复码

## 常见问题

### Q: 用户丢失了 TOTP 设备怎么办？

A: 用户可以使用回复码中的任一码登录。登录后，用户应立即禁用旧的 TOTP 并重新启用新的 TOTP。

### Q: 回复码用完了怎么办？

A: 用户可以调用"重新生成回复码"接口生成新的回复码。旧的回复码将被删除。

### Q: TOTP 码过期了怎么办？

A: TOTP 码每 30 秒更新一次。如果用户输入的码已过期，可以等待下一个码生成或使用回复码。

### Q: 可以在多个设备上使用 TOTP 吗？

A: 可以。用户在启用 TOTP 时获得的密钥可以在多个身份验证器应用中添加，这样可以在多个设备上生成相同的码。

### Q: TOTP 算法是什么？

A: TOTP 遵循 RFC 6238 标准，使用 HMAC-SHA1 算法和 30 秒的时间步长生成 6 位数字码。

## 集成建议

1. **前端集成**
   - 调用 `/auth/totp/registration-options` 获取二维码 URL
   - 使用二维码库显示二维码给用户
   - 调用 `/auth/totp/registration-verify` 确认注册

2. **登录集成**
   - 在密码验证成功后，检查用户是否启用了 TOTP
   - 如果启用，显示 TOTP 输入框
   - 调用 `/auth/totp/verify` 验证 TOTP 码或回复码

3. **用户管理**
   - 调用 `/auth/totp/status` 显示 TOTP 启用状态
   - 提供禁用 TOTP 的选项
   - 提供重新生成回复码的选项

## 技术细节

### TOTP 密钥生成

```
1. 生成 32 字节的随机数据
2. 使用 Base32 编码
3. 移除填充字符（=）
```

### TOTP 码生成

```
1. 计算当前时间步长：T = floor(Unix_timestamp / 30)
2. 使用 HMAC-SHA1 生成哈希值
3. 动态截断（Dynamic Truncation）获取 31 位的数字
4. 模 10^6 得到 6 位数字
5. 支持 T-1、T 和 T+1 三个时间步长进行验证（容错）
```

### 回复码生成

```
1. 生成 8 位随机数字
2. 使用密码编码器（PasswordEncoder）哈希
3. 存储到数据库
```

## 参考资源

- [RFC 6238 - TOTP](https://tools.ietf.org/html/rfc6238)
- [Google Authenticator](https://support.google.com/accounts/answer/1066447)
- [Authy](https://authy.com/)
