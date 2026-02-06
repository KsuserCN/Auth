# TOTP 验证接口文档

## 端点

```
POST /auth/totp/verify
```

## 功能描述

验证用户提供的 TOTP 码或回复码。用户可以在登录或敏感操作时使用此接口进行双因素认证。

## 请求

### URL
```
POST /auth/totp/verify
```

### Headers
```
Authorization: Bearer {access_token}
Content-Type: application/json
```

### Body

#### 使用 TOTP 码验证
```json
{
  "code": "123456"
}
```

#### 使用回复码验证
```json
{
  "recoveryCode": "12345678"
}
```

### 请求字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| code | string | 否 | 6 位 TOTP 码 |
| recoveryCode | string | 否 | 8 位回复码 |

### 示例 cURL

#### TOTP 码验证
```bash
curl -X POST http://localhost:8080/auth/totp/verify \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{"code": "123456"}'
```

#### 回复码验证
```bash
curl -X POST http://localhost:8080/auth/totp/verify \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{"recoveryCode": "12345678"}'
```

## 响应

### 成功响应 (200 OK)

#### TOTP 码验证成功
```json
{
  "code": 200,
  "message": "TOTP 验证成功",
  "data": {
    "success": true,
    "message": "验证成功"
  }
}
```

#### 回复码验证成功
```json
{
  "code": 200,
  "message": "使用回复码验证成功",
  "data": {
    "success": true,
    "message": "使用回复码验证成功"
  }
}
```

### 错误响应

#### 验证失败 (401 Unauthorized)
```json
{
  "code": 401,
  "message": "验证失败",
  "data": {
    "success": false,
    "message": "TOTP 码或回复码无效"
  }
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

### 1. 登录流程集成

```javascript
// 用户输入用户名和密码进行初步认证
const response = await fetch('/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: userEmail,
    password: userPassword
  })
});

// 如果用户启用了 TOTP，需要进行第二因素认证
if (response.status === 200) {
  const loginResult = await response.json();
  
  if (loginResult.requiresTwoFactor) {
    // 显示 TOTP 码输入框
    const totpCode = await promptUserForTotp();
    
    // 验证 TOTP 码
    const verifyResponse = await fetch('/auth/totp/verify', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${tempAccessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ code: totpCode })
    });
    
    if (verifyResponse.status === 200) {
      // 验证成功，发放完整访问令牌
      const finalAccessToken = loginResult.accessToken;
    }
  }
}
```

### 2. 使用回复码

当用户丢失 TOTP 设备时，可以使用回复码：

```javascript
// 在 TOTP 码验证失败后，提示用户可以使用回复码
const recoveryCode = await promptUserForRecoveryCode();

const verifyResponse = await fetch('/auth/totp/verify', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${tempAccessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ recoveryCode: recoveryCode })
});
```

### 3. 后端验证逻辑

```java
@PostMapping("/auth/totp/verify")
public ResponseEntity<ApiResponse<TotpVerifyResponse>> verifyTotp(
        Authentication authentication,
        @RequestBody TotpVerifyRequest request) {
    
    // 1. 验证用户已认证
    if (authentication == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiResponse<>(401, "未认证"));
    }
    
    // 2. 获取当前用户
    User user = getCurrentUser(authentication);
    
    // 3. 尝试 TOTP 码验证
    if (request.getCode() != null && !request.getCode().isEmpty()) {
        if (totpService.verifyTotpCode(user.getId(), request.getCode())) {
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "TOTP 验证成功",
                    new TotpVerifyResponse(true, "验证成功")));
        }
    }
    
    // 4. 尝试回复码验证
    if (request.getRecoveryCode() != null && !request.getRecoveryCode().isEmpty()) {
        if (totpService.verifyRecoveryCode(user.getId(), request.getRecoveryCode())) {
            return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(200, "使用回复码验证成功",
                    new TotpVerifyResponse(true, "使用回复码验证成功")));
        }
    }
    
    // 5. 验证失败
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ApiResponse<>(401, "验证失败",
            new TotpVerifyResponse(false, "TOTP 码或回复码无效")));
}
```

## 技术细节

### TOTP 码验证算法

```
1. 计算当前时间步长：T = floor(Unix_timestamp / 30)
2. 验证 T-1、T 和 T+1 时间步长对应的码
   - 允许 ±30 秒的时间误差，以容忍设备时间不同步
3. 如果任何一个时间步长的码匹配，验证成功
```

### 回复码验证过程

```
1. 根据 user_id 和 code_hash 查找回复码
2. 检查回复码是否已使用（is_used）
3. 如果未使用，标记为已使用，记录使用时间
4. 删除对应的数据库记录（防止重复使用）
```

## 注意事项

1. **码的有效性**
   - TOTP 码每 30 秒更新一次
   - 支持前后各 30 秒的时间误差
   - 码输入后应立即验证，避免延迟导致码过期

2. **回复码使用**
   - 每个回复码只能使用一次
   - 使用后自动标记为已使用
   - 建议用户使用回复码后立即重新启用 TOTP

3. **安全考虑**
   - 不应该在错误日志中记录用户的 TOTP 码或回复码
   - 应该使用 HTTPS 传输
   - 建议限制验证尝试次数，防止暴力破解

4. **时间同步**
   - TOTP 验证依赖设备的本地时间
   - 如果设备时间相差超过 1 分钟，验证可能失败
   - 建议提示用户同步设备时间

## 错误处理

### 常见错误和解决方案

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| 验证失败 | TOTP 码错误或已过期 | 等待下一个码生成，或使用回复码 |
| 验证失败 | 设备时间不同步 | 同步设备时间 |
| 验证失败 | 回复码已使用 | 生成新的回复码或使用其他未使用的回复码 |
| 未认证 | 访问令牌无效 | 重新登录 |

## 相关接口

- [TOTP 注册选项](/docs/totp-registration-options.md)
- [TOTP 注册确认](/docs/totp-registration-verify.md)
- [TOTP 状态](/docs/totp-status.md)
- [TOTP 禁用](/docs/totp-disable.md)

## 常见问题

### Q: TOTP 码验证失败，提示码无效，应该怎么办？

A: 可能的原因有：
1. 设备时间不同步 - 同步设备时间
2. 码已过期 - 等待下一个码生成
3. 用户未启用 TOTP - 检查 TOTP 启用状态

如果以上都不是问题，可以使用回复码进行验证。

### Q: 回复码验证失败，提示码无效，应该怎么办？

A: 可能的原因有：
1. 回复码已使用 - 使用其他未使用的回复码
2. 回复码输入错误 - 检查输入
3. 用户未启用 TOTP - 检查 TOTP 启用状态

如果所有回复码都已使用，用户可以重新生成回复码。

### Q: 用户说 TOTP 码一直验证失败，应该怎么办？

A: 最常见的原因是设备时间不同步。建议：
1. 检查用户设备的时间
2. 提示用户同步设备时间
3. 如果仍然失败，可以使用回复码进行验证

### Q: 可以限制 TOTP 码的验证次数吗？

A: 可以。建议在 `TotpController` 中添加速率限制，防止暴力破解。
