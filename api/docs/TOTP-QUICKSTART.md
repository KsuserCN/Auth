# TOTP 快速启动指南

## 🚀 5 分钟快速开始

### 第一步：更新数据库

SQL 脚本已包含在 [sql/init.sql](../sql/init.sql) 中。启动应用时会自动初始化：

```sql
-- 两个新表会自动创建：
-- 1. user_totp - 存储用户 TOTP 配置
-- 2. totp_recovery_codes - 存储用户回复码
```

### 第二步：启动应用

```bash
cd /Users/ksuserkqy/work/api
./gradlew bootRun
```

应用将在 `http://localhost:8080` 启动。

### 第三步：测试 TOTP API

#### 方式 1：使用 Postman（推荐）

1. 打开 Postman
2. 导入 [docs/postman/TOTP-API.json](postman/TOTP-API.json)
3. 设置环境变量：
   - `base_url`: `http://localhost:8080`
   - `access_token`: 你的访问令牌
4. 运行测试请求

#### 方式 2：使用 cURL

```bash
# 1. 获取注册选项
curl -X POST http://localhost:8080/auth/totp/registration-options \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json"

# 2. 验证 TOTP 码
curl -X POST http://localhost:8080/auth/totp/verify \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{"code": "123456"}'

# 3. 获取 TOTP 状态
curl -X GET http://localhost:8080/auth/totp/status \
  -H "Authorization: Bearer {access_token}"
```

## 📚 核心概念

### TOTP 是什么？

Time-based One-Time Password（基于时间的一次性密码），是一种双因素认证（2FA）方案。

- **密钥**：用户扫描二维码或手动输入的密钥
- **TOTP 码**：身份验证器应用每 30 秒生成的 6 位数字
- **回复码**：用户丢失设备时的备用登录码

### 工作流程

```
用户启用 TOTP
    ↓
获取密钥和二维码
    ↓
在身份验证器应用中扫描二维码
    ↓
输入 6 位码确认
    ↓
保存回复码
    ↓
TOTP 启用完成
```

## 🔑 API 快速参考

### 1️⃣ 启用 TOTP（3 步）

#### 步骤 1：获取注册选项
```bash
POST /auth/totp/registration-options
Authorization: Bearer {token}
```

**返回**：
```json
{
  "secret": "JBSWY3DPEBLW64TMMQ",
  "qrCodeUrl": "otpauth://totp/...",
  "recoveryCodes": ["12345678", ...]
}
```

#### 步骤 2：扫描二维码
- 使用 Google Authenticator、Microsoft Authenticator 等应用
- 或手动输入 `secret` 值

#### 步骤 3：确认注册
```bash
POST /auth/totp/registration-verify
Authorization: Bearer {token}
Content-Type: application/json

{ "code": "123456" }
```

### 2️⃣ 验证 TOTP

#### 使用 TOTP 码
```bash
POST /auth/totp/verify
Authorization: Bearer {token}
Content-Type: application/json

{ "code": "123456" }
```

#### 使用回复码（备用）
```bash
POST /auth/totp/verify
Authorization: Bearer {token}
Content-Type: application/json

{ "recoveryCode": "12345678" }
```

### 3️⃣ 管理 TOTP

#### 查看状态
```bash
GET /auth/totp/status
Authorization: Bearer {token}
```

#### 查看回复码
```bash
GET /auth/totp/recovery-codes
Authorization: Bearer {token}
```

#### 重新生成回复码
```bash
POST /auth/totp/recovery-codes/regenerate
Authorization: Bearer {token}
```

#### 禁用 TOTP
```bash
POST /auth/totp/disable
Authorization: Bearer {token}
Content-Type: application/json

{ "password": "user_password" }
```

## 👨‍💻 前端集成示例

### React 组件示例

```javascript
import { useState } from 'react';
import QRCode from 'qrcode';

function TotpSetup() {
  const [qrCode, setQrCode] = useState('');
  const [secret, setSecret] = useState('');
  const [codes, setCodes] = useState([]);
  const [totpCode, setTotpCode] = useState('');

  // 步骤 1：获取注册选项
  const getOptions = async () => {
    const res = await fetch('/auth/totp/registration-options', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });

    const data = await res.json();
    setSecret(data.data.secret);
    setCodes(data.data.recoveryCodes);

    // 生成二维码
    const canvas = document.getElementById('qrcode');
    QRCode.toCanvas(canvas, data.data.qrCodeUrl);
  };

  // 步骤 2：确认注册
  const confirmTotp = async () => {
    const res = await fetch('/auth/totp/registration-verify', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ code: totpCode })
    });

    if (res.status === 200) {
      alert('TOTP 启用成功！');
      // 显示回复码需要被保存
    }
  };

  return (
    <div>
      <button onClick={getOptions}>启用 TOTP</button>
      <canvas id="qrcode"></canvas>
      <p>密钥：{secret}</p>
      <input 
        value={totpCode} 
        onChange={(e) => setTotpCode(e.target.value)}
        placeholder="输入 6 位码"
      />
      <button onClick={confirmTotp}>确认</button>
      <div>
        <h3>回复码（妥善保管）</h3>
        {codes.map(code => <p key={code}>{code}</p>)}
      </div>
    </div>
  );
}
```

### Vue 组件示例

```vue
<template>
  <div class="totp-setup">
    <button @click="getOptions">启用 TOTP</button>
    <canvas id="qrcode"></canvas>
    <p>密钥: {{ secret }}</p>
    <input v-model="totpCode" placeholder="输入 6 位码">
    <button @click="confirmTotp">确认</button>
    <div v-if="recoveryCodes.length">
      <h3>回复码</h3>
      <p v-for="code in recoveryCodes" :key="code">{{ code }}</p>
    </div>
  </div>
</template>

<script>
import QRCode from 'qrcode';

export default {
  data() {
    return {
      secret: '',
      totpCode: '',
      recoveryCodes: []
    }
  },
  methods: {
    async getOptions() {
      const res = await fetch('/auth/totp/registration-options', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${this.accessToken}`,
          'Content-Type': 'application/json'
        }
      });

      const data = await res.json();
      this.secret = data.data.secret;
      this.recoveryCodes = data.data.recoveryCodes;

      QRCode.toCanvas(
        document.getElementById('qrcode'),
        data.data.qrCodeUrl
      );
    },
    async confirmTotp() {
      const res = await fetch('/auth/totp/registration-verify', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${this.accessToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ code: this.totpCode })
      });

      if (res.status === 200) {
        alert('TOTP 启用成功！');
      }
    }
  }
}
</script>
```

## 🧪 测试步骤

### 1. 使用真实的身份验证器应用

推荐的应用：
- ✅ Google Authenticator（Android/iOS）
- ✅ Microsoft Authenticator（Android/iOS）
- ✅ Authy（Android/iOS/Web）
- ✅ FreeOTP（Android/iOS）

### 2. 测试场景

```
场景 1：正常 TOTP 登录
1. 在身份验证器应用中输入密钥
2. 使用生成的 6 位码验证
3. ✅ 验证成功

场景 2：使用回复码
1. 在 TOTP 验证时选择"使用回复码"
2. 输入保存的回复码
3. ✅ 验证成功，码被标记为已使用

场景 3：时间误差容忍
1. 设置 TOTP 码后立即验证
2. 验证过期的 TOTP 码（30 秒前）
3. ✅ 验证失败，提示用户使用新码或回复码

场景 4：禁用 TOTP
1. 验证用户密码
2. 禁用 TOTP
3. ✅ 禁用成功，所有回复码被删除
```

## 📊 文件结构

```
api/
├── src/main/java/cn/ksuser/api/
│   ├── controller/
│   │   └── TotpController.java          ✨ TOTP API 端点
│   ├── service/
│   │   └── TotpService.java             ✨ TOTP 核心逻辑
│   ├── entity/
│   │   ├── UserTotp.java                ✨ TOTP 配置实体
│   │   └── TotpRecoveryCode.java        ✨ 回复码实体
│   ├── repository/
│   │   ├── UserTotpRepository.java      ✨ TOTP 数据操作
│   │   └── TotpRecoveryCodeRepository.java ✨ 回复码数据操作
│   └── dto/
│       ├── TotpRegistrationOptionsResponse.java
│       ├── TotpVerifyRequest.java
│       ├── TotpVerifyResponse.java
│       ├── TotpRegistrationConfirmRequest.java
│       ├── TotpDisableRequest.java
│       └── TotpStatusResponse.java
│
├── sql/
│   └── init.sql                         ✨ 数据库表定义
│
└── docs/
    ├── TOTP-IMPLEMENTATION.md           ✨ 实现总结
    ├── TOTP-GUIDE.md                    ✨ 完整使用指南
    ├── totp.md                          ✨ API 文档
    ├── totp-registration-options.md
    ├── totp-registration-verify.md
    ├── totp-verify.md
    ├── totp-status.md
    ├── totp-disable.md
    ├── totp-recovery-codes-regenerate.md
    ├── totp-recovery-codes.md
    └── postman/
        └── TOTP-API.json                ✨ Postman 测试集合
```

## 🔒 安全提示

1. **保护密钥**
   - 不要在聊天/邮件中共享密钥
   - 不要在日志中显示密钥

2. **保护回复码**
   - 将回复码保存到密码管理器
   - 打印并妥善保管
   - 不要在公共电脑上查看

3. **时间同步**
   - 确保服务器和设备时间同步
   - 如果验证总是失败，检查设备时间

4. **定期维护**
   - 定期检查回复码数量
   - 当回复码即将用完时重新生成

## ❓ 常见问题

### Q: 如何重置用户的 TOTP？

A: 用户可以：
1. 调用 `POST /auth/totp/disable` 禁用 TOTP
2. 调用 `POST /auth/totp/registration-options` 启用新的 TOTP

### Q: 如何在多个设备上使用 TOTP？

A: 在启用 TOTP 时：
1. 获取密钥（secret）
2. 在多个设备的身份验证器应用中添加此密钥
3. 所有设备都会生成相同的 TOTP 码

### Q: 回复码用完了怎么办？

A: 调用 `POST /auth/totp/recovery-codes/regenerate` 生成新的回复码。

### Q: TOTP 码输入错误会怎样？

A: 系统会拒绝登录，用户可以：
1. 等待 30 秒获取新的 TOTP 码
2. 使用保存的回复码

## 📞 获取帮助

- 📖 查看 [TOTP 完整文档](totp.md)
- 📚 查看 [完整使用指南](TOTP-GUIDE.md)
- 🧪 查看 [Postman 测试集合](postman/TOTP-API.json)
- 💬 查看 [实现总结](TOTP-IMPLEMENTATION.md)

## ✅ 检查清单

启动前请检查：

- [ ] 数据库已更新（包含 user_totp 和 totp_recovery_codes 表）
- [ ] 应用已成功启动
- [ ] 可以正常登录并获取访问令牌
- [ ] Postman 集合已导入
- [ ] 身份验证器应用已安装

启用 TOTP 前请检查：

- [ ] 用户已登录
- [ ] 用户具有有效的访问令牌
- [ ] 设备时间正确同步
- [ ] 身份验证器应用已打开

---

**需要帮助？** 查看完整文档或 Postman 集合中的示例。

**准备好了？** 现在可以开始使用 TOTP 功能！🎉
