# TOTP 回复码重新生成接口文档

## 端点

```
POST /auth/totp/recovery-codes/regenerate
```

## 功能描述

重新生成用户的 TOTP 回复码。旧的回复码将被删除，生成 10 个新的回复码。需要先完成敏感操作验证（参考修改密码流程）。用户在消耗了大量回复码或怀疑回复码泄露时，可以调用此接口重新生成。

## 敏感操作验证流程

此接口是敏感操作，调用前需要先完成以下验证步骤：

### 步骤 1: 生成敏感操作验证选项
```
POST /auth/verify/sensitive-verification-options
```

获取用于敏感操作验证的challenge信息（使用Passkey）。

### 步骤 2: 验证敏感操作
```
POST /auth/verify/sensitive-verification?challengeId={challengeId}
```

使用Passkey完成验证，获取敏感操作验证标记。

### 步骤 3: 调用重新生成恢复码接口
验证成功后，即可调用此接口重新生成恢复码。

## 请求

### URL
```
POST /auth/totp/recovery-codes/regenerate
```

### Headers
```
Authorization: Bearer {access_token}
Content-Type: application/json
```

### Body
无需请求体

### 示例 cURL
```bash
# 步骤 1: 获取敏感操作验证选项
curl -X POST http://localhost:8000/auth/verify/sensitive-verification-options \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json"

# 步骤 2: 使用 Passkey 验证敏感操作
curl -X POST "http://localhost:8000/auth/verify/sensitive-verification?challengeId={challengeId}" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "id": "credential_id",
    "rawId": "...",
    "response": {
      "clientDataJSON": "...",
      "authenticatorData": "...",
      "signature": "..."
    },
    "type": "public-key"
  }'

# 步骤 3: 重新生成恢复码（验证成功后）
curl -X POST http://localhost:8000/auth/totp/recovery-codes/regenerate \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json"
```

## 响应

### 成功响应 (200 OK)
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

### 错误响应

#### 敏感操作验证失败 (403 Forbidden)
```json
{
  "code": 403,
  "message": "请先完成敏感操作验证"
}
```

#### 用户未启用 TOTP (400 Bad Request)
```json
{
  "code": 400,
  "message": "用户未启用 TOTP"
}
```

#### 未认证 (401 Unauthorized)
```json
{
  "code": 401,
  "message": "未认证"
}
```

#### 用户不存在 (404 Not Found)
```json
{
  "code": 404,
  "message": "用户不存在"
}
```

## 使用说明

### 1. 前端集成

```javascript
// 重新生成回复码（包含敏感操作验证）
async function regenerateRecoveryCodes() {
  // 要求用户确认
  const confirmed = confirm(
    '重新生成回复码会删除所有旧的回复码。' +
    '请确保您已保存新的回复码后再确认。'
  );
  
  if (!confirmed) {
    return;
  }
  
  try {
    // 步骤 1: 获取敏感操作验证选项
    const optionsResponse = await fetch('/auth/verify/sensitive-verification-options', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });
    
    const optionsResult = await optionsResponse.json();
    
    if (optionsResult.code !== 200) {
      alert('获取验证选项失败：' + optionsResult.msg);
      return;
    }
    
    // 步骤 2: 使用 Passkey 验证
    const challengeId = optionsResult.data.challengeId;
    const options = optionsResult.data;
    
    // 调用 WebAuthn API 进行验证
    const assertion = await navigator.credentials.get({
      publicKey: {
        challenge: new Uint8Array(options.challenge),
        timeout: 60000,
        userVerification: "required",
        allowCredentials: options.allowCredentials
      }
    });
    
    // 步骤 3: 发送验证结果
    const verifyResponse = await fetch(`/auth/verify/sensitive-verification?challengeId=${challengeId}`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: assertion.id,
        rawId: Array.from(new Uint8Array(assertion.rawId)),
        response: {
          clientDataJSON: Array.from(new Uint8Array(assertion.response.clientDataJSON)),
          authenticatorData: Array.from(new Uint8Array(assertion.response.authenticatorData)),
          signature: Array.from(new Uint8Array(assertion.response.signature))
        },
        type: assertion.type
      })
    });
    
    const verifyResult = await verifyResponse.json();
    
    if (verifyResult.code !== 200) {
      alert('敏感操作验证失败：' + verifyResult.msg);
      return;
    }
    
    // 步骤 4: 调用重新生成恢复码接口
    const regenerateResponse = await fetch('/auth/totp/recovery-codes/regenerate', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });
    
    const regenerateResult = await regenerateResponse.json();
    
    if (regenerateResult.code === 200) {
      // 显示新的回复码
      showRecoveryCodesDialog(regenerateResult.data);
    } else if (regenerateResult.code === 400) {
      alert('您未启用 TOTP');
    } else {
      alert('重新生成失败：' + regenerateResult.msg);
    }
  } catch (error) {
    console.error('错误：', error);
    alert('操作失败：' + error.message);
  }
}

// 显示回复码对话框
function showRecoveryCodesDialog(codes) {
  const dialog = document.createElement('div');
  dialog.innerHTML = `
    <div class="recovery-codes-dialog">
      <h3>新的回复码已生成</h3>
      <p>请妥善保管以下回复码。每个码只能使用一次。</p>
      <ul class="recovery-codes">
        ${codes.map(code => `<li>${code}</li>`).join('')}
      </ul>
      <button onclick="downloadRecoveryCodes(${JSON.stringify(codes)})">下载</button>
      <button onclick="copyRecoveryCodes(${JSON.stringify(codes)})">复制</button>
      <button onclick="this.parentElement.parentElement.remove()">已保存</button>
    </div>
  `;
  
  document.body.appendChild(dialog);
}

// 复制回复码到剪贴板
function copyRecoveryCodes(codes) {
  const text = codes.join('\n');
  navigator.clipboard.writeText(text).then(() => {
    alert('已复制到剪贴板');
  });
}

// 下载回复码
function downloadRecoveryCodes(codes) {
  const text = 'TOTP 回复码\n' 
              new Date().toLocaleString() + '\n' +
              '每个码只能使用一次\n\n' +
              codes.join('\n');
  
  const blob = new Blob([text], { type: 'text/plain' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'recovery-codes.txt';
  a.click();
  window.URL.revokeObjectURL(url);
}
```

### 2. 用户账户管理页面

```html
<div class="recovery-codes-management">
  <h3>回复码管理</h3>
  
  <p id="recovery-codes-status"></p>
  
  <button id="regenerate-btn" onclick="regenerateRecoveryCodes()" style="display: none;">
    重新生成回复码
  </button>
  <span id="warning-message" style="color: orange; display: none;">
    ⚠️ 您的回复码即将用完，建议立即重新生成
  </span>
</div>

<script>
  async function updateRecoveryCodesUI() {
    const statusResponse = await fetch('/auth/totp/status', {
      headers: { 'Authorization': `Bearer ${accessToken}` }
    });
    
    const statusResult = await statusResponse.json();
    const { enabled, recoveryCodesCount } = statusResult.data;
    
    const statusDiv = document.getElementById('recovery-codes-status');
    const regenerateBtn = document.getElementById('regenerate-btn');
    const warningMsg = document.getElementById('warning-message');
    
    if (!enabled) {
      statusDiv.textContent = 'TOTP 未启用';
      regenerateBtn.style.display = 'none';
      return;
    }
    
    statusDiv.textContent = `剩余回复码：${recoveryCodesCount} 个`;
    regenerateBtn.style.display = 'block';
    
    if (recoveryCodesCount < 3) {
      warningMsg.style.display = 'block';
    }
  }
  
  updateRecoveryCodesUI();
</script>
```

### 3. 后端实现

```java
@PostMapping("/auth/totp/recovery-codes/regenerate")
public ResponseEntity<ApiResponse<String[]>> regenerateRecoveryCodes(
        Authentication authentication,
        HttpServletRequest request) {
    
    // 1. 验证用户已认证
    if (authentication == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiResponse<>(401, "未认证"));
    }
    
    // 2. 获取当前用户
    String userUuid = (String) authentication.getPrincipal();
    User user = userService.findByUuid(userUuid).orElse(null);
    if (user == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiResponse<>(404, "用户不存在"));
    }
    
    // 3. 检查是否已完成敏感操作验证
    String clientIp = rateLimitService.getClientIp(request);
    if (!sensitiveOperationService.isVerified(userUuid, clientIp)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiResponse<>(403, "请先完成敏感操作验证"));
    }
    
    // 4. 检查用户是否启用了 TOTP
    if (!totpService.isTotpEnabled(user.getId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse<>(400, "用户未启用 TOTP"));
    }
    
    try {
        // 5. 重新生成回复码
        byte[] masterKey = encryptionUtil.getMasterKey();
        String[] newCodes = totpService.regenerateRecoveryCodes(user.getId(), masterKey);
        
        // 6. 可选：记录审计日志
        auditLogger.log(user.getId(), "重新生成回复码", "成功");
        
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "回复码已重新生成", newCodes));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiResponse<>(500, "重新生成回复码失败：" + e.getMessage()));
    }
}
```

## 业务逻辑

### 重新生成回复码的过程

```java
@Transactional
public String[] regenerateRecoveryCodes(Long userId) {
    // 1. 删除旧的回复码
    recoveryCodeRepository.deleteByUserId(userId);
    
    // 2. 生成新的回复码
    String[] newCodes = generateRecoveryCodes(RECOVERY_CODES_COUNT);
    
    // 3. 保存新的回复码
    for (String code : newCodes) {
        TotpRecoveryCode codeEntity = new TotpRecoveryCode(
            userId,
            hashCode(code)  // 存储哈希值而非明文
        );
        recoveryCodeRepository.save(codeEntity);
    }
    
    return newCodes;
}
```

## 重要注意事项

1. **敏感操作验证**
   - 重新生成回复码需要先完成敏感操作验证
   - 使用 Passkey（如生物识别）完成验证
   - 验证标记有效期为 15 分钟

2. **数据删除**
   - 重新生成会删除所有旧的回复码
   - 用户无法再使用旧的回复码
   - 删除操作是不可逆的

3. **新回复码保管**
   - 用户必须妥善保管新的回复码
   - 建议在替换前获取确认
   - 建议提供下载或打印选项

4. **及时行动**
   - 建议在回复码即将用完时重新生成
   - 不要等到所有回复码都用完

5. **安全考虑**
   - 回复码应该保存在安全的位置
   - 不应该存储在浏览器本地存储中
   - 建议定期检查回复码数量

6. **IP 验证**
   - 敏感操作验证会记录客户端 IP
   - 后续操作必须来自同一 IP（15 分钟内）
   - 如果 IP 变化，需要重新进行敏感操作验证

## 使用场景

### 场景 1：回复码消耗过多

用户多次使用回复码登录后，回复码数量减少。当剩余回复码数量少于 3 个时，提示用户重新生成。

```javascript
// 在获取 TOTP 状态时检查
if (recoveryCodesCount < 3) {
  showPromptToRegenerateRecoveryCodes();
}
```

### 场景 2：回复码可能泄露

用户怀疑回复码被泄露，可以立即重新生成新的回复码。

```javascript
// 提供在账户安全设置中快速重新生成的选项
<button onclick="regenerateRecoveryCodes()">重新生成回复码</button>
```

### 场景 3：定期维护

建议定期（例如每季度）提示用户检查和重新生成回复码。

## 最佳实践

1. **主动通知**
   - 在剩余回复码数量少于 3 个时主动通知用户
   - 提供快速重新生成的选项

2. **确认机制**
   - 在重新生成前要求用户确认
   - 说明旧回复码将被删除

3. **保存选项**
   - 提供多种保存方式：复制、下载、打印
   - 让用户选择最适合的保管方式

4. **审计日志**
   - 记录所有回复码重新生成事件
   - 用于安全审计和故障排除

5. **用户教育**
   - 解释回复码的重要性
   - 提醒用户妥善保管回复码

## 错误处理

### 常见错误和解决方案

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| 用户未启用 TOTP | 用户的 TOTP 已禁用或未启用 | 提示用户先启用 TOTP |
| 未认证 | 访问令牌无效 | 重新登录 |

## 相关接口

- [TOTP 注册选项](/docs/totp-registration-options.md)
- [TOTP 验证](/docs/totp-verify.md)
- [TOTP 状态](/docs/totp-status.md)
- [TOTP 禁用](/docs/totp-disable.md)
- [TOTP 回复码列表](/docs/totp-recovery-codes.md)

## 常见问题

### Q: 为什么回复码会用完？

A: 每次使用回复码进行验证时，该码就会被标记为已使用。当用户多次在丢失 TOTP 码时使用回复码，或在设备切换时使用回复码，回复码数量就会减少。

### Q: 旧的回复码会被保留吗？

A: 不会。重新生成时会删除所有旧的回复码。用户无法再使用旧的回复码。

### Q: 可以多长时间重新生成一次？

A: 可以随时重新生成。建议在以下情况下重新生成：
- 回复码即将用完（少于 3 个）
- 怀疑回复码泄露
- 定期维护（例如每季度）

### Q: 重新生成会影响 TOTP 的其他功能吗？

A: 不会。重新生成只是替换回复码，不会影响 TOTP 密钥或验证逻辑。

### Q: 如果用户没有保存新的回复码怎么办？

A: 用户可以调用"获取回复码列表"接口再次查看未使用的回复码。
