<template>
  <el-dialog
    :model-value="modelValue"
    class="sensitive-verification-dialog"
    :title="t('ui.sensitiveVerificationDialog.message001')"
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
        <span class="intro-icon"
          ><el-icon><Lock /></el-icon
        ></span>
        <div>
          <div class="intro-title">{{ t('ui.sensitiveVerificationDialog.message002') }}</div>
          <p class="subtitle">{{ t('ui.sensitiveVerificationDialog.message003') }}</p>
        </div>
      </div>
      <el-button
        v-if="appleBound && !enrollmentMode"
        class="apple-verify-button"
        :loading="appleLoading"
        @click="handleAppleVerify"
      >
        <i class="fa-brands fa-apple" aria-hidden="true"></i
        >{{ t('ui.sensitiveVerificationDialog.message004') }}
      </el-button>
      <div class="method-list">
        <button
          v-for="method in allMethods"
          :key="method"
          type="button"
          class="method-item"
          :disabled="methodSelecting || !isMethodSelectable(method)"
          @click="selectMethod(method)"
        >
          <span class="method-icon"
            ><el-icon><component :is="methodIconMap[method]" /></el-icon
          ></span>
          <span class="method-copy">
            <span class="method-title">{{ methodLabelMap[method] }}</span>
            <span class="method-desc">{{ methodDescMap[method] }}</span>
          </span>
          <el-icon class="method-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </div>

    <div v-else-if="step === 'password'" class="panel">
      <p class="subtitle">{{ t('ui.sensitiveVerificationDialog.message005') }}</p>
      <el-form
        ref="passwordFormRef"
        :model="passwordInput"
        :rules="passwordRules"
        label-position="top"
        @submit.prevent="handlePasswordVerify"
      >
        <el-form-item prop="password">
          <el-input
            v-model="passwordInput.password"
            type="password"
            show-password
            :placeholder="t('ui.sensitiveVerificationDialog.message006')"
            autocomplete="current-password"
          />
        </el-form-item>
      </el-form>
      <div class="actions">
        <el-button @click="backToMethod">{{
          t('ui.sensitiveVerificationDialog.message007')
        }}</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="handlePasswordVerify">{{
          t('ui.sensitiveVerificationDialog.message008')
        }}</el-button>
      </div>
    </div>

    <div v-else-if="step === 'email-code'" class="panel">
      <p class="subtitle">{{ t('ui.sensitiveVerificationDialog.message009') }}</p>
      <el-form
        ref="codeFormRef"
        :model="codeInput"
        :rules="codeRules"
        label-position="top"
        @submit.prevent="handleCodeVerify"
      >
        <el-form-item prop="code">
          <el-input
            v-model="codeInput.code"
            :placeholder="t('ui.sensitiveVerificationDialog.message010')"
            maxlength="6"
            @input="codeInput.code = codeInput.code.replace(/[^\d]/g, '')"
          />
        </el-form-item>
      </el-form>
      <div class="code-actions">
        <el-button v-if="!canResendCode" disabled
          >{{ codeCountdown }}{{ t('ui.sensitiveVerificationDialog.message011') }}</el-button
        >
        <el-button v-else @click="resendCode">{{
          t('ui.sensitiveVerificationDialog.message012')
        }}</el-button>
      </div>
      <div class="actions">
        <el-button @click="backToMethod">{{
          t('ui.sensitiveVerificationDialog.message007')
        }}</el-button>
        <el-button type="primary" :loading="codeLoading" @click="handleCodeVerify">{{
          t('ui.sensitiveVerificationDialog.message008')
        }}</el-button>
      </div>
    </div>

    <div v-else-if="step === 'passkey'" class="panel">
      <p class="subtitle">{{ t('ui.sensitiveVerificationDialog.message013') }}</p>
      <div class="actions">
        <el-button @click="backToMethod">{{
          t('ui.sensitiveVerificationDialog.message007')
        }}</el-button>
        <el-button type="primary" :loading="passkeyLoading" @click="handlePasskeyVerify">
          {{
            passkeyLoading
              ? t('ui.sensitiveVerificationDialog.message014')
              : t('ui.sensitiveVerificationDialog.message015')
          }}
        </el-button>
      </div>
    </div>

    <div v-else-if="step === 'qr'" class="panel">
      <p class="subtitle">{{ t('ui.sensitiveVerificationDialog.message016') }}</p>
      <div class="qr-wrap">
        <img
          v-if="qrCodeImage"
          :src="qrCodeImage"
          :alt="t('ui.sensitiveVerificationDialog.message017')"
          class="qr-image"
        />
        <div v-else class="qr-placeholder">
          {{ t('ui.mobileBridgeQrSidebarAction.message004') }}
        </div>
        <div class="qr-meta">
          {{ t('ui.accountRecoveryCard.message009') }}{{ Math.max(qrExpiresInSeconds, 0) }}
          {{ t('ui.accountRecoveryCard.message010') }}
        </div>
      </div>
      <div class="actions">
        <el-button @click="backToMethod">{{
          t('ui.sensitiveVerificationDialog.message007')
        }}</el-button>
        <el-button type="primary" :loading="qrRefreshing" @click="refreshQrVerification">
          {{ t('ui.mobileBridgeQrSidebarAction.message006') }}
        </el-button>
      </div>
    </div>

    <div v-else-if="step === 'totp'" class="panel">
      <p class="subtitle">{{ t('ui.sensitiveVerificationDialog.message018') }}</p>
      <div
        class="totp-mode-switch"
        role="tablist"
        :aria-label="t('ui.sensitiveVerificationDialog.message019')"
      >
        <button
          type="button"
          class="chip"
          :class="{ active: totpMode === 'totp' }"
          @click="setTotpMode('totp')"
        >
          {{ t('ui.sensitiveVerificationDialog.message020') }}
        </button>
        <button
          type="button"
          class="chip"
          :class="{ active: totpMode === 'recovery' }"
          @click="setTotpMode('recovery')"
        >
          {{ t('ui.sensitiveVerificationDialog.message021') }}
        </button>
      </div>
      <el-form
        ref="totpFormRef"
        :model="totpInput"
        :rules="totpRules"
        label-position="top"
        @submit.prevent="handleTotpVerify"
      >
        <el-form-item prop="code">
          <el-input
            v-model="totpInput.code"
            :placeholder="
              totpMode === 'totp'
                ? t('ui.sensitiveVerificationDialog.message022')
                : t('ui.sensitiveVerificationDialog.message023')
            "
            :maxlength="totpMode === 'totp' ? 6 : 8"
            @input="handleTotpInput"
          />
        </el-form-item>
      </el-form>
      <div class="actions">
        <el-button @click="backToMethod">{{
          t('ui.sensitiveVerificationDialog.message007')
        }}</el-button>
        <el-button type="primary" :loading="totpLoading" @click="handleTotpVerify">{{
          t('ui.sensitiveVerificationDialog.message008')
        }}</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { t, LocalizedError } from '@/i18n'

import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import QRCode from 'qrcode'
import { ArrowRight, Connection, Iphone, Key, Lock, Message } from '@element-plus/icons-vue'
import {
  checkSensitiveVerification,
  getAppleStatus,
  getPasskeySensitiveVerificationOptions,
  getTotpEnrollmentPasskeyOptions,
  initQrSensitive,
  pollQrStatus,
  sendSensitiveVerificationCode,
  type QrChallengeStatusResponse,
  type QrSensitivePurpose,
  verifyPasskeySensitiveOperation,
  verifySensitiveOperation,
  verifyTotpEnrollment,
  verifyTotpEnrollmentPasskey,
} from '@/api/auth'
import { prepareAppleSignIn, verifySensitiveWithApple } from '@/utils/appleSignIn'
import {
  extractAuthenticationData,
  getPasskeyCredential,
  isWebAuthnSupported,
} from '@/utils/webauthn'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    disableQrMethod?: boolean
    purpose?: QrSensitivePurpose
    enrollmentMode?: boolean
  }>(),
  {
    disableQrMethod: false,
    purpose: 'login-options',
    enrollmentMode: false,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', proofId?: string): void
  (e: 'cancel'): void
}>()

const allMethods: Array<'password' | 'email-code' | 'passkey' | 'totp' | 'qr'> = [
  'password',
  'email-code',
  'passkey',
  'totp',
  'qr',
]

const methodLabelMap = computed<
  Record<'password' | 'email-code' | 'passkey' | 'totp' | 'qr', string>
>(() => ({
  password: t('ui.sensitiveVerificationDialog.message024'),
  'email-code': t('ui.sensitiveVerificationDialog.message025'),
  passkey: t('ui.sensitiveVerificationDialog.message026'),
  totp: t('ui.sensitiveVerificationDialog.message027'),
  qr: t('ui.sensitiveVerificationDialog.message028'),
}))

const methodDescMap = computed<
  Record<'password' | 'email-code' | 'passkey' | 'totp' | 'qr', string>
>(() => ({
  password: t('ui.sensitiveVerificationDialog.message029'),
  'email-code': t('ui.sensitiveVerificationDialog.message030'),
  passkey: t('ui.sensitiveVerificationDialog.message031'),
  totp: t('ui.sensitiveVerificationDialog.message032'),
  qr: t('ui.sensitiveVerificationDialog.message033'),
}))

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
  if (props.enrollmentMode) return
  if (appleLoading.value) return
  appleLoading.value = true
  try {
    await verifySensitiveWithApple()
    ElMessage.success(t('ui.sensitiveVerificationDialog.message034'))
    emit('success')
    setVisible(false)
  } catch (error: unknown) {
    ElMessage.error(
      error instanceof Error ? error.message : t('ui.sensitiveVerificationDialog.message035'),
    )
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

const passwordRules = computed(() => ({
  password: [
    { required: true, message: t('ui.sensitiveVerificationDialog.message036'), trigger: 'blur' },
  ],
}))

const codeRules = computed(() => ({
  code: [
    { required: true, message: t('ui.sensitiveVerificationDialog.message037'), trigger: 'blur' },
    { len: 6, message: t('ui.sensitiveVerificationDialog.message038'), trigger: 'blur' },
  ],
}))

const totpRules = computed(() => ({
  code: [
    { required: true, message: t('ui.sensitiveVerificationDialog.message039'), trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        const v = (value || '').trim()
        if (totpMode.value === 'totp' && !/^[0-9]{6}$/.test(v)) {
          callback(new LocalizedError('ui.sensitiveVerificationDialog.message040'))
          return
        }

        if (totpMode.value === 'recovery' && !/^[A-Z]{8}$/.test(v)) {
          callback(new LocalizedError('ui.sensitiveVerificationDialog.message041'))
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
  if (props.enrollmentMode && (method === 'qr' || method === 'totp')) return false
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
    const status = await checkSensitiveVerification(props.purpose)
    appleBound.value = !props.enrollmentMode && await getAppleStatus()
      .then((result) => result.enabled)
      .catch(() => false)
    if (appleBound.value) void prepareAppleSignIn().catch(() => {})
    if (!props.enrollmentMode && status.verified && status.remainingSeconds > 0) {
      emit('success')
      setVisible(false)
      return
    }

    const methods = status.methods ?? allMethods
    if (appleBound.value && status.preferredMethod === 'apple') {
      step.value = 'method'
      return
    }
    const sanitizedMethods = methods.filter((item): item is (typeof allMethods)[number] =>
      allMethods.includes(item),
    )

    if (props.enrollmentMode) {
      availableMethods.value = sanitizedMethods.filter((method) =>
        method === 'password' || method === 'email-code' || method === 'passkey',
      )
      const preferred = [status.preferredMethod, ...availableMethods.value].find((method) =>
        method && isMethodSelectable(method as (typeof allMethods)[number]),
      ) as (typeof allMethods)[number] | undefined
      if (preferred) await selectMethod(preferred)
      else step.value = 'method'
      return
    }

    const fallbackMethods = props.disableQrMethod
      ? allMethods.filter((item) => item !== 'qr')
      : allMethods
    const mergedMethodsBase = sanitizedMethods.length > 1 ? sanitizedMethods : fallbackMethods
    const mergedMethods = props.disableQrMethod
      ? mergedMethodsBase.filter((item) => item !== 'qr')
      : [...new Set([...mergedMethodsBase, 'qr'])]
    availableMethods.value = mergedMethods as Array<
      'password' | 'email-code' | 'passkey' | 'totp' | 'qr'
    >

    // 默认进入偏好方式，同时保留返回入口让用户切换其他方式。
    const preferredMethod = status.preferredMethod
    const defaultMethod = [preferredMethod, ...availableMethods.value].find(
      (item) =>
        !!item && isMethodSelectable(item as 'password' | 'email-code' | 'passkey' | 'totp' | 'qr'),
    ) as 'password' | 'email-code' | 'passkey' | 'totp' | 'qr' | undefined

    if (defaultMethod) {
      await selectMethod(defaultMethod)
      return
    }

    step.value = 'method'
  } catch (error) {
    console.error('Load sensitive verification preference failed:', error)
    ElMessage.error(t('ui.sensitiveVerificationDialog.message042'))
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
    ElMessage.error(t('ui.sensitiveVerificationDialog.message043'))
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
  ElMessage.error(t('ui.sensitiveVerificationDialog.message044'))
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
      ElMessage.error(t('ui.sensitiveVerificationDialog.message045'))
      return
    }
    ElMessage.warning(t('ui.sensitiveVerificationDialog.message046'))
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
    const payload = await initQrSensitive(props.purpose)
    qrChallengeId.value = payload.challengeId
    qrPollToken.value = payload.pollToken
    qrExpiresInSeconds.value = payload.expiresInSeconds
    qrCodeImage.value = await QRCode.toDataURL(payload.qrText, { width: 220, margin: 1 })
    step.value = 'qr'
    startQrPolling()
  } catch (error) {
    console.error('Init QR sensitive verification failed:', error)
    ElMessage.error(t('ui.sensitiveVerificationDialog.message047'))
  } finally {
    qrRefreshing.value = false
  }
}

const refreshQrVerification = async () => {
  await startQrVerification()
}

const verifySuccess = (proofId?: string) => {
  if (!props.modelValue) return
  if (props.enrollmentMode && !proofId) throw new LocalizedError('ui.sensitiveVerificationDialog.message035')
  ElMessage.success(t('ui.sensitiveVerificationDialog.message048'))
  if (proofId) emit('success', proofId)
  else emit('success')
  setVisible(false)
}

const handlePasswordVerify = async () => {
  if (passwordLoading.value) return
  passwordLoading.value = true
  try {
    await passwordFormRef.value?.validate()
    const input = {
      method: 'password',
      password: passwordInput.value.password,
    } as const
    if (props.enrollmentMode) verifySuccess((await verifyTotpEnrollment(input)).proofId)
    else {
      await verifySensitiveOperation(input)
      verifySuccess()
    }
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
  ElMessage.success(t('ui.sensitiveVerificationDialog.message049'))
}

const resendCode = async () => {
  try {
    codeLoading.value = true
    await sendSensitiveVerificationCode()
    codeInput.value.code = ''
    startCodeCountdown()
    ElMessage.success(t('ui.sensitiveVerificationDialog.message050'))
  } catch (error) {
    console.error('Resend code failed:', error)
  } finally {
    codeLoading.value = false
  }
}

const handleCodeVerify = async () => {
  if (codeLoading.value) return
  codeLoading.value = true
  try {
    await codeFormRef.value?.validate()
    const input = { method: 'email-code', code: codeInput.value.code } as const
    if (props.enrollmentMode) verifySuccess((await verifyTotpEnrollment(input)).proofId)
    else {
      await verifySensitiveOperation(input)
      verifySuccess()
    }
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

  totpInput.value.code = value
    .replace(/[^a-zA-Z]/g, '')
    .toUpperCase()
    .slice(0, 8)
}

const setTotpMode = (mode: 'totp' | 'recovery') => {
  if (totpMode.value === mode) return
  totpMode.value = mode
  totpInput.value.code = ''
}

const handleTotpVerify = async () => {
  if (totpLoading.value) return
  totpLoading.value = true
  try {
    await totpFormRef.value?.validate()

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
    ElMessage.error(t('ui.sensitiveVerificationDialog.message051'))
    return
  }

  try {
    passkeyLoading.value = true
    const options = props.enrollmentMode
      ? await getTotpEnrollmentPasskeyOptions()
      : await getPasskeySensitiveVerificationOptions()
    const credential = await getPasskeyCredential({
      challenge: options.challenge,
      timeout: options.timeout,
      rpId: options.rpId,
      userVerification: options.userVerification,
      allowCredentials: options.allowCredentials,
    })

    if (!credential) {
      throw new LocalizedError('ui.sensitiveVerificationDialog.message052')
    }

    const authData = extractAuthenticationData(credential)
    if (props.enrollmentMode) {
      verifySuccess((await verifyTotpEnrollmentPasskey(options.challengeId, authData)).proofId)
    } else {
      await verifyPasskeySensitiveOperation(options.challengeId, authData)
      verifySuccess()
    }
  } catch (error) {
    if (error instanceof Error && error.name === 'NotAllowedError') {
      ElMessage.error(t('ui.sensitiveVerificationDialog.message053'))
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
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
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
  transition:
    color 0.2s ease,
    transform 0.2s ease;
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
  box-shadow:
    0 0 0 1px var(--el-color-primary) inset,
    0 0 0 3px var(--el-color-primary-light-9);
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
