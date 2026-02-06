# TOTP 回复码列表接口文档

## 端点

```
GET /auth/totp/recovery-codes
```

## 功能描述

获取用户的未使用恢复码列表，返回原始恢复码内容。需要先完成敏感操作验证（参考修改密码流程）。

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

### 步骤 3: 调用获取恢复码接口
验证成功后，即可调用此接口获取恢复码列表。

## 请求

### URL
```
GET /auth/totp/recovery-codes
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

# 步骤 3: 获取回复码列表（验证成功后）
curl -X GET http://localhost:8000/auth/totp/recovery-codes \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json"
```

## 响应

### 成功响应 (200 OK)

#### 用户有未使用的回复码
```json
{
  "code": 200,
  "message": "获取回复码成功",
  "data": [
    "12345678",
    "87654321",
    "11112222"
  ]
}
```

#### 用户没有未使用的回复码
```json
{
  "code": 200,
  "message": "获取回复码成功",
  "data": []
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | integer | 响应状态码（200 表示成功）|
| message | string | 响应消息 |
| data | array | 恢复码列表（原始恢复码） |

### 错误响应

#### 敏感操作验证失败 (403 Forbidden)
```json
{
  "code": 403,
  "message": "请先完成敏感操作验证"
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
// 获取回复码列表（包含敏感操作验证）
async function getRecoveryCodes() {
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
    
    // 步骤 4: 调用获取回复码接口
    const response = await fetch('/auth/totp/recovery-codes', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });
    
    const result = await response.json();
    
    if (result.code === 200) {
      const codes = result.data;
      
      if (codes.length === 0) {
        console.log('您没有未使用的回复码');
        // 提示用户重新生成回复码
        promptUserToRegenerateRecoveryCodes();
      } else {
        console.log(`您还有 ${codes.length} 个未使用的回复码`);
        displayRecoveryCodes(codes);
      }
    } else {
      alert('获取回复码失败：' + result.msg);
    }
  } catch (error) {
    console.error('错误：', error);
    alert('操作失败：' + error.message);
  }
}

// 显示回复码
function displayRecoveryCodes(codes) {
  const list = document.getElementById('recovery-codes-list');
  list.innerHTML = '';
  
  codes.forEach(code => {
    const li = document.createElement('li');
    li.textContent = code;
    list.appendChild(li);
  });
}
```

### 2. 用户账户管理页面

```html
<div class="recovery-codes-view">
  <h3>未使用的回复码</h3>
  
  <p id="recovery-codes-count"></p>
  
  <ul id="recovery-codes-list"></ul>
  
  <div id="warning-message" style="display: none; color: orange;">
    ⚠️ 您的回复码即将用完！
    <button onclick="regenerateRecoveryCodes()">立即重新生成</button>
  </div>
</div>

<script>
  async function loadRecoveryCodes() {
    const response = await fetch('/auth/totp/recovery-codes', {
      headers: { 'Authorization': `Bearer ${accessToken}` }
    });
    
    const result = await response.json();
    const codes = result.data;
    
    const countDiv = document.getElementById('recovery-codes-count');
    const list = document.getElementById('recovery-codes-list');
    const warning = document.getElementById('warning-message');
    
    countDiv.textContent = `总计 ${codes.length} 个未使用的回复码`;
    
    list.innerHTML = codes
      .map(code => `<li>${code}</li>`)
      .join('');
    
    // 如果回复码少于 3 个，显示警告
    if (codes.length < 3) {
      warning.style.display = 'block';
    }
  }
  
  loadRecoveryCodes();
</script>
```

### 3. 后端实现

```java
@GetMapping("/auth/totp/recovery-codes")
public ResponseEntity<ApiResponse<List<String>>> getRecoveryCodes(
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
    
    try {
        // 4. 获取回复码列表
        byte[] masterKey = encryptionUtil.getMasterKey();
        List<String> codes = totpService.getRecoveryCodes(user.getId(), masterKey);
        
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "获取回复码成功", codes));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiResponse<>(500, "获取回复码失败：" + e.getMessage()));
    }
}
```

## 技术细节

### 回复码显示策略

为了防止用户的回复码在屏幕上泄露，接口只返回码的部分信息：

```
完整回复码：12345678
显示的信息：完整恢复码（如 12345678）

这样用户可以识别自己的回复码，但即使屏幕被拍照也不会泄露完整信息。
```

### 数据库查询

```java
public List<String> getRecoveryCodes(Long userId, byte[] masterKey) {
  // 1. 查询用户的所有未使用的恢复码
  List<TotpRecoveryCode> codes = 
    recoveryCodeRepository.findByUserIdAndUnusedOrderByCreatedAtAsc(userId);
    
  // 2. 解密并返回原始恢复码
  List<String> result = new ArrayList<>();
  for (TotpRecoveryCode code : codes) {
    byte[] plaintext = decryptAesGcm(code.getCodeCiphertext(), masterKey);
    result.add(new String(plaintext));
  }
    
  return result;
}
```

## 使用场景

### 场景 1：用户想查看剩余回复码

用户登录后，在账户安全设置中查看还有多少个未使用的回复码。

```javascript
// 每次打开账户设置时调用
async function showAccountSecurityPage() {
  const response = await fetch('/auth/totp/recovery-codes', {
    headers: { 'Authorization': `Bearer ${accessToken}` }
  });
  
  const result = await response.json();
  console.log(`您还有 ${result.data.length} 个回复码`);
}
```

### 场景 2：系统检查回复码是否用完

在用户成功登录后，检查剩余的回复码数量，如果过少则提示用户。

```javascript
// 登录成功后
async function checkRecoveryCodesAfterLogin() {
  const response = await fetch('/auth/totp/recovery-codes', {
    headers: { 'Authorization': `Bearer ${accessToken}` }
  });
  
  const result = await response.json();
  const codes = result.data;
  
  if (codes.length === 0) {
    showAlert('所有回复码都已使用，请立即重新生成');
  } else if (codes.length < 3) {
    showAlert('您只有 ' + codes.length + ' 个回复码剩余，建议重新生成');
  }
}
```

### 场景 3：恢复账户时查看回复码

用户需要使用回复码登录时，先查看有哪些可用的回复码。

```javascript
// 在 TOTP 验证失败，用户选择使用回复码时
async function showAvailableRecoveryCodes() {
  const response = await fetch('/auth/totp/recovery-codes', {
    headers: { 'Authorization': `Bearer ${tempToken}` }
  });
  
  const result = await response.json();
  
  if (result.data.length === 0) {
    showAlert('您没有可用的回复码，请重新生成');
  } else {
    showMessage(`您有 ${result.data.length} 个可用的回复码`);
  }
}
```

## 重要注意事项

1. **敏感操作验证**
   - 获取回复码需要先完成敏感操作验证
   - 使用 Passkey（如生物识别）完成验证
   - 验证标记有效期为 15 分钟

2. **安全显示**
   - 此接口会返回原始恢复码，请限制在安全场景使用
   - 不应该在日志或错误消息中显示完整的码

3. **仅显示未使用的码**
   - 已使用的码不再显示
   - 这防止用户尝试重复使用已用的码

4. **隐私保护**
   - 不应该在邮件或短信中发送回复码
   - 不应该在非 HTTPS 连接上传输

5. **访问控制**
   - 用户只能查看自己的回复码
   - 不允许用户访问他人的回复码

6. **IP 验证**
   - 敏感操作验证会记录客户端 IP
   - 后续操作必须来自同一 IP（15 分钟内）
   - 如果 IP 变化，需要重新进行敏感操作验证

## 错误处理

### 常见情况和处理方案

| 情况 | 处理方案 |
|------|---------|
| 没有未使用的回复码 | 提示用户重新生成回复码 |
| 回复码少于 3 个 | 显示警告信息，建议重新生成 |
| 未认证 | 要求用户重新登录 |

## 最佳实践

1. **定期检查**
   - 建议定期检查回复码数量
   - 在登录成功后自动检查

2. **主动提示**
   - 当回复码数量过少时主动提示用户
   - 提供快速重新生成的选项

3. **清晰显示**
   - 清楚地显示还有多少个回复码
   - 显示码的生成时间（可选）

4. **安全提醒**
   - 提醒用户保护自己的回复码
   - 不要在公共电脑上查看回复码

## 相关接口

- [TOTP 注册选项](/docs/totp-registration-options.md)
- [TOTP 验证](/docs/totp-verify.md)
- [TOTP 状态](/docs/totp-status.md)
- [TOTP 回复码重新生成](/docs/totp-recovery-codes-regenerate.md)
- [TOTP 禁用](/docs/totp-disable.md)

## 常见问题

### Q: 为什么现在返回完整的恢复码？

A: 按当前需求允许查看原始恢复码，因此会返回完整内容，请仅在受控环境下调用。

### Q: 用户可以看到自己使用过的回复码吗？

A: 不行。已使用的回复码不会显示在列表中，用户无法看到。

### Q: 如果用户记得完整的回复码，但这里只显示部分字符，可以使用吗？

A: 可以。用户在验证时输入完整的回复码，系统会验证完整性。

### Q: 剩余的回复码会在某个时间后过期吗？

A: 不会。回复码不会过期，直到被使用或用户重新生成新的码。

### Q: 这个接口会被频繁调用吗？会影响性能吗？

A: 不会频繁调用，只在用户查看账户设置时调用。性能影响微乎其微。
