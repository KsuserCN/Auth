# 检查敏感操作验证状态接口

## 基本信息
- 方法：GET
- 路径：/auth/check-sensitive-verification
- 需要认证：是（使用 AccessToken）
- 请求类型：无请求体

## 用途
此接口用于检查当前用户是否已完成敏感操作验证，以及验证的剩余有效时间。主要用于前端判断是否需要显示验证对话框，不影响后端的实际验证逻辑。

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求示例
```bash
curl -X GET \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/auth/check-sensitive-verification
```

## 成功响应
- HTTP Status：200

### 已验证状态
```json
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "verified": true,
    "remainingSeconds": 720
  }
}
```

### 未验证或已过期状态
```json
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "verified": false,
    "remainingSeconds": 0
  }
}
```

## 响应字段说明
- verified: 是否已验证（boolean）
  - true: 已验证且在有效期内
  - false: 未验证或已过期
- remainingSeconds: 剩余有效时间（秒）
  - 大于 0: 验证仍然有效，显示剩余秒数
  - 0: 未验证或已过期

## 失败响应

### 1) 未登录
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录"
}
```

### 2) 用户不存在
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "用户不存在"
}
```

## 验证规则说明

### 验证有效性检查
此接口检查两个条件：
1. **时间有效性**：验证是否在 15 分钟有效期内
2. **设备一致性**：当前设备（IP）是否与验证时的设备相同

只有同时满足这两个条件，`verified` 才为 `true`。

### 跨设备检查
如果用户在设备 A 完成验证，然后从设备 B 查询状态：
```json
{
  "verified": false,
  "remainingSeconds": 0
}
```
即使验证未过期，由于设备不同，仍返回 `false`。

## 使用场景

### 场景1：优化用户体验
在用户访问敏感操作页面时，先调用此接口检查验证状态：
```javascript
// 检查验证状态
const response = await fetch('/auth/check-sensitive-verification', {
  headers: { 'Authorization': `Bearer ${accessToken}` }
});
const { verified, remainingSeconds } = response.data;

if (verified) {
  // 直接显示敏感操作表单
  showSensitiveOperationForm();
  // 显示倒计时提醒
  showCountdown(remainingSeconds);
} else {
  // 先显示验证对话框
  showVerificationDialog();
}
```

### 场景2：显示验证状态提示
在界面上实时显示验证状态：
```javascript
if (verified && remainingSeconds > 0) {
  const minutes = Math.floor(remainingSeconds / 60);
  const seconds = remainingSeconds % 60;
  showMessage(`身份验证有效，剩余 ${minutes}分${seconds}秒`);
}
```

### 场景3：自动重新验证
当验证即将过期时提醒用户：
```javascript
if (verified && remainingSeconds < 60) {
  showWarning('验证即将过期，请尽快完成操作');
}
```

## 前端实现建议

### 推荐的使用流程
```
1. 用户进入敏感操作页面
2. 调用此接口检查验证状态
3. 根据返回结果：
   - verified=true: 直接显示操作表单，显示倒计时
   - verified=false: 显示验证对话框
4. 用户完成验证后，刷新状态
5. 显示操作表单
```

### React 示例
```javascript
import { useState, useEffect } from 'react';

function SensitiveOperationPage() {
  const [verificationStatus, setVerificationStatus] = useState({
    verified: false,
    remainingSeconds: 0
  });

  useEffect(() => {
    checkVerificationStatus();
  }, []);

  const checkVerificationStatus = async () => {
    const response = await fetch('/auth/check-sensitive-verification', {
      headers: { 'Authorization': `Bearer ${accessToken}` }
    });
    const result = await response.json();
    setVerificationStatus(result.data);
  };

  if (!verificationStatus.verified) {
    return <VerificationDialog onSuccess={checkVerificationStatus} />;
  }

  return (
    <div>
      <Countdown seconds={verificationStatus.remainingSeconds} />
      <SensitiveOperationForm />
    </div>
  );
}
```

### Vue 示例
```vue
<template>
  <div>
    <VerificationDialog 
      v-if="!verified" 
      @success="checkStatus" 
    />
    <div v-else>
      <p>验证有效，剩余 {{ formatTime(remainingSeconds) }}</p>
      <SensitiveOperationForm />
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      verified: false,
      remainingSeconds: 0
    };
  },
  mounted() {
    this.checkStatus();
  },
  methods: {
    async checkStatus() {
      const response = await this.$http.get('/auth/check-sensitive-verification');
      this.verified = response.data.verified;
      this.remainingSeconds = response.data.remainingSeconds;
    },
    formatTime(seconds) {
      const min = Math.floor(seconds / 60);
      const sec = seconds % 60;
      return `${min}分${sec}秒`;
    }
  }
};
</script>
```

## 注意事项

### 1. 仅供前端参考
- 此接口返回的状态**仅供前端 UI 展示使用**
- 后端执行敏感操作时仍会进行完整的验证检查
- 不要依赖此接口的返回值作为安全判断依据

### 2. 设备绑定
- 验证状态与设备（IP）绑定
- 跨设备访问时，即使验证未过期也会返回 `false`
- 用户需要在每个设备上分别进行验证

### 3. 实时性
- 建议在关键操作前重新查询状态
- 可以实现定时刷新（如每 30 秒）
- 当剩余时间小于 60 秒时提醒用户

### 4. 错误处理
- 如果返回 401，说明 AccessToken 无效或过期
- 需要引导用户重新登录
- 登录后重新执行验证流程

## 性能优化建议

### 缓存策略
```javascript
// 使用本地缓存减少请求
let cachedStatus = null;
let lastCheckTime = 0;
const CACHE_DURATION = 10000; // 10秒缓存

async function getVerificationStatus() {
  const now = Date.now();
  if (cachedStatus && (now - lastCheckTime) < CACHE_DURATION) {
    return cachedStatus;
  }
  
  const response = await fetch('/auth/check-sensitive-verification');
  cachedStatus = response.data;
  lastCheckTime = now;
  return cachedStatus;
}
```

### 避免频繁请求
- 不要在每次页面渲染时都调用
- 使用事件驱动的检查（如用户点击按钮时）
- 合理设置刷新间隔

## 测试用例

### 测试1：未验证状态
```bash
curl -X GET \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/auth/check-sensitive-verification

# 预期返回：
# {"code":200,"msg":"查询成功","data":{"verified":false,"remainingSeconds":0}}
```

### 测试2：已验证状态
```bash
# 先进行验证
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"password","password":"mypass"}' \
  http://localhost:8000/auth/verify-sensitive

# 然后检查状态
curl -X GET \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/auth/check-sensitive-verification

# 预期返回：
# {"code":200,"msg":"查询成功","data":{"verified":true,"remainingSeconds":900}}
# (900秒 = 15分钟)
```

### 测试3：跨设备检查
```bash
# 在设备A进行验证（IP: 192.168.1.100）
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"password","password":"mypass"}' \
  http://localhost:8000/auth/verify-sensitive

# 在设备B检查状态（IP: 192.168.1.101）
curl -X GET \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/auth/check-sensitive-verification

# 预期返回：
# {"code":200,"msg":"查询成功","data":{"verified":false,"remainingSeconds":0}}
# (设备不同，返回未验证)
```

## 相关文档
- [敏感操作验证接口](auth-verify-sensitive.md)
- [更改邮箱接口](auth-change-email.md)

## 安全说明
- 此接口不会泄露敏感信息
- 仅返回验证状态和剩余时间
- 实际的敏感操作仍需要后端完整验证
- AccessToken 必须有效且未过期
