# TOTP 状态接口文档

## 端点

```
GET /auth/totp/status
```

## 功能描述

获取用户的 TOTP 启用状态和剩余回复码数量。

## 请求

### URL
```
GET /auth/totp/status
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
curl -X GET http://localhost:8080/auth/totp/status \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json"
```

## 响应

### 成功响应 (200 OK)

#### 用户已启用 TOTP
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

#### 用户未启用 TOTP
```json
{
  "code": 200,
  "message": "获取 TOTP 状态成功",
  "data": {
    "enabled": false,
    "recoveryCodesCount": 0
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | integer | 响应状态码（200 表示成功）|
| message | string | 响应消息 |
| data | object | 响应数据 |
| data.enabled | boolean | TOTP 是否启用 |
| data.recoveryCodesCount | long | 剩余未使用的回复码数量 |

### 错误响应

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

### 1. 前端集成

```javascript
// 获取 TOTP 状态
async function getTotpStatus() {
  const response = await fetch('/auth/totp/status', {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    }
  });
  
  const result = await response.json();
  
  if (result.code === 200) {
    const { enabled, recoveryCodesCount } = result.data;
    
    if (enabled) {
      console.log('TOTP 已启用');
      console.log(`剩余回复码数量：${recoveryCodesCount}`);
      
      // 如果回复码即将用完，提示用户重新生成
      if (recoveryCodesCount < 3) {
        promptUserToRegenerateRecoveryCodes();
      }
    } else {
      console.log('TOTP 未启用');
    }
  }
}
```

### 2. 用户账户管理页面

```html
<div class="account-security">
  <h2>账户安全</h2>
  
  <div class="totp-status">
    <h3>双因素认证 (TOTP)</h3>
    <div id="totp-status-info"></div>
    <button id="totp-action-btn">启用 TOTP</button>
  </div>
</div>

<script>
  async function updateTotpStatus() {
    const response = await fetch('/auth/totp/status', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });
    
    const result = await response.json();
    const { enabled, recoveryCodesCount } = result.data;
    
    const statusDiv = document.getElementById('totp-status-info');
    const actionBtn = document.getElementById('totp-action-btn');
    
    if (enabled) {
      statusDiv.innerHTML = `
        <p>状态：<strong>已启用</strong></p>
        <p>剩余回复码：<strong>${recoveryCodesCount}</strong></p>
        ${recoveryCodesCount < 3 ? '<p style="color: orange;">⚠️ 回复码即将用完，建议重新生成</p>' : ''}
      `;
      actionBtn.textContent = '禁用 TOTP';
      actionBtn.onclick = () => disableTotp();
    } else {
      statusDiv.innerHTML = '<p>状态：<strong>未启用</strong></p>';
      actionBtn.textContent = '启用 TOTP';
      actionBtn.onclick = () => enableTotp();
    }
  }
  
  updateTotpStatus();
</script>
```

### 3. 后端实现

```java
@GetMapping("/auth/totp/status")
public ResponseEntity<ApiResponse<TotpStatusResponse>> getTotpStatus(
        Authentication authentication) {
    
    // 1. 验证用户已认证
    if (authentication == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiResponse<>(401, "未认证"));
    }
    
    // 2. 获取当前用户
    User user = getCurrentUser(authentication);
    
    // 3. 获取 TOTP 状态
    Map<String, Object> status = totpService.getTotpStatus(user.getId());
    
    // 4. 构造响应
    TotpStatusResponse response = new TotpStatusResponse(
        (Boolean) status.get("enabled"),
        (Long) status.get("recoveryCodesCount")
    );
    
    return ResponseEntity.status(HttpStatus.OK)
        .body(new ApiResponse<>(200, "获取 TOTP 状态成功", response));
}
```

## 业务逻辑

### TOTP 状态判断

```java
public Map<String, Object> getTotpStatus(Long userId) {
    Optional<UserTotp> userTotpOpt = userTotpRepository.findByUserId(userId);
    
    Map<String, Object> result = new HashMap<>();
    
    if (userTotpOpt.isEmpty()) {
        // TOTP 未初始化
        result.put("enabled", false);
        result.put("recoveryCodesCount", 0);
    } else {
        UserTotp userTotp = userTotpOpt.get();
        // TOTP 已初始化，检查是否启用
        result.put("enabled", userTotp.getIsEnabled());
        
        if (userTotp.getIsEnabled()) {
            // 统计未使用的回复码
            long count = recoveryCodeRepository.countByUserIdAndIsUsedFalse(userId);
            result.put("recoveryCodesCount", count);
        } else {
            result.put("recoveryCodesCount", 0);
        }
    }
    
    return result;
}
```

## 使用场景

### 场景 1：显示账户安全状态

用户登录后，应用调用此接口获取 TOTP 状态，显示在用户账户信息页面。

```javascript
// 在用户登录后调用
async function displayAccountSecurity() {
  const response = await fetch('/auth/totp/status', {
    headers: { 'Authorization': `Bearer ${accessToken}` }
  });
  
  const result = await response.json();
  const { enabled, recoveryCodesCount } = result.data;
  
  if (enabled) {
    document.getElementById('security-status').innerHTML = '✅ 双因素认证已启用';
  }
}
```

### 场景 2：检查是否需要重新生成回复码

如果剩余回复码过少，提示用户重新生成。

```javascript
async function checkRecoveryCodesStatus() {
  const response = await fetch('/auth/totp/status', {
    headers: { 'Authorization': `Bearer ${accessToken}` }
  });
  
  const result = await response.json();
  const { enabled, recoveryCodesCount } = result.data;
  
  if (enabled && recoveryCodesCount < 3) {
    showNotification('您的回复码即将用完，建议立即重新生成');
  }
}
```

### 场景 3：判断是否需要 TOTP 验证

登录时判断用户是否启用了 TOTP，决定是否需要进行第二因素认证。

```javascript
async function login(email, password) {
  // 1. 验证用户名和密码
  const loginResponse = await fetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password })
  });
  
  if (loginResponse.status === 200) {
    const loginResult = await loginResponse.json();
    const tempToken = loginResult.data.tempToken;
    
    // 2. 获取 TOTP 状态
    const statusResponse = await fetch('/auth/totp/status', {
      headers: { 'Authorization': `Bearer ${tempToken}` }
    });
    
    const statusResult = await statusResponse.json();
    
    if (statusResult.data.enabled) {
      // 3. 需要进行 TOTP 验证
      showTotpVerificationDialog();
    } else {
      // 4. 直接发放访问令牌
      finishLogin(loginResult.data.accessToken);
    }
  }
}
```

## 注意事项

1. **刷新频率**
   - 不需要频繁调用此接口
   - 建议在用户打开账户设置页面时调用一次

2. **回复码警告**
   - 当回复码数量少于 3 个时，建议提示用户重新生成
   - 可以在每次登录成功后检查回复码数量

3. **状态一致性**
   - 此接口返回的状态应与服务器端的真实状态一致
   - 避免在前端做状态缓存，每次需要时重新获取

## 相关接口

- [TOTP 注册选项](/docs/totp-registration-options.md)
- [TOTP 注册确认](/docs/totp-registration-verify.md)
- [TOTP 验证](/docs/totp-verify.md)
- [TOTP 禁用](/docs/totp-disable.md)
- [TOTP 回复码重新生成](/docs/totp-recovery-codes-regenerate.md)

## 常见问题

### Q: 为什么要检查回复码数量？

A: 如果回复码用完了，用户丢失 TOTP 设备时将无法使用回复码登录。建议在回复码即将用完时提示用户重新生成。

### Q: 回复码为什么会用完？

A: 每次使用回复码进行验证时，该码就会被标记为已使用。当用户多次丢失 TOTP 码或使用回复码登录时，回复码数量会减少。

### Q: 可以手动清零回复码计数吗？

A: 不建议。回复码计数应该由系统自动管理。如果用户想重新生成回复码，应该调用"重新生成回复码"接口。

### Q: TOTP 状态会自动失效吗？

A: 不会。TOTP 一旦启用，就会一直保持启用状态，直到用户主动禁用。
