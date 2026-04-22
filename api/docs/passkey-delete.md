# Passkey 删除接口

## 基本信息
- 方法：DELETE
- 路径：/auth/passkey/{passkeyId}
- 需要认证：是（使用 AccessToken）
- 请求类型：无需请求体

## 用途
此接口用于删除指定的 Passkey。用户可以删除不再使用的 Passkey 认证器。

## 安全说明
- 仅能删除自己的 Passkey
- 删除后该 Passkey 将无法再用于登录或敏感操作
- 删除操作不可恢复

## 路径参数
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| passkeyId | Long | 是 | 要删除的 Passkey ID，可从 Passkey 列表接口获取 |

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求示例
```bash
curl -X DELETE \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/auth/passkey/1
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "message": "Passkey 删除成功"
}
```

## 失败响应

### 1) 未登录
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录"
}
```

### 2) Passkey ID 不存在
- HTTP Status：404

```json
{
  "code": 404,
  "msg": "Passkey 不存在"
}
```

### 3) Passkey 不属于当前用户
- HTTP Status：403

```json
{
  "code": 403,
  "msg": "无权删除此 Passkey"
}
```

## 前端集成示例

```javascript
// 删除指定的 Passkey
async function deletePasskey(passkeyId) {
  // 可选：弹出确认对话框
  if (!confirm('确定要删除此 Passkey 吗？删除后将无法使用此认证器登录。')) {
    return;
  }
  
  try {
    const response = await fetch(`/auth/passkey/${passkeyId}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    });
    
    const result = await response.json();
    
    if (result.code === 200) {
      console.log('Passkey 删除成功');
      // 刷新 Passkey 列表
      refreshPasskeyList();
    } else {
      console.error('删除失败:', result.msg);
    }
  } catch (error) {
    console.error('删除失败:', error);
  }
}

// 刷新 Passkey 列表
async function refreshPasskeyList() {
  const response = await fetch('/auth/passkey/list', {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${accessToken}`
    }
  });
  
  const result = await response.json();
  if (result.code === 200) {
    // 更新 UI 显示
    displayPasskeyList(result.data.passkeys);
  }
}
```

## UI 交互建议

### 删除按钮位置
- 在 Passkey 列表的每个项目旁边显示删除按钮
- 删除按钮建议使用红色或警告色，标明这是危险操作

### 确认对话框
建议在删除前显示确认对话框，包含以下信息：
- Passkey 名称
- 创建时间
- 警告信息："删除后将无法使用此认证器登录"
- 确认和取消按钮

示例：
```javascript
const confirmed = confirm(
  `确定要删除 Passkey "${passkeyName}" 吗？\n` +
  `创建于：${createdAt}\n\n` +
  `删除后将无法使用此认证器登录。`
);
```

### 删除后处理
1. 显示成功提示："Passkey 已删除"
2. 自动刷新 Passkey 列表
3. 如果删除后没有任何 Passkey，显示引导信息："您还未注册 Passkey，点击注册"

## 注意事项
1. 用户只能删除属于自己的 Passkey
2. 删除操作不可恢复，建议在删除前显示确认对话框
3. 删除 Passkey 不会影响现有的登录会话（AccessToken 和 RefreshToken 仍然有效）
4. 建议保留至少一个 Passkey 或其他登录方式（密码、邮箱验证码），避免用户无法登录
5. 如果用户删除了所有 Passkey，仍可使用密码或验证码登录

## 安全考虑
- 只能删除属于当前登录用户的 Passkey
- 使用 AccessToken 验证用户身份
- 删除操作会记录日志（如需审计）

## 批量删除（未实现）
如果需要批量删除 Passkey，可以：
1. 前端循环调用此接口
2. 或实现新的批量删除接口 `POST /auth/passkey/batch-delete`

## 相关接口
- [获取 Passkey 列表](passkey-list.md)
- [重命名 Passkey](passkey-rename.md)
- [注册 Passkey](passkey-registration-options.md)
- [Passkey 登录](passkey-authentication-options.md)
