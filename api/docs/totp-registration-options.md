# TOTP 注册选项接口文档

## 端点

```
POST /auth/totp/registration-options
```

## 功能描述

获取 TOTP 注册所需的选项，包括密钥、二维码 URL 和回复码。用户通过这个接口获取信息后，使用身份验证器应用扫描二维码或手动输入密钥来添加账户。

## 请求

### URL
```
POST /auth/totp/registration-options
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
curl -X POST http://localhost:8080/auth/totp/registration-options \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json"
```

## 响应

### 成功响应 (200 OK)

```json
{
  "code": 200,
  "message": "获取 TOTP 注册选项成功",
  "data": {
    "secret": "JBSWY3DPEBLW64TMMQ======",
    "qrCodeUrl": "otpauth://totp/KSUser:user123?secret=JBSWY3DPEBLW64TMMQ======&issuer=KSUser",
    "recoveryCodes": [
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
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | integer | 响应状态码（200 表示成功）|
| message | string | 响应消息 |
| data | object | 响应数据 |
| data.secret | string | TOTP 密钥（Base32 编码），用户可手动输入到身份验证器应用 |
| data.qrCodeUrl | string | 二维码 URL（otpauth:// 协议），用户可用身份验证器应用扫描 |
| data.recoveryCodes | array | 10 个回复码，用户丢失设备时可用于登录 |

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

#### 用户已启用 TOTP (400 Bad Request)
```json
{
  "code": 400,
  "message": "用户已启用 TOTP",
  "data": null
}
```

## 使用说明

### 1. 前端集成步骤

#### 步骤 1：调用接口获取数据
```javascript
const response = await fetch('/auth/totp/registration-options', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }
});

const result = await response.json();
if (result.code === 200) {
  const { secret, qrCodeUrl, recoveryCodes } = result.data;
}
```

#### 步骤 2：显示二维码
使用二维码库（如 `qrcode.js`）显示二维码
```html
<div id="qrcode"></div>
<script>
  import QRCode from 'qrcode';
  
  QRCode.toCanvas(document.getElementById('qrcode'), qrCodeUrl, error => {
    if (error) console.error(error);
  });
</script>
```

#### 步骤 3：让用户保存回复码
```html
<div class="recovery-codes">
  <h3>请妥善保管以下回复码</h3>
  <ul>
    {{#each recoveryCodes}}
      <li>{{this}}</li>
    {{/each}}
  </ul>
</div>
```

### 2. 后端使用流程

```java
@PostMapping("/auth/totp/registration-options")
public ResponseEntity<ApiResponse<TotpRegistrationOptionsResponse>> getTotpRegistrationOptions(
        Authentication authentication) {
    // 1. 验证用户是否已认证
    if (authentication == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiResponse<>(401, "未认证"));
    }
    
    // 2. 获取当前用户信息
    User user = getCurrentUser(authentication);
    
    // 3. 检查用户是否已启用 TOTP
    if (totpService.isTotpEnabled(user.getId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse<>(400, "用户已启用 TOTP"));
    }
    
    // 4. 生成 TOTP 密钥和回复码
    Map<String, Object> secretData = totpService.generateTotpSecret(user.getId());
    
    // 5. 构造响应
    return ResponseEntity.status(HttpStatus.OK)
        .body(new ApiResponse<>(200, "获取 TOTP 注册选项成功", response));
}
```

## 技术细节

### TOTP 密钥格式

密钥是 Base32 编码的 32 字节随机数据，格式如下：
```
JBSWY3DPEBLW64TMMQ======
```

### 二维码 URL 格式

遵循 `otpauth://` URI 方案，格式如下：
```
otpauth://totp/{label}?secret={secret}&issuer={issuer}
```

其中：
- `label`：通常为 `{应用名}:{用户名}`
- `secret`：Base32 编码的密钥
- `issuer`：应用发行者名称

### 回复码格式

每个回复码是 8 位的随机数字，如：
```
12345678
87654321
...
```

## 注意事项

1. **密钥保管**
   - 密钥应该安全存储，不应该通过不安全的通道传输
   - 建议使用 HTTPS 传输

2. **回复码保管**
   - 用户应该将回复码保存到安全的位置（如密码管理器或纸质备份）
   - 一旦回复码丢失，用户可能无法在丢失 TOTP 设备时恢复账户

3. **二维码扫描**
   - 不同的身份验证器应用支持不同的功能
   - 建议支持多个身份验证器应用

4. **时间同步**
   - TOTP 依赖设备的本地时间
   - 建议提示用户同步设备时间

## 相关接口

- [TOTP 注册确认](/docs/totp-registration-verify.md)
- [TOTP 验证](/docs/totp-verify.md)
- [TOTP 状态](/docs/totp-status.md)

## 常见问题

### Q: 为什么返回 10 个回复码？

A: 10 个回复码是标准数量，足以应对大多数情况。如果用户消耗了所有回复码，可以重新生成新的回复码。

### Q: 可以修改回复码数量吗？

A: 可以。在 `TotpService` 中修改 `RECOVERY_CODES_COUNT` 常数即可。

### Q: 密钥可以变更吗？

A: 可以。用户可以禁用当前 TOTP，然后重新启用新的 TOTP。

### Q: 回复码可以用于其他用途吗？

A: 不建议。回复码应该仅用于在丢失 TOTP 设备时登录。
