# TOTP 注册确认接口文档

## 端点

```
POST /auth/totp/registration-verify
```

## 功能描述

确认 TOTP 注册。用户使用身份验证器应用扫描二维码后，输入应用中生成的 6 位码来确认注册。验证成功后，TOTP 将被启用。

## 请求

### URL
```
POST /auth/totp/registration-verify
```

### Headers
```
Authorization: Bearer {access_token}
Content-Type: application/json
```

### Body
```json
{
  "code": "123456"
}
```

### 请求字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| code | string | 是 | 身份验证器应用中显示的 6 位数字码 |

### 示例 cURL
```bash
curl -X POST http://localhost:8080/auth/totp/registration-verify \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{"code": "123456"}'
```

## 响应

### 成功响应 (200 OK)
```json
{
  "code": 200,
  "message": "TOTP 注册成功",
  "data": "TOTP 已启用"
}
```

### 错误响应

#### 验证码无效 (401 Unauthorized)
```json
{
  "code": 401,
  "message": "验证码无效",
  "data": null
}
```

#### 验证码为空 (400 Bad Request)
```json
{
  "code": 400,
  "message": "验证码不能为空",
  "data": null
}
```

#### 用户已启用 TOTP (400 Bad Request)
```json
{
  "code": 400,
  "message": "用户已启用 TOTP",
  "data": null
}
```

#### 未认证 (401 Unauthorized)
```json
{
  "code": 401,
  "message": "未认证",
  "data": null
}
```

#### 用户不存在 (404 Not Found)
```json
{
  "code": 404,
  "message": "用户不存在",
  "data": null
}
```

## 使用说明

### 1. 完整的 TOTP 启用流程

#### 步骤 1：获取 TOTP 注册选项
```javascript
const optionsResponse = await fetch('/auth/totp/registration-options', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }
});

const optionsResult = await optionsResponse.json();
const { secret, qrCodeUrl, recoveryCodes } = optionsResult.data;
```

#### 步骤 2：显示二维码和回复码给用户
```html
<div id="totp-setup">
  <h2>启用双因素认证 (TOTP)</h2>
  
  <div id="qrcode"></div>
  
  <h3>手动输入密钥（如果扫描失败）</h3>
  <p>密钥：<code id="secret"></code></p>
  
  <h3>保存这些回复码</h3>
  <ul id="recovery-codes-list"></ul>
  
  <h3>验证 TOTP</h3>
  <input type="text" id="totp-code" placeholder="输入 6 位验证码" maxlength="6">
  <button onclick="confirmTotp()">确认</button>
</div>

<script>
  document.getElementById('secret').textContent = secret;
  
  const recoveryCodesList = document.getElementById('recovery-codes-list');
  recoveryCodes.forEach(code => {
    const li = document.createElement('li');
    li.textContent = code;
    recoveryCodesList.appendChild(li);
  });
  
  // 显示二维码
  import QRCode from 'qrcode';
  QRCode.toCanvas(
    document.getElementById('qrcode'),
    qrCodeUrl,
    error => { if (error) console.error(error); }
  );
</script>
```

#### 步骤 3：用户在身份验证器应用中输入密钥或扫描二维码

用户可以使用以下身份验证器应用之一：
- Google Authenticator
- Microsoft Authenticator
- Authy
- FreeOTP
- Totp Authenticator

#### 步骤 4：用户输入验证码并确认

```javascript
async function confirmTotp() {
  const code = document.getElementById('totp-code').value;
  
  const response = await fetch('/auth/totp/registration-verify', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ code: code })
  });
  
  const result = await response.json();
  
  if (result.code === 200) {
    alert('TOTP 启用成功！请保存回复码到安全位置。');
    // 显示成功消息
  } else {
    alert('验证失败，请检查验证码是否正确');
  }
}
```

### 2. 后端实现

```java
@PostMapping("/auth/totp/registration-verify")
public ResponseEntity<ApiResponse<String>> confirmTotpRegistration(
        Authentication authentication,
        @RequestBody TotpRegistrationConfirmRequest request) {
    
    // 1. 验证用户已认证
    if (authentication == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiResponse<>(401, "未认证"));
    }
    
    // 2. 验证请求参数
    if (request.getCode() == null || request.getCode().isEmpty()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse<>(400, "验证码不能为空"));
    }
    
    // 3. 获取当前用户
    User user = getCurrentUser(authentication);
    
    // 4. 检查用户是否已启用 TOTP
    if (totpService.isTotpEnabled(user.getId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse<>(400, "用户已启用 TOTP"));
    }
    
    // 5. 从 Redis 获取待确认的秘钥（或其他临时存储）
    String secretKey = getTempSecretKeyFromRedis(user.getId());
    
    // 6. 生成回复码
    String[] recoveryCodes = totpService.generateRecoveryCodes(10);
    
    // 7. 确认 TOTP 注册
    boolean success = totpService.confirmTotpRegistration(
        user.getId(),
        secretKey,
        request.getCode(),
        recoveryCodes
    );
    
    if (success) {
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "TOTP 注册成功", "TOTP 已启用"));
    } else {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiResponse<>(401, "验证码无效"));
    }
}
```

## 技术细节

### TOTP 码验证过程

1. 获取用户在临时存储中保存的秘钥
2. 使用 TOTP 算法验证用户输入的 6 位码
3. 为了容忍时间误差，支持验证当前时间步长、前一个时间步长和后一个时间步长的码

### 数据库操作

```java
// 1. 生成并保存 TOTP 配置
UserTotp userTotp = new UserTotp(
    userId,
    secretKey,
    passwordEncoder.encode(secretKey)
);
userTotp.setIsEnabled(true);
userTotpRepository.save(userTotp);

// 2. 保存回复码
for (String code : recoveryCodes) {
    TotpRecoveryCode recoveryCode = new TotpRecoveryCode(
        userId,
        passwordEncoder.encode(code)
    );
    recoveryCodeRepository.save(recoveryCode);
}
```

## 重要注意事项

1. **密钥保管**
   - 秘钥应该在服务器上妥善保管，不应该多次发送
   - 建议使用 Redis 存储临时秘钥，设置过期时间

2. **回复码保管**
   - 回复码应该在启用 TOTP 后立即显示给用户
   - 用户应该将回复码保存到安全的位置
   - 回复码不应该再次显示给用户

3. **验证失败处理**
   - 如果验证失败，用户应该重新扫描二维码或重新输入密钥
   - 建议提供重试次数限制，防止暴力破解

4. **时间同步**
   - TOTP 依赖准确的系统时间
   - 建议在验证前检查用户设备的时间
   - 建议在验证失败时提示用户检查设备时间

## 错误处理

### 场景 1：用户输入错误的验证码

```javascript
if (result.code === 401) {
  // 提示用户验证码错误，让用户重新输入
  alert('验证码无效，请检查是否正确输入');
}
```

### 场景 2：用户设备时间不同步

如果用户的设备时间与服务器时间相差超过 1 分钟，验证会失败。建议：
1. 检测验证失败次数
2. 提示用户检查设备时间
3. 提供离线验证选项（如使用备用码）

### 场景 3：用户丢失了秘钥

如果用户在确认前丢失了秘钥或浏览器会话过期：
1. 用户需要重新调用 `/auth/totp/registration-options`
2. 获取新的秘钥和二维码
3. 重新进行注册流程

## 最佳实践

1. **用户体验**
   - 在显示 TOTP 设置时，同时显示秘钥、二维码和回复码
   - 提供清晰的步骤说明
   - 在确认前要求用户保存回复码

2. **安全性**
   - 使用 HTTPS 传输数据
   - 在服务器端验证码有效性
   - 限制验证尝试次数
   - 定期审计 TOTP 使用情况

3. **可靠性**
   - 支持多个身份验证器应用
   - 提供时间同步检查
   - 提供回复码作为备份选项
   - 支持禁用和重新启用 TOTP

## 相关接口

- [TOTP 注册选项](/docs/totp-registration-options.md)
- [TOTP 验证](/docs/totp-verify.md)
- [TOTP 状态](/docs/totp-status.md)
- [TOTP 禁用](/docs/totp-disable.md)

## 常见问题

### Q: 为什么需要确认 TOTP？

A: 确认 TOTP 是为了确保用户确实拥有身份验证器应用，并且秘钥已正确输入或扫描。

### Q: 如果用户在确认前关闭了浏览器，秘钥会怎样？

A: 秘钥仅存储在临时存储中（如 Redis），不会保存到数据库。用户需要重新开始 TOTP 注册流程。

### Q: 可以在多个设备上使用同一个秘钥吗？

A: 可以。秘钥是静态的，可以在多个身份验证器应用中添加，这样可以在多个设备上生成相同的码。

### Q: 验证码验证失败了，用户应该怎么办？

A: 最常见的原因是设备时间不同步。用户应该：
1. 检查设备时间
2. 同步设备时间
3. 重新输入新生成的验证码
4. 如果仍然失败，可以重新开始 TOTP 注册流程
