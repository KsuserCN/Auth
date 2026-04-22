# TOTP 禁用接口文档

## 端点

```
POST /auth/totp/disable
```

## 功能描述

禁用用户的 TOTP。需要先完成敏感操作验证（参考修改密码流程），禁用后，所有回复码也会被删除，用户需要重新启用 TOTP 才能使用双因素认证。

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

### 步骤 3: 调用禁用 TOTP 接口
验证成功后，即可调用此接口禁用 TOTP。

## 请求

### URL
```
POST /auth/totp/disable
```

### Headers
```
Authorization: Bearer {access_token}
Content-Type: application/json
```

### 无请求体
此接口不需要请求体，验证通过cookie中的session信息进行

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

# 步骤 3: 禁用 TOTP（验证成功后）
curl -X POST http://localhost:8000/auth/totp/disable \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json"
```

## 响应

### 成功响应 (200 OK)
```json
{
  "code": 200,
  "message": "TOTP 禁用成功"
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

#### 用户未启用 TOTP (404 Not Found)
```json
{
  "code": 404,
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

## 使用说明

### 1. 前端集成

```javascript
// 禁用 TOTP（两步流程）
async function disableTotp() {
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
    
    // 步骤 4: 调用禁用 TOTP 接口
    const disableResponse = await fetch('/auth/totp/disable', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });
    
    const disableResult = await disableResponse.json();
    
    if (disableResult.code === 200) {
      alert('TOTP 已禁用');
      location.reload();
    } else {
      alert('禁用失败：' + disableResult.msg);
    }
  } catch (error) {
    console.error('错误：', error);
    alert('操作失败：' + error.message);
  }
}
```

### 2. 用户账户管理页面

```html
<div class="totp-management">
  <h3>双因素认证（TOTP）</h3>
  
  <div id="totp-status"></div>
  
  <div id="totp-actions">
    <button id="disable-btn" onclick="confirmDisableTotp()">禁用 TOTP</button>
    <button id="regenerate-btn" onclick="regenerateRecoveryCodes()">重新生成回复码</button>
  </div>
</div>

<script>
  function confirmDisableTotp() {
    if (confirm('确定要禁用 TOTP 吗？禁用后将需要重新启用才能使用双因素认证。')) {
      disableTotp();
    }
  }
  
  async function disableTotp() {
    // 调用上面定义的 disableTotp 函数，会自动进行敏感操作验证
    await disableTotpWithSensitiveVerification();
  }
</script>
```

### 3. 后端实现

```java
@PostMapping("/auth/totp/disable")
public ResponseEntity<ApiResponse<Void>> disableTotp(
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
    
    // 4. 禁用 TOTP
    boolean success = totpService.disableTotp(user.getId());
    
    if (success) {
        // 5. 可选：记录审计日志
        auditLogger.log(user.getId(), "禁用 TOTP", "成功");
        
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "TOTP 禁用成功"));
    } else {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiResponse<>(404, "用户未启用 TOTP"));
    }
}
```

## 业务逻辑

### 禁用 TOTP 的过程

```java
@Transactional
public boolean disableTotp(Long userId) {
    // 1. 查询用户的 TOTP 配置
    Optional<UserTotp> userTotpOpt = userTotpRepository.findByUserId(userId);
    
    if (userTotpOpt.isEmpty()) {
        return false; // 用户未启用 TOTP
    }
    
    // 2. 删除 TOTP 配置
    UserTotp userTotp = userTotpOpt.get();
    userTotpRepository.delete(userTotp);
    
    // 3. 删除所有回复码
    recoveryCodeRepository.deleteByUserId(userId);
    
    return true;
}
```

## 重要注意事项

1. **敏感操作验证**
   - 禁用 TOTP 需要先完成敏感操作验证
   - 使用 Passkey（如生物识别）完成验证
   - 验证标记有效期为 15 分钟

2. **数据删除**
   - 禁用 TOTP 会删除所有相关数据（配置和回复码）
   - 删除操作是不可逆的
   - 建议在禁用前要求用户确认

3. **回复码丢失**
   - 禁用 TOTP 后，所有回复码都会被删除
   - 用户无法再使用回复码登录
   - 用户需要重新启用 TOTP 才能恢复双因素认证

4. **安全审计**
   - 建议记录 TOTP 禁用事件，用于安全审计
   - 可以考虑在禁用后发送确认邮件给用户

5. **IP 验证**
   - 敏感操作验证会记录客户端 IP
   - 后续操作必须来自同一 IP（15 分钟内）
   - 如果 IP 变化，需要重新进行敏感操作验证

## 使用场景

### 场景 1：用户要更换身份验证器

1. 用户禁用旧的 TOTP
2. 用户启用新的 TOTP，添加到新的身份验证器应用

### 场景 2：用户因安全原因禁用 TOTP

1. 用户怀疑身份验证器被破坏
2. 用户禁用 TOTP
3. 用户重新启用 TOTP 并获取新的回复码

### 场景 3：管理员禁用用户的 TOTP

在某些情况下，管理员可能需要禁用用户的 TOTP（例如用户无法访问身份验证器）。可以添加管理员专用的禁用接口。

## 最佳实践

1. **用户确认**
   - 在禁用前要求用户确认操作
   - 提示用户禁用后的影响

2. **密码验证**
   - 始终要求输入密码
   - 防止意外禁用

3. **审计日志**
   - 记录所有 TOTP 禁用事件
   - 包括操作时间、用户信息等

4. **恢复选项**
   - 禁用后，用户可以随时重新启用
   - 提供清晰的重新启用说明

## 错误处理

### 常见错误和解决方案

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| 敏感操作验证失败 | 未完成敏感操作验证或验证过期 | 重新进行敏感操作验证 |
| IP 不匹配 | 验证后 IP 地址改变 | 重新进行敏感操作验证 |
| 用户未启用 TOTP | 用户的 TOTP 已禁用或未启用 | 提示用户 TOTP 状态 |
| 未认证 | 访问令牌无效 | 重新登录 |

## 相关接口

- [TOTP 注册选项](/docs/totp-registration-options.md)
- [TOTP 验证](/docs/totp-verify.md)
- [TOTP 状态](/docs/totp-status.md)
- [TOTP 回复码重新生成](/docs/totp-recovery-codes-regenerate.md)

## 常见问题

### Q: 禁用 TOTP 后，回复码会怎样？

A: 禁用 TOTP 时会同时删除所有回复码。用户重新启用 TOTP 时会获得新的回复码。

### Q: 用户忘记了密码，无法禁用 TOTP，应该怎么办？

A: 用户应该：
1. 使用"忘记密码"功能重置密码
2. 使用新密码禁用 TOTP

或者联系管理员获取帮助。

### Q: 禁用 TOTP 会影响其他设备上的登录吗？

A: 不会。禁用 TOTP 只是关闭了双因素认证，不会影响已有的登录会话。

### Q: 禁用 TOTP 后，用户可以立即重新启用吗？

A: 可以。用户可以立即调用"获取 TOTP 注册选项"接口重新启用 TOTP。
