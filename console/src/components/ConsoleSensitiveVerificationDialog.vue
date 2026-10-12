<template>
  <el-dialog
    :model-value="verificationDialogVisible"
    class="sensitive-verification-dialog"
    title="敏感操作验证"
    width="560px"
    align-center
    :close-on-click-modal="true"
    @close="handleClose"
  >
    <div v-if="initializing" class="loading-panel">
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else-if="step === 'method'" class="panel">
      <div class="verification-intro">
        <span class="intro-icon"><el-icon><Lock /></el-icon></span>
        <div>
          <div class="intro-title">确认您的身份</div>
          <p class="subtitle">请选择一种方式继续敏感操作</p>
        </div>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <div class="method-list">
        <button
          v-for="method in availableMethods"
          :key="method"
          type="button"
          class="method-item"
          @click="selectMethod(method)"
        >
          <span class="method-icon"><el-icon><component :is="methodIconMap[method]" /></el-icon></span>
          <span class="method-copy">
            <span class="method-title">{{ methodLabelMap[method] }}</span>
            <span class="method-desc">{{ methodDescMap[method] }}</span>
          </span>
          <el-icon class="method-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
      <el-button v-if="error" @click="loadContext">重新加载验证方式</el-button>
    </div>

    <div v-else-if="step === 'password'" class="panel">
      <p class="subtitle">请输入登录密码</p>
      <el-form ref="passwordFormRef" :model="passwordInput" :rules="passwordRules" label-position="top">
        <el-form-item prop="password">
          <el-input
            v-model="passwordInput.password"
            type="password"
            show-password
            placeholder="输入密码"
            autocomplete="current-password"
            :disabled="passwordLoading"
            @input="error = ''"
            @keyup.enter="handlePasswordVerify"
          />
        </el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <div class="actions">
        <el-button @click="backToMethod">返回</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="handlePasswordVerify">验证</el-button>
      </div>
    </div>

    <div v-else-if="step === 'passkey'" class="panel">
      <p class="subtitle">请按照浏览器提示完成 Passkey 验证</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <div class="actions">
        <el-button :disabled="passkeyLoading" @click="backToMethod">返回</el-button>
        <el-button type="primary" :loading="passkeyLoading" @click="handlePasskeyVerify">
          {{ passkeyLoading ? '验证中...' : '验证身份' }}
        </el-button>
      </div>
    </div>

    <div v-else-if="step === 'qr'" class="panel">
      <p class="subtitle">请使用已登录手机端扫描二维码完成敏感验证</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <div class="qr-wrap">
        <img
          v-if="qrCodeImage"
          :src="qrCodeImage"
          alt="敏感验证扫码二维码"
          class="qr-image"
        />
        <div v-else class="qr-placeholder">{{ qrRefreshing ? '正在生成二维码...' : '二维码暂不可用' }}</div>
        <div class="qr-meta">剩余有效期：{{ Math.max(qrExpiresInSeconds, 0) }} 秒</div>
      </div>
      <div class="actions">
        <el-button :disabled="qrRefreshing" @click="backToMethod">返回</el-button>
        <el-button type="primary" :loading="qrRefreshing" @click="refreshQrVerification">
          刷新二维码
        </el-button>
      </div>
    </div>

    <div v-else-if="step === 'totp'" class="panel">
      <p class="subtitle">请输入动态码或恢复码</p>
      <div class="totp-mode-switch" role="tablist" aria-label="TOTP 模式切换">
        <button type="button" class="chip" :class="{ active: totpMode === 'totp' }" @click="setTotpMode('totp')">
          动态码
        </button>
        <button type="button" class="chip" :class="{ active: totpMode === 'recovery' }" @click="setTotpMode('recovery')">
          恢复码
        </button>
      </div>
      <el-form ref="totpFormRef" :model="totpInput" :rules="totpRules" label-position="top">
        <el-form-item prop="code">
          <el-input
            v-model="totpInput.code"
            :placeholder="totpMode === 'totp' ? '输入6位动态码' : '输入8位恢复码'"
            :maxlength="totpMode === 'totp' ? 6 : 8"
            :disabled="totpLoading"
            autocomplete="one-time-code"
            @input="handleTotpInput"
            @keyup.enter="handleTotpVerify"
          />
        </el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <div class="actions">
        <el-button :disabled="totpLoading" @click="backToMethod">返回</el-button>
        <el-button type="primary" :loading="totpLoading" @click="handleTotpVerify">验证</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { ArrowRight, Connection, Iphone, Key, Lock } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { consoleApi, type ConsolePasskeyCredential, type ConsoleVerificationContext } from '@/api/console'
import { useSessionStore } from '@/stores/session'
import {
  completeConsoleVerification,
  verificationDialogVisible,
  verificationRequestId,
} from '@/utils/consoleVerification'

type VerificationMethod = 'password' | 'passkey' | 'totp' | 'qr'
type VerificationStep = 'method' | VerificationMethod

const store = useSessionStore()
const step = ref<VerificationStep>('method')
const initializing = ref(false)
const error = ref('')
const context = ref<ConsoleVerificationContext | null>(null)
const passkeySupported = typeof window !== 'undefined' && typeof window.PublicKeyCredential === 'function'
const passwordLoading = ref(false)
const passkeyLoading = ref(false)
const totpLoading = ref(false)
const passwordFormRef = ref<FormInstance>()
const totpFormRef = ref<FormInstance>()
const passwordInput = ref({ password: '' })
const totpInput = ref({ code: '' })
const totpMode = ref<'totp' | 'recovery'>('totp')
const abortController = ref<AbortController | null>(null)
const qrRefreshing = ref(false)
const qrCodeImage = ref('')
const qrChallengeId = ref('')
const qrPollToken = ref('')
const qrExpiresInSeconds = ref(0)
let qrPollingTimer: number | null = null
let qrCountdownTimer: number | null = null
let qrGeneration = 0
let qrPollInFlightGeneration: number | null = null

const methodLabelMap: Record<VerificationMethod, string> = {
  password: '密码验证',
  passkey: 'Passkey 验证',
  totp: 'TOTP 验证',
  qr: '扫码验证',
}
const methodDescMap: Record<VerificationMethod, string> = {
  password: '输入登录密码',
  passkey: '使用生物识别或安全密钥',
  totp: '输入动态码或恢复码',
  qr: '使用已登录手机端扫码',
}
const methodIconMap = { password: Lock, passkey: Key, totp: Connection, qr: Iphone }
const availableMethods = computed<VerificationMethod[]>(() => {
  const methods: VerificationMethod[] = []
  if (context.value?.hasPassword) methods.push('password')
  if (context.value?.hasPasskey && passkeySupported) methods.push('passkey')
  if (context.value?.hasTotp) methods.push('totp')
  methods.push('qr')
  return methods
})
const passwordRules = {
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const totpRules = computed(() => ({
  code: [
    { required: true, message: '请输入验证码或恢复码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (failure?: Error) => void) => {
        const input = (value || '').trim()
        const valid = totpMode.value === 'totp' ? /^[0-9]{6}$/.test(input) : /^[A-Z]{8}$/.test(input)
        if (!valid) {
          callback(new Error(totpMode.value === 'totp' ? '请输入 6 位数字动态码' : '请输入 8 位大写字母恢复码'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}))

watch(verificationDialogVisible, (visible) => {
  if (visible) {
    resetState()
    void loadContext()
  } else {
    abortController.value?.abort()
    resetState()
  }
})

async function loadContext() {
  const requestId = verificationRequestId.value
  if (!requestId) return
  initializing.value = true
  error.value = ''
  try {
    const result = await consoleApi.verificationContext(requestId)
    if (!verificationDialogVisible.value || verificationRequestId.value !== requestId) return
    context.value = result
    if (!availableMethods.value.length) error.value = '当前账号没有可用的验证方式。'
  } catch (failure) {
    if (verificationDialogVisible.value && verificationRequestId.value === requestId)
      error.value = failure instanceof Error ? failure.message : '加载验证方式失败，请稍后重试。'
  } finally {
    if (verificationRequestId.value === requestId) initializing.value = false
  }
}

function selectMethod(method: VerificationMethod) {
  if (method === 'qr') {
    void startQrVerification()
    return
  }
  step.value = method
  error.value = ''
}

function backToMethod() {
  cleanupQrPolling()
  step.value = 'method'
  error.value = ''
  passwordInput.value.password = ''
  totpInput.value.code = ''
  totpMode.value = 'totp'
  qrCodeImage.value = ''
  qrChallengeId.value = ''
  qrPollToken.value = ''
  qrExpiresInSeconds.value = 0
}

async function startQrVerification() {
  cleanupQrPolling()
  const generation = qrGeneration
  const requestId = verificationRequestId.value
  qrRefreshing.value = true
  qrCodeImage.value = ''
  qrChallengeId.value = ''
  qrPollToken.value = ''
  qrExpiresInSeconds.value = 0
  error.value = ''
  step.value = 'qr'
  try {
    const payload = await consoleApi.initQrVerification(requestId)
    if (!isCurrentQrFlow(generation, requestId)) return
    qrChallengeId.value = payload.challengeId
    qrPollToken.value = payload.pollToken
    qrExpiresInSeconds.value = payload.expiresInSeconds
    qrCodeImage.value = await QRCode.toDataURL(payload.qrText, { width: 220, margin: 1 })
    if (!isCurrentQrFlow(generation, requestId)) return
    startQrPolling(generation, requestId)
  } catch (failure) {
    if (isCurrentQrFlow(generation, requestId))
      showVerificationError(failure instanceof Error ? failure : new Error('生成二维码失败，请重试。'))
  } finally {
    if (qrGeneration === generation) qrRefreshing.value = false
  }
}

async function refreshQrVerification() {
  await startQrVerification()
}

function startQrPolling(generation: number, requestId: string) {
  cleanupQrTimersOnly()
  qrPollingTimer = window.setInterval(() => void pollQrVerification(generation, requestId), 2000)
  qrCountdownTimer = window.setInterval(() => {
    if (qrGeneration !== generation) return
    qrExpiresInSeconds.value = Math.max(0, qrExpiresInSeconds.value - 1)
    if (qrExpiresInSeconds.value === 0) {
      cleanupQrPolling()
      error.value = '二维码已过期，请刷新后重试。'
    }
  }, 1000)
  void pollQrVerification(generation, requestId)
}

async function pollQrVerification(generation: number, requestId: string) {
  if (!isCurrentQrFlow(generation, requestId) || !qrChallengeId.value || !qrPollToken.value
    || qrPollInFlightGeneration === generation) return
  qrPollInFlightGeneration = generation
  try {
    const status = await consoleApi.pollQrVerification(
      requestId, qrChallengeId.value, qrPollToken.value,
    )
    if (!isCurrentQrFlow(generation, requestId)) return
    qrExpiresInSeconds.value = status.expiresInSeconds
    if (status.status === 'pending') return
    if (status.status === 'approved') {
      cleanupQrPolling()
      if (!status.session) {
        error.value = '手机端已确认，但 Console 会话未能完成验证，请重试。'
        return
      }
      store.session = status.session
      verifySuccess()
    } else if (status.status === 'rejected') {
      cleanupQrPolling()
      qrExpiresInSeconds.value = 0
      error.value = '手机端已拒绝本次验证，请重新生成二维码。'
    } else {
      cleanupQrPolling()
      error.value = '二维码已过期，请刷新后重试。'
    }
  } catch (failure) {
    if (isCurrentQrFlow(generation, requestId)) {
      cleanupQrPolling()
      showVerificationError(failure)
    }
  } finally {
    if (qrPollInFlightGeneration === generation) qrPollInFlightGeneration = null
  }
}

function isCurrentQrFlow(generation: number, requestId: string) {
  return qrGeneration === generation && verificationDialogVisible.value
    && verificationRequestId.value === requestId
}

function cleanupQrTimersOnly() {
  if (qrPollingTimer !== null) window.clearInterval(qrPollingTimer)
  if (qrCountdownTimer !== null) window.clearInterval(qrCountdownTimer)
  qrPollingTimer = null
  qrCountdownTimer = null
}

function cleanupQrPolling() {
  qrGeneration += 1
  cleanupQrTimersOnly()
}

async function handlePasswordVerify() {
  if (passwordLoading.value) return
  try {
    await passwordFormRef.value?.validate()
    passwordLoading.value = true
    const session = await consoleApi.verifyPassword(verificationRequestId.value, passwordInput.value.password)
    store.session = session
    verifySuccess()
  } catch (failure) {
    showVerificationError(failure)
  } finally {
    passwordLoading.value = false
  }
}

async function handleTotpVerify() {
  if (totpLoading.value) return
  try {
    await totpFormRef.value?.validate()
    totpLoading.value = true
    const code = totpInput.value.code.trim()
    const session = await consoleApi.verifyTotp(
      verificationRequestId.value,
      totpMode.value === 'recovery'
        ? { recoveryCode: code.toUpperCase() }
        : { code },
    )
    store.session = session
    verifySuccess()
  } catch (failure) {
    showVerificationError(failure)
  } finally {
    totpLoading.value = false
  }
}

async function handlePasskeyVerify() {
  if (passkeyLoading.value) return
  if (!passkeySupported) {
    error.value = '当前浏览器不支持 Passkey。'
    return
  }

  passkeyLoading.value = true
  error.value = ''
  try {
    const options = await consoleApi.passkeyVerificationOptions(verificationRequestId.value)
    const controller = new AbortController()
    abortController.value = controller
    const credential = await navigator.credentials.get({
      publicKey: {
        challenge: decodeBase64Url(options.challenge),
        timeout: Number(options.timeout),
        rpId: options.rpId,
        userVerification: options.userVerification as UserVerificationRequirement,
      },
      signal: controller.signal,
    })
    if (!credential || !(credential instanceof PublicKeyCredential)) throw new Error('未获取到凭证。')
    const assertion = credential.response as AuthenticatorAssertionResponse
    const data: ConsolePasskeyCredential = {
      credentialRawId: encodeBase64Url(credential.rawId),
      clientDataJSON: encodeBase64Url(assertion.clientDataJSON),
      authenticatorData: encodeBase64Url(assertion.authenticatorData),
      signature: encodeBase64Url(assertion.signature),
    }
    store.session = await consoleApi.verifyPasskey(verificationRequestId.value, options.challengeId, data)
    verifySuccess()
  } catch (failure) {
    if (failure instanceof Error && ['NotAllowedError', 'AbortError'].includes(failure.name)) {
      error.value = '用户取消了认证，可以重新尝试或返回选择其他验证方式。'
    } else if (failure instanceof Error && failure.name === 'SecurityError') {
      error.value = '此 Passkey 暂时无法用于管理面板，请切换密码或 TOTP。'
    } else {
      showVerificationError(failure)
    }
  } finally {
    passkeyLoading.value = false
    abortController.value = null
  }
}

function setTotpMode(mode: 'totp' | 'recovery') {
  if (totpMode.value === mode) return
  totpMode.value = mode
  totpInput.value.code = ''
}

function handleTotpInput(value: string) {
  error.value = ''
  totpInput.value.code = totpMode.value === 'totp'
    ? value.replace(/[^\d]/g, '').slice(0, 6)
    : value.replace(/[^a-zA-Z]/g, '').toUpperCase().slice(0, 8)
}

function verifySuccess() {
  ElMessage.success('验证成功')
  completeConsoleVerification(true)
}

function showVerificationError(failure: unknown) {
  error.value = failure instanceof Error ? failure.message : '验证失败，请重试。'
}

function handleClose() {
  abortController.value?.abort()
  cleanupQrPolling()
  completeConsoleVerification(false)
}

function resetState() {
  cleanupQrPolling()
  step.value = 'method'
  initializing.value = false
  error.value = ''
  context.value = null
  passwordInput.value.password = ''
  totpInput.value.code = ''
  totpMode.value = 'totp'
  passwordLoading.value = false
  passkeyLoading.value = false
  totpLoading.value = false
  qrRefreshing.value = false
  qrCodeImage.value = ''
  qrChallengeId.value = ''
  qrPollToken.value = ''
  qrExpiresInSeconds.value = 0
}

function decodeBase64Url(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const bytes = Uint8Array.from(atob(padded), (character) => character.charCodeAt(0))
  return bytes.buffer
}

function encodeBase64Url(value: ArrayBuffer): string {
  const bytes = new Uint8Array(value)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

onBeforeUnmount(() => {
  abortController.value?.abort()
  cleanupQrPolling()
  if (verificationDialogVisible.value) completeConsoleVerification(false)
})
</script>

<style scoped>
:deep(.sensitive-verification-dialog.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.18);
}
:deep(.sensitive-verification-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
:deep(.sensitive-verification-dialog .el-dialog__title) {
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 650;
}
:deep(.sensitive-verification-dialog .el-dialog__headerbtn) {
  top: 17px;
  right: 18px;
  width: 32px;
  height: 32px;
  border-radius: 9px;
}
:deep(.sensitive-verification-dialog .el-dialog__headerbtn:hover) {
  background: var(--el-fill-color-light);
}
:deep(.sensitive-verification-dialog .el-dialog__body) {
  padding: 22px 24px 24px;
}
.loading-panel {
  min-height: 200px;
  display: flex;
  align-items: center;
}
.panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.subtitle {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}
.verification-intro {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-fill-color-extra-light);
}
.intro-icon,
.method-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.intro-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  font-size: 20px;
}
.intro-title {
  margin-bottom: 3px;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 650;
}
.method-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.method-item {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  text-align: left;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color);
  padding: 13px 12px;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}
.method-item:hover:not(:disabled) {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-fill-color-extra-light);
  box-shadow: 0 5px 14px color-mix(in srgb, var(--el-color-primary) 10%, transparent);
  transform: translateY(-1px);
}
.method-item:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
.method-item:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}
.method-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  font-size: 17px;
}
.method-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.method-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-size: 13px;
}
.method-desc {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.35;
}
.method-arrow {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  transition: color 0.2s ease, transform 0.2s ease;
}
.method-item:hover:not(:disabled) .method-arrow {
  color: var(--el-color-primary);
  transform: translateX(2px);
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 2px;
}
.actions :deep(.el-button) {
  min-height: 38px;
  border-radius: 10px;
  font-weight: 600;
}
.qr-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 8px 0 4px;
}
.qr-image {
  width: 200px;
  height: 200px;
  border-radius: 14px;
  padding: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  box-sizing: border-box;
}
.qr-placeholder {
  width: 200px;
  height: 200px;
  border-radius: 14px;
  border: 1px dashed var(--el-border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.qr-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.panel :deep(.el-form-item) {
  margin-bottom: 0;
}
.panel :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}
.panel :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset, 0 0 0 3px var(--el-color-primary-light-9);
}
.totp-mode-switch {
  display: flex;
  gap: 8px;
  padding: 4px;
  border-radius: 11px;
  background: var(--el-fill-color-light);
  width: fit-content;
}
.chip {
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  padding: 7px 12px;
  color: var(--el-text-color-secondary);
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.chip.active {
  border-color: var(--el-border-color-lighter);
  background: var(--el-bg-color);
  color: var(--el-color-primary);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}
@media (max-width: 640px) {
  :deep(.sensitive-verification-dialog .el-dialog__header) {
    padding: 18px 18px 14px;
  }
  :deep(.sensitive-verification-dialog .el-dialog__body) {
    padding: 18px;
  }
  .method-list {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .method-item {
    padding: 11px 12px;
  }
}
</style>
