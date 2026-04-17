<template>
  <div class="consent-container" :class="{ dark: isDark }">
    <div v-if="loading || autoRedirecting" class="consent-box consent-box--loading">
      <div class="loading-stage">
        <div class="brand-line">
          <img src="/favicon.ico" alt="Ksuser" class="brand-logo" />
          <span class="brand-text">Ksuser 统一认证中心</span>
        </div>

        <div class="loading-card">
          <div v-if="context" class="loading-app">
            <img v-if="context.logoUrl" :src="context.logoUrl" :alt="context.appName" class="loading-app-logo" />
            <div v-else class="loading-app-logo loading-app-logo-fallback">
              {{ context.appName.slice(0, 1).toUpperCase() }}
            </div>
            <div class="loading-app-name">{{ context.appName }}</div>
          </div>

          <el-icon class="loading-spinner" :size="34">
            <Loading />
          </el-icon>
          <div class="loading-title">{{ autoRedirecting ? '已完成授权，正在继续…' : '正在准备授权信息…' }}</div>
          <div class="loading-description">
            {{ autoRedirecting ? '请稍候，页面会自动回到应用。' : '正在校验应用信息与当前登录状态。' }}
          </div>
        </div>
      </div>
    </div>

    <div v-else class="consent-box">
      <!-- 左侧：应用信息与授权内容 -->
      <div class="consent-left">
        <div class="brand-line">
          <img src="/favicon.ico" alt="Ksuser" class="brand-logo" />
          <span class="brand-text">Ksuser 统一认证中心</span>
        </div>

        <div class="left-body">
          <div v-if="context" class="app-header">
            <div class="app-logo-box">
              <img v-if="context.logoUrl" :src="context.logoUrl" :alt="context.appName" class="app-logo" />
              <div v-else class="app-logo app-logo-fallback">
                {{ context.appName.slice(0, 1).toUpperCase() }}
              </div>
            </div>
            <div class="app-meta">
              <h1 class="app-name">{{ context.appName }}</h1>
              <p class="app-subtitle">{{ leftSubtitle }}</p>
              <p v-if="context.contactInfo" class="app-support">联系方式：{{ context.contactInfo }}</p>
            </div>
          </div>

          <div v-else class="app-header app-header--placeholder">
            <div class="app-logo-box">
              <div class="app-logo app-logo-fallback">?</div>
            </div>
            <div class="app-meta">
              <h1 class="app-name">授权确认</h1>
              <p class="app-subtitle">正在加载应用信息…</p>
            </div>
          </div>

          <div v-if="context && user && !loading && !autoRedirecting && !errorMessage" class="permission-box">
            <div class="permission-title">将允许该应用：</div>
            <ul class="permission-list">
              <li v-for="item in permissionItems" :key="item">{{ item }}</li>
            </ul>
            <div class="permission-footnote">我们不会向应用透露您的密码。</div>
          </div>
        </div>
      </div>

      <!-- 右侧：账号信息与操作 -->
      <div class="consent-right">
        <div v-if="errorMessage" class="state-panel error-panel">
          <el-icon><CircleCloseFilled /></el-icon>
          <p>{{ errorMessage }}</p>
          <el-button type="primary" @click="backToLogin">返回登录</el-button>
        </div>

        <div v-else-if="context && user" class="right-body">
          <div class="account-picker">
            <button class="account-row account-row--active" type="button">
              <el-avatar :size="44" :src="user.avatarUrl || undefined" class="account-avatar">
                {{ user.username?.slice(0, 1)?.toUpperCase() }}
              </el-avatar>
              <div class="account-meta">
                <div class="account-name">{{ user.username }}</div>
                <div class="account-email">{{ user.email }}</div>
              </div>
            </button>

            <button class="account-row account-row--switch" type="button" @click="switchAccount">
              <div class="account-meta">
                <div class="switch-title">使用其他账号</div>
              </div>
            </button>
          </div>

          <div class="consent-actions">
            <el-button class="consent-secondary-btn" @click="handleDeny">拒绝</el-button>
            <el-button class="consent-primary-btn" type="primary" :loading="approving" @click="handleApprove">
              同意
            </el-button>
          </div>

          <div class="right-note">
            点击“同意”即表示允许 <span class="right-note-app">{{ context.appName }}</span> 使用您的 Ksuser 账号继续。
          </div>
        </div>

        <div v-else-if="context" class="right-body">
          <div class="state-panel">
            <template v-if="desktopBridgeUser">
              <p class="unauth-title">检测到桌面端已登录</p>
              <button class="account-row account-row--active" type="button">
                <el-avatar :size="44" :src="desktopBridgeUser.avatarUrl || undefined" class="account-avatar">
                  {{ desktopBridgeUser.username?.slice(0, 1)?.toUpperCase() }}
                </el-avatar>
                <div class="account-meta">
                  <div class="account-name">{{ desktopBridgeUser.username }}</div>
                  <div class="account-email">{{ desktopBridgeUser.email }}</div>
                </div>
              </button>

              <div class="consent-actions">
                <el-button
                  class="consent-primary-btn"
                  type="primary"
                  :loading="desktopSigningIn"
                  @click="continueWithDesktopAccount"
                >
                  使用该账号继续
                </el-button>
                <el-button class="consent-secondary-btn" @click="switchAccount">使用其他账号登录</el-button>
              </div>
            </template>

            <template v-else>
              <p class="unauth-title">当前浏览器尚未登录</p>
              <p class="unauth-description">请先登录后再完成授权，或打开桌面端后刷新页面自动检测登录状态。</p>
              <div class="consent-actions">
                <el-button class="consent-primary-btn" type="primary" @click="switchAccount">
                  登录账号
                </el-button>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useDark } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCloseFilled, Loading } from '@element-plus/icons-vue'
import { logout } from '@/api/auth'
import {
  approveOAuth2Authorize,
  getOAuth2AuthorizeContext,
} from '@/api/oauth2'
import {
  approveSSOAuthorize,
  getSSOAuthorizeContext,
} from '@/api/sso'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'
import { clearAuthSession, getStoredAccessToken } from '@/utils/authSession'
import {
  exchangeDesktopSessionToWeb,
  finalizeWebLogin,
  getDesktopBridgeStatus,
  type DesktopBridgeUser,
} from '@/utils/desktopBridge'

type NormalizedAuthorizeContext = {
  clientId: string
  appName: string
  logoUrl?: string
  contactInfo?: string
  redirectUri: string
  requestedScopes: string[]
  alreadyAuthorized: boolean
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { user } = storeToRefs(userStore)

const loading = ref(true)
const approving = ref(false)
const desktopSigningIn = ref(false)
const autoRedirecting = ref(false)
const errorMessage = ref('')
const context = ref<NormalizedAuthorizeContext | null>(null)
const desktopBridgeUser = ref<DesktopBridgeUser | null>(null)

const mode = computed<'oauth' | 'sso'>(() => (route.path.startsWith('/sso/') ? 'sso' : 'oauth'))

// 使用 VueUse 的 useDark 同步暗黑模式
const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})

const requestParams = computed(() => {
  const clientId = typeof route.query.client_id === 'string' ? route.query.client_id.trim() : ''
  const redirectUri =
    typeof route.query.redirect_uri === 'string' ? route.query.redirect_uri.trim() : ''
  const responseType =
    typeof route.query.response_type === 'string' ? route.query.response_type.trim() : ''
  const scope = typeof route.query.scope === 'string' ? route.query.scope.trim() : ''
  const state = typeof route.query.state === 'string' ? route.query.state : undefined
  const nonce = typeof route.query.nonce === 'string' ? route.query.nonce.trim() : ''
  const codeChallenge =
    typeof route.query.code_challenge === 'string' ? route.query.code_challenge.trim() : ''
  const codeChallengeMethod =
    typeof route.query.code_challenge_method === 'string'
      ? route.query.code_challenge_method.trim().toUpperCase()
      : ''

  return {
    clientId,
    redirectUri,
    responseType,
    scope: scope || undefined,
    state,
    nonce: nonce || undefined,
    codeChallenge: codeChallenge || undefined,
    codeChallengeMethod: codeChallengeMethod === 'S256' ? ('S256' as const) : undefined,
  }
})

const leftSubtitle = computed(() =>
  mode.value === 'sso' ? '想要使用您的 Ksuser 账号登录' : '想要访问您的 Ksuser 账号信息',
)

const permissionItems = computed(() => {
  const currentContext = context.value
  if (!currentContext) return []

  const scopes = currentContext.requestedScopes
    .map((scope) => scope.trim().toLowerCase())
    .filter(Boolean)

  const items: string[] = ['确认您的身份（基础信息）']

  if (scopes.includes('email')) {
    items.push('获取您的邮箱地址')
  }

  if (scopes.includes('profile')) {
    items.push('获取您的昵称与头像')
  }

  const knownScopes = new Set(['openid', 'email', 'profile'])
  const hasUnknownScope = scopes.some((scope) => !knownScopes.has(scope))
  if (hasUnknownScope) {
    items.push('获取您的其他账号信息')
  }

  return items
})

const buildDeniedRedirect = () => {
  if (!context.value) {
    return null
  }

  const url = new URL(context.value.redirectUri)
  url.searchParams.set('error', 'access_denied')
  url.searchParams.set('error_description', 'user denied the authorization request')
  if (requestParams.value.state) {
    url.searchParams.set('state', requestParams.value.state)
  }
  return url.toString()
}

const redirectToLogin = () => {
  router.replace({
    path: '/login',
    query: {
      redirect: route.fullPath,
    },
  })
}

const refreshDesktopBridgeStatus = async () => {
  const status = await getDesktopBridgeStatus()
  if (status?.authenticated && status.user) {
    desktopBridgeUser.value = status.user
    return
  }
  desktopBridgeUser.value = null
}

const ensureAuthenticated = async (): Promise<boolean> => {
  if (!getStoredAccessToken()) {
    userStore.clearUser()
    return false
  }

  try {
    await userStore.fetchUserInfo()
    desktopBridgeUser.value = null
    return true
  } catch {
    userStore.clearUser()
    clearAuthSession()
    return false
  }
}

const loadAuthorizeContext = async () => {
  const { clientId, redirectUri, responseType, scope, nonce, codeChallenge, codeChallengeMethod } =
    requestParams.value

  if (mode.value === 'sso') {
    const response = await getSSOAuthorizeContext({
      clientId,
      redirectUri,
      responseType,
      scope,
      nonce,
      codeChallenge,
      codeChallengeMethod,
    })
    context.value = {
      clientId: response.clientId,
      appName: response.clientName,
      logoUrl: response.logoUrl,
      redirectUri: response.redirectUri,
      requestedScopes: response.requestedScopes,
      alreadyAuthorized: response.alreadyAuthorized,
    }
    return
  }

  const response = await getOAuth2AuthorizeContext({
    clientId,
    redirectUri,
    responseType,
    scope,
  })
  context.value = {
    clientId: response.clientId,
    appName: response.appName,
    logoUrl: response.logoUrl,
    contactInfo: response.contactInfo,
    redirectUri: response.redirectUri,
    requestedScopes: response.requestedScopes,
    alreadyAuthorized: response.alreadyAuthorized,
  }
}

const loadContext = async () => {
  const { clientId, redirectUri, responseType } = requestParams.value

  if (!clientId || !redirectUri || !responseType) {
    errorMessage.value = '授权链接无效或缺少必要信息，请返回应用后重新发起授权。'
    loading.value = false
    return
  }

  try {
    await loadAuthorizeContext()

    const authenticated = await ensureAuthenticated()
    if (!authenticated) {
      await refreshDesktopBridgeStatus()
      return
    }

    await loadAuthorizeContext()

    if (context.value?.alreadyAuthorized) {
      loading.value = false
      autoRedirecting.value = true
      await handleApprove(true)
      return
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    if (
      message &&
      (message.toLowerCase().includes('redirect') ||
        message.toLowerCase().includes('callback') ||
        message.toLowerCase().includes('client') ||
        message.toLowerCase().includes('scope') ||
        message.toLowerCase().includes('pkce'))
    ) {
      errorMessage.value = '授权请求无效，请返回应用重试或联系应用支持。'
    } else {
      errorMessage.value = message || '授权请求无效，请返回应用重试或联系应用支持。'
    }
  } finally {
    if (!autoRedirecting.value) {
      loading.value = false
    }
  }
}

const handleApprove = async (silent = false) => {
  if (!context.value) {
    return
  }

  approving.value = true
  try {
    if (mode.value === 'sso') {
      const response = await approveSSOAuthorize({
        clientId: requestParams.value.clientId,
        redirectUri: requestParams.value.redirectUri,
        responseType: 'code',
        scope: requestParams.value.scope,
        state: requestParams.value.state,
        nonce: requestParams.value.nonce,
        codeChallenge: requestParams.value.codeChallenge,
        codeChallengeMethod: requestParams.value.codeChallengeMethod,
      })
      window.location.replace(response.redirectUrl)
      return
    }

    const response = await approveOAuth2Authorize({
      clientId: requestParams.value.clientId,
      redirectUri: requestParams.value.redirectUri,
      responseType: 'code',
      scope: requestParams.value.scope,
      state: requestParams.value.state,
    })
    window.location.replace(response.redirectUrl)
  } catch (error) {
    const rawMessage = error instanceof Error ? error.message : ''
    const message =
      rawMessage &&
      (rawMessage.toLowerCase().includes('redirect') ||
        rawMessage.toLowerCase().includes('callback') ||
        rawMessage.toLowerCase().includes('client') ||
        rawMessage.toLowerCase().includes('scope') ||
        rawMessage.toLowerCase().includes('pkce'))
        ? '授权失败，请返回应用后重试。'
        : rawMessage || '授权失败，请稍后重试'
    autoRedirecting.value = false
    loading.value = false
    if (silent) {
      errorMessage.value = `${message}，请手动确认一次授权。`
      return
    }
    ElMessage.error(message)
  } finally {
    approving.value = false
  }
}

const handleDeny = () => {
  const deniedRedirect = buildDeniedRedirect()
  if (!deniedRedirect) {
    errorMessage.value = '无法构造拒绝回调地址，请返回来源应用后重试。'
    return
  }
  window.location.replace(deniedRedirect)
}

const backToLogin = () => {
  router.push('/login')
}

const continueWithDesktopAccount = async () => {
  if (desktopSigningIn.value) {
    return
  }

  try {
    desktopSigningIn.value = true
    const response = await exchangeDesktopSessionToWeb()
    await finalizeWebLogin({
      accessToken: response.accessToken,
      user: response.user,
      syncDesktop: false,
    })
    userStore.clearUser()
    await userStore.fetchUserInfo()
    desktopBridgeUser.value = null
    await loadAuthorizeContext()

    if (context.value?.alreadyAuthorized) {
      autoRedirecting.value = true
      await handleApprove(true)
      return
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '使用桌面端登录失败'
    ElMessage.error(message)
    await refreshDesktopBridgeStatus()
  } finally {
    desktopSigningIn.value = false
  }
}

const switchAccount = async () => {
  if (!user.value) {
    redirectToLogin()
    return
  }

  try {
    if (getStoredAccessToken()) {
      await logout()
    }
  } catch {
    // Keep the switch-account flow moving even if the server-side logout call fails.
  } finally {
    userStore.clearUser()
    clearAuthSession()
    redirectToLogin()
  }
}

onMounted(() => {
  void loadContext()
})
</script>

<style scoped>
.consent-container {
  width: 100%;
  height: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  overflow: hidden;
}

.consent-container.dark {
  background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
}

.consent-box {
  width: 100%;
  max-width: 1000px;
  display: flex;
  background: var(--el-bg-color);
  border-radius: 20px;
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.12),
    0 16px 40px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(10px);
  border: 1px solid var(--el-border-color-light);
  overflow: hidden;
}

.consent-box--loading {
  min-height: 640px;
}

.loading-stage {
  width: 100%;
  padding: 32px 40px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.loading-card {
  flex: 1;
  min-height: 520px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
}

.loading-app {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  margin-bottom: 8px;
}

.loading-app-logo {
  width: 72px;
  height: 72px;
  border-radius: 18px;
  object-fit: contain;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
}

.loading-app-logo-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(140deg, #1f9d6c 0%, #2fbf7d 100%);
  border-color: transparent;
  color: #fff;
  font-size: 28px;
  font-weight: 700;
}

.loading-app-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.loading-spinner {
  color: var(--el-color-primary);
  animation: consent-spin 1s linear infinite;
}

.loading-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.loading-description {
  max-width: 320px;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

@keyframes consent-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.consent-left {
  flex: 1;
  padding: 56px 48px;
  background: linear-gradient(135deg, #fff8f0, #fffbf5);
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
}

.consent-container.dark .consent-left {
  background: var(--el-bg-color-overlay) !important;
}

.consent-right {
  flex: 1;
  padding: 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: var(--el-bg-color);
}

.brand-line {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--el-text-color-secondary);
}

.brand-logo {
  width: 28px;
  height: 28px;
}

.brand-text {
  font-size: 14px;
  font-weight: 600;
}

.left-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 26px;
}

.app-header {
  display: flex;
  align-items: center;
  gap: 18px;
}

.app-header--placeholder {
  opacity: 0.9;
}

.app-logo-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 74px;
  height: 74px;
  flex-shrink: 0;
}

.app-logo {
  width: 74px;
  height: 74px;
  border-radius: 18px;
  object-fit: contain;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
}

.app-logo-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(140deg, #1f9d6c 0%, #2fbf7d 100%);
  border-color: transparent;
  color: #fff;
  font-size: 28px;
  font-weight: 700;
}

.app-name {
  margin: 0;
  font-size: 28px;
  letter-spacing: -0.2px;
  color: var(--el-text-color-primary);
}

.app-subtitle {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

.app-support {
  margin: 10px 0 0;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  word-break: break-word;
}

.state-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px 18px;
  border-radius: 18px;
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-regular);
}

.state-skeleton {
  width: min(280px, 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.state-skeleton-hero {
  width: 92px;
  height: 92px;
  border-radius: 22px;
}

.state-skeleton-copy {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.state-skeleton-title {
  width: 58%;
  height: 16px;
}

.state-skeleton-line {
  width: 100%;
  height: 14px;
}

.state-skeleton-line.short {
  width: 76%;
}

.error-panel {
  color: var(--el-color-danger);
}

.unauth-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.unauth-description {
  margin: 0;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

.permission-box {
  padding: 18px;
  border-radius: 18px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.permission-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.permission-list {
  margin: 12px 0 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}

.permission-footnote {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.right-body {
  display: flex;
  flex-direction: column;
  gap: 24px;
  align-items: stretch;
}

.account-picker {
  width: min(560px, 100%);
  align-self: center;
  border-radius: 20px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  overflow: hidden;
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.12),
    0 16px 40px rgba(0, 0, 0, 0.08);
}

.account-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 24px;
  background: transparent;
  border: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.account-row + .account-row {
  border-top: 1px solid var(--el-border-color);
}

.account-row--active {
  cursor: default;
}

.account-row--switch {
  transition: background-color 0.2s ease;
}

.account-row--switch:hover {
  background: var(--el-fill-color-light);
}

.account-row--switch .account-meta {
  width: 100%;
}

.account-avatar {
  flex-shrink: 0;
}

.account-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.account-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.account-email {
  font-size: 15px;
  color: var(--el-text-color-regular);
  word-break: break-word;
}

.switch-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.consent-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: min(560px, 100%);
  align-self: center;
}

.consent-primary-btn,
.consent-secondary-btn {
  min-width: 120px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.consent-primary-btn {
  background: var(--el-color-primary);
  border: none;
  color: #fff;
  padding: 0 28px;
  flex: 1;
}

.consent-primary-btn:hover {
  box-shadow: 0 8px 16px rgba(255, 185, 15, 0.3);
  transform: translateY(-2px);
}

.consent-primary-btn:active {
  transform: translateY(0) scale(0.98);
}

.consent-secondary-btn {
  background: var(--el-bg-color);
  border: 1.5px solid var(--el-border-color);
  color: var(--el-text-color-primary);
  padding: 0 28px;
}

.consent-secondary-btn:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.consent-secondary-btn:active {
  transform: scale(0.98);
}

.right-note {
  width: min(560px, 100%);
  align-self: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.right-note-app {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

@media (max-width: 900px) {
  .consent-box {
    flex-direction: column;
  }

  .consent-left {
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-light);
    padding: 40px 32px;
  }

  .consent-right {
    padding: 32px;
  }

  .consent-actions {
    flex-direction: column-reverse;
  }

  .consent-primary-btn,
  .consent-secondary-btn {
    width: 100%;
  }

  .loading-stage {
    padding: 24px;
  }

  .loading-card {
    min-height: 420px;
  }
}

:global(html.dark) .account-picker {
  background: var(--el-bg-color-overlay);
}
</style>
