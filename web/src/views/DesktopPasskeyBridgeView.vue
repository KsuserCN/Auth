<template>
  <div class="desktop-bridge-shell" :class="{ dark: isDark }">
    <div class="desktop-bridge-card">
      <div class="desktop-bridge-header">
        <div>
          <h1>{{ title }}</h1>
          <p>{{ subtitle }}</p>
        </div>
        <el-tag :type="statusTagType" effect="dark">{{ statusLabel }}</el-tag>
      </div>

      <el-alert
        v-if="errorMessage"
        type="error"
        :closable="false"
        :title="renderMessage(errorMessage)"
        show-icon
      />

      <div v-else-if="completed" class="result-block success">
        <el-icon><CircleCheckFilled /></el-icon>
        <span>{{ renderMessage(successMessage) }}</span>
      </div>

      <div v-else-if="needsTotp" class="totp-panel">
        <p class="panel-hint">
          {{ t('ui.desktopPasskeyBridge.message001') }}
        </p>

        <div
          class="mode-switch"
          role="tablist"
          :aria-label="t('ui.desktopPasskeyBridge.message002')"
        >
          <button
            type="button"
            class="mode-chip"
            :class="{ active: totpMode === 'totp' }"
            @click="setTotpMode('totp')"
          >
            {{ t('ui.sensitiveVerificationDialog.message020') }}
          </button>
          <button
            type="button"
            class="mode-chip"
            :class="{ active: totpMode === 'recovery' }"
            @click="setTotpMode('recovery')"
          >
            {{ t('ui.sensitiveVerificationDialog.message021') }}
          </button>
        </div>

        <el-input
          v-model="totpInput"
          :placeholder="
            totpMode === 'totp'
              ? t('ui.desktopPasskeyBridge.message003')
              : t('ui.desktopPasskeyBridge.message004')
          "
          :maxlength="totpMode === 'totp' ? 6 : 8"
          @input="handleTotpInput"
          @keyup.enter="submitTotp"
        />

        <div class="panel-actions">
          <el-button @click="retry" :disabled="busy">{{
            t('ui.desktopPasskeyBridge.message005')
          }}</el-button>
          <el-button type="primary" @click="submitTotp" :loading="busy">{{
            t('ui.desktopPasskeyBridge.message006')
          }}</el-button>
        </div>
      </div>

      <div v-else class="pending-block">
        <div class="pending-skeleton">
          <el-skeleton-item variant="image" class="pending-skeleton-hero" />
          <div class="pending-skeleton-copy">
            <el-skeleton-item variant="text" class="pending-skeleton-title" />
            <el-skeleton-item variant="text" class="pending-skeleton-line" />
            <el-skeleton-item variant="text" class="pending-skeleton-line short" />
          </div>
        </div>
        <div>
          <strong>{{ pendingTitle }}</strong>
          <p>{{ pendingDescription }}</p>
        </div>
      </div>

      <div class="bridge-footer">
        <el-button v-if="showRetryButton" @click="retry" :disabled="busy">{{
          t('ui.desktopPasskeyBridge.message007')
        }}</el-button>
        <span>{{ t('ui.desktopPasskeyBridge.message008') }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { localizedError } from '@/utils/apiResult'
import { t, uiMessage, renderMessage, type LocalizedMessage } from '@/i18n'

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useDark } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled } from '@element-plus/icons-vue'
import {
  createSessionTransfer,
  getPasskeyAuthenticationOptions,
  getPasskeyRegistrationOptions,
  getPasskeySensitiveVerificationOptions,
  verifyPasskeyAuthentication,
  verifyPasskeyForLoginMFA,
  verifyPasskeyRegistration,
  verifyPasskeySensitiveOperation,
  verifyTOTPForLogin,
  type MFAChallenge,
  type LoginResponse,
} from '@/api/auth'
import { getRequestBaseUrl, setRequestBaseUrl } from '@/utils/request'
import { ApiError } from '@/utils/apiResult'
import {
  clearStoredAccessToken,
  getStoredAccessToken,
  setStoredAccessToken,
} from '@/utils/authSession'
import {
  createPasskeyCredential,
  extractAuthenticationData,
  extractRegistrationData,
  getPasskeyCredential,
  isWebAuthnSupported,
} from '@/utils/webauthn'

type BridgeMode = 'login' | 'mfa' | 'sensitive' | 'register'
type TotpMode = 'totp' | 'recovery'

interface BridgeCallbackPayload {
  status: 'success' | 'error'
  state: string
  errorCode?: string
  params?: Record<string, unknown>
  accessToken?: string
  transferCode?: string
  verified?: boolean
  registered?: boolean
}

const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})

const mode = ref<BridgeMode>('login')
const callbackUrl = ref('')
const state = ref('')
const mfaChallengeId = ref('')
const passkeyName = ref('Ksuser Desktop')
const accessToken = ref('')
const previousAccessToken = ref<string | null>(null)
const busy = ref(false)
const completed = ref(false)
const successMessage = ref<LocalizedMessage>(uiMessage('ui.desktopPasskeyBridge.message009'))
const errorMessage = ref<LocalizedMessage>('')
const totpInput = ref('')
const totpMode = ref<TotpMode>('totp')
const pendingMfaChallenge = ref<MFAChallenge | null>(null)

const needsTotp = computed(() => pendingMfaChallenge.value !== null)
const statusLabel = computed(() => {
  if (errorMessage.value) return t('ui.desktopPasskeyBridge.message010')
  if (completed.value) return t('ui.desktopPasskeyBridge.message011')
  if (needsTotp.value) return t('ui.desktopPasskeyBridge.message012')
  return t('ui.desktopPasskeyBridge.message013')
})
const statusTagType = computed(() => {
  if (errorMessage.value) return 'danger'
  if (completed.value) return 'success'
  if (needsTotp.value) return 'warning'
  return 'info'
})
const title = computed(() => {
  switch (mode.value) {
    case 'mfa':
      return t('ui.desktopPasskeyBridge.message014')
    case 'sensitive':
      return t('ui.desktopPasskeyBridge.message015')
    case 'register':
      return t('ui.desktopPasskeyBridge.message016')
    default:
      return t('ui.desktopPasskeyBridge.message017')
  }
})
const subtitle = computed(() => {
  switch (mode.value) {
    case 'mfa':
      return t('ui.desktopPasskeyBridge.message018')
    case 'sensitive':
      return t('ui.desktopPasskeyBridge.message019')
    case 'register':
      return t('ui.desktopPasskeyBridge.message020')
    default:
      return t('ui.desktopPasskeyBridge.message021')
  }
})
const pendingTitle = computed(() => {
  switch (mode.value) {
    case 'mfa':
      return t('ui.desktopPasskeyBridge.message022')
    case 'sensitive':
      return t('ui.desktopPasskeyBridge.message023')
    case 'register':
      return t('ui.desktopPasskeyBridge.message024')
    default:
      return t('ui.desktopPasskeyBridge.message025')
  }
})
const pendingDescription = computed(() => {
  switch (mode.value) {
    case 'mfa':
      return t('ui.desktopPasskeyBridge.message026')
    case 'sensitive':
      return t('ui.desktopPasskeyBridge.message027')
    case 'register':
      return t('ui.desktopPasskeyBridge.message028')
    default:
      return t('ui.desktopPasskeyBridge.message029')
  }
})
const showRetryButton = computed(() => !completed.value && !busy.value)

const parseHash = (): URLSearchParams => {
  const raw = window.location.hash.startsWith('#')
    ? window.location.hash.slice(1)
    : window.location.hash
  return new URLSearchParams(raw)
}

const sanitizeUrl = () => {
  const cleanUrl = `${window.location.origin}${window.location.pathname}${window.location.search}`
  window.history.replaceState({}, document.title, cleanUrl)
}

const isLoopbackCallback = (value: string): boolean => {
  try {
    const url = new URL(value)
    return (
      url.protocol === 'http:' && ['127.0.0.1', 'localhost', '[::1]', '::1'].includes(url.hostname)
    )
  } catch {
    return false
  }
}

const buildCallbackRedirectUrl = (baseUrl: string, payload: BridgeCallbackPayload): string => {
  const callback = new URL(baseUrl)
  callback.searchParams.set('bridgePayload', JSON.stringify(payload))
  return callback.toString()
}

const callbackToDesktop = async (payload: BridgeCallbackPayload) => {
  if (!callbackUrl.value) {
    throw new ApiError({ errorCode: 'BAD_REQUEST' })
  }

  try {
    await fetch(callbackUrl.value, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    })
  } catch (error) {
    // Some deployments block loopback fetch via CSP; fall back to top-level navigation.
    window.location.replace(buildCallbackRedirectUrl(callbackUrl.value, payload))
    return
  }
}

const restoreAccessToken = () => {
  if (!accessToken.value) {
    return
  }
  if (previousAccessToken.value === null) {
    clearStoredAccessToken()
    return
  }
  setStoredAccessToken(previousAccessToken.value)
}

const completeWithSuccess = async (payload: Omit<BridgeCallbackPayload, 'status' | 'state'>) => {
  await callbackToDesktop({
    status: 'success',
    state: state.value,
    ...payload,
  })
  completed.value = true
  errorMessage.value = ''
}

const createDesktopTransferCode = async (nextAccessToken: string): Promise<string> => {
  const currentAccessToken = getStoredAccessToken()

  try {
    setStoredAccessToken(nextAccessToken)
    const transfer = await createSessionTransfer('desktop', 'auth_bridge_internal')
    return transfer.transferCode
  } finally {
    if (currentAccessToken) {
      setStoredAccessToken(currentAccessToken)
    } else {
      clearStoredAccessToken()
    }
  }
}

const failWithResult = async (result: ApiError) => {
  errorMessage.value = { render: () => result.message }
  try {
    await callbackToDesktop({
      status: 'error',
      state: state.value,
      errorCode: result.errorCode,
      params: result.params,
    })
  } catch {
    // Desktop callback may already be unavailable; keep the browser error visible.
  }
}

const normalizeMfaMethods = (
  methods: MFAChallenge['methods'],
  fallbackMethod: MFAChallenge['method'],
): Array<'totp' | 'passkey'> => {
  const normalized = (methods ?? []).filter(
    (item): item is 'totp' | 'passkey' => item === 'totp' || item === 'passkey',
  )

  if (normalized.length > 0) {
    return [...new Set(normalized)]
  }

  return fallbackMethod === 'passkey' ? ['passkey'] : ['totp']
}

const handleLoginResponse = async (response: LoginResponse | MFAChallenge) => {
  if ('challengeId' in response) {
    const methods = normalizeMfaMethods(response.methods, response.method)
    pendingMfaChallenge.value = response

    if (methods.includes('passkey') && isWebAuthnSupported()) {
      await runPasskeyMfa(response.challengeId)
      return
    }

    if (methods.includes('totp')) {
      ElMessage.info(t('ui.desktopPasskeyBridge.message030'))
      return
    }

    throw new ApiError({ errorCode: 'PASSKEY_MFA_UNSUPPORTED' })
  }

  const transferCode = await createDesktopTransferCode(response.accessToken)
  await completeWithSuccess({
    accessToken: response.accessToken,
    transferCode,
    errorCode: 'REQUEST_SUCCESS',
  })
  successMessage.value = uiMessage('ui.desktopPasskeyBridge.message031')
  restoreAccessToken()
}

const runPasskeyLogin = async () => {
  const options = await getPasskeyAuthenticationOptions()
  const credential = await getPasskeyCredential({
    challenge: options.challenge,
    timeout: options.timeout,
    rpId: options.rpId,
    userVerification: options.userVerification,
    allowCredentials: options.allowCredentials,
  })

  if (!credential) {
    throw new ApiError({ errorCode: 'PASSKEY_CREDENTIAL_UNAVAILABLE' })
  }

  const response = await verifyPasskeyAuthentication(
    options.challengeId,
    extractAuthenticationData(credential),
  )
  await handleLoginResponse(response)
}

const runPasskeyMfa = async (challengeId: string) => {
  const options = await getPasskeyAuthenticationOptions()
  const credential = await getPasskeyCredential({
    challenge: options.challenge,
    timeout: options.timeout,
    rpId: options.rpId,
    userVerification: options.userVerification,
    allowCredentials: options.allowCredentials,
  })

  if (!credential) {
    throw new ApiError({ errorCode: 'PASSKEY_CREDENTIAL_UNAVAILABLE' })
  }

  const response = await verifyPasskeyForLoginMFA({
    mfaChallengeId: challengeId,
    passkeyChallengeId: options.challengeId,
    ...extractAuthenticationData(credential),
  })

  const transferCode = await createDesktopTransferCode(response.accessToken)
  await completeWithSuccess({
    accessToken: response.accessToken,
    transferCode,
    errorCode: 'REQUEST_SUCCESS',
  })
  successMessage.value = uiMessage('ui.desktopPasskeyBridge.message032')
  restoreAccessToken()
}

const runSensitiveVerification = async () => {
  const options = await getPasskeySensitiveVerificationOptions()
  const credential = await getPasskeyCredential({
    challenge: options.challenge,
    timeout: options.timeout,
    rpId: options.rpId,
    userVerification: options.userVerification,
    allowCredentials: options.allowCredentials,
  })

  if (!credential) {
    throw new ApiError({ errorCode: 'PASSKEY_CREDENTIAL_UNAVAILABLE' })
  }

  await verifyPasskeySensitiveOperation(options.challengeId, extractAuthenticationData(credential))
  await completeWithSuccess({
    verified: true,
    errorCode: 'REQUEST_SUCCESS',
  })
  successMessage.value = uiMessage('ui.desktopPasskeyBridge.message033')
  restoreAccessToken()
}

const runRegistration = async () => {
  const options = await getPasskeyRegistrationOptions({
    passkeyName: passkeyName.value,
    authenticatorType: 'auto',
  })
  const credential = await createPasskeyCredential(options)

  if (!credential) {
    throw new ApiError({ errorCode: 'PASSKEY_CREDENTIAL_UNAVAILABLE' })
  }

  await verifyPasskeyRegistration({
    ...extractRegistrationData(credential),
    passkeyName: passkeyName.value,
  })

  await completeWithSuccess({
    registered: true,
    errorCode: 'REQUEST_SUCCESS',
  })
  successMessage.value = uiMessage('ui.desktopPasskeyBridge.message034')
  restoreAccessToken()
}

const submitTotp = async () => {
  if (!pendingMfaChallenge.value) {
    return
  }

  const normalized = totpInput.value.trim()
  if (totpMode.value === 'totp' && !/^[0-9]{6}$/.test(normalized)) {
    ElMessage.error(t('ui.desktopPasskeyBridge.message035'))
    return
  }
  if (totpMode.value === 'recovery' && !/^[A-Z]{8}$/.test(normalized.toUpperCase())) {
    ElMessage.error(t('ui.desktopPasskeyBridge.message036'))
    return
  }

  busy.value = true
  errorMessage.value = ''
  try {
    const response = await verifyTOTPForLogin(
      totpMode.value === 'recovery'
        ? {
            challengeId: pendingMfaChallenge.value.challengeId,
            recoveryCode: normalized.toUpperCase(),
          }
        : { challengeId: pendingMfaChallenge.value.challengeId, code: normalized },
    )

    pendingMfaChallenge.value = null
    const transferCode = await createDesktopTransferCode(response.accessToken)
    await completeWithSuccess({
      accessToken: response.accessToken,
      transferCode,
      errorCode: 'REQUEST_SUCCESS',
    })
    successMessage.value = uiMessage('ui.desktopPasskeyBridge.message037')
  } catch (error: unknown) {
    errorMessage.value = localizedError(error, 'ui.desktopPasskeyBridge.message038')
  } finally {
    busy.value = false
  }
}

const handleTotpInput = (value: string | number) => {
  const input = String(value || '')
  totpInput.value =
    totpMode.value === 'totp'
      ? input.replace(/[^\d]/g, '').slice(0, 6)
      : input
          .replace(/[^a-zA-Z]/g, '')
          .toUpperCase()
          .slice(0, 8)
}

const setTotpMode = (value: TotpMode) => {
  totpMode.value = value
  totpInput.value = ''
}

const runFlow = async () => {
  if (!isWebAuthnSupported()) {
    throw new ApiError({ errorCode: 'PASSKEY_UNSUPPORTED' })
  }

  switch (mode.value) {
    case 'mfa':
      if (!mfaChallengeId.value) {
        throw new ApiError({ errorCode: 'BAD_REQUEST' })
      }
      await runPasskeyMfa(mfaChallengeId.value)
      return
    case 'sensitive':
      await runSensitiveVerification()
      return
    case 'register':
      await runRegistration()
      return
    default:
      await runPasskeyLogin()
  }
}

const retry = async () => {
  pendingMfaChallenge.value = null
  totpInput.value = ''
  errorMessage.value = ''
  completed.value = false
  await start()
}

const start = async () => {
  busy.value = true
  errorMessage.value = ''

  try {
    await runFlow()
  } catch (error: unknown) {
    let result = error instanceof ApiError ? error : new ApiError({ errorCode: 'REQUEST_FAILED' })

    if (error instanceof Error) {
      if (error.name === 'NotAllowedError') {
        result = new ApiError({ errorCode: 'PASSKEY_CANCELLED' })
      } else if (error.name === 'NotSupportedError') {
        result = new ApiError({ errorCode: 'PASSKEY_UNSUPPORTED' })
      } else if (error.name === 'SecurityError') {
        result = new ApiError({ errorCode: 'PASSKEY_SECURITY_ERROR' })
      } else if (error.name === 'InvalidStateError') {
        result = new ApiError({ errorCode: 'PASSKEY_CREDENTIAL_UNAVAILABLE' })
      }
    }

    await failWithResult(result)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const hash = parseHash()

  mode.value = (params.get('mode') as BridgeMode) || 'login'
  callbackUrl.value = params.get('callback') || ''
  state.value = params.get('state') || ''
  mfaChallengeId.value = params.get('mfaChallengeId') || ''
  passkeyName.value = params.get('passkeyName') || passkeyName.value
  accessToken.value = hash.get('accessToken') || ''

  sanitizeUrl()

  if (!callbackUrl.value || !state.value) {
    errorMessage.value = uiMessage('ui.desktopPasskeyBridge.message039')
    return
  }
  if (!isLoopbackCallback(callbackUrl.value)) {
    errorMessage.value = uiMessage('ui.desktopPasskeyBridge.message040')
    return
  }

  const apiBaseUrl = params.get('apiBaseUrl') || getRequestBaseUrl()
  setRequestBaseUrl(apiBaseUrl)

  if (accessToken.value) {
    previousAccessToken.value = getStoredAccessToken()
    setStoredAccessToken(accessToken.value)
  }

  await start()
})

onBeforeUnmount(() => {
  restoreAccessToken()
})
</script>

<style scoped>
:global(html),
:global(body),
:global(#app) {
  width: 100%;
  height: 100%;
  margin: 0;
}

.desktop-bridge-shell {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: linear-gradient(135deg, #f6f4ee 0%, #fff8de 100%);
}

.desktop-bridge-shell.dark {
  background: linear-gradient(135deg, #1f1f1f 0%, #2d2414 100%);
}

.desktop-bridge-card {
  width: min(720px, 100%);
  padding: 28px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.12);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.desktop-bridge-shell.dark .desktop-bridge-card {
  background: rgba(23, 23, 23, 0.94);
  color: #f5f5f5;
}

.desktop-bridge-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.desktop-bridge-header h1 {
  margin: 0;
  font-size: 28px;
}

.desktop-bridge-header p,
.pending-block p,
.bridge-footer span,
.panel-hint {
  margin: 8px 0 0;
  color: #5b6473;
}

.desktop-bridge-shell.dark .desktop-bridge-header p,
.desktop-bridge-shell.dark .pending-block p,
.desktop-bridge-shell.dark .bridge-footer span,
.desktop-bridge-shell.dark .panel-hint {
  color: #c9ced8;
}

.pending-block,
.result-block {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  border-radius: 18px;
  background: #fbfaf5;
}

.pending-block strong {
  display: block;
  margin-bottom: 6px;
}

.pending-skeleton {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.pending-skeleton-hero {
  width: 52px;
  height: 52px;
  border-radius: 16px;
}

.pending-skeleton-copy {
  width: 160px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pending-skeleton-title {
  width: 72%;
  height: 14px;
}

.pending-skeleton-line {
  width: 100%;
  height: 12px;
}

.pending-skeleton-line.short {
  width: 82%;
}

.result-block.success {
  color: #0b7a37;
}

.totp-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mode-switch {
  display: flex;
  gap: 12px;
}

.mode-chip {
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #fff;
  color: inherit;
  padding: 10px 16px;
  border-radius: 999px;
  cursor: pointer;
}

.mode-chip.active {
  background: #ffb90f;
  border-color: #ffb90f;
}

.panel-actions,
.bridge-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
</style>
