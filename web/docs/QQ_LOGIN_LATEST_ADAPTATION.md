# QQ OAuth 登录最新适配说明

## 更新日期

2026年2月10日

## 主要变更

根据后端最新接口文档 `/Users/ksuserkqy/work/api/docs/oauth-qq.md`，完成了前端适配。

### 1. HTTP 状态码明确化

后端根据不同场景返回不同的 HTTP 状态码：

| HTTP 状态码 | 场景                       | 响应体                                                     |
| ----------- | -------------------------- | ---------------------------------------------------------- |
| **200**     | 直接登录成功（已绑定用户） | `{ code: 200, data: { accessToken, user } }`               |
| **201**     | 需要 MFA 验证              | `{ code: 201, data: { challengeId, method } }`             |
| **202**     | 需要绑定账号（未绑定用户） | `{ code: 202, data: { needBind: true, openid, message } }` |

### 2. 响应拦截器更新 ([src/utils/request.ts](src/utils/request.ts))

```typescript
// 支持 202 状态码处理
if (code === 200 || code === 201 || code === 202) {
  return response.data as any
}
```

新增对 HTTP 202 状态码的支持，使未绑定用户的响应能被正常处理而不被拦截器拒绝。

### 3. QQ 回调响应类型更新 ([src/api/auth.ts](src/api/auth.ts#L815-L830))

```typescript
export interface QQCallbackResponse {
  // 已绑定用户 (HTTP 200)
  accessToken?: string
  user?: { id: string; username: string; email: string; [key: string]: any }

  // 未绑定用户 (HTTP 202)
  needBind?: boolean
  openid?: string
  message?: string // 新增：后端返回的提示信息

  // MFA 场景 (HTTP 201)
  challengeId?: string
  method?: string
}
```

### 4. 回调页面处理逻辑更新 ([src/views/OAuthQQCallbackView.vue](src/views/OAuthQQCallbackView.vue#L85-L130))

#### 场景 1：未绑定账号 (HTTP 202)

```typescript
if (response.needBind === true) {
  // 使用后端返回的 message 替代硬编码提示
  const warningMsg = response.message || '该 QQ 账号尚未绑定，请先绑定或注册账号'
  ElMessage.warning(warningMsg)

  // 保存 openid 供后续绑定使用
  if (response.openid) {
    sessionStorage.setItem('qq_openid_pending', response.openid)
  }

  // 跳转到登录页面
  setTimeout(() => {
    router.push('/login')
  }, 1500)
}
```

#### 场景 2：需要 MFA 验证 (HTTP 201)

```typescript
if (response.challengeId) {
  // 跳转到敏感操作验证页面
  router.push({
    path: '/sensitive-verification',
    query: {
      challengeId: response.challengeId,
      method: response.method || 'totp',
    },
  })
}
```

#### 场景 3：直接登录成功 (HTTP 200)

```typescript
if (response.accessToken) {
  // 保存 token 和用户信息
  sessionStorage.setItem('accessToken', response.accessToken)
  if (response.user) {
    sessionStorage.setItem('user', JSON.stringify(response.user))
  }

  // 跳转到首页
  setTimeout(() => {
    router.push('/home')
  }, 1000)
}
```

## 实现细节

### State 参数验证

- 生成随机字符串 + `;` + `VITE_DEBUG_STATE`
- 存储到 `sessionStorage` 中
- 回调时严格校验返回的 state 与本地存储是否一致
- 无论成功或失败都会清理 `sessionStorage` 中的 `qq_oauth_state`

### 未绑定账号处理流程

1. 用户点击 QQ 登录 → 跳转 QQ 授权页
2. QQ 返回 `code` → 前端回调页接收 `code` 和 `state`
3. 校验 state → 调用 `POST /oauth/qq/callback`
4. 后端返回 HTTP 202 + `needBind: true` → 前端提示用户
5. 保存 `openid` 到 sessionStorage（key: `qq_openid_pending`）
6. 跳转到登录页面
7. 用户可以：
   - 选择"绑定已有账号"：输入账号密码，验证通过后调用绑定接口
   - 选择"注册新账号"：完成注册后调用绑定接口，将 `openid` 写入 `user_oauth_accounts`

### MFA 验证流程

1. 后端返回 HTTP 201 + `challengeId` + `method: 'totp'`
2. 前端跳转到 `/sensitive-verification` 页面
3. 用户完成 TOTP 验证
4. 验证成功后获取 `accessToken` 并登录

## 测试验证

✅ 构建成功，无 TypeScript 编译错误  
✅ 响应拦截器支持 HTTP 200/201/202 三种状态码  
✅ 三种回调场景处理完善  
✅ 错误提示使用后端返回的 message  
✅ State 参数校验完整

## 相关文件修改

- [src/utils/request.ts](src/utils/request.ts) - 响应拦截器支持 HTTP 202
- [src/api/auth.ts](src/api/auth.ts#L815-L830) - 更新 QQCallbackResponse 类型
- [src/views/OAuthQQCallbackView.vue](src/views/OAuthQQCallbackView.vue#L85-L130) - 完善回调处理逻辑

## 后续可扩展

1. **绑定页面**：创建专门的 BindQQAccountView 组件
   - 展示未绑定 QQ 账号的 openid
   - 提供"绑定已有账号"和"注册新账号"两个选项
   - 调用绑定接口完成关联

2. **解绑功能**：实现 `POST /oauth/qq/unbind` 接口调用
   - 检查是否需要敏感操作验证
   - 检查是否为最后登录方式
   - 完成解绑操作

3. **其他 OAuth 提供商**：使用相同模式支持微信、GitHub 等

## 常见问题

**Q: 为什么使用 HTTP 202 而不是 200？**  
A: HTTP 202 表示"已接受但需要进一步处理"，比 200 更准确地反映了"未绑定需要进一步操作"的语义。

**Q: openid 存储在哪里？**  
A: 未绑定账号时，`openid` 临时存储在 `sessionStorage` 中（key: `qq_openid_pending`），供后续绑定或注册使用。页面刷新后会丢失，这是安全设计的一部分。

**Q: 如何处理绑定 openid？**  
A: 后端需要提供绑定接口，前端在用户登录或注册完成后调用此接口，将 `openid` 写入 `user_oauth_accounts` 表。
