# TOTP 安全改进快速参考

## 文件变更清单

### 核心代码变更（共 6 个文件）

```
✅ src/main/java/cn/ksuser/api/entity/UserTotp.java
   - 新增：secretKeyCiphertext, keyVersion, pendingSecretCiphertext, 
           pendingExpiresAt, confirmedAt, lastUsedStep
   - 删除：secretKey, secretKeyHash, backupVerificationCode

✅ src/main/java/cn/ksuser/api/entity/TotpRecoveryCode.java
   - 改动：codeHash 从 String 改为 byte[]
   - 删除：isUsed 字段
   - 新增：usedAt (LocalDateTime)
   - 新增方法：isUsed() 检查是否使用

✅ src/main/java/cn/ksuser/api/service/TotpService.java
   - 完全重写
   - 新增：AES-GCM 加密/解密、SHA-256、Base32 编码、防重放
   - 方法签名改变：所有方法需要 masterEncryptionKey 参数

✅ src/main/java/cn/ksuser/api/repository/TotpRecoveryCodeRepository.java
   - 所有查询改用 used_at IS NULL

✅ src/main/java/cn/ksuser/api/controller/TotpController.java
   - 新增依赖：UserTotpRepository, EncryptionUtil
   - 所有方法更新：传入 masterEncryptionKey

✅ src/main/java/cn/ksuser/api/util/EncryptionUtil.java （新增）
   - 主加密密钥管理

```

### 配置变更（1 个文件）

```
✅ src/main/resources/application.properties
   - 新增：app.encryption.master-key=${ENCRYPTION_MASTER_KEY:...}

✅ sql/init.sql
   - user_totp 表：新增 8 个字段，删除 2 个字段
   - totp_recovery_codes 表：重新设计，新增 UNIQUE 约束
```

---

## 关键改动点总结

| 改动 | 文件 | 详情 |
|------|------|------|
| 密钥加密 | UserTotp | `secretKey` → `secretKeyCiphertext` (VARBINARY) |
| 临时密钥 | UserTotp | `backupVerificationCode` → `pendingSecretCiphertext` + `pendingExpiresAt` |
| 防重放 | UserTotp | 新增 `lastUsedStep` 字段 |
| 恢复码 | TotpRecoveryCode | `code_hash` String → byte[], `isUsed` → `usedAt` |
| 哈希算法 | TotpService | Argon2id → SHA-256 |
| Service 签名 | TotpService | 方法需要 `byte[] masterEncryptionKey` 参数 |

---

## 编译检查

```bash
cd /Users/ksuserkqy/work/api

# 构建
./gradlew build -x test

# 检查错误
./gradlew check
```

**当前状态**：✅ BUILD SUCCESSFUL

---

## 数据库迁移步骤

### 开发/测试环境

1. 备份现有数据库
2. 删除旧的 user_totp 和 totp_recovery_codes 表
3. 运行 `sql/init.sql` 创建新表
4. 更新代码并重新启动

### 生产环境

```sql
-- 备份旧数据（可选）
CREATE TABLE user_totp_backup AS SELECT * FROM user_totp;
CREATE TABLE totp_recovery_codes_backup AS SELECT * FROM totp_recovery_codes;

-- 删除旧表和约束
DROP TABLE IF EXISTS totp_recovery_codes;
DROP TABLE IF EXISTS user_totp;

-- 创建新表（从 sql/init.sql 复制）
-- ... 见 sql/init.sql 中的 CREATE TABLE 语句
```

---

## 主加密密钥管理

### 密钥生成

```bash
python3 -c "import os, base64; key = os.urandom(32); print(base64.b64encode(key).decode())"
```

### 配置方式

**开发环境**：
```properties
# application.properties
app.encryption.master-key=hXYmuT9xcqx4HZfF0DWadMiRB+jvLW7ZR0fUTPFkxuk=
```

**生产环境**：
```bash
# 设置环境变量
export ENCRYPTION_MASTER_KEY="your-base64-encoded-32-byte-key"

# 或在 docker-compose.yml 中
environment:
  - ENCRYPTION_MASTER_KEY=your-base64-encoded-32-byte-key

# 或在 Kubernetes Secrets 中
kubectl create secret generic encryption-keys \
  --from-literal=master-key="your-base64-encoded-32-byte-key"
```

---

## API 变更

### 变更的端点

所有 TOTP 端点都需要调用时传入加密密钥（内部处理）：

- `POST /auth/totp/registration-options` - 新增秘密临时存储逻辑
- `POST /auth/totp/registration-verify` - 使用 pending_secret 字段验证
- `POST /auth/totp/verify` - 防重放检查
- `POST /auth/totp/recovery-codes/regenerate` - SHA-256 哈希
- `GET /auth/totp/recovery-codes` - 返回格式可能改变

### 不变的端点

- `GET /auth/totp/status` - 无变化
- `POST /auth/totp/disable` - 无变化

---

## 测试检查清单

### 单元测试
- [ ] AES-GCM 加密/解密对称性
- [ ] Base32 编码正确性
- [ ] SHA-256 哈希确定性
- [ ] 防重放逻辑

### 集成测试
- [ ] TOTP 完整注册流程
- [ ] 码验证（含时间偏差）
- [ ] 恢复码使用
- [ ] 防重放验证
- [ ] pending_secret 过期处理

### 兼容性测试
- [ ] Google Authenticator 应用生成的码
- [ ] Authy 应用生成的码
- [ ] 其他标准 TOTP 应用

---

## 性能影响

| 操作 | 复杂度 | 说明 |
|------|--------|------|
| 密钥生成 | O(1) | 32字节随机 + Base32 编码 |
| 密钥加密 | O(n) | AES-GCM，n=32字节 |
| 码验证 | O(1) | 3 次 HMAC-SHA1（时间误差容忍） |
| 防重放检查 | O(1) | 整数比较 |
| 恢复码哈希 | O(n) | SHA-256，n=8字符 |

**预期延迟**：< 10ms/请求

---

## 回滚计划

如果发现严重问题，可回滚到旧版本：

1. 停止新版本服务
2. 恢复数据库备份（user_totp_backup, totp_recovery_codes_backup）
3. 恢复旧版本代码
4. 重启服务

**数据丢失风险**：
- 回滚期间注册/验证的 TOTP 数据将丢失
- 用户需要重新注册 TOTP

---

## 故障排除

### 错误："主加密密钥未配置"

**解决**：在 application.properties 或环境变量中配置 `app.encryption.master-key`

```bash
export ENCRYPTION_MASTER_KEY="base64-encoded-32-byte-key"
```

### 错误："主加密密钥长度必须是 32 字节"

**解决**：密钥必须是 256 位（32 字节）。重新生成：

```bash
python3 -c "import os, base64; print(base64.b64encode(os.urandom(32)).decode())"
```

### 错误："主加密密钥必须是有效的 Base64 格式"

**解决**：确保密钥是有效的 Base64 编码，不包含特殊字符

### TOTP 验证失败

**检查清单**：
1. 用户手机时间是否与服务器同步（允许 ±30秒）
2. 防重放检查是否拒绝了重复码
3. TOTP 是否已启用（检查 is_enabled 和 confirmed_at）
4. 密钥是否正确解密

---

## 监控指标

建议监控以下指标：

- TOTP 注册成功率
- TOTP 验证成功率
- 恢复码使用率
- 防重放拒绝次数
- 加密/解密耗时

---

## 文档索引

- [完整改进说明](TOTP_SECURITY_IMPROVEMENTS.md)
- [API 文档](./auth-xxxx.md)
- [快速启动指南](TOTP_QUICK_START.md)（如有）
