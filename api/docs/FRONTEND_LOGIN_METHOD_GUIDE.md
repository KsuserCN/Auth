# 前端登录方式识别与展示指南

## 概述

本文档说明如何在前端正确识别和展示所有可能的登录方式（`login_method`），特别是包含MFA二步验证的登录流程。

## 所有登录方式

### 1. 无MFA的登录方式

| login_method | 说明 | 推荐展示文案 | 图标建议 |
|--------------|------|--------------|----------|
| `PASSWORD` | 密码登录 | 密码登录 | 🔑 |
| `EMAIL_CODE` | 邮箱验证码登录 | 验证码登录 | 📧 |
| `PASSKEY` | Passkey登录 | Passkey登录 | 🔐 |

### 2. 含MFA的登录方式

| login_method | 说明 | 推荐展示文案 | 图标建议 |
|--------------|------|--------------|----------|
| `["password", "mfa"]` | 密码+TOTP二步验证 | 密码+二步验证 | 🔑🛡️ |
| `["email", "mfa"]` | 验证码+TOTP二步验证 | 验证码+二步验证 | 📧🛡️ |
| `["passkey", "mfa"]` | Passkey+TOTP二步验证 | Passkey+二步验证 | 🔐🛡️ |

## MFA登录的双记录特性

### 重要说明

当用户使用MFA登录时，会产生**两条日志记录**：

1. **第一条记录** - 第一因素验证通过
   - `login_method`: `PASSWORD_MFA` / `EMAIL_CODE_MFA` / `PASSKEY_MFA`
   - `result`: `SUCCESS`
   - 含义：密码/验证码/Passkey验证通过，等待TOTP验证

2. **第二条记录** - TOTP验证结果
   - `login_method`: 同上（相同的 `_MFA` 方式）
   - `result`: `SUCCESS` (验证通过) 或 `FAILURE` (验证失败)
   - 含义：TOTP二步验证的最终结果

### 示例数据

```json
{
  "data": [
    {
      "id": 125,
      "operationType": "LOGIN",
      "loginMethod": "PASSWORD_MFA",
      "result": "SUCCESS",
      "failureReason": null,
      "createdAt": "2026-02-09T10:00:05",
      "ipAddress": "192.168.1.100",
      "riskScore": 0
    },
    {
      "id": 124,
      "operationType": "LOGIN",
      "loginMethod": "PASSWORD_MFA",
      "result": "SUCCESS",
      "failureReason": null,
      "createdAt": "2026-02-09T10:00:00",
      "ipAddress": "192.168.1.100",
      "riskScore": 5
    }
  ]
}
```

**时间线解读：**
- `10:00:00` - 用户输入密码，验证通过，进入MFA流程
- `10:00:05` - 用户输入TOTP码，验证通过，登录成功

## 前端实现代码示例

### 1. 登录方式文案映射

```javascript
// 登录方式展示文案
export const LOGIN_METHOD_TEXT = {
  'PASSWORD': '密码登录',
  'PASSWORD_MFA': '密码 + 二步验证',
  'EMAIL_CODE': '验证码登录',
  'EMAIL_CODE_MFA': '验证码 + 二步验证',
  'PASSKEY': 'Passkey登录',
  'PASSKEY_MFA': 'Passkey + 二步验证'
};

// 获取登录方式显示文案
export function getLoginMethodText(loginMethod) {
  return LOGIN_METHOD_TEXT[loginMethod] || '未知登录方式';
}
```

### 2. 登录方式图标映射

```javascript
// 图标映射（使用emoji或图标库）
export const LOGIN_METHOD_ICON = {
  'PASSWORD': '🔑',
  'PASSWORD_MFA': '🔑🛡️',
  'EMAIL_CODE': '📧',
  'EMAIL_CODE_MFA': '📧🛡️',
  'PASSKEY': '🔐',
  'PASSKEY_MFA': '🔐🛡️'
};

// 如果使用IconFont/FontAwesome
export const LOGIN_METHOD_ICON_CLASS = {
  'PASSWORD': 'icon-password',
  'PASSWORD_MFA': 'icon-password-shield',
  'EMAIL_CODE': 'icon-email',
  'EMAIL_CODE_MFA': 'icon-email-shield',
  'PASSKEY': 'icon-passkey',
  'PASSKEY_MFA': 'icon-passkey-shield'
};
```

### 3. 判断是否为MFA登录

```javascript
// 判断是否为MFA登录方式
export function isMFALogin(loginMethod) {
  return loginMethod && loginMethod.endsWith('_MFA');
}

// 获取基础登录方式（去除MFA后缀）
export function getBaseLoginMethod(loginMethod) {
  return loginMethod ? loginMethod.replace('_MFA', '') : '';
}
```

### 4. 登录日志展示组件示例（Vue 3）

```vue
<template>
  <div class="login-log-item">
    <div class="log-icon">
      {{ getLoginIcon(log.loginMethod) }}
    </div>
    <div class="log-content">
      <div class="log-method">
        {{ getLoginMethodText(log.loginMethod) }}
        <span v-if="isMFALogin(log.loginMethod)" class="mfa-badge">
          MFA
        </span>
      </div>
      <div class="log-details">
        <span class="log-time">{{ formatTime(log.createdAt) }}</span>
        <span class="log-ip">{{ log.ipAddress }}</span>
        <span class="log-location">{{ log.ipLocation }}</span>
      </div>
      <div class="log-status">
        <span :class="['status-badge', log.result.toLowerCase()]">
          {{ log.result === 'SUCCESS' ? '成功' : '失败' }}
        </span>
        <span v-if="log.failureReason" class="failure-reason">
          {{ log.failureReason }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { LOGIN_METHOD_TEXT, LOGIN_METHOD_ICON } from '@/utils/loginMethod';

const props = defineProps({
  log: Object
});

function getLoginMethodText(method) {
  return LOGIN_METHOD_TEXT[method] || method;
}

function getLoginIcon(method) {
  return LOGIN_METHOD_ICON[method] || '🔒';
}

function isMFALogin(method) {
  return method && method.endsWith('_MFA');
}

function formatTime(datetime) {
  return new Date(datetime).toLocaleString('zh-CN');
}
</script>
```

### 5. React 组件示例

```jsx
import React from 'react';

const LOGIN_METHOD_TEXT = {
  'PASSWORD': '密码登录',
  'PASSWORD_MFA': '密码 + 二步验证',
  'EMAIL_CODE': '验证码登录',
  'EMAIL_CODE_MFA': '验证码 + 二步验证',
  'PASSKEY': 'Passkey登录',
  'PASSKEY_MFA': 'Passkey + 二步验证'
};

const LoginLogItem = ({ log }) => {
  const isMFA = log.loginMethod?.endsWith('_MFA');
  
  return (
    <div className="login-log-item">
      <div className="log-method">
        {LOGIN_METHOD_TEXT[log.loginMethod] || log.loginMethod}
        {isMFA && <span className="mfa-badge">MFA</span>}
      </div>
      <div className="log-info">
        <span>{new Date(log.createdAt).toLocaleString('zh-CN')}</span>
        <span>{log.ipAddress}</span>
        <span>{log.ipLocation}</span>
      </div>
      <div className={`log-status ${log.result.toLowerCase()}`}>
        {log.result === 'SUCCESS' ? '✓ 成功' : '✗ 失败'}
        {log.failureReason && <span>: {log.failureReason}</span>}
      </div>
    </div>
  );
};

export default LoginLogItem;
```

## 处理MFA双记录的建议

### 方案1：分别展示两条记录（推荐）

**优点：** 完整展示登录流程，用户可以看到每个验证阶段的时间和IP

```javascript
// 不做特殊处理，直接展示所有记录
logs.forEach(log => {
  if (log.loginMethod.endsWith('_MFA')) {
    // 标记为MFA登录，添加特殊样式
    log.isMFA = true;
  }
});
```

### 方案2：合并相邻的MFA记录

**优点：** 界面更简洁，避免重复

```javascript
function mergeMFALogs(logs) {
  const merged = [];
  let i = 0;
  
  while (i < logs.length) {
    const current = logs[i];
    
    // 检查是否是MFA登录的第一条记录
    if (current.loginMethod?.endsWith('_MFA') && i + 1 < logs.length) {
      const next = logs[i + 1];
      
      // 如果下一条也是相同的MFA登录，且时间相近（5秒内）
      if (next.loginMethod === current.loginMethod &&
          Math.abs(new Date(current.createdAt) - new Date(next.createdAt)) < 5000) {
        // 合并为一条记录
        merged.push({
          ...current,
          isMerged: true,
          mfaResult: current.result, // TOTP验证结果
          firstFactorTime: next.createdAt, // 第一因素验证时间
          totpVerifyTime: current.createdAt // TOTP验证时间
        });
        i += 2; // 跳过下一条
        continue;
      }
    }
    
    merged.push(current);
    i++;
  }
  
  return merged;
}
```

### 方案3：仅展示最终结果

**优点：** 最简洁

```javascript
function filterMFALogs(logs) {
  const filtered = [];
  const mfaMap = new Map();
  
  logs.forEach(log => {
    if (log.loginMethod?.endsWith('_MFA')) {
      const key = `${log.loginMethod}_${log.createdAt.substring(0, 16)}`; // 按分钟分组
      
      // 如果这分钟内已有该MFA记录，只保留最新的
      if (!mfaMap.has(key) || new Date(log.createdAt) > new Date(mfaMap.get(key).createdAt)) {
        mfaMap.set(key, log);
      }
    } else {
      filtered.push(log);
    }
  });
  
  // 将MFA记录加回
  mfaMap.forEach(log => filtered.push(log));
  
  // 重新按时间排序
  return filtered.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
}
```

## 样式建议

```css
/* MFA标记徽章 */
.mfa-badge {
  display: inline-block;
  padding: 2px 6px;
  margin-left: 8px;
  font-size: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 3px;
  font-weight: 600;
}

/* 登录状态 */
.status-badge.success {
  color: #10b981;
}

.status-badge.failure {
  color: #ef4444;
}

/* 登录图标 */
.log-icon {
  font-size: 24px;
  margin-right: 12px;
}
```

## 注意事项

1. **时间相近性检查**：MFA的两条记录通常在几秒内产生，可以用时间差判断是否为一次登录
2. **IP一致性**：同一次MFA登录的两条记录IP地址应该相同
3. **失败情况**：如果第一因素通过但TOTP失败，第二条记录的 `result` 会是 `FAILURE`
4. **向后兼容**：确保代码能处理不存在 `_MFA` 后缀的旧数据
5. **国际化**：登录方式文案应支持多语言

## 总结

- 所有登录方式都有对应的展示文案和图标
- MFA登录会产生两条记录，前端可选择合并或分别展示
- 使用 `endsWith('_MFA')` 判断是否为MFA登录
- 提供清晰的视觉标识（徽章、图标）区分MFA登录
