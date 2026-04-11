<template>
  <div class="authorize-page">
    <div class="authorize-card">
      <div class="brand-line">
        <img src="/favicon.ico" alt="Ksuser" class="brand-logo" />
        <span class="brand-text">Ksuser 统一认证中心</span>
      </div>

      <div class="title-area">
        <h1 class="title">{{ pageTitle }}</h1>
        <p class="subtitle">{{ pageSubtitle }}</p>
      </div>

      <div v-if="loading" class="state-panel">
        <el-icon class="spin-icon"><Loading /></el-icon>
        <p>正在校验客户端与回调地址...</p>
      </div>

      <div v-else-if="errorMessage" class="state-panel error-panel">
        <el-icon><CircleCloseFilled /></el-icon>
        <p>{{ errorMessage }}</p>
        <el-button type="primary" @click="backToLogin">返回登录</el-button>
      </div>

      <div v-else-if="context && user" class="authorize-content">
        <div class="client-logo-box">
          <img v-if="context.logoUrl" :src="context.logoUrl" alt="Client Logo" class="client-logo" />
          <div v-else class="client-logo client-logo-fallback">
            {{ context.appName.slice(0, 1).toUpperCase() }}
          </div>
        </div>

        <div class="summary-box">
          <div class="summary-row">
            <span class="summary-label">{{ mode === 'sso' ? '客户端名称' : '应用名称' }}</span>
            <span class="summary-value">{{ context.appName }}</span>
          </div>
          <div v-if="context.contactInfo" class="summary-row">
            <span class="summary-label">开发者联系方式</span>
            <span class="summary-value">{{ context.contactInfo }}</span>
          </div>
          <div class="summary-row">
            <span class="summary-label">登录账号</span>
            <span class="summary-value">{{ user.username }} · {{ user.email }}</span>
          </div>
          <div class="summary-row">
            <span class="summary-label">回调地址</span>
            <span class="summary-value mono">{{ context.redirectUri }}</span>
          </div>
        </div>

        <div class="permission-box">
          <div class="permission-title">本次授权后客户端可获取：</div>
          <ul class="permission-list">
            <li v-if="mode === 'oauth'">`openid` 与 `unionid`</li>
            <li v-if="mode === 'sso'">`sub`、`access_token` 与 `id_token`</li>
            <li v-if="!context.requestedScopes.length">仅基础身份标识</li>
            <li v-for="scope in context.requestedScopes" :key="scope">
              {{ scopeDescription(scope) }}
            </li>
            <li v-if="mode === 'sso' && requestParams.codeChallenge">本次请求启用了 PKCE 校验</li>
          </ul>
        </div>

        <div class="action-row">
          <el-button @click="handleDeny">拒绝</el-button>
          <el-button type="primary" :loading="approving" @click="handleApprove">同意授权</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCloseFilled, Loading } from '@element-plus/icons-vue'
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
import { getStoredAccessToken } from '@/utils/authSession'

type NormalizedAuthorizeContext = {
  clientId: string
  appName: string
  logoUrl?: string
  contactInfo?: string
  redirectUri: string
  requestedScopes: string[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { user } = storeToRefs(userStore)

const loading = ref(true)
const approving = ref(false)
const errorMessage = ref('')
const context = ref<NormalizedAuthorizeContext | null>(null)

const mode = computed<'oauth' | 'sso'>(() => (route.path.startsWith('/sso/') ? 'sso' : 'oauth'))

const pageTitle = computed(() => (mode.value === 'sso' ? 'SSO 授权确认' : 'OAuth2.0 授权确认'))

const pageSubtitle = computed(() =>
  mode.value === 'sso'
    ? '确认后将签发 SSO 授权码，客户端可继续换取 Access Token 与 ID Token。'
    : '确认后将向第三方应用签发一次性授权码。'
)

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

const scopeDescription = (scope: string) => {
  if (scope === 'email') return '邮箱地址'
  if (scope === 'profile') return '昵称与头像'
  if (scope === 'openid') return '基础身份标识'
  return scope
}

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

const ensureAuthenticated = async () => {
  if (!getStoredAccessToken()) {
    redirectToLogin()
    return false
  }

  try {
    await userStore.fetchUserInfo()
    return true
  } catch {
    redirectToLogin()
    return false
  }
}

const loadContext = async () => {
  const { clientId, redirectUri, responseType, scope, nonce, codeChallenge, codeChallengeMethod } =
    requestParams.value

  if (!clientId || !redirectUri || !responseType) {
    errorMessage.value = '授权链接缺少必要参数，请检查 client_id、redirect_uri 与 response_type。'
    loading.value = false
    return
  }

  try {
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
      }
    } else {
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
      }
    }

    const authenticated = await ensureAuthenticated()
    if (!authenticated) {
      return
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '授权请求无效，请联系应用开发者检查配置。'
  } finally {
    loading.value = false
  }
}

const handleApprove = async () => {
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
    const message = error instanceof Error ? error.message : '授权失败，请稍后重试'
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

onMounted(() => {
  void loadContext()
})
</script>

<style scoped>
.authorize-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.16), transparent 35%),
    radial-gradient(circle at bottom right, rgba(230, 162, 60, 0.16), transparent 40%),
    var(--el-bg-color-page);
}

.authorize-card {
  width: min(720px, 100%);
  padding: 28px;
  border-radius: 24px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.08);
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

.title-area {
  margin: 22px 0 24px;
}

.title {
  margin: 0;
  font-size: 28px;
  color: var(--el-text-color-primary);
}

.subtitle {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
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

.error-panel {
  color: var(--el-color-danger);
}

.spin-icon {
  animation: spin 1s linear infinite;
}

.authorize-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.client-logo-box {
  display: flex;
  justify-content: center;
}

.client-logo {
  width: 72px;
  height: 72px;
  border-radius: 16px;
  object-fit: contain;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
}

.client-logo-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(140deg, #1f9d6c 0%, #2fbf7d 100%);
  border-color: transparent;
  color: #fff;
  font-size: 28px;
  font-weight: 700;
}

.summary-box,
.permission-box {
  padding: 18px;
  border-radius: 18px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.summary-row {
  display: flex;
  gap: 16px;
  padding: 10px 0;
}

.summary-row + .summary-row {
  border-top: 1px dashed var(--el-border-color);
}

.summary-label {
  width: 108px;
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}

.summary-value {
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
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

.action-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .authorize-card {
    padding: 22px;
  }

  .summary-row,
  .action-row {
    flex-direction: column;
  }

  .summary-label {
    width: auto;
  }
}
</style>
