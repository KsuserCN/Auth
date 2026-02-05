# Passkey 重命名接口

## 基本信息
- 方法：PUT
- 路径：/auth/passkey/{passkeyId}/rename
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json

## 用途
此接口用于修改指定 Passkey 的名称。用户可以为 Passkey 设置更有意义的名称，方便识别和管理多个认证器。

## 路径参数
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| passkeyId | Long | 是 | 要重命名的 Passkey ID，可从 Passkey 列表接口获取 |

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
```json
{
  "newName": "MacBook Pro Touch ID"
}
```

## 字段说明
- newName: 新的 Passkey 名称，不能为空，不能超过 50 个字符

## 请求示例
```bash
curl -X PUT \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"newName":"MacBook Pro Touch ID"}' \
  http://localhost:8000/auth/passkey/1/rename
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "message": "Passkey 重命名成功"
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

### 2) 新名称为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "Passkey 名称不能为空"
}
```

### 3) 新名称过长
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "Passkey 名称长度不能超过 50 个字符"
}
```

### 4) Passkey ID 不存在
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "Passkey 不存在"
}
```

### 5) Passkey 不属于当前用户
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "无权限修改此 Passkey"
}
```

## 前端集成示例

```javascript
// 重命名指定的 Passkey
async function renamePasskey(passkeyId, newName) {
  // 验证输入
  if (!newName || newName.trim().length === 0) {
    alert('请输入 Passkey 名称');
    return;
  }
  
  if (newName.length > 50) {
    alert('Passkey 名称不能超过 50 个字符');
    return;
  }
  
  try {
    const response = await fetch(`/auth/passkey/${passkeyId}/rename`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        newName: newName.trim()
      })
    });
    
    const result = await response.json();
    
    if (result.code === 200) {
      console.log('Passkey 重命名成功');
      // 刷新 Passkey 列表
      refreshPasskeyList();
    } else {
      console.error('重命名失败:', result.msg);
      alert(result.msg);
    }
  } catch (error) {
    console.error('重命名失败:', error);
    alert('网络错误，请稍后重试');
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

### 重命名方式1：内联编辑
- 在 Passkey 列表的每个项目名称旁显示"编辑"图标
- 点击后名称变为输入框，可直接修改
- 提供"保存"和"取消"按钮
- 或在失去焦点时自动保存

```javascript
function enableInlineEdit(passkeyId, currentName) {
  // 显示输入框
  const input = document.createElement('input');
  input.value = currentName;
  input.maxLength = 50;
  
  // 保存按钮
  const saveBtn = document.createElement('button');
  saveBtn.textContent = '保存';
  saveBtn.onclick = () => {
    renamePasskey(passkeyId, input.value);
  };
  
  // 取消按钮
  const cancelBtn = document.createElement('button');
  cancelBtn.textContent = '取消';
  cancelBtn.onclick = () => {
    // 恢复原始显示
  };
}
```

### 重命名方式2：弹窗/对话框
- 点击"重命名"按钮打开对话框
- 对话框包含输入框、确认和取消按钮
- 实时显示剩余字符数（50 字符限制）

```javascript
function showRenameDialog(passkeyId, currentName) {
  const newName = prompt(`请输入新的 Passkey 名称（当前：${currentName}）`, currentName);
  
  if (newName !== null && newName !== currentName) {
    renamePasskey(passkeyId, newName);
  }
}
```

### 建议的名称格式
提示用户使用有意义的名称：
- 设备名称：iPhone 13、MacBook Pro、iPad Air
- 认证器类型：Touch ID、Face ID、Windows Hello
- 安全密钥型号：YubiKey 5C、YubiKey 5 NFC
- 组合方式：MacBook Pro Touch ID、iPhone 13 Face ID

## 注意事项
1. 用户只能重命名属于自己的 Passkey
2. 新名称会自动去除首尾空格
3. 名称最长 50 个字符（中英文均计 1 个字符）
4. 建议使用有意义的名称，方便识别多个认证器
5. 重命名操作会更新 Passkey 的 updatedAt 时间戳

## 推荐的名称命名规范
- **平台内置认证器**：设备名 + 认证方式
  - ✅ 好：MacBook Pro Touch ID、iPhone 13 Face ID
  - ❌ 差：我的电脑、手机
  
- **USB 安全密钥**：品牌 + 型号
  - ✅ 好：YubiKey 5C、Feitian ePass
  - ❌ 差：USB 密钥、蓝色的那个
  
- **多个同类设备**：添加编号或位置
  - ✅ 好：工作 MacBook、家用 iPhone、备用 YubiKey
  - ❌ 差：MacBook 1、MacBook 2

## 安全考虑
- 只能重命名属于当前登录用户的 Passkey
- 使用 AccessToken 验证用户身份
- 重命名操作不影响 Passkey 的安全性
- 名称仅用于前端显示，不参与认证流程

## 相关接口
- [获取 Passkey 列表](passkey-list.md)
- [注册 Passkey](passkey-registration-options.md)
- [删除 Passkey](passkey-delete.md)
