<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <aside class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Ksuser" class="logo-icon" />
        </div>
        <h1 class="login-title">创建账户</h1>
        <p class="login-description">使用 Apple 身份开始您的安全之旅</p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20"><Lock /></el-icon>
            <span>安全保护</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20"><Lightning /></el-icon>
            <span>快速开始</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20"><Key /></el-icon>
            <span>无需设置密码</span>
          </div>
        </div>
      </aside>

      <main class="login-right">
        <div class="step-container">
          <div class="provider-label">
            <i class="fa-brands fa-apple" aria-hidden="true"></i>
            <span>{{ ticket ? 'Apple 身份已验证' : '使用 Apple 注册' }}</span>
          </div>
          <h2 class="step-title">选择用户名</h2>
          <p class="step-subtitle">留空则自动生成，也可以输入您喜欢的用户名。</p>

          <el-alert
            v-if="!ticket"
            title="Apple 验证已过期，请返回注册页重新授权"
            type="warning"
            :closable="false"
            class="expired-alert"
          />

          <el-form label-position="top" @submit.prevent="submit">
            <el-form-item label="用户名（可选）">
              <el-input
                v-model="username"
                maxlength="20"
                placeholder="3-20 个字符，留空则自动生成"
                autocomplete="username"
                :disabled="!ticket"
              />
            </el-form-item>
            <p class="field-hint">支持中文、字母、数字、下划线和连字符</p>

            <el-checkbox v-model="acceptTerms" class="terms-checkbox" :disabled="!ticket">
              我已阅读并同意服务协议与隐私政策
            </el-checkbox>

            <div class="step-actions">
              <el-button class="back-btn" @click="router.push('/register')">返回</el-button>
              <el-button
                class="next-btn"
                native-type="submit"
                :loading="loading"
                :disabled="!ticket || !acceptTerms"
              >
                创建账户
              </el-button>
            </div>
          </el-form>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useDark } from '@vueuse/core'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lightning, Lock } from '@element-plus/icons-vue'
import { checkUsername, registerPendingApple } from '@/api/auth'
import { finalizeWebLogin } from '@/utils/desktopBridge'
import { consumePostLoginRedirect } from '@/utils/postLoginRedirect'

const router = useRouter()
const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})
const ticket = ref(sessionStorage.getItem('apple_register_ticket') || '')
const username = ref('')
const acceptTerms = ref(false)
const loading = ref(false)

const submit = async () => {
  if (!ticket.value || !acceptTerms.value || loading.value) return
  loading.value = true
  let ticketSubmitted = false
  try {
    const requestedUsername = username.value.trim()
    if (requestedUsername && (!/^[a-zA-Z0-9_\-\u4e00-\u9fa5]{3,20}$/.test(requestedUsername) || !await checkUsername(requestedUsername))) {
      throw new Error('用户名格式无效或已被使用，请修改后重试')
    }
    ticketSubmitted = true
    const result = await registerPendingApple(ticket.value, acceptTerms.value, requestedUsername)
    sessionStorage.removeItem('apple_register_ticket')
    ticket.value = ''
    if ('challengeId' in result) {
      await router.replace({ path: '/login', query: {
        challengeId: result.challengeId,
        method: result.method,
        methods: result.methods?.join(','),
        mfaFrom: 'apple',
      } })
      return
    }
    await finalizeWebLogin({ accessToken: result.accessToken, user: result.user })
    ElMessage.success('Apple 账号创建成功')
    await router.replace(consumePostLoginRedirect() || '/home/overview')
  } catch (error: unknown) {
    if (ticketSubmitted) {
      // The Apple ticket is single use. A failed attempt requires fresh authorization.
      sessionStorage.removeItem('apple_register_ticket')
      ticket.value = ''
      ElMessage.error('注册失败，请返回注册页重新进行 Apple 授权')
    } else {
      ElMessage.error(error instanceof Error ? error.message : '请检查用户名后重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  width: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.login-container.dark {
  background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
}

.login-box {
  width: 100%;
  max-width: 1000px;
  min-height: 560px;
  display: flex;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12), 0 16px 40px rgba(0, 0, 0, 0.08);
  animation: slide-in 0.6s ease-out;
}

.login-left,
.login-right {
  flex: 1 1 50%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-left {
  padding: 60px 48px;
  background: linear-gradient(135deg, #fff8f0, #fffbf5);
  border-right: 1px solid var(--el-border-color-light);
}

.dark .login-left {
  background: var(--el-bg-color-overlay);
}

.login-right {
  padding: 48px;
  background: var(--el-bg-color);
}

.logo-section {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 32px;
}

.logo-icon {
  width: 56px;
  height: 56px;
  display: block;
  border-radius: 14px;
}

.login-title {
  margin: 0 0 12px;
  color: var(--el-text-color-primary);
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.login-description {
  margin: 0 0 32px;
  color: var(--el-text-color-regular);
  font-size: 15px;
  line-height: 1.6;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.feature-icon {
  color: var(--el-color-primary);
  min-width: 24px;
}

.step-container {
  width: 100%;
}

.provider-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.provider-label i {
  color: var(--el-text-color-primary);
  font-size: 17px;
}

.step-title {
  margin: 0 0 8px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.3px;
}

.step-subtitle {
  margin: 0 0 24px;
  color: var(--el-text-color-regular);
  font-size: 16px;
  line-height: 1.6;
}

.expired-alert {
  margin-bottom: 20px;
}

:deep(.el-form-item) {
  margin-bottom: 8px;
}

:deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  padding: 14px 16px;
  background: var(--el-fill-color-light);
  border: 1.5px solid var(--el-border-color);
  border-radius: 12px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-input__wrapper:hover),
:deep(.el-input__wrapper.is-focus) {
  background: var(--el-bg-color);
  border-color: var(--el-color-primary);
}

:deep(.el-input__inner) {
  font-size: 16px;
}

.field-hint {
  margin: 0 0 22px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.terms-checkbox {
  max-width: 100%;
  height: auto;
  white-space: normal;
}

:deep(.terms-checkbox .el-checkbox__label) {
  white-space: normal;
  line-height: 1.5;
}

.step-actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}

.next-btn,
.back-btn {
  min-width: 100px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
}

.next-btn {
  flex: 1;
  background: var(--el-color-primary);
  border: none;
  color: white;
}

.next-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(255, 185, 15, 0.3);
}

.back-btn {
  background: var(--el-bg-color);
  border: 1.5px solid var(--el-border-color);
  color: var(--el-text-color-primary);
}

.back-btn:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

@keyframes slide-in {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 900px) {
  .login-box { flex-direction: column; }
  .login-left { padding: 40px 32px; border-right: 0; border-bottom: 1px solid var(--el-border-color-light); }
  .login-right { padding: 40px 32px; }
  .login-title { font-size: 28px; }
  .step-title { font-size: 24px; }
}

@media (max-width: 600px) {
  .login-container { padding: 20px; align-items: flex-start; }
  .login-box { border-radius: 12px; }
  .login-left, .login-right { padding: 32px 24px; }
  .login-title { font-size: 24px; }
  .step-title { font-size: 20px; }
  .step-actions { flex-direction: column-reverse; }
  .next-btn, .back-btn { width: 100%; }
}
</style>
