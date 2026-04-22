# Passkey 获取列表接口

## 基本信息
- 方法：GET
- 路径：/auth/passkey/list
- 需要认证：是（使用 AccessToken）
- 请求类型：无需请求体

## 用途
此接口用于获取当前登录用户的所有已注册 Passkey 列表，包括 Passkey 名称、传输方式、最后使用时间和创建时间。

## 安全说明
- 仅返回当前登录用户的 Passkey 列表
- 公钥数据不会返回给前端（只存储在服务器）
- 最后使用时间可用于检查哪些 Passkey 仍在使用

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求示例
```bash
curl -X GET \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/auth/passkey/list
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "passkeys": [
      {
        "id": 1,
        "name": "iPhone 13",
        "transports": "internal",
        "lastUsedAt": "2026-02-05T10:30:00",
        "createdAt": "2026-02-01T09:00:00"
      },
      {
        "id": 2,
        "name": "YubiKey 5C",
        "transports": "usb,nfc",
        "lastUsedAt": null,
        "createdAt": "2026-02-03T14:20:00"
      }
    ]
  }
}
```

## 响应字段说明
- passkeys: Passkey 列表数组
  - id: Passkey ID，用于删除操作
  - name: Passkey 名称，用户自定义
  - transports: 传输方式，多个值用逗号分隔
    - `usb`: USB 连接
    - `nfc`: NFC 近场通信
    - `ble`: 蓝牙连接
    - `internal`: 平台内置认证器（如 Face ID、Touch ID、Windows Hello）
  - lastUsedAt: 最后使用时间（ISO 8601 格式），未使用过则为 null
  - createdAt: 创建时间（ISO 8601 格式）

## 失败响应

### 1) 未登录
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录"
}
```

## 前端集成示例

```javascript
// 获取 Passkey 列表
const response = await fetch('/auth/passkey/list', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${accessToken}`
  }
});

const result = await response.json();
if (result.code === 200) {
  const passkeys = result.data.passkeys;
  
  // 显示 Passkey 列表
  passkeys.forEach(passkey => {
    console.log(`ID: ${passkey.id}`);
    console.log(`名称: ${passkey.name}`);
    console.log(`类型: ${getPasskeyTypeLabel(passkey.transports)}`);
    console.log(`最后使用: ${passkey.lastUsedAt || '未使用'}`);
    console.log(`创建时间: ${passkey.createdAt}`);
  });
}

// 根据 transports 显示合适的图标和标签
function getPasskeyTypeLabel(transports) {
  if (transports.includes('internal')) {
    return '平台内置认证器 (Face ID / Touch ID / Windows Hello)';
  } else if (transports.includes('usb')) {
    return 'USB 安全密钥 (如 YubiKey)';
  } else if (transports.includes('nfc')) {
    return 'NFC 安全密钥';
  } else if (transports.includes('ble')) {
    return '蓝牙安全密钥';
  }
  return '其他认证器';
}
```

## UI 展示建议

### Transports 图标映射
根据 transports 字段显示对应的图标：

| Transports | 图标建议 | 标签 |
|-----------|---------|------|
| internal | 📱/💻 | Face ID / Touch ID / Windows Hello |
| usb | 🔑 | USB 安全密钥 |
| nfc | 📡 | NFC 安全密钥 |
| ble | 🔵 | 蓝牙安全密钥 |
| usb,nfc | 🔑📡 | USB/NFC 安全密钥 |

### 列表排序建议
1. 按最后使用时间降序（最近使用的在前）
2. 未使用过的在后
3. 创建时间降序（最新创建的在前）

### 操作按钮
- 重命名：允许用户修改 Passkey 名称，详见 [重命名接口](passkey-rename.md)
- 删除：调用删除接口，详见 [删除接口](passkey-delete.md)
- 查看详情：显示完整的创建时间、最后使用时间等

## 注意事项
1. 列表仅显示当前登录用户的 Passkey
2. 如果用户未注册任何 Passkey，返回空数组
3. lastUsedAt 为 null 表示该 Passkey 从未使用过
4. transports 字段帮助前端显示合适的图标和说明
5. 建议在用户设置页面显示此列表，方便管理

## 空列表处理
如果用户未注册 Passkey：
```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "passkeys": []
  }
}
```

前端可以显示引导信息：
- "您还未注册 Passkey"
- "点击下方按钮注册您的第一个 Passkey"
- "Passkey 让您无需密码即可快速安全地登录"

## 相关接口
- [注册 Passkey](passkey-registration-options.md)
- [重命名 Passkey](passkey-rename.md)
- [删除 Passkey](passkey-delete.md)
- [Passkey 登录](passkey-authentication-options.md)
