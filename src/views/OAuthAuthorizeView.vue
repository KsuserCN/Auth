<template>
  <div class="authorize-page">
    <div class="authorize-card">
      <div class="brand-line">
        <img src="/favicon.ico" alt="Ksuser" class="brand-logo" />
        <span class="brand-text">Ksuser 统一认证中心</span>
      </div>

      <div class="title-area">
        <h1 class="title">应用授权确认</h1>
        <p class="subtitle">
          {{ context?.oidcRequest ? '确认后将签发授权码，应用可继续换取 Access Token 与 ID Token。' : '确认后将向第三方应用签发一次性授权码。' }}
        </p>
      </div>

      <div v-if="loading" class="state-panel">
        <el-icon class="spin-icon"><Loading /></el-icon>
        <p>正在校验应用与回调地址...</p>
      </div>

      <div v-else-if="errorMessage" class="state-panel error-panel">
        <el-icon><CircleCloseFilled /></el-icon>
        <p>{{ errorMessage }}</p>
        <el-button type="primary" @click="backToLogin">返回登录</el-button>
      </div>

      <div v-else-if="context && user" class="authorize-content">
        <div class="summary-box">
          <div class="summary-row">
            <span class="summary-label">应用名称</span>
            <span class="summary-value">{{ context.appName }}</span>
          </div>
          <div class="summary-row">
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
          <div class="permission-title">本次授权后应用可获取：</div>
          <ul class="permission-list">
            <li>`openid` 与 `unionid`</li>
            <li v-if="context.oidcRequest">`sub` 与 `id_token`（OIDC 标准身份断言）</li>
            <li v-if="!context.requestedScopes.length">仅基础身份标识，不额外读取昵称或邮箱</li>
            <li v-for="scope in context.requestedScopes" :key="scope">
              {{ scopeDescription(scope) }}
            </li>
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
import { approveOAuth2Authorize, getOAuth2AuthorizeContext, type OAuth2AuthorizeContext } from '@/api/oauth2'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'
import { getStoredAccessToken } from '@/utils/authSession'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { user } = storeToRefs(userStore)

const loading = ref(true)
const approving = ref(false)
const errorMessage = ref('')
const context = ref<OAuth2AuthorizeContext | null>(null)

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
  const normalizedCodeChallengeMethod = codeChallengeMethod === 'S256' ? ('S256' as const) : undefined

  return {
    clientId,
    redirectUri,
    responseType,
    scope: scope || undefined,
    state,
    nonce: nonce || undefined,
    codeChallenge: codeChallenge || undefined,
    codeChallengeMethod: normalizedCodeChallengeMethod,
  }
})

const scopeDescription = (scope: 'profile' | 'email') => {
  if (scope === 'email') return '邮箱地址'
  return '昵称与头像'
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
  const { clientId, redirectUri, responseType, scope } = requestParams.value
  if (!clientId || !redirectUri || !responseType) {
    errorMessage.value = '授权链接缺少必要参数，请联系应用开发者检查 client_id、redirect_uri 与 response_type。'
    loading.value = false
    return
  }

  try {
    context.value = await getOAuth2AuthorizeContext({
      clientId,
      redirectUri,
      responseType,
      scope,
      nonce: requestParams.value.nonce,
      codeChallenge: requestParams.value.codeChallenge,
      codeChallengeMethod: requestParams.value.codeChallengeMethod,
    })
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
    const response = await approveOAuth2Authorize({
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
  width: 26px;
  height: 26px;
}

.title-area {
  margin: 18px 0 20px;
}

.title {
  margin: 0;
  font-size: 28px;
  color: var(--el-text-color-primary);
}

.subtitle {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
}

.state-panel {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--el-text-color-secondary);
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

.summary-box,
.permission-box {
  padding: 18px;
  border-radius: 18px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
}

.summary-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-row + .summary-row {
  margin-top: 14px;
}

.summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-value {
  color: var(--el-text-color-primary);
  line-height: 1.6;
  word-break: break-all;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.permission-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}

.permission-list {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-primary);
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
  .authorize-page {
    padding: 16px;
  }

  .authorize-card {
    padding: 22px;
  }

  .action-row {
    flex-direction: column-reverse;
  }

  .action-row :deep(.el-button) {
    width: 100%;
  }
}
</style>
