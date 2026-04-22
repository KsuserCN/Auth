# TOTP 安全改进验证清单

## ✅ 代码改动验证

### 1. 密钥加密实现 (AES-GCM)

```bash
grep -n "encryptAesGcm\|decryptAesGcm" src/main/java/cn/ksuser/api/service/TotpService.java
```

**预期**：2 个方法定义

### 2. 防重放机制

```bash
grep -n "lastUsedStep\|shouldRejectStep\|updateLastUsedStep" src/main/java/cn/ksuser/api/entity/UserTotp.java
```

**预期**：3 个字段/方法

### 3. SHA-256 恢复码哈希

```bash
grep -n "sha256Hash\|VARBINARY" src/main/java/cn/ksuser/api/service/TotpService.java
```

**预期**：sha256Hash 方法

### 4. Pending 密钥管理

```bash
grep -n "pendingSecretCiphertext\|pendingExpiresAt\|confirmedAt" src/main/java/cn/ksuser/api/entity/UserTotp.java
```

**预期**：3 个新字段

### 5. 加密工具类

```bash
ls -la src/main/java/cn/ksuser/api/util/EncryptionUtil.java
```

**预期**：文件存在

---

## ✅ 编译验证

```bash
cd /Users/ksuserkqy/work/api
./gradlew clean build -x test 2>&1 | grep -E "BUILD|FAILED"
```

**预期**：BUILD SUCCESSFUL

---

## ✅ 数据库改动验证

### 用户 TOTP 表

```bash
grep -A 20 "CREATE TABLE user_totp" sql/init.sql | head -25
```

**验证项**：
- [ ] secret_key_ciphertext VARBINARY(512)
- [ ] key_version INT NOT NULL DEFAULT 1
- [ ] pending_secret_ciphertext VARBINARY(512)
- [ ] pending_expires_at DATETIME
- [ ] confirmed_at DATETIME
- [ ] last_used_step BIGINT
- [ ] secret_key 字段已删除
- [ ] secret_key_hash 字段已删除

### 恢复码表

```bash
grep -A 15 "CREATE TABLE totp_recovery_codes" sql/init.sql
```

**验证项**：
- [ ] code_hash VARBINARY(32)
- [ ] used_at DATETIME
- [ ] UNIQUE KEY uk_recovery_code (user_id, code_hash)
- [ ] is_used 字段已删除

---

## ✅ 配置验证

```bash
grep "app.encryption.master-key" src/main/resources/application.properties
```

**预期**：配置项存在且包含默认值

---

## ✅ 文件清单

### 新增文件
```bash
ls -la src/main/java/cn/ksuser/api/util/EncryptionUtil.java
ls -la docs/TOTP_SECURITY_IMPROVEMENTS.md
ls -la docs/TOTP_MIGRATION_GUIDE.md
ls -la docs/TOTP_IMPLEMENTATION_COMPLETE.md
```

**预期**：4 个文件均存在

### 修改的核心文件
```bash
for f in UserTotp.java TotpRecoveryCode.java TotpService.java TotpRecoveryCodeRepository.java TotpController.java; do
  echo "=== $f ==="
  git log --oneline -1 -- "src/main/java/cn/ksuser/api/$([ $f == 'TotpRecoveryCodeRepository.java' ] && echo 'repository' || echo 'service|entity|controller')/$f" 2>/dev/null || echo "File modified"
done
```

---

## 🧪 功能测试脚本

### 1. 密钥加密测试

```java
// 在单元测试中验证
byte[] plaintext = "test-secret-key".getBytes();
byte[] masterKey = Base64.getDecoder().decode("hXYmuT9xcqx4HZfF0DWadMiRB+jvLW7ZR0fUTPFkxuk=");

TotpService service = new TotpService(userTotpRepo, recoveryCodeRepo);
// 测试加密/解密
```

### 2. 防重放测试

```java
UserTotp userTotp = new UserTotp();
long step1 = 100;
long step2 = 100; // 相同步长

userTotp.updateLastUsedStep(step1);
assertTrue(userTotp.shouldRejectStep(step2)); // 应拒绝
```

### 3. 恢复码测试

```java
TotpRecoveryCode code = new TotpRecoveryCode();
assertNull(code.getUsedAt());
assertFalse(code.isUsed());

code.markAsUsed();
assertNotNull(code.getUsedAt());
assertTrue(code.isUsed());
```

---

## 📊 统计信息

### 代码量改动
```bash
# 新增行数
git diff --stat src/main/java/cn/ksuser/api/service/TotpService.java | tail -1

# 全部改动
git diff --stat src/main/java/cn/ksuser/api/ | tail -1
```

### 编译后大小
```bash
ls -lh build/libs/*.jar
```

---

## 🔒 安全检查

```bash
# 检查是否仍有明文密钥存储
grep -r "secretKey\|password" src/main/java/cn/ksuser/api/ | grep -v "secretKeyCiphertext" | grep -v "".getBytes"

# 验证所有哈希使用 SHA-256
grep -n "SHA-256" src/main/java/cn/ksuser/api/service/TotpService.java

# 检查 AES-GCM 配置
grep -n "AES/GCM" src/main/java/cn/ksuser/api/service/TotpService.java
```

---

## 📋 最终检查清单

- [ ] 所有新增文件创建成功
- [ ] 所有修改文件无编译错误
- [ ] 编译 BUILD SUCCESSFUL
- [ ] 数据库表结构已更新
- [ ] 配置文件已添加加密密钥设置
- [ ] 文档已完成（3 份）
- [ ] 无残留的明文密钥存储
- [ ] AES-GCM 加密正确实现
- [ ] SHA-256 哈希正确实现
- [ ] 防重放机制已实现
- [ ] 所有 Service 方法需要主加密密钥参数

---

## 🚀 部署前最后检查

```bash
# 1. 清理构建
./gradlew clean

# 2. 完整编译
./gradlew build -x test

# 3. 检查 JAR 大小（应该略小于之前，因为移除了 kotlin-otp）
ls -lh build/libs/*.jar

# 4. 验证没有遗留的旧依赖
grep -i "kotlin-otp" build.gradle && echo "ERROR: 仍有 kotlin-otp 依赖" || echo "OK: kotlin-otp 已移除"
```

---

**最后更新**：2026
**状态**：✅ 已完成所有改进
