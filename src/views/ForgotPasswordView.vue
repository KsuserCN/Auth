<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">账号恢复</h1>
        <p class="login-description">
          使用另一台已登录设备为当前设备背书。恢复成功后会立即重置密码，并撤销旧会话。
        </p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Monitor />
            </el-icon>
            <span class="feature-text">已登录设备先完成敏感验证</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Connection />
            </el-icon>
            <span class="feature-text">新设备扫码或输入恢复码进入</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Key />
            </el-icon>
            <span class="feature-text">设置新密码并自动签发新会话</span>
          </div>
        </div>
      </div>

      <div class="login-right">
        <Transition :name="stepDirection === 'forward' ? 'step-slide-forward' : 'step-slide-backward'" mode="out-in">
          <div v-if="step === 'recovery-code'" key="recovery-code" class="step-container">
            <h2 class="step-title">输入恢复码</h2>
            <p class="step-subtitle">请在另一台已登录设备中发起“账号恢复”，然后扫码或输入恢复码</p>

            <el-form ref="recoveryFormRef" :model="recoveryInput" :rules="recoveryRules" label-position="top">
              <el-form-item prop="recoveryCode">
                <el-input
                  v-model="recoveryInput.recoveryCode"
                  placeholder="输入一次性恢复码"
                  @keyup.enter="loadRecoveryContext"
                  autofocus
                />
              </el-form-item>
            </el-form>

            <div class="qr-entry-card">
              <div class="qr-entry-header">
                <div>
                  <div class="qr-entry-title">使用手机扫码背书</div>
                  <p class="qr-entry-desc">
                    如果你的 Android 客户端已登录，可在手机端打开“安全”页后扫码，为当前恢复请求直接背书。
                  </p>
                </div>
                <el-button :loading="qrRecoveryLoading" @click="handleInitQrRecovery">
                  {{ qrRecoveryImage ? '刷新二维码' : '生成二维码' }}
                </el-button>
              </div>

              <div v-if="qrRecoveryImage" class="qr-recovery-panel">
                <img :src="qrRecoveryImage" alt="手机扫码背书二维码" class="qr-recovery-image" />
                <div class="qr-recovery-meta">
                  <span>剩余有效期：{{ Math.max(qrRecoveryExpiresInSeconds, 0) }} 秒</span>
                  <span>手机端扫码确认后，当前页面会自动回填恢复码。</span>
                </div>
              </div>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="goToLogin">返回登录</el-button>
              <el-button class="next-btn" :loading="recoveryLoading" @click="loadRecoveryContext">
                确认恢复授权
              </el-button>
            </div>
          </div>

          <div v-else-if="step === 'new-password'" key="new-password" class="step-container">
            <h2 class="step-title">设置新密码</h2>
            <p class="step-subtitle">恢复授权已确认，请设置一个新的安全密码</p>

            <div v-if="recoveryStatus" class="recovery-context-card">
              <div class="context-title">当前恢复对象</div>
              <div class="context-account">{{ recoveryStatus.username }} · {{ recoveryStatus.maskedEmail }}</div>
              <div class="context-meta">
                <span>背书设备：{{ recoveryStatus.sponsorClientName || '已登录设备' }}</span>
                <span>位置：{{ recoveryStatus.sponsorIpLocation || '未知位置' }}</span>
                <span>剩余有效期：{{ Math.max(recoveryStatus.expiresInSeconds, 0) }} 秒</span>
              </div>
            </div>

            <el-form ref="newPasswordFormRef" :model="passwordInput" :rules="newPasswordRules" label-position="top">
              <el-form-item prop="newPassword">
                <el-input
                  v-model="passwordInput.newPassword"
                  type="password"
                  placeholder="输入新密码"
                  show-password
                  autocomplete="new-password"
                  @keyup.enter="goToConfirmStep"
                  autofocus
                />
              </el-form-item>
            </el-form>

            <div class="password-requirements">
              <div class="requirement-title">密码要求：</div>
              <div
                v-for="(item, index) in requirementItems"
                :key="index"
                class="requirement-item"
                :class="{ met: item.met }"
              >
                <el-icon :size="14">
                  <CircleCheck v-if="item.met" />
                  <CircleClose v-else />
                </el-icon>
                <span>{{ item.text }}</span>
              </div>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToRecoveryCode">返回</el-button>
              <el-button class="next-btn" @click="goToConfirmStep">下一步</el-button>
            </div>
          </div>

          <div v-else key="confirm-password" class="step-container">
            <h2 class="step-title">确认新密码</h2>
            <p class="step-subtitle">请再次输入新密码以完成恢复</p>

            <el-form ref="confirmPasswordFormRef" :model="passwordInput" :rules="confirmPasswordRules" label-position="top">
              <el-form-item prop="confirmPassword">
                <el-input
                  v-model="passwordInput.confirmPassword"
                  type="password"
                  placeholder="再次输入新密码"
                  show-password
                  autocomplete="new-password"
                  @keyup.enter="handleSubmit"
                  autofocus
                />
              </el-form-item>
            </el-form>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToNewPassword">返回</el-button>
              <el-button class="next-btn" :loading="submitLoading" @click="handleSubmit">
                完成恢复
              </el-button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useDark } from '@vueuse/core'
import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  CircleCheck,
  CircleClose,
  Connection,
  Key,
  Monitor,
} from '@element-plus/icons-vue'
import {
  completeAccountRecovery,
  getAccountRecoveryStatus,
  getPasswordRequirement,
  initQrAccountRecovery,
  pollQrStatus,
  type AccountRecoveryStatusResponse,
  type PasswordRequirement,
  type QrChallengeStatusResponse,
} from '@/api/auth'
import { storeAuthSession } from '@/utils/authSession'
import { setRequestBaseUrl } from '@/utils/request'

const router = useRouter()
const route = useRoute()

const recoveryFormRef = ref<FormInstance>()
const newPasswordFormRef = ref<FormInstance>()
const confirmPasswordFormRef = ref<FormInstance>()

const step = ref<'recovery-code' | 'new-password' | 'confirm-password'>('recovery-code')
const stepDirection = ref<'forward' | 'backward'>('forward')
const stepOrder = ['recovery-code', 'new-password', 'confirm-password']

const updateStep = (nextStep: 'recovery-code' | 'new-password' | 'confirm-password') => {
  const currentIndex = stepOrder.indexOf(step.value)
  const nextIndex = stepOrder.indexOf(nextStep)
  stepDirection.value = nextIndex > currentIndex ? 'forward' : 'backward'
  step.value = nextStep
}

const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})

const recoveryLoading = ref(false)
const submitLoading = ref(false)
const recoveryStatus = ref<AccountRecoveryStatusResponse | null>(null)
const recoveryInput = ref({ recoveryCode: '' })
const passwordInput = ref({ newPassword: '', confirmPassword: '' })
const qrRecoveryLoading = ref(false)
const qrRecoveryImage = ref('')
const qrRecoveryExpiresInSeconds = ref(0)
const qrRecoveryChallengeId = ref('')
const qrRecoveryPollToken = ref('')

const passwordRequirement = ref<PasswordRequirement>({
  minLength: 6,
  maxLength: 66,
  requireUppercase: true,
  requireLowercase: true,
  requireDigits: true,
  requireSpecialChars: false,
  rejectCommonWeakPasswords: true,
  requirementMessage: '密码强度不足：需包含大写字母、小写字母、数字',
})
let recoveryCountdownTimer: number | null = null
let qrRecoveryPollingTimer: number | null = null

const clearRecoveryCountdown = () => {
  if (recoveryCountdownTimer !== null) {
    window.clearInterval(recoveryCountdownTimer)
    recoveryCountdownTimer = null
  }
}

const clearQrRecoveryPolling = () => {
  if (qrRecoveryPollingTimer !== null) {
    window.clearInterval(qrRecoveryPollingTimer)
    qrRecoveryPollingTimer = null
  }
}

const resetQrRecoveryFlow = () => {
  clearQrRecoveryPolling()
  qrRecoveryChallengeId.value = ''
  qrRecoveryPollToken.value = ''
  qrRecoveryImage.value = ''
  qrRecoveryExpiresInSeconds.value = 0
}

const startRecoveryCountdown = () => {
  clearRecoveryCountdown()
  recoveryCountdownTimer = window.setInterval(() => {
    if (!recoveryStatus.value) {
      clearRecoveryCountdown()
      return
    }
    if (recoveryStatus.value.expiresInSeconds <= 0) {
      clearRecoveryCountdown()
      return
    }
    recoveryStatus.value = {
      ...recoveryStatus.value,
      expiresInSeconds: recoveryStatus.value.expiresInSeconds - 1,
    }
  }, 1000)
}

const syncRecoveryCodeQuery = (recoveryCode?: string) => {
  const nextQuery = { ...route.query }
  if (recoveryCode) {
    nextQuery.recoveryCode = recoveryCode
  } else {
    delete nextQuery.recoveryCode
  }
  router.replace({ path: '/forgot-password', query: nextQuery })
}

const validateRecoveryCode = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const normalized = (value || '').trim()
  if (!normalized) {
    callback(new Error('请输入恢复码'))
    return
  }
  if (!/^[A-Za-z0-9_-]{16,}$/.test(normalized)) {
    callback(new Error('恢复码格式不正确'))
    return
  }
  callback()
}

const validatePassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const req = passwordRequirement.value

  if (value === '') {
    callback(new Error('请输入新密码'))
  } else if (value.length < req.minLength || value.length > req.maxLength) {
    callback(new Error(`密码长度必须在${req.minLength}-${req.maxLength}个字符之间`))
  } else if (req.requireUppercase && !/[A-Z]/.test(value)) {
    callback(new Error('密码必须包含至少一个大写字母'))
  } else if (req.requireLowercase && !/[a-z]/.test(value)) {
    callback(new Error('密码必须包含至少一个小写字母'))
  } else if (req.requireDigits && !/\d/.test(value)) {
    callback(new Error('密码必须包含至少一个数字'))
  } else if (req.requireSpecialChars && !/[!@#$%^&*(),.?":{}|<>]/.test(value)) {
    callback(new Error('密码必须包含至少一个特殊字符'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value === '') {
    callback(new Error('请再次输入新密码'))
  } else if (value !== passwordInput.value.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const recoveryRules: FormRules = {
  recoveryCode: [{ required: true, validator: validateRecoveryCode, trigger: 'blur' }],
}

const newPasswordRules: FormRules = {
  newPassword: [{ required: true, validator: validatePassword, trigger: 'blur' }],
}

const confirmPasswordRules: FormRules = {
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }],
}

const requirements = computed(() => {
  const pwd = passwordInput.value.newPassword
  const req = passwordRequirement.value

  return {
    length: pwd.length >= req.minLength && pwd.length <= req.maxLength,
    hasUpperCase: !req.requireUppercase || /[A-Z]/.test(pwd),
    hasLowerCase: !req.requireLowercase || /[a-z]/.test(pwd),
    hasNumber: !req.requireDigits || /\d/.test(pwd),
    hasSpecialChar: !req.requireSpecialChars || /[!@#$%^&*(),.?":{}|<>]/.test(pwd),
  }
})

const requirementItems = computed(() => {
  const req = passwordRequirement.value
  const items = [
    {
      text: `${req.minLength}-${req.maxLength}个字符`,
      met: requirements.value.length,
    },
  ]

  if (req.requireUppercase) {
    items.push({ text: '至少一个大写字母', met: requirements.value.hasUpperCase })
  }
  if (req.requireLowercase) {
    items.push({ text: '至少一个小写字母', met: requirements.value.hasLowerCase })
  }
  if (req.requireDigits) {
    items.push({ text: '至少一个数字', met: requirements.value.hasNumber })
  }
  if (req.requireSpecialChars) {
    items.push({ text: '至少一个特殊字符', met: requirements.value.hasSpecialChar })
  }

  return items
})

const loadRecoveryContext = async () => {
  try {
    await recoveryFormRef.value?.validate()
    recoveryLoading.value = true

    const normalized = recoveryInput.value.recoveryCode.trim()
    const status = await getAccountRecoveryStatus(normalized)
    resetQrRecoveryFlow()
    recoveryStatus.value = status
    startRecoveryCountdown()
    recoveryInput.value.recoveryCode = normalized
    syncRecoveryCodeQuery(normalized)
    updateStep('new-password')
    ElMessage.success('恢复授权已确认')
  } catch (error) {
    console.error('Load recovery context failed:', error)
  } finally {
    recoveryLoading.value = false
  }
}

const handleQrRecoveryApproved = async (status: QrChallengeStatusResponse) => {
  if (!status.recoveryCode) {
    ElMessage.error('手机扫码背书结果无效，请刷新二维码后重试')
    return
  }
  recoveryInput.value.recoveryCode = status.recoveryCode.trim()
  ElMessage.success('手机扫码背书已通过')
  await loadRecoveryContext()
}

const pollRecoveryQrStatus = async () => {
  if (!qrRecoveryChallengeId.value || !qrRecoveryPollToken.value) return

  try {
    const status = await pollQrStatus(qrRecoveryChallengeId.value, qrRecoveryPollToken.value)
    qrRecoveryExpiresInSeconds.value = status.expiresInSeconds || 0

    if (status.status === 'pending') {
      return
    }

    clearQrRecoveryPolling()

    if (status.status === 'approved') {
      await handleQrRecoveryApproved(status)
      return
    }

    if (status.status === 'rejected') {
      ElMessage.error('手机扫码背书已被拒绝，请刷新二维码后重试')
      return
    }

    ElMessage.warning('扫码背书二维码已过期，请重新生成')
  } catch (error) {
    clearQrRecoveryPolling()
    console.error('Poll account recovery QR status failed:', error)
  }
}

const startQrRecoveryPolling = () => {
  clearQrRecoveryPolling()
  qrRecoveryPollingTimer = window.setInterval(() => {
    void pollRecoveryQrStatus()
  }, 2000)
  void pollRecoveryQrStatus()
}

const handleInitQrRecovery = async () => {
  try {
    qrRecoveryLoading.value = true
    const payload = await initQrAccountRecovery()
    qrRecoveryChallengeId.value = payload.challengeId
    qrRecoveryPollToken.value = payload.pollToken
    qrRecoveryExpiresInSeconds.value = payload.expiresInSeconds
    qrRecoveryImage.value = await QRCode.toDataURL(payload.qrText, {
      width: 240,
      margin: 1,
    })
    startQrRecoveryPolling()
  } catch (error) {
    console.error('Init account recovery QR failed:', error)
    ElMessage.error('生成手机扫码背书二维码失败，请重试')
  } finally {
    qrRecoveryLoading.value = false
  }
}

const goToConfirmStep = async () => {
  try {
    await newPasswordFormRef.value?.validate()
    updateStep('confirm-password')
  } catch {
    // 表单验证失败由组件自行提示
  }
}

const backToRecoveryCode = () => {
  updateStep('recovery-code')
}

const backToNewPassword = () => {
  passwordInput.value.confirmPassword = ''
  updateStep('new-password')
}

const goToLogin = () => {
  syncRecoveryCodeQuery(undefined)
  router.push('/login')
}

const handleSubmit = async () => {
  try {
    await confirmPasswordFormRef.value?.validate()
    submitLoading.value = true

    const result = await completeAccountRecovery({
      recoveryCode: recoveryInput.value.recoveryCode.trim(),
      newPassword: passwordInput.value.newPassword,
    })

    storeAuthSession(result.accessToken, result.user)
    ElMessage.success('账号恢复成功，已登录到当前设备')
    await router.push('/home/overview')
  } catch (error) {
    console.error('Complete account recovery failed:', error)
  } finally {
    submitLoading.value = false
  }
}

onMounted(async () => {
  const apiBaseUrl =
    typeof route.query.apiBaseUrl === 'string' ? route.query.apiBaseUrl.trim() : ''
  if (apiBaseUrl) {
    setRequestBaseUrl(apiBaseUrl)
  }

  try {
    passwordRequirement.value = await getPasswordRequirement()
  } catch (error) {
    console.error('Failed to get password requirement:', error)
  }

  const queryCode = typeof route.query.recoveryCode === 'string' ? route.query.recoveryCode.trim() : ''
  if (queryCode) {
    recoveryInput.value.recoveryCode = queryCode
    await loadRecoveryContext()
  }
})

onBeforeUnmount(() => {
  clearRecoveryCountdown()
  clearQrRecoveryPolling()
})
</script>

<style scoped>
:global(html),
:global(body),
:global(#app) {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

.login-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  overflow: hidden;
}

.login-container.dark {
  background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
}

.login-box {
  width: 100%;
  max-width: 1080px;
  display: flex;
  background: var(--el-bg-color);
  border-radius: 20px;
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.12),
    0 16px 40px rgba(0, 0, 0, 0.08);
  border: 1px solid var(--el-border-color-light);
  overflow: hidden;
}

.login-left {
  flex: 1;
  padding: 60px 48px;
  background: linear-gradient(135deg, #fff8f0, #fffbf5);
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-container.dark .login-left {
  background: var(--el-bg-color-overlay) !important;
}

.login-right {
  flex: 1;
  padding: 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: var(--el-bg-color);
}

.step-slide-forward-enter-active,
.step-slide-forward-leave-active,
.step-slide-backward-enter-active,
.step-slide-backward-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.step-slide-forward-enter-from {
  opacity: 0;
  transform: translateX(40px);
}

.step-slide-forward-leave-to {
  opacity: 0;
  transform: translateX(-40px);
}

.step-slide-backward-enter-from {
  opacity: 0;
  transform: translateX(-40px);
}

.step-slide-backward-leave-to {
  opacity: 0;
  transform: translateX(40px);
}

.logo-section {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 32px;
}

.logo-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: block;
}

.login-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0 0 12px 0;
  letter-spacing: -0.5px;
}

.login-description {
  font-size: 15px;
  color: var(--el-text-color-regular);
  margin: 0 0 32px 0;
  line-height: 1.6;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.feature-icon {
  color: var(--el-color-primary);
  min-width: 24px;
}

.step-container {
  width: 100%;
}

.step-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 8px 0;
}

.step-subtitle {
  font-size: 16px;
  color: var(--el-text-color-regular);
  margin: 0 0 32px 0;
  line-height: 1.6;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-input__wrapper) {
  border: 1.5px solid var(--el-border-color);
  border-radius: 12px;
  background: var(--el-fill-color-light);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  padding: 14px 16px;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 12px rgba(255, 185, 15, 0.25);
  background: var(--el-bg-color);
}

.step-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.qr-entry-card {
  margin-top: 8px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
}

.qr-entry-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.qr-entry-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.qr-entry-desc {
  margin: 8px 0 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.qr-recovery-panel {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.qr-recovery-image {
  width: 220px;
  height: 220px;
  border-radius: 16px;
  background: #fff;
  padding: 10px;
  box-sizing: border-box;
}

.qr-recovery-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.back-btn,
.next-btn {
  height: 44px;
  border-radius: 12px;
  padding: 0 20px;
}

.next-btn {
  flex: 1;
}

.password-requirements {
  margin-top: 4px;
  padding: 16px;
  border-radius: 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
}

.requirement-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
}

.requirement-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.requirement-item:last-child {
  margin-bottom: 0;
}

.requirement-item.met {
  color: var(--el-color-success);
}

.recovery-context-card {
  margin-bottom: 20px;
  padding: 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #fff8f0 0%, #fffdf9 100%);
  border: 1px solid var(--el-color-warning-light-5);
}

.login-container.dark .recovery-context-card {
  background: rgba(255, 185, 15, 0.08);
}

.context-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.context-account {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
}

.context-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 900px) {
  .login-container {
    padding: 20px;
    box-sizing: border-box;
  }

  .login-box {
    flex-direction: column;
    max-height: 100%;
    overflow: auto;
  }

  .login-left,
  .login-right {
    padding: 28px 24px;
  }

  .qr-entry-header {
    flex-direction: column;
  }
}
</style>
