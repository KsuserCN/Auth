<template>
  <div class="consent-container" :class="{ dark: isDark }">
    <ApplicationQrAuthorization
      v-if="qrVisible && qrSupported"
      :authorization="requestParams"
      :mode="mode"
      @web-login="resumeWebLogin"
    />
    <div v-else-if="loading || autoRedirecting" class="consent-box consent-box--loading">
      <div class="loading-stage">
        <div class="brand-line">
          <img src="/favicon.ico" alt="Ksuser" class="brand-logo" />
          <span class="brand-text">{{ t('ui.mainLayout.message001') }}</span>
        </div>

        <div class="loading-card">
          <div v-if="context" class="loading-app">
            <img
              v-if="context.logoUrl"
              :src="context.logoUrl"
              :alt="context.appName"
              class="loading-app-logo"
            />
            <div v-else class="loading-app-logo loading-app-logo-fallback">
              {{ context.appName.slice(0, 1).toUpperCase() }}
            </div>
            <div class="loading-app-name">{{ context.appName }}</div>
          </div>

          <el-icon class="loading-spinner" :size="34">
            <Loading />
          </el-icon>
          <div class="loading-title">
            {{
              autoRedirecting
                ? t('ui.oAuthAuthorize.message009')
                : t('ui.oAuthAuthorize.message010')
            }}
          </div>
          <div class="loading-description">
            {{
              autoRedirecting
                ? t('ui.oAuthAuthorize.message011')
                : t('ui.oAuthAuthorize.message012')
            }}
          </div>
        </div>
      </div>
    </div>

    <div v-else class="consent-box">
      <button
        v-if="qrSupported"
        class="qr-login-toggle"
        type="button"
        :aria-label="t('ui.applicationQr.button')"
        :title="t('ui.applicationQr.button')"
        :disabled="approving || desktopSigningIn"
        @click="qrVisible = true"
      >
        <i class="fa-solid fa-qrcode" aria-hidden="true"></i>
      </button>
      <!-- 左侧：应用信息与授权内容 -->
      <div class="consent-left">
        <ApplicationAuthorizationInfo
          v-if="context"
          :app-name="context.appName"
          :logo-url="context.logoUrl"
          :redirect-uri="context.redirectUri"
          :requested-scopes="context.requestedScopes"
          :console-application="context.consoleApplication"
          :subject-name="context.subjectName"
          :subject-verification-type="context.subjectVerificationType"
          :mode="mode"
        />
        <div v-else class="left-placeholder">
          <div class="brand-line">
            <img src="/favicon.ico" alt="Ksuser" class="brand-logo" />
            <span class="brand-text">{{ t('ui.mainLayout.message001') }}</span>
          </div>
          <div class="app-header app-header--placeholder">
            <div class="app-logo-box">
              <div class="app-logo app-logo-fallback">?</div>
            </div>
            <div class="app-meta">
              <h1 class="app-name">{{ t('ui.oAuthAuthorize.message014') }}</h1>
              <p class="app-subtitle">{{ t('ui.oAuthAuthorize.message015') }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：账号信息与操作 -->
      <div class="consent-right">
        <div v-if="errorMessage" class="state-panel error-panel">
          <el-icon><CircleCloseFilled /></el-icon>
          <p>{{ renderMessage(errorMessage) }}</p>
          <el-button type="primary" @click="backToLogin">{{
            t('ui.appleRegister.message015')
          }}</el-button>
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
                <div class="switch-title">{{ t('ui.oAuthAuthorize.message018') }}</div>
              </div>
            </button>
          </div>

          <div v-if="mode !== 'official'" class="grant-policy-card">
            <div class="grant-policy-header">
              <div class="grant-policy-title">{{ t('ui.oAuthAuthorize.message019') }}</div>
              <div class="grant-policy-subtitle">
                {{ t('ui.oAuthAuthorize.message020') }}
              </div>
            </div>

            <div v-if="context.alreadyAuthorized && hasConsentPrompt" class="grant-policy-banner">
              {{ t('ui.oAuthAuthorize.message021') }}
            </div>

            <el-radio-group v-model="grantMode" class="grant-policy-options">
              <el-radio-button
                v-for="option in grantModeOptions"
                :key="option.value"
                :label="option.value"
              >
                {{ option.label }}
              </el-radio-button>
            </el-radio-group>

            <div class="grant-policy-note">{{ grantModeDescription }}</div>

            <div v-if="grantMode === 'TIME_LIMITED'" class="grant-ttl-row">
              <span class="grant-ttl-label">{{ t('ui.oAuthAuthorize.message022') }}</span>
              <el-select v-model="grantTtlSeconds" class="grant-ttl-select">
                <el-option
                  v-for="option in grantTtlOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </div>

            <div v-if="existingGrantSummary" class="grant-policy-footnote">
              {{ t('ui.oAuthAuthorize.message023') }}{{ existingGrantSummary }}
            </div>
          </div>

          <div v-else class="grant-policy-card">
            <div class="grant-policy-title">{{ t('ui.official.consentTitle') }}</div>
            <p class="grant-policy-note">{{ t('ui.official.consentNotice') }}</p>
          </div>

          <div class="consent-actions">
            <el-button class="consent-secondary-btn" @click="handleDeny">{{
              t('ui.oAuthAuthorize.message024')
            }}</el-button>
            <el-button
              class="consent-primary-btn"
              type="primary"
              :loading="approving"
              @click="handleApprove"
            >
              {{ t('ui.oAuthAuthorize.message025') }}
            </el-button>
          </div>

          <div class="right-note">
            {{ t('ui.oAuthAuthorize.message026') }}
            <span class="right-note-app">{{ context.appName }}</span>
            {{ t('ui.oAuthAuthorize.message027') }}
          </div>
        </div>

        <div v-else-if="context" class="right-body">
          <div class="state-panel">
            <template v-if="desktopBridgeUser">
              <p class="unauth-title">{{ t('ui.login.message014') }}</p>
              <button
                class="account-row account-row--active account-row--desktop-bridge"
                type="button"
              >
                <el-avatar
                  :size="44"
                  :src="desktopBridgeUser.avatarUrl || undefined"
                  class="account-avatar"
                >
                  {{ desktopBridgeUser.username?.slice(0, 1)?.toUpperCase() }}
                </el-avatar>
                <div class="account-meta">
                  <div
                    class="account-identity"
                    :title="`${desktopBridgeUser.username} · ${desktopBridgeUser.email}`"
                  >
                    <span class="account-identity-name">{{ desktopBridgeUser.username }}</span>
                    <span class="account-identity-separator">·</span>
                    <span class="account-identity-email">{{ desktopBridgeUser.email }}</span>
                  </div>
                </div>
              </button>

              <div class="consent-actions">
                <el-button
                  class="consent-primary-btn"
                  type="primary"
                  :loading="desktopSigningIn"
                  @click="continueWithDesktopAccount"
                >
                  {{ t('ui.oAuthAuthorize.message028') }}
                </el-button>
                <el-button class="consent-secondary-btn" @click="switchAccount">{{
                  t('ui.oAuthAuthorize.message029')
                }}</el-button>
              </div>
            </template>

            <template v-else>
              <p class="unauth-title">{{ t('ui.oAuthAuthorize.message030') }}</p>
              <p class="unauth-description">
                {{ t('ui.oAuthAuthorize.message031') }}
              </p>
              <div class="consent-actions">
                <el-button class="consent-primary-btn" type="primary" @click="switchAccount">
                  {{ t('ui.oAuthAuthorize.message032') }}
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
import { localizedError } from '@/utils/apiResult'
import { intlLocale, t, uiMessage, renderMessage, type LocalizedMessage } from '@/i18n'

import { computed, onMounted, ref } from 'vue'
import { useDark } from '@vueuse/core'
import ApplicationQrAuthorization from '@/components/ApplicationQrAuthorization.vue'
import ApplicationAuthorizationInfo from '@/components/ApplicationAuthorizationInfo.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCloseFilled, Loading } from '@element-plus/icons-vue'
import { isMobileAuthorizationBrowser } from '@/utils/mobileAuthorization'
import { logout } from '@/api/auth'
import {
  approveOAuth2Authorize,
  getOAuth2AuthorizeContext,
  type AuthorizationGrantMode,
} from '@/api/oauth2'
import { approveSSOAuthorize, getSSOAuthorizeContext } from '@/api/sso'
import {
  approveOfficialAuthorize,
  getOfficialAuthorizeContext,
} from '@/api/official'
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
  redirectUri: string
  requestedScopes: string[]
  consoleApplication: boolean
  subjectName?: string | null
  subjectVerificationType?: string | null
  alreadyAuthorized: boolean
  existingGrantMode: AuthorizationGrantMode
  existingGrantExpiresAt?: string | null
}

type GrantModeOption = {
  value: AuthorizationGrantMode
  label: string
  description: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { user } = storeToRefs(userStore)

const loading = ref(true)
const approving = ref(false)
const desktopSigningIn = ref(false)
const autoRedirecting = ref(false)
const errorMessage = ref<LocalizedMessage>('')
const context = ref<NormalizedAuthorizeContext | null>(null)
const desktopBridgeUser = ref<DesktopBridgeUser | null>(null)
const qrVisible = ref(false)
const qrSupported = !isMobileAuthorizationBrowser()
const resumeWebLogin = async () => {
  qrVisible.value = false
  // Do not force QR again when Passport returns after the user explicitly chose web login.
  const destination = new URL(route.fullPath, window.location.origin)
  destination.searchParams.delete('login_method')
  await router.replace({
    path: '/login',
    query: { redirect: `${destination.pathname}${destination.search}${destination.hash}` },
  })
}
const grantMode = ref<AuthorizationGrantMode>('PERSISTENT')
const grantTtlSeconds = ref(3600)

const mode = computed<'oauth' | 'sso' | 'official'>(() =>
  route.path.startsWith('/official/')
    ? 'official'
    : route.path.startsWith('/sso/')
      ? 'sso'
      : 'oauth',
)

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
  const prompt = typeof route.query.prompt === 'string' ? route.query.prompt.trim() : ''

  return {
    clientId,
    redirectUri,
    responseType,
    scope: scope || undefined,
    state,
    nonce: nonce || undefined,
    codeChallenge: codeChallenge || undefined,
    codeChallengeMethod: codeChallengeMethod === 'S256' ? ('S256' as const) : undefined,
    prompt: prompt || undefined,
  }
})

const grantModeOptions = computed<GrantModeOption[]>(() => [
  {
    value: 'PERSISTENT',
    label: t('ui.oAuthAuthorize.message036'),
    description: t('ui.oAuthAuthorize.message037'),
  },
  {
    value: 'ONE_TIME',
    label: t('ui.oAuthAuthorize.message038'),
    description: t('ui.oAuthAuthorize.message039'),
  },
  {
    value: 'TIME_LIMITED',
    label: t('ui.oAuthAuthorize.message040'),
    description: t('ui.oAuthAuthorize.message041'),
  },
])

const grantTtlOptions = computed(() => [
  { value: 300, label: t('ui.oAuthAuthorize.message042') },
  { value: 3600, label: t('ui.oAuthAuthorize.message043') },
  { value: 86400, label: t('ui.oAuthAuthorize.message044') },
  { value: 604800, label: t('ui.oAuthAuthorize.message045') },
])

const hasConsentPrompt = computed(() => {
  const prompt = requestParams.value.prompt?.toLowerCase()
  if (!prompt) return false
  return prompt.split(/\s+/).includes('consent')
})

const grantModeDescription = computed(() => {
  return (
    grantModeOptions.value.find((option) => option.value === grantMode.value)?.description ?? ''
  )
})

const formatGrantDateTime = (value?: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(intlLocale.value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

const existingGrantSummary = computed(() => {
  const currentContext = context.value
  if (!currentContext?.alreadyAuthorized) return ''
  if (currentContext.existingGrantMode === 'ONE_TIME') {
    return t('ui.oAuthAuthorize.message038')
  }
  if (currentContext.existingGrantMode === 'TIME_LIMITED') {
    const expiresAt = formatGrantDateTime(currentContext.existingGrantExpiresAt)
    return expiresAt
      ? t('ui.oAuthAuthorize.message052', { arg0: expiresAt })
      : t('ui.oAuthAuthorize.message040')
  }
  return t('ui.oAuthAuthorize.message036')
})

const buildDeniedRedirect = () => {
  if (!context.value) {
    return null
  }

  const url = new URL(context.value.redirectUri)
  url.searchParams.set('error', 'access_denied')
  url.searchParams.set('errorCode', 'FORBIDDEN')
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

  if (mode.value === 'official') {
    const response = await getOfficialAuthorizeContext(requestParams.value)
    context.value = {
      clientId: response.clientId,
      appName: response.clientName,
      logoUrl: response.logoUrl,
      redirectUri,
      requestedScopes: response.requestedScopes,
      consoleApplication: response.consoleApplication,
      alreadyAuthorized: response.loggedIn && !response.consentRequired,
      existingGrantMode: 'PERSISTENT',
    }
    return
  }

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
      consoleApplication: false,
      subjectName: response.subjectName,
      subjectVerificationType: response.subjectVerificationType,
      alreadyAuthorized: response.alreadyAuthorized,
      existingGrantMode: response.existingGrantMode,
      existingGrantExpiresAt: response.existingGrantExpiresAt,
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
    redirectUri: response.redirectUri,
    requestedScopes: response.requestedScopes,
    consoleApplication: false,
    subjectName: response.subjectName,
    subjectVerificationType: response.subjectVerificationType,
    alreadyAuthorized: response.alreadyAuthorized,
    existingGrantMode: response.existingGrantMode,
    existingGrantExpiresAt: response.existingGrantExpiresAt,
  }
}

const loadContext = async () => {
  const { clientId, redirectUri, responseType } = requestParams.value

  if (!clientId || !redirectUri || !responseType) {
    errorMessage.value = uiMessage('ui.oAuthAuthorize.message053')
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
  } catch (error) {
    errorMessage.value = localizedError(error, 'ui.oAuthAuthorize.message054')
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
    if (mode.value === 'official') {
      const response = await approveOfficialAuthorize({
        ...requestParams.value,
        responseType: 'code',
        approved: true,
      })
      window.location.replace(response.redirectUrl)
      return
    }
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
        grantMode: grantMode.value,
        grantTtlSeconds: grantMode.value === 'TIME_LIMITED' ? grantTtlSeconds.value : undefined,
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
      grantMode: grantMode.value,
      grantTtlSeconds: grantMode.value === 'TIME_LIMITED' ? grantTtlSeconds.value : undefined,
    })
    window.location.replace(response.redirectUrl)
  } catch (error) {
    const rawMessage = error instanceof Error ? error.message : ''
    const message = rawMessage || t('ui.oAuthAuthorize.message055')
    autoRedirecting.value = false
    loading.value = false
    if (silent) {
      errorMessage.value = {
        render: () =>
          t('ui.oAuthAuthorize.message056', {
            arg0: renderMessage(localizedError(error, 'ui.oAuthAuthorize.message055')),
          }),
      }
      return
    }
    ElMessage.error(message)
  } finally {
    approving.value = false
  }
}

const handleDeny = async () => {
  if (mode.value === 'official') {
    if (approving.value) return
    approving.value = true
    try {
      const response = await approveOfficialAuthorize({
        ...requestParams.value,
        responseType: 'code',
        approved: false,
      })
      window.location.replace(response.redirectUrl)
    } catch (error) {
      errorMessage.value = localizedError(error, 'ui.oAuthAuthorize.message057')
    } finally {
      approving.value = false
    }
    return
  }
  const deniedRedirect = buildDeniedRedirect()
  if (!deniedRedirect) {
    errorMessage.value = uiMessage('ui.oAuthAuthorize.message057')
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

    if (context.value?.alreadyAuthorized && !hasConsentPrompt.value) {
      autoRedirecting.value = true
      await handleApprove(true)
      return
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : t('ui.oAuthAuthorize.message058')
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
  if (qrSupported && route.query.login_method === 'qr') {
    qrVisible.value = true
    loading.value = false
  } else void loadContext()
})
</script>

<style scoped>
.consent-container {
  --consent-card-width: 1000px;
  --consent-card-radius: 20px;
  --consent-card-shadow: 0 8px 24px rgb(0 0 0 / 12%), 0 16px 40px rgb(0 0 0 / 8%);
  --consent-panel-bg: linear-gradient(135deg, #fff8f0, #fffbf5);
  --passport-link-color: #805c00;
  --passport-link-hover: #5e4200;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 100%;
  display: flex;
  align-items: safe center;
  justify-content: safe center;
  padding: 24px;
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  overflow-y: auto;
  overflow-x: hidden;
}

.consent-container.dark {
  --consent-panel-bg: var(--el-bg-color-overlay);
  --passport-link-color: #ffd36b;
  --passport-link-hover: #ffe29b;
  background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
}

.consent-box {
  position: relative;
  width: 100%;
  max-width: var(--consent-card-width);
  display: flex;
  background: var(--el-bg-color);
  border-radius: var(--consent-card-radius);
  box-shadow: var(--consent-card-shadow);
  backdrop-filter: blur(10px);
  border: 1px solid var(--el-border-color-light);
  overflow: hidden;
}

.mobile-consent-actions {
  flex-direction: column;
  max-width: 320px;
}

.mobile-consent-actions :deep(.el-button) {
  margin-left: 0;
  width: 100%;
  min-height: 44px;
  border-radius: 12px;
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
  background: var(--consent-panel-bg);
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
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

.grant-policy-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.grant-policy-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.grant-policy-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.grant-policy-subtitle,
.grant-policy-note,
.grant-policy-footnote {
  color: var(--el-text-color-secondary);
  line-height: 1.7;
  font-size: 13px;
}

.grant-policy-banner {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(31, 157, 108, 0.08);
  color: #1f7a56;
  font-size: 13px;
  line-height: 1.6;
}

.grant-policy-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.grant-ttl-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.grant-ttl-label {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.grant-ttl-select {
  width: 160px;
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

.account-row--desktop-bridge .account-meta {
  min-width: 0;
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

.account-identity {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
  font-size: 16px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.account-identity-name {
  flex: 0 1 auto;
  min-width: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
}

.account-identity-separator {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
}

.account-identity-email {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
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

.qr-login-toggle {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--passport-link-color);
  font-size: 22px;
  cursor: pointer;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}

.qr-login-toggle:hover:not(:disabled) {
  color: var(--passport-link-hover);
  background: var(--el-fill-color-light);
}

.qr-login-toggle:focus-visible {
  outline: 2px solid var(--passport-link-color);
  outline-offset: 2px;
}

.qr-login-toggle:disabled {
  opacity: 0.5;
  cursor: default;
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
  .consent-container {
    align-items: flex-start;
    justify-content: flex-start;
    padding: 0;
  }

  .consent-box {
    flex-direction: column;
    min-height: 100dvh;
    border-radius: 0;
  }

  .consent-left {
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-light);
    padding: 40px 32px;
  }

  .consent-right {
    padding: 32px;
    justify-content: flex-start;
  }

  .consent-actions {
    flex-direction: column-reverse;
  }

  .mobile-consent-actions {
    flex-direction: column;
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

  .loading-stage--mobile-handoff {
    box-sizing: border-box;
    padding: max(24px, env(safe-area-inset-top)) 20px max(32px, env(safe-area-inset-bottom));
  }

  .loading-stage--mobile-handoff .brand-line {
    justify-content: center;
  }

  .loading-card--mobile-handoff {
    flex: 0 1 auto;
    width: min(460px, 100%);
    min-height: 0;
    margin: 36px auto 0;
    gap: 16px;
  }

  .loading-card--mobile-handoff .loading-title {
    margin: 0;
    font-size: clamp(20px, 5vw, 24px);
    line-height: 1.4;
  }

  .loading-card--mobile-handoff .loading-description {
    margin: 0;
  }

  .loading-card--mobile-handoff .mobile-consent-actions {
    margin-top: 8px;
  }
}

:global(html.dark) .account-picker {
  background: var(--el-bg-color-overlay);
}
</style>
