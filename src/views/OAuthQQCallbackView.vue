<template>
  <div class="oauth-callback-page">
    <div class="glow glow-left" />
    <div class="glow glow-right" />

    <div class="callback-card">
      <div class="brand-line">
        <img src="/favicon.ico" alt="Ksuser" class="brand-logo" />
        <span class="brand-text">Ksuser 统一认证中心</span>
      </div>

      <div class="title-area">
        <h1 class="title">QQ 登录授权结果</h1>
        <p class="subtitle">正在完成身份校验与安全检查，请稍候。</p>
      </div>

      <div class="state-shell" :class="`state-${state}`">
        <div v-if="state === 'processing'" class="state-block processing">
          <div class="icon-ring ring-loading">
            <el-icon class="state-icon loading-icon">
              <Loading />
            </el-icon>
          </div>
          <p class="state-title">正在验证身份信息</p>
          <p class="state-description">系统正在确认授权参数并安全登录您的账号。</p>
        </div>

        <div v-else-if="state === 'success'" class="state-block success">
          <div class="icon-ring ring-success">
            <el-icon class="state-icon success-icon">
              <CircleCheckFilled />
            </el-icon>
          </div>
          <p class="state-title">登录成功</p>
          <p class="state-description">验证通过，正在为您跳转到首页...</p>
        </div>

        <div v-else-if="state === 'error'" class="state-block error">
          <div class="icon-ring ring-error">
            <el-icon class="state-icon error-icon">
              <CircleCloseFilled />
            </el-icon>
          </div>
          <p class="state-title">登录失败</p>
          <p class="state-description">{{ errorMessage }}</p>
          <div class="action-row">
            <el-button class="ghost-btn" @click="retryCallback">
              <el-icon>
                <RefreshRight />
              </el-icon>
              重试
            </el-button>
            <el-button type="primary" class="back-btn" @click="goBackToLogin">
              <el-icon>
                <ArrowLeft />
              </el-icon>
              返回登录
            </el-button>
          </div>
        </div>
      </div>

      <div class="tip-line">
        登录异常时请确认浏览器未禁用 Cookie，且授权流程未在多个窗口同时进行。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useDark, useStorage } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  CircleCheckFilled,
  CircleCloseFilled,
  Loading,
  RefreshRight,
} from '@element-plus/icons-vue'
import { handleQQCallback } from '@/api/auth'

const router = useRouter()

const state = ref<'processing' | 'success' | 'error'>('processing')
const errorMessage = ref<string>('')
const themeMode = useStorage<'light' | 'dark' | 'system'>('theme-mode', 'system')
const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})

interface ParsedQQState {
  verifyToken: string
  operation: 'login' | 'bind' | 'unbind'
  env: 'dev' | 'prd'
}

const parseQQState = (rawState: string): ParsedQQState => {
  const parts = rawState.split(';')

  if (parts.length !== 3) {
    throw new Error('状态参数格式错误，请重新登录')
  }

  const verifyToken = parts[0]?.trim()
  const operation = parts[1]?.trim()
  const env = parts[2]?.trim()

  if (!verifyToken) {
    throw new Error('状态参数校验值缺失，请重新登录')
  }

  if (operation !== 'login' && operation !== 'bind' && operation !== 'unbind') {
    throw new Error('状态参数操作类型无效，请重新登录')
  }

  if (env !== 'dev' && env !== 'prd') {
    throw new Error('状态参数环境值无效，请重新登录')
  }

  return {
    verifyToken,
    operation,
    env,
  }
}

const getRedirectUri = (): string => {
  return 'https://auth.ksuser.cn/oauth/qq/callback'
}

const handleCallback = async () => {
  try {
    // 从 URL 中读取 code 和 state
    const params = new URLSearchParams(window.location.search)
    const code = params.get('code')
    const returnedState = params.get('state')

    // 检查是否获取到 code
    if (!code) {
      throw new Error('未获取到授权码，请重试')
    }

    // 检查是否获取到 state
    if (!returnedState) {
      throw new Error('未获取到状态参数，请重试')
    }

    const returnedParsedState = parseQQState(returnedState)

    // 开发环境回调先跳转到本地地址，由本地页面继续处理回调
    if (returnedParsedState.env === 'dev' && window.location.origin !== 'http://localhost:5173') {
      window.location.replace(`http://localhost:5173/oauth/qq/callback${window.location.search}`)
      return
    }

    // 从 sessionStorage 中读取本地存储的 state
    const storedState = sessionStorage.getItem('qq_oauth_state')

    // 校验 state 是否一致
    if (!storedState) {
      throw new Error('本地状态信息丢失，请重新登录')
    }

    const storedParsedState = parseQQState(storedState)

    if (storedParsedState.verifyToken !== returnedParsedState.verifyToken) {
      throw new Error('状态验证失败，可能存在安全风险，请重新登录')
    }

    if (storedParsedState.operation !== returnedParsedState.operation) {
      throw new Error('状态操作类型不匹配，请重新发起操作')
    }

    // 获取 redirect_uri
    const redirectUri = getRedirectUri()

    // 调用后端接口处理 OAuth 回调
    const response = await handleQQCallback({
      code: code,
      redirectUri: redirectUri,
      state: returnedState,
    })

    // 清理 OAuth state（无论什么情况都清理）
    sessionStorage.removeItem('qq_oauth_state')

    // 情况 1: 需要绑定账号 (HTTP 202，needBind=true)
    if (response.needBind === true) {
      const warningMsg = response.message || '该 QQ 账号尚未绑定，请先绑定或注册账号'
      ElMessage.warning(warningMsg)
      // 保存 openid 供后续绑定使用
      if (response.openid) {
        sessionStorage.setItem('qq_openid_pending', response.openid)
      }
      // 跳转到登录页面（后续可扩展为专门的绑定页面）
      setTimeout(() => {
        router.push('/login')
      }, 1500)
      return
    }

    // 情况 2: 需要 MFA 验证 (HTTP 201，challengeId 存在)
    if (response.challengeId) {
      ElMessage.info('需要进行二步验证')
      // 跳转到 MFA 验证页面
      router.push({
        path: '/sensitive-verification',
        query: {
          challengeId: response.challengeId,
          method: response.method || 'totp',
        },
      })
      return
    }

    // 情况 3: 直接登录成功 (HTTP 200，accessToken 存在)
    if (response.accessToken) {
      // 存储 Access Token 到 sessionStorage
      sessionStorage.setItem('accessToken', response.accessToken)

      // 如果返回了用户信息，也一并存储
      if (response.user) {
        sessionStorage.setItem('user', JSON.stringify(response.user))
      }

      // 显示成功消息
      state.value = 'success'
      ElMessage.success('QQ 登录成功')

      // 延迟后跳转到首页
      setTimeout(() => {
        router.push('/home')
      }, 1000)
      return
    }

    // 如果以上情况都不满足，说明返回数据异常
    throw new Error('登录响应数据异常，请重试')
  } catch (error: unknown) {
    state.value = 'error'

    if (error instanceof Error) {
      errorMessage.value = error.message
      console.error('OAuth QQ callback failed:', error)
    } else {
      errorMessage.value = '登录失败，请重试'
      console.error('OAuth QQ callback failed:', error)
    }

    ElMessage.error(errorMessage.value)
  }
}

const goBackToLogin = () => {
  // 清理 OAuth state
  sessionStorage.removeItem('qq_oauth_state')
  router.push('/login')
}

const retryCallback = () => {
  state.value = 'processing'
  errorMessage.value = ''
  handleCallback()
}

watch(
  themeMode,
  (mode) => {
    if (mode === 'system') {
      isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
    } else {
      isDark.value = mode === 'dark'
    }
  },
  { immediate: true }
)

onMounted(() => {
  handleCallback()
})
</script>

<style scoped>
.oauth-callback-page {
  --cb-bg-start: var(--el-bg-color-page);
  --cb-bg-end: var(--el-fill-color-extra-light);
  --cb-card-bg: color-mix(in srgb, var(--el-bg-color) 92%, #ffffff 8%);
  --cb-border: var(--el-border-color-light);
  --cb-shadow: 0 20px 44px rgba(20, 24, 36, 0.1);
  --cb-text-main: var(--el-text-color-primary);
  --cb-text-sub: var(--el-text-color-secondary);
  --cb-highlight: var(--el-color-primary);

  position: relative;
  width: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 12% 18%, rgba(255, 185, 15, 0.2) 0%, transparent 45%),
    radial-gradient(circle at 88% 82%, rgba(255, 185, 15, 0.15) 0%, transparent 42%),
    linear-gradient(135deg, var(--cb-bg-start) 0%, var(--cb-bg-end) 100%);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  overflow: hidden;
}

.glow {
  position: absolute;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  filter: blur(50px);
  background: rgba(255, 185, 15, 0.22);
  pointer-events: none;
}

.glow-left {
  left: -80px;
  top: -90px;
}

.glow-right {
  right: -90px;
  bottom: -110px;
}

.callback-card {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  background: var(--cb-card-bg);
  border: 1px solid var(--cb-border);
  border-radius: 24px;
  padding: 28px;
  box-shadow: var(--cb-shadow);
  max-width: 520px;
  width: 100%;
  backdrop-filter: blur(10px);
}

.brand-line {
  display: flex;
  align-items: center;
  gap: 10px;
  align-self: flex-start;
  color: var(--cb-text-sub);
  font-size: 13px;
}

.brand-logo {
  width: 24px;
  height: 24px;
  border-radius: 7px;
}

.brand-text {
  letter-spacing: 0.2px;
}

.title-area {
  width: 100%;
}

.title {
  margin: 0;
  font-size: 26px;
  line-height: 1.25;
  color: var(--cb-text-main);
}

.subtitle {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--cb-text-sub);
}

.state-shell {
  width: 100%;
  border-radius: 18px;
  border: 1px solid var(--cb-border);
  background: color-mix(in srgb, var(--el-bg-color) 88%, var(--el-fill-color-light) 12%);
  padding: 28px 22px;
}

.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}

.icon-ring {
  width: 82px;
  height: 82px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  border: 1px solid transparent;
}

.ring-loading {
  border-color: color-mix(in srgb, var(--cb-highlight) 26%, transparent 74%);
  background: color-mix(in srgb, var(--cb-highlight) 12%, transparent 88%);
}

.ring-success {
  border-color: color-mix(in srgb, #67c23a 25%, transparent 75%);
  background: color-mix(in srgb, #67c23a 12%, transparent 88%);
}

.ring-error {
  border-color: color-mix(in srgb, #f56c6c 25%, transparent 75%);
  background: color-mix(in srgb, #f56c6c 12%, transparent 88%);
}

.state-icon {
  font-size: 44px;
}

.loading-icon {
  color: var(--cb-highlight);
  animation: rotate 1s linear infinite;
}

.success-icon {
  color: #67c23a;
}

.error-icon {
  color: #f56c6c;
}

.state-title {
  margin: 4px 0 0;
  font-size: 21px;
  font-weight: 700;
  color: var(--cb-text-main);
}

.state-description {
  margin: 0;
  max-width: 360px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--cb-text-sub);
}

.action-row {
  margin-top: 8px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.ghost-btn {
  border-radius: 10px;
  border: 1px solid var(--cb-border);
  background: color-mix(in srgb, var(--el-bg-color) 88%, transparent 12%);
}

.back-btn {
  border-radius: 10px;
  font-weight: 600;
}

.tip-line {
  width: 100%;
  padding-top: 2px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--cb-text-sub);
}

:global(html.dark) .oauth-callback-page {
  --cb-bg-end: #0f1116;
  --cb-card-bg: color-mix(in srgb, var(--el-bg-color-overlay) 90%, #11141d 10%);
  --cb-border: color-mix(in srgb, var(--el-border-color) 76%, transparent 24%);
  --cb-shadow: 0 24px 46px rgba(0, 0, 0, 0.42);
}

:global(html.dark) .glow {
  background: rgba(255, 185, 15, 0.16);
}

@media (max-width: 640px) {
  .oauth-callback-page {
    padding: 16px;
  }

  .callback-card {
    border-radius: 20px;
    padding: 20px;
    gap: 16px;
  }

  .title {
    font-size: 22px;
  }

  .state-shell {
    padding: 22px 16px;
  }

  .state-title {
    font-size: 19px;
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
