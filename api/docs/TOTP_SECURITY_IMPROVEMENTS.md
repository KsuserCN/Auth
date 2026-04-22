# TOTP 安全性改进总结

本文档总结了 TOTP 双因素认证实现的安全性改进。

## 改进背景

初版的 TOTP 实现存在 5 个关键的安全性和设计问题，现已全部修复。

## 改进内容

### 1. 移除冗余的 secret_key_hash 字段

**问题**：
- TOTP 验证需要原始的 secret，哈希不可逆
- 无法用于任何实际验证，造成误导

**解决方案**：
- 删除了 `UserTotp.secretKeyHash` 字段
- TOTP 密钥现在使用 AES-GCM 加密存储

**涉及文件**：
- [UserTotp.java](src/main/java/cn/ksuser/api/entity/UserTotp.java)
- [sql/init.sql](sql/init.sql)

---

### 2. 实现密钥加密存储（AES-256-GCM）

**问题**：
- 原来 secret_key 以明文 VARCHAR(255) 存储
- 数据库泄露 = TOTP 全盘皆输

**解决方案**：
- 使用 **AES-256-GCM** 加密存储密钥
- 密钥存储为 `secretKeyCiphertext` (VARBINARY(512))
- 新增 `keyVersion` 字段，便于密钥轮换
- 加密/解密由 TotpService 完成，主加密密钥从配置读取

**技术细节**：
- 算法：AES-256-GCM（Galois/Counter Mode）
- IV 长度：12 字节
- TAG 长度：128 位
- 返回格式：IV(12字节) + 密文 + TAG(16字节)

**涉及文件**：
- [UserTotp.java](src/main/java/cn/ksuser/api/entity/UserTotp.java) - 新增 `secretKeyCiphertext`, `keyVersion`
- [TotpService.java](src/main/java/cn/ksuser/api/service/TotpService.java) - 新增加密/解密方法
- [EncryptionUtil.java](src/main/java/cn/ksuser/api/util/EncryptionUtil.java) - 主加密密钥管理

---

### 3. 优化临时秘密管理（改用 pending_* 字段 + 过期时间）

**问题**：
- 原来用 `backup_verification_code` 字段存临时数据
- 临时数据长期落表，缺少清理机制

**解决方案**：
- 引入 `pending_secret_ciphertext` - 待确认的秘密
- 引入 `pending_expires_at` - 过期时间（建议 10 分钟）
- 注册流程中，秘密先存在 `pending_*` 字段
- 用户验证通过后，移动到 `secretKeyCiphertext`
- 增加 `confirmed_at` 字段，记录首次确认时间

**工作流**：
1. `/auth/totp/registration-options` - 生成秘密，存入 pending 字段（10分钟过期）
2. `/auth/totp/registration-verify` - 验证码通过后，移至正式字段，清空 pending

**涉及文件**：
- [UserTotp.java](src/main/java/cn/ksuser/api/entity/UserTotp.java) - 新增 `pendingSecretCiphertext`, `pendingExpiresAt`, `confirmedAt`
- [TotpService.java](src/main/java/cn/ksuser/api/service/TotpService.java) - `confirmTotpRegistration()` 实现
- [sql/init.sql](sql/init.sql) - 新增字段和索引

---

### 4. 实现防重放机制

**问题**：
- TOTP 有 30 秒的时间窗口
- 同一个码在 30 秒内可能被重复使用（重放攻击）

**解决方案**：
- 新增 `lastUsedStep` 字段记录上次验证的时间步长
- 验证时计算当前时间步：`floor(Unix_timestamp / 30)`
- 如果 `currentStep <= lastUsedStep`，则拒绝（防重放）
- 验证成功后更新 `lastUsedStep`

**时间步计算**：
```
currentStep = System.currentTimeMillis() / 1000 / 30
```

**涉及文件**：
- [UserTotp.java](src/main/java/cn/ksuser/api/entity/UserTotp.java) - 新增 `lastUsedStep`, `shouldRejectStep()`, `updateLastUsedStep()`
- [TotpService.java](src/main/java/cn/ksuser/api/service/TotpService.java) - `verifyTotpCode()` 实现防重放
- [sql/init.sql](sql/init.sql) - 新增 `last_used_step` 列

---

### 5. 改进恢复码设计（SHA-256 哈希 + used_at IS NULL）

**问题**：
- 原来用 Argon2id 哈希恢复码，存储为 VARCHAR(255)
- 长度不固定，不严谨
- 用 `is_used` 字段标记使用状态

**解决方案**：
- 改用 **SHA-256** 哈希恢复码
- 存储为 `code_hash` VARBINARY(32)（SHA-256 的输出长度）
- 删除 `isUsed` 字段
- 改用 `used_at IS NULL` 判断是否使用
- 新增 `UNIQUE(user_id, code_hash)` 防止重复

**优点**：
- SHA-256 输出固定 32 字节，更清晰
- `used_at IS NULL` 更语义化，便于查询和索引
- 可溯源：记录使用时间

**SQL 示例**：
```sql
-- 查询未使用的恢复码
SELECT * FROM totp_recovery_codes 
WHERE user_id = ? AND used_at IS NULL;

-- 标记为已使用
UPDATE totp_recovery_codes 
SET used_at = NOW() 
WHERE user_id = ? AND code_hash = ?;
```

**涉及文件**：
- [TotpRecoveryCode.java](src/main/java/cn/ksuser/api/entity/TotpRecoveryCode.java) - 改为 `byte[] codeHash`, `usedAt`, 移除 `isUsed`
- [TotpRecoveryCodeRepository.java](src/main/java/cn/ksuser/api/repository/TotpRecoveryCodeRepository.java) - 查询条件改为 `used_at IS NULL`
- [TotpService.java](src/main/java/cn/ksuser/api/service/TotpService.java) - SHA-256 哈希，更新查询逻辑
- [sql/init.sql](sql/init.sql) - 字段类型改为 VARBINARY(32), 添加 UNIQUE 约束

---

## 数据库变更

### user_totp 表

| 字段 | 类型 | 说明 | 变更 |
|------|------|------|------|
| id | BIGINT UNSIGNED | 主键 | - |
| user_id | BIGINT UNSIGNED | 用户ID | - |
| secret_key_ciphertext | VARBINARY(512) | **AES-GCM加密的密钥** | 新增（替代 secret_key, secret_key_hash） |
| key_version | INT | 密钥版本 | 新增 |
| is_enabled | TINYINT(1) | 是否启用 | - |
| pending_secret_ciphertext | VARBINARY(512) | 待确认密钥 | 新增（替代 backup_verification_code） |
| pending_expires_at | DATETIME | 待确认过期时间 | 新增 |
| confirmed_at | DATETIME | 确认启用时间 | 新增 |
| last_used_step | BIGINT | 上次验证的时间步 | 新增（防重放） |
| created_at | TIMESTAMP | 创建时间 | - |
| updated_at | TIMESTAMP | 更新时间 | - |

### totp_recovery_codes 表

| 字段 | 类型 | 说明 | 变更 |
|------|------|------|------|
| id | BIGINT UNSIGNED | 主键 | - |
| user_id | BIGINT UNSIGNED | 用户ID | - |
| code_hash | VARBINARY(32) | **SHA-256哈希** | 改为 VARBINARY(32)（替代 VARCHAR(255)） |
| used_at | DATETIME | 使用时间 | 新增（替代 is_used） |
| created_at | TIMESTAMP | 创建时间 | - |
| updated_at | TIMESTAMP | 更新时间 | - |
| 约束 | UNIQUE | (user_id, code_hash) | 新增 |

---

## 配置变更

### application.properties

新增主加密密钥配置：

```properties
# TOTP 加密配置
# 主加密密钥（用于加密 TOTP 密钥）
# 32 字节（256 位）的 Base64 编码密钥
# 建议在生产环境中从环境变量读取：ENCRYPTION_MASTER_KEY
app.encryption.master-key=${ENCRYPTION_MASTER_KEY:hXYmuT9xcqx4HZfF0DWadMiRB+jvLW7ZR0fUTPFkxuk=}
```

**生成密钥方法**：
```bash
python3 -c "import os, base64; key = os.urandom(32); print(base64.b64encode(key).decode())"
```

---

## 代码变更概览

### 新增文件

- [EncryptionUtil.java](src/main/java/cn/ksuser/api/util/EncryptionUtil.java) - 加密工具类，管理主加密密钥

### 修改文件

1. **实体类**：
   - [UserTotp.java](src/main/java/cn/ksuser/api/entity/UserTotp.java) - 8 个字段改动
   - [TotpRecoveryCode.java](src/main/java/cn/ksuser/api/entity/TotpRecoveryCode.java) - 字段结构调整

2. **Repository**：
   - [TotpRecoveryCodeRepository.java](src/main/java/cn/ksuser/api/repository/TotpRecoveryCodeRepository.java) - 查询方法改用 `used_at IS NULL`

3. **Service**：
   - [TotpService.java](src/main/java/cn/ksuser/api/service/TotpService.java) - 核心重写
     - 新增：AES-GCM 加密/解密、SHA-256 哈希、防重放检查
     - 改动：所有方法都需要主加密密钥参数

4. **Controller**：
   - [TotpController.java](src/main/java/cn/ksuser/api/controller/TotpController.java) - 调用 Service 时传入主加密密钥

5. **配置**：
   - [application.properties](src/main/resources/application.properties) - 新增主加密密钥配置

6. **数据库**：
   - [sql/init.sql](sql/init.sql) - 表结构重新设计

### 依赖变更

- 移除了 `dev.turingcomplete:kotlin-otp:2.4.0`（自实现 TOTP 逻辑）
- 保留 `commons-codec:commons-codec:1.15`（虽然目前未直接使用，但可用于 Base32 编码）

---

## 安全建议

### 生产环境

1. **主加密密钥**：
   - 不要硬编码在代码或配置文件中
   - 从环境变量 `ENCRYPTION_MASTER_KEY` 读取
   - 使用强随机密钥生成
   - 定期轮换密钥（使用 `keyVersion` 字段支持）

2. **恢复码**：
   - 注册时，用户应立即保存并销毁（不要截图）
   - 考虑限制每次使用的恢复码数量（如限制 1 个）
   - 监控异常使用模式

3. **API 安全**：
   - `/registration-options` 和 `/registration-verify` 不应在同一请求完成
   - 待确认秘密应由 Redis 或数据库临时存储，并有过期机制
   - 考虑添加速率限制

4. **日志**：
   - 不要记录加密密钥或原始 TOTP 秘密
   - 记录 TOTP 验证成功/失败事件用于审计

---

## 向后兼容性

**破坏性变更**：
- TOTP Service 的方法签名改变（需要 masterEncryptionKey 参数）
- 数据库表结构改变

**迁移步骤**：
1. 备份数据库
2. 运行 [sql/init.sql](sql/init.sql) 中的新建表语句
3. 对于现有用户，需要重新注册 TOTP（删除旧的 TOTP 记录）
4. 更新代码并重新部署

---

## 测试建议

### 单元测试

- [ ] AES-GCM 加密/解密
- [ ] Base32 编码
- [ ] SHA-256 哈希
- [ ] 时间步长计算和防重放
- [ ] 恢复码生成和验证

### 集成测试

- [ ] TOTP 注册流程（包括过期处理）
- [ ] TOTP 验证（时间误差容忍）
- [ ] 防重放验证
- [ ] 恢复码使用
- [ ] TOTP 禁用和恢复码重新生成

### 安全测试

- [ ] 时间同步差异（-30秒到+30秒）
- [ ] 同步码重放尝试
- [ ] 恢复码重复使用
- [ ] 密钥加密验证

---

## 参考资源

- [RFC 6238 - TOTP](https://tools.ietf.org/html/rfc6238)
- [RFC 4648 - Base32](https://tools.ietf.org/html/rfc4648)
- [NIST SP 800-63B - Authentication](https://pages.nist.gov/800-63-3/sp800-63b.html)
- [OWASP - Multi-Factor Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#multi-factor-authentication)
