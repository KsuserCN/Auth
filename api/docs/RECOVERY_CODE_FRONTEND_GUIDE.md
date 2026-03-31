# 恢复码快速实现指南（Frontend）

## 核心改变总结

| 内容 | 变化 |
|------|------|
| 恢复码格式 | `8位数字` → `8位大写字母` |
| 登录时 | 不支持 → **支持恢复码作为 TOTP 备用** |
| 全部消耗后 | - | 自动删除 TOTP |

---

## 代码示例

### 1. 注册 TOTP 时显示恢复码

```typescript
// 获取恢复码
const response = await fetch('/auth/totp/registration-options', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }
});

const data = await response.json();
const { secret, qrCodeUrl, recoveryCodes } = data.data;

// 显示恢复码
console.log('请妥善保管以下恢复码:');
recoveryCodes.forEach((code, idx) => {
  console.log(`${idx + 1}. ${code}`);  // 例如: ABCDEFGH
});

// 注册确认
const verifyResponse = await fetch('/auth/totp/registration-verify', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    code: userInputCode  // 6 位数字
  })
});
```

### 2. LoginMFA 时支持恢复码

```typescript
// 用户选择输入方式
let verifyPayload = {};

if (useRecoveryCode) {
  // 使用恢复码（8 位字母）
  verifyPayload = {
    challengeId: mfaChallengeId,
    recoveryCode: userInput  // 例如: "ABCDEFGH"
  };
} else {
  // 使用 TOTP 码（6 位数字）
  verifyPayload = {
    challengeId: mfaChallengeId,
    code: userInput  // 例如: "123456"
  };
}

const response = await fetch('/auth/totp/mfa-verify', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify(verifyPayload)
});

if (response.ok) {
  const { data } = await response.json();
  localStorage.setItem('accessToken', data.accessToken);
  // 登录成功，跳转到首页
} else {
  const errorData = await response.json();
  console.error(errorData.message);
  // 提示用户验证失败
}
```

### 3. UI 输入切换

```html
<!-- TOTP 码输入 (默认) -->
<div id="totpInput" class="mfa-input active">
  <label>输入 6 位验证码</label>
  <input 
    type="text" 
    placeholder="例如: 123456" 
    maxlength="6"
    pattern="[0-9]{6}"
  />
  <small>来自 Google Authenticator 或其他客户端</small>
</div>

<!-- 恢复码输入 (切换后显示) -->
<div id="recoveryCodeInput" class="mfa-input">
  <label>输入恢复码</label>
  <input 
    type="text" 
    placeholder="例如: ABCDEFGH" 
    maxlength="8"
    pattern="[A-Z]{8}"
  />
  <small>8 个大写字母，来自注册时保存的恢复码</small>
</div>

<!-- 切换按钮 -->
<button id="toggleInput" onclick="toggleInputMode()">
  使用恢复码
</button>

<script>
function toggleInputMode() {
  const totp = document.getElementById('totpInput');
  const recovery = document.getElementById('recoveryCodeInput');
  const button = document.getElementById('toggleInput');
  
  totp.classList.toggle('active');
  recovery.classList.toggle('active');
  
  button.textContent = totp.classList.contains('active') 
    ? '使用恢复码' 
    : '使用 TOTP 码';
}
</script>
```

### 4. 输入验证

```typescript
// 验证输入格式
function validateInput(input: string, type: 'totp' | 'recovery'): boolean {
  if (type === 'totp') {
    return /^[0-9]{6}$/.test(input);
  } else {
    // 恢复码：8 位大写字母
    return /^[A-Z]{8}$/.test(input);
  }
}

// 使用示例
const totpInput = '123456';
const recoveryInput = 'ABCDEFGH';

console.log(validateInput(totpInput, 'totp'));        // true
console.log(validateInput(recoveryInput, 'recovery')); // true
```

### 5. 错误处理

```typescript
async function submitMfaVerification(payload) {
  try {
    const response = await fetch('/auth/totp/mfa-verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (response.ok) {
      // 成功
      const { data } = await response.json();
      return { success: true, accessToken: data.accessToken };
    }

    const errorData = await response.json();
    const errorMsg = errorData.message;

    if (errorMsg.includes('剩余尝试次数')) {
      // 解析剩余次数并显示给用户
      const match = errorMsg.match(/剩余尝试次数：(\d+)/);
      const remaining = match ? match[1] : '未知';
      return { 
        success: false, 
        error: `验证失败，还可尝试 ${remaining} 次` 
      };
    } else if (errorMsg.includes('次数过多')) {
      return { 
        success: false, 
        error: '尝试次数过多，请重新登录',
        needRelogin: true
      };
    }

    return { success: false, error: errorMsg };
  } catch (err) {
    return { success: false, error: '网络错误' };
  }
}
```

---

## 恢复码显示模板

### HTML 模板

```html
<div class="recovery-codes-container">
  <div class="header">
    <h2>✅ TOTP 设置成功</h2>
    <p>请妥善保管以下恢复码</p>
  </div>

  <div class="codes-list">
    <ol>
      {% for code in recoveryCodes %}
      <li class="code-item">
        <code>{{ code }}</code>
        <button onclick="copyCode('{{ code }}')">复制</button>
      </li>
      {% endfor %}
    </ol>
  </div>

  <div class="warning">
    ⚠️ <strong>重要提示:</strong>
    <ul>
      <li>如果丢失了 TOTP 设备，可使用这些恢复码登录</li>
      <li>每个恢复码只能使用一次</li>
      <li>建议下载或打印保存</li>
    </ul>
  </div>

  <div class="actions">
    <button onclick="downloadRecoveryCodes()">📥 下载</button>
    <button onclick="printRecoveryCodes()">🖨️ 打印</button>
    <button onclick="copyAllCodes()">📋 全部复制</button>
  </div>
</div>
```

### 样式参考

```css
.recovery-codes-container {
  max-width: 500px;
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: #f9f9f9;
}

.header {
  text-align: center;
  margin-bottom: 20px;
}

.codes-list {
  background: white;
  padding: 15px;
  border-radius: 4px;
  margin: 15px 0;
}

.codes-list ol {
  list-style: decimal-leading-zero;
  font-size: 14px;
}

.code-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 0;
  line-height: 1.6;
}

.code-item code {
  font-family: 'Courier New', monospace;
  font-weight: bold;
  letter-spacing: 1px;
  color: #333;
}

.code-item button {
  padding: 2px 8px;
  font-size: 12px;
  cursor: pointer;
  border: none;
  border-radius: 3px;
  background: #007bff;
  color: white;
}

.warning {
  background: #fff3cd;
  border-left: 4px solid #ffc107;
  padding: 12px;
  margin: 15px 0;
  border-radius: 4px;
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.actions button {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 4px;
  background: #28a745;
  color: white;
  cursor: pointer;
  font-weight: bold;
}

.actions button:hover {
  background: #218838;
}
```

---

## 快速测试

### 测试 TOTP 注册流程

```bash
# 1. 注册恢复码
curl -X POST http://localhost:8080/auth/totp/registration-options \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json"

# 2. 确认注册（需要从 Google Authenticator 复制验证码）
curl -X POST http://localhost:8080/auth/totp/registration-verify \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code": "123456"}'

# 3. 获取恢复码列表
curl -X GET http://localhost:8080/auth/totp/recovery-codes \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

### 测试 MFA 登录

```bash
# 1. 用户名密码登录（得到 challengeId）
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'

# 2. 使用 TOTP 码验证
curl -X POST http://localhost:8080/auth/totp/mfa-verify \
  -H "Content-Type: application/json" \
  -d '{
    "challengeId": "CHALLENGE_ID_HERE",
    "code": "123456"
  }'

# 或使用恢复码验证
curl -X POST http://localhost:8080/auth/totp/mfa-verify \
  -H "Content-Type: application/json" \
  -d '{
    "challengeId": "CHALLENGE_ID_HERE",
    "recoveryCode": "ABCDEFGH"
  }'
```

---

## 常见前端问题

### Q: 如何区分 TOTP 码和恢复码的输入？
**A**: 使用正则表达式验证：
- TOTP 码：`/^[0-9]{6}$/`（6 位纯数字）
- 恢复码：`/^[A-Z]{8}$/`（8 位大写字母）

### Q: 恢复码需要区分大小写吗？
**A**: 需要。恢复码必须是**大写英文字母**（A-Z），小写字母会被拒绝。

### Q: 用户输入错误的恢复码怎么办？
**A**: 显示错误信息并提示：
- "恢复码格式错误，需为 8 位大写字母"
- "该恢复码已被使用或无效"
- "剩余尝试次数：X"

### Q: 登录页面应该如何设计？
**A**: 建议提供两个输入区域的切换：
1. 默认显示 TOTP 码输入（6 位数字）
2. 提供 "使用恢复码" 按钮切换到恢复码输入（8 位字母）

---

## 兼容性检查

- 需要支持**大写字母 A-Z** 的输入
- 需要支持长度为 **8 的文本输入**（恢复码）
- 需要支持长度为 **6 的数字输入**（TOTP 码）
- 建议使用 HTML5 的 `pattern` 属性做前端验证

```html
<!-- TOTP -->
<input type="text" pattern="[0-9]{6}" maxlength="6" />

<!-- Recovery Code -->
<input type="text" pattern="[A-Z]{8}" maxlength="8" />
```
