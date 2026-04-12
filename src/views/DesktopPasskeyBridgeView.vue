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
        :title="errorMessage"
        show-icon
      />

      <div v-else-if="completed" class="result-block success">
        <el-icon><CircleCheckFilled /></el-icon>
        <span>{{ successMessage }}</span>
      </div>

      <div v-else-if="needsTotp" class="totp-panel">
        <p class="panel-hint">
          当前账号还需要完成一次 MFA 验证。浏览器桥接页会直接把结果回传给桌面应用。
        </p>

        <div class="mode-switch" role="tablist" aria-label="验证码类型选择">
          <button
            type="button"
            class="mode-chip"
            :class="{ active: totpMode === 'totp' }"
            @click="setTotpMode('totp')"
          >
            动态码
          </button>
          <button
            type="button"
            class="mode-chip"
            :class="{ active: totpMode === 'recovery' }"
            @click="setTotpMode('recovery')"
          >
            恢复码
          </button>
        </div>

        <el-input
          v-model="totpInput"
          :placeholder="totpMode === 'totp' ? '输入 6 位动态码' : '输入 8 位恢复码'"
          :maxlength="totpMode === 'totp' ? 6 : 8"
          @input="handleTotpInput"
          @keyup.enter="submitTotp"
        />

        <div class="panel-actions">
          <el-button @click="retry" :disabled="busy">重新开始</el-button>
          <el-button type="primary" @click="submitTotp" :loading="busy">完成验证</el-button>
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
        <el-button v-if="showRetryButton" @click="retry" :disabled="busy">重试</el-button>
        <span>完成后桌面端会自动继续，无需手动复制 Token。</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
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
  message?: string
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
const successMessage = ref('验证已完成，桌面端会自动继续。')
const errorMessage = ref('')
const totpInput = ref('')
const totpMode = ref<TotpMode>('totp')
const pendingMfaChallenge = ref<MFAChallenge | null>(null)

const needsTotp = computed(() => pendingMfaChallenge.value !== null)
const statusLabel = computed(() => {
  if (errorMessage.value) return '失败'
  if (completed.value) return '已完成'
  if (needsTotp.value) return '等待输入'
  return '处理中'
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
      return '桌面端 Passkey MFA'
    case 'sensitive':
      return '桌面端敏感操作验证'
    case 'register':
      return '桌面端新增 Passkey'
    default:
      return '桌面端 Passkey 登录'
  }
})
const subtitle = computed(() => {
  switch (mode.value) {
    case 'mfa':
      return '当前浏览器负责完成 WebAuthn MFA，然后把结果回传给桌面应用。'
    case 'sensitive':
      return '当前浏览器负责完成敏感操作的 Passkey 验证，然后返回桌面应用。'
    case 'register':
      return '当前浏览器负责创建并登记 Passkey，完成后桌面端会自动刷新列表。'
    default:
      return '当前浏览器负责完成 WebAuthn 登录，然后把结果回传给桌面应用。'
  }
})
const pendingTitle = computed(() => {
  switch (mode.value) {
    case 'mfa':
      return '正在完成二次验证'
    case 'sensitive':
      return '正在等待 Passkey 验证'
    case 'register':
      return '正在创建新的 Passkey'
    default:
      return '正在等待 Passkey 登录'
  }
})
const pendingDescription = computed(() => {
  switch (mode.value) {
    case 'mfa':
      return '如果系统弹出了浏览器或安全密钥提示，请按提示完成验证。'
    case 'sensitive':
      return '验证完成后，桌面端会继续执行刚才的敏感操作。'
    case 'register':
      return '如果系统弹出了浏览器或安全密钥提示，请按提示创建并保存新的 Passkey。'
    default:
      return '如果系统弹出了浏览器或安全密钥提示，请按提示完成登录。'
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

const callbackToDesktop = async (payload: BridgeCallbackPayload) => {
  if (!callbackUrl.value) {
    throw new Error('回调地址缺失')
  }

  await fetch(callbackUrl.value, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
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
    const transfer = await createSessionTransfer('desktop')
    return transfer.transferCode
  } finally {
    if (currentAccessToken) {
      setStoredAccessToken(currentAccessToken)
    } else {
      clearStoredAccessToken()
    }
  }
}

const failWithMessage = async (message: string) => {
  errorMessage.value = message
  try {
    await callbackToDesktop({
      status: 'error',
      state: state.value,
      message,
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
      ElMessage.info('还需要完成一次 TOTP 验证')
      return
    }

    throw new Error('当前账号需要额外的 MFA 验证，但浏览器桥接页无法继续完成')
  }

  const transferCode = await createDesktopTransferCode(response.accessToken)
  await completeWithSuccess({
    accessToken: response.accessToken,
    transferCode,
    message: 'Passkey 登录成功',
  })
  successMessage.value = 'Passkey 登录成功，桌面端会自动进入工作台。'
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
    throw new Error('未获取到凭证')
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
    throw new Error('未获取到凭证')
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
    message: 'MFA 验证成功',
  })
  successMessage.value = 'Passkey 二次验证成功，桌面端会自动继续登录。'
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
    throw new Error('未获取到凭证')
  }

  await verifyPasskeySensitiveOperation(options.challengeId, extractAuthenticationData(credential))
  await completeWithSuccess({
    verified: true,
    message: '敏感操作验证成功',
  })
  successMessage.value = '敏感操作验证成功，可以直接返回桌面端。'
  restoreAccessToken()
}

const runRegistration = async () => {
  const options = await getPasskeyRegistrationOptions({
    passkeyName: passkeyName.value,
    authenticatorType: 'auto',
  })
  const credential = await createPasskeyCredential(options)

  if (!credential) {
    throw new Error('未创建凭证')
  }

  await verifyPasskeyRegistration({
    ...extractRegistrationData(credential),
    passkeyName: passkeyName.value,
  })

  await completeWithSuccess({
    registered: true,
    message: 'Passkey 已添加',
  })
  successMessage.value = 'Passkey 已添加，桌面端会自动刷新列表。'
  restoreAccessToken()
}

const submitTotp = async () => {
  if (!pendingMfaChallenge.value) {
    return
  }

  const normalized = totpInput.value.trim()
  if (totpMode.value === 'totp' && !/^[0-9]{6}$/.test(normalized)) {
    ElMessage.error('请输入 6 位动态码')
    return
  }
  if (totpMode.value === 'recovery' && !/^[A-Z]{8}$/.test(normalized.toUpperCase())) {
    ElMessage.error('请输入 8 位大写恢复码')
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
      message: 'MFA 验证成功',
    })
    successMessage.value = 'MFA 验证成功，桌面端会自动继续登录。'
  } catch (error: unknown) {
    errorMessage.value = error instanceof Error ? error.message : 'MFA 验证失败'
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
    throw new Error('当前浏览器不支持 WebAuthn / Passkey')
  }

  switch (mode.value) {
    case 'mfa':
      if (!mfaChallengeId.value) {
        throw new Error('缺少 MFA 挑战信息')
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
    let message = error instanceof Error ? error.message : 'Passkey 验证失败'

    if (error instanceof Error) {
      if (error.name === 'NotAllowedError') {
        message = '用户取消了当前 Passkey 操作'
      } else if (error.name === 'NotSupportedError') {
        message = '当前浏览器不支持该 Passkey 操作'
      } else if (error.name === 'SecurityError') {
        message = '浏览器安全校验失败，请确认当前使用受信任地址'
      } else if (error.name === 'InvalidStateError') {
        message = '当前账号没有可用的 Passkey，或 Passkey 状态不正确'
      }
    }

    await failWithMessage(message)
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
    errorMessage.value = '桌面端回调参数缺失'
    return
  }
  if (!isLoopbackCallback(callbackUrl.value)) {
    errorMessage.value = '非法回调地址，桥接页已拒绝继续执行'
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
