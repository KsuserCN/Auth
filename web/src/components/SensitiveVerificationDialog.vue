<template>
  <el-dialog
    :model-value="modelValue"
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
      <el-button v-if="appleBound" class="apple-verify-button" :loading="appleLoading" @click="handleAppleVerify">
        <i class="fa-brands fa-apple" aria-hidden="true"></i>&nbsp; 使用 Apple 验证
      </el-button>
      <div class="method-list">
        <button v-for="method in allMethods" :key="method" type="button" class="method-item"
          :disabled="methodSelecting || !isMethodSelectable(method)" @click="selectMethod(method)">
          <span class="method-icon"><el-icon><component :is="methodIconMap[method]" /></el-icon></span>
          <span class="method-copy">
            <span class="method-title">{{ methodLabelMap[method] }}</span>
            <span class="method-desc">{{ methodDescMap[method] }}</span>
          </span>
          <el-icon class="method-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </div>

    <div v-else-if="step === 'password'" class="panel">
      <p class="subtitle">请输入登录密码</p>
      <el-form ref="passwordFormRef" :model="passwordInput" :rules="passwordRules" label-position="top">
        <el-form-item prop="password">
          <el-input v-model="passwordInput.password" type="password" show-password placeholder="输入密码"
            autocomplete="current-password" @keyup.enter="handlePasswordVerify" />
        </el-form-item>
      </el-form>
      <div class="actions">
        <el-button @click="backToMethod">返回</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="handlePasswordVerify">验证</el-button>
      </div>
    </div>

    <div v-else-if="step === 'email-code'" class="panel">
      <p class="subtitle">验证码已发送到您的邮箱</p>
      <el-form ref="codeFormRef" :model="codeInput" :rules="codeRules" label-position="top">
        <el-form-item prop="code">
          <el-input v-model="codeInput.code" placeholder="输入6位验证码" maxlength="6"
            @input="codeInput.code = codeInput.code.replace(/[^\d]/g, '')" @keyup.enter="handleCodeVerify" />
        </el-form-item>
      </el-form>
      <div class="code-actions">
        <el-button v-if="!canResendCode" disabled>{{ codeCountdown }}s 后可重发</el-button>
        <el-button v-else @click="resendCode">重新发送验证码</el-button>
      </div>
      <div class="actions">
        <el-button @click="backToMethod">返回</el-button>
        <el-button type="primary" :loading="codeLoading" @click="handleCodeVerify">验证</el-button>
      </div>
    </div>

    <div v-else-if="step === 'passkey'" class="panel">
      <p class="subtitle">请按照浏览器提示完成 Passkey 验证</p>
      <div class="actions">
        <el-button @click="backToMethod">返回</el-button>
        <el-button type="primary" :loading="passkeyLoading" @click="handlePasskeyVerify">
          {{ passkeyLoading ? '验证中...' : '验证身份' }}
        </el-button>
      </div>
    </div>

    <div v-else-if="step === 'qr'" class="panel">
      <p class="subtitle">请使用已登录手机端扫描二维码完成敏感验证</p>
      <div class="qr-wrap">
        <img v-if="qrCodeImage" :src="qrCodeImage" alt="敏感验证扫码二维码" class="qr-image" />
        <div v-else class="qr-placeholder">正在生成二维码...</div>
        <div class="qr-meta">剩余有效期：{{ Math.max(qrExpiresInSeconds, 0) }} 秒</div>
      </div>
      <div class="actions">
        <el-button @click="backToMethod">返回</el-button>
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
        <button type="button" class="chip" :class="{ active: totpMode === 'recovery' }"
          @click="setTotpMode('recovery')">
          恢复码
        </button>
      </div>
      <el-form ref="totpFormRef" :model="totpInput" :rules="totpRules" label-position="top">
        <el-form-item prop="code">
          <el-input v-model="totpInput.code" :placeholder="totpMode === 'totp' ? '输入6位动态码' : '输入8位恢复码'"
            :maxlength="totpMode === 'totp' ? 6 : 8" @input="handleTotpInput" @keyup.enter="handleTotpVerify" />
        </el-form-item>
      </el-form>
      <div class="actions">
        <el-button @click="backToMethod">返回</el-button>
        <el-button type="primary" :loading="totpLoading" @click="handleTotpVerify">验证</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import QRCode from 'qrcode'
import { ArrowRight, Connection, Iphone, Key, Lock, Message } from '@element-plus/icons-vue'
import {
  checkSensitiveVerification,
  getAppleStatus,
  getPasskeySensitiveVerificationOptions,
  initQrSensitive,
  pollQrStatus,
  sendSensitiveVerificationCode,
  type QrChallengeStatusResponse,
  verifyPasskeySensitiveOperation,
  verifySensitiveOperation,
} from '@/api/auth'
import { prepareAppleSignIn, verifySensitiveWithApple } from '@/utils/appleSignIn'
import { extractAuthenticationData, getPasskeyCredential, isWebAuthnSupported } from '@/utils/webauthn'

const props = withDefaults(defineProps<{
  modelValue: boolean
  disableQrMethod?: boolean
}>(), {
  disableQrMethod: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
  (e: 'cancel'): void
}>()

const allMethods: Array<'password' | 'email-code' | 'passkey' | 'totp' | 'qr'> = [
  'password',
  'email-code',
  'passkey',
  'totp',
  'qr',
]

const methodLabelMap: Record<'password' | 'email-code' | 'passkey' | 'totp' | 'qr', string> = {
  password: '密码验证',
  'email-code': '邮箱验证码',
  passkey: 'Passkey 验证',
  totp: 'TOTP 验证',
  qr: '扫码验证',
}

const methodDescMap: Record<'password' | 'email-code' | 'passkey' | 'totp' | 'qr', string> = {
  password: '输入登录密码',
  'email-code': '使用邮箱验证码',
  passkey: '使用生物识别或安全密钥',
  totp: '输入动态码或恢复码',
  qr: '使用已登录手机端扫码',
}

const methodIconMap = {
  password: Lock,
  'email-code': Message,
  passkey: Key,
  totp: Connection,
  qr: Iphone,
}

const step = ref<'method' | 'password' | 'email-code' | 'passkey' | 'totp' | 'qr'>('method')
const initializing = ref(false)
const methodSelecting = ref(false)
const availableMethods = ref<Array<'password' | 'email-code' | 'passkey' | 'totp' | 'qr'>>([
  'password',
  'email-code',
  'passkey',
  'totp',
  'qr',
])
const passkeySupported = ref(false)
const appleBound = ref(false)
const appleLoading = ref(false)

const handleAppleVerify = async () => {
  if (appleLoading.value) return
  appleLoading.value = true
  try {
    await verifySensitiveWithApple()
    ElMessage.success('Apple 身份验证成功')
    emit('success')
    setVisible(false)
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : 'Apple 验证失败，请重试')
  } finally {
    appleLoading.value = false
  }
}

const passwordFormRef = ref<FormInstance>()
const codeFormRef = ref<FormInstance>()
const totpFormRef = ref<FormInstance>()

const passwordInput = ref({ password: '' })
const codeInput = ref({ code: '' })
const totpInput = ref({ code: '' })
const totpMode = ref<'totp' | 'recovery'>('totp')

const passwordRules = {
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const codeRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码应为6位数字', trigger: 'blur' },
  ],
}

const totpRules = computed(() => ({
  code: [
    { required: true, message: '请输入验证码或恢复码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        const v = (value || '').trim()
        if (totpMode.value === 'totp' && !/^[0-9]{6}$/.test(v)) {
          callback(new Error('请输入 6 位数字动态码'))
          return
        }

        if (totpMode.value === 'recovery' && !/^[A-Z]{8}$/.test(v)) {
          callback(new Error('请输入 8 位大写字母恢复码'))
          return
        }

        callback()
      },
      trigger: 'blur',
    },
  ],
}))

const passwordLoading = ref(false)
const codeLoading = ref(false)
const passkeyLoading = ref(false)
const totpLoading = ref(false)
const qrRefreshing = ref(false)
const qrCodeImage = ref('')
const qrChallengeId = ref('')
const qrPollToken = ref('')
const qrExpiresInSeconds = ref(0)
let qrPollingTimer: number | null = null

const codeCountdown = ref(0)
const canResendCode = ref(false)
let codeCountdownTimer: number | null = null

const isMethodSelectable = (method: 'password' | 'email-code' | 'passkey' | 'totp' | 'qr') => {
  if (props.disableQrMethod && method === 'qr') return false
  if (!availableMethods.value.includes(method)) return false
  if (method === 'passkey' && !passkeySupported.value) return false
  return true
}

const setVisible = (value: boolean) => {
  emit('update:modelValue', value)
}

const resetState = () => {
  step.value = 'method'
  initializing.value = false
  passwordInput.value.password = ''
  codeInput.value.code = ''
  totpInput.value.code = ''
  totpMode.value = 'totp'
  cleanupCodeCountdown()
  cleanupQrPolling()
  qrCodeImage.value = ''
  qrChallengeId.value = ''
  qrPollToken.value = ''
  qrExpiresInSeconds.value = 0
}

const handleClose = () => {
  emit('cancel')
  setVisible(false)
  resetState()
}

const initDialog = async () => {
  initializing.value = true
  passkeySupported.value = isWebAuthnSupported()

  try {
    const status = await checkSensitiveVerification()
    appleBound.value = await getAppleStatus().then((result) => result.enabled).catch(() => false)
    if (appleBound.value) void prepareAppleSignIn().catch(() => {})
    if (status.verified && status.remainingSeconds > 0) {
      emit('success')
      setVisible(false)
      return
    }

    const methods = status.methods ?? allMethods
    if (appleBound.value && status.preferredMethod === 'apple') {
      step.value = 'method'
      return
    }
    const sanitizedMethods = methods.filter((item): item is typeof allMethods[number] =>
      allMethods.includes(item),
    )

    const fallbackMethods = props.disableQrMethod
      ? allMethods.filter((item) => item !== 'qr')
      : allMethods
    const mergedMethodsBase = sanitizedMethods.length > 1 ? sanitizedMethods : fallbackMethods
    const mergedMethods = props.disableQrMethod
      ? mergedMethodsBase.filter((item) => item !== 'qr')
      : [...new Set([...mergedMethodsBase, 'qr'])]
    availableMethods.value = mergedMethods as Array<'password' | 'email-code' | 'passkey' | 'totp' | 'qr'>

    // 默认进入偏好方式，同时保留返回入口让用户切换其他方式。
    const preferredMethod = status.preferredMethod
    const defaultMethod = [preferredMethod, ...availableMethods.value].find(
      (item) => !!item && isMethodSelectable(item as 'password' | 'email-code' | 'passkey' | 'totp' | 'qr'),
    ) as 'password' | 'email-code' | 'passkey' | 'totp' | 'qr' | undefined

    if (defaultMethod) {
      await selectMethod(defaultMethod)
      return
    }

    step.value = 'method'
  } catch (error) {
    console.error('Load sensitive verification preference failed:', error)
    ElMessage.error('加载验证方式失败，请稍后重试')
  } finally {
    initializing.value = false
  }
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (visible) {
      resetState()
      await initDialog()
      return
    }

    resetState()
  },
)

const selectMethod = async (method: 'password' | 'email-code' | 'passkey' | 'totp' | 'qr') => {
  if (methodSelecting.value) return
  if (!isMethodSelectable(method)) {
    ElMessage.error('当前不可使用该验证方式')
    return
  }

  methodSelecting.value = true
  try {
    if (method === 'email-code') {
      await sendCode()
    } else if (method === 'qr') {
      await startQrVerification()
    } else {
      step.value = method
    }
  } finally {
    methodSelecting.value = false
  }
}

const backToMethod = () => {
  step.value = 'method'
  codeInput.value.code = ''
  totpInput.value.code = ''
  totpMode.value = 'totp'
  cleanupCodeCountdown()
  cleanupQrPolling()
}

const handleQrStatusApproved = async (status: QrChallengeStatusResponse) => {
  if (status.verified) {
    verifySuccess()
    return
  }
  ElMessage.error('扫码验证结果无效，请重试')
}

const pollQrVerification = async () => {
  if (!qrChallengeId.value || !qrPollToken.value) return

  try {
    const status = await pollQrStatus(qrChallengeId.value, qrPollToken.value)
    qrExpiresInSeconds.value = status.expiresInSeconds || 0
    if (status.status === 'pending') return

    cleanupQrPolling()
    if (status.status === 'approved') {
      await handleQrStatusApproved(status)
      return
    }
    if (status.status === 'rejected') {
      ElMessage.error('扫码请求已被拒绝，请刷新二维码')
      return
    }
    ElMessage.warning('二维码已过期，请刷新二维码')
  } catch (error) {
    cleanupQrPolling()
    console.error('QR sensitive polling failed:', error)
  }
}

const startQrPolling = () => {
  cleanupQrPolling()
  qrPollingTimer = window.setInterval(() => {
    void pollQrVerification()
  }, 2000)
  void pollQrVerification()
}

const startQrVerification = async () => {
  try {
    qrRefreshing.value = true
    const payload = await initQrSensitive()
    qrChallengeId.value = payload.challengeId
    qrPollToken.value = payload.pollToken
    qrExpiresInSeconds.value = payload.expiresInSeconds
    qrCodeImage.value = await QRCode.toDataURL(payload.qrText, { width: 220, margin: 1 })
    step.value = 'qr'
    startQrPolling()
  } catch (error) {
    console.error('Init QR sensitive verification failed:', error)
    ElMessage.error('初始化扫码验证失败，请重试')
  } finally {
    qrRefreshing.value = false
  }
}

const refreshQrVerification = async () => {
  await startQrVerification()
}

const verifySuccess = () => {
  ElMessage.success('验证成功')
  emit('success')
  setVisible(false)
}

const handlePasswordVerify = async () => {
  try {
    await passwordFormRef.value?.validate()
    passwordLoading.value = true
    await verifySensitiveOperation({
      method: 'password',
      password: passwordInput.value.password,
    })
    verifySuccess()
  } catch (error) {
    console.error('Password verify failed:', error)
  } finally {
    passwordLoading.value = false
  }
}

const sendCode = async () => {
  await sendSensitiveVerificationCode()
  step.value = 'email-code'
  codeInput.value.code = ''
  startCodeCountdown()
  ElMessage.success('验证码已发送')
}

const resendCode = async () => {
  try {
    codeLoading.value = true
    await sendSensitiveVerificationCode()
    codeInput.value.code = ''
    startCodeCountdown()
    ElMessage.success('验证码已重新发送')
  } catch (error) {
    console.error('Resend code failed:', error)
  } finally {
    codeLoading.value = false
  }
}

const handleCodeVerify = async () => {
  try {
    await codeFormRef.value?.validate()
    codeLoading.value = true
    await verifySensitiveOperation({ method: 'email-code', code: codeInput.value.code })
    verifySuccess()
  } catch (error) {
    console.error('Code verify failed:', error)
  } finally {
    codeLoading.value = false
  }
}

const handleTotpInput = (value: string) => {
  if (totpMode.value === 'totp') {
    totpInput.value.code = value.replace(/[^\d]/g, '').slice(0, 6)
    return
  }

  totpInput.value.code = value.replace(/[^a-zA-Z]/g, '').toUpperCase().slice(0, 8)
}

const setTotpMode = (mode: 'totp' | 'recovery') => {
  if (totpMode.value === mode) return
  totpMode.value = mode
  totpInput.value.code = ''
}

const handleTotpVerify = async () => {
  try {
    await totpFormRef.value?.validate()
    totpLoading.value = true

    const normalizedInput = totpInput.value.code.trim()
    await verifySensitiveOperation(
      totpMode.value === 'recovery'
        ? { method: 'totp', recoveryCode: normalizedInput.toUpperCase() }
        : { method: 'totp', code: normalizedInput },
    )

    verifySuccess()
  } catch (error) {
    console.error('TOTP verify failed:', error)
  } finally {
    totpLoading.value = false
  }
}

const handlePasskeyVerify = async () => {
  if (passkeyLoading.value) return
  if (!passkeySupported.value) {
    ElMessage.error('当前浏览器不支持 Passkey')
    return
  }

  try {
    passkeyLoading.value = true
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

    const authData = extractAuthenticationData(credential)
    await verifyPasskeySensitiveOperation(options.challengeId, authData)
    verifySuccess()
  } catch (error) {
    if (error instanceof Error && error.name === 'NotAllowedError') {
      ElMessage.error('用户取消了认证')
      return
    }
    console.error('Passkey verify failed:', error)
  } finally {
    passkeyLoading.value = false
  }
}

const startCodeCountdown = () => {
  cleanupCodeCountdown()
  codeCountdown.value = 60
  canResendCode.value = false

  codeCountdownTimer = window.setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0) {
      cleanupCodeCountdown()
      canResendCode.value = true
    }
  }, 1000)
}

const cleanupCodeCountdown = () => {
  if (codeCountdownTimer !== null) {
    clearInterval(codeCountdownTimer)
    codeCountdownTimer = null
  }
}

const cleanupQrPolling = () => {
  if (qrPollingTimer !== null) {
    clearInterval(qrPollingTimer)
    qrPollingTimer = null
  }
}

onBeforeUnmount(() => {
  cleanupCodeCountdown()
  cleanupQrPolling()
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

.apple-verify-button {
  width: 100%;
  height: 42px;
  border-radius: 10px;
  font-weight: 600;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 2px;
}

.actions :deep(.el-button),
.code-actions :deep(.el-button) {
  min-height: 38px;
  border-radius: 10px;
  font-weight: 600;
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

.code-actions {
  display: flex;
  justify-content: flex-start;
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
