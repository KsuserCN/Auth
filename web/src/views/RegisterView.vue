<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">
          {{ pendingOAuthBind ? t('ui.register.message001') : t('ui.register.message002') }}
        </h1>
        <p class="login-description">
          {{ pendingOAuthBind ? t('ui.register.message003') : t('ui.register.message004') }}
        </p>
        <p v-if="!pendingOAuthBind" class="login-description">
          {{ t('ui.register.message005')
          }}<router-link to="/login" class="link">{{ t('ui.register.message006') }}</router-link>
        </p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20"><Lock /></el-icon>
            <span class="feature-text">{{ t('ui.appleRegister.message003') }}</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20"><Lightning /></el-icon>
            <span class="feature-text">{{ t('ui.appleRegister.message004') }}</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20"><Key /></el-icon>
            <span class="feature-text">{{ t('ui.changePassword.message005') }}</span>
          </div>
        </div>
      </div>

      <div class="login-right">
        <p class="step-progress" role="status">
          {{ t('ui.register.progress', { current: step === 'details' ? 1 : 2, total: 2 }) }}
        </p>
        <Transition
          :name="step === 'code' ? 'step-slide-forward' : 'step-slide-backward'"
          mode="out-in"
          @after-enter="focusCurrentStep"
        >
          <div v-if="accountCreated" key="created" class="step-container created-account">
            <h2 class="step-title">{{ t('ui.register.accountCreatedTitle') }}</h2>
            <p v-if="completionError" class="form-notice" role="alert">
              {{ t('ui.register.completionFailed') }}
            </p>
            <el-button
              class="next-btn continue-account-btn"
              :loading="registerLoading"
              :disabled="registerLoading"
              @click="retryCompletion"
            >
              {{ t('ui.register.openAccount') }}
            </el-button>
          </div>
          <div v-else-if="step === 'details'" key="details" class="step-container">
            <h2 class="step-title">{{ t('ui.register.detailsTitle') }}</h2>
            <p class="step-subtitle">{{ t('ui.register.detailsSubtitle') }}</p>
            <p v-if="detailsError" class="form-notice" role="alert">
              {{ renderMessage(detailsError) }}
            </p>
            <el-form
              ref="detailsFormRef"
              data-testid="details-form"
              :model="formData"
              :rules="detailsRules"
              :disabled="busy"
              label-position="top"
              novalidate
              @submit.prevent="handleContinue"
            >
              <el-form-item prop="username" :label="t('ui.register.usernameLabel')">
                <el-input
                  id="register-username"
                  v-model="formData.username"
                  name="username"
                  :placeholder="t('ui.deleteAccount.message012')"
                  autocomplete="username"
                  autofocus
                  aria-describedby="register-username-hint"
                />
              </el-form-item>
              <p id="register-username-hint" class="field-hint">
                {{ t('ui.register.message007') }}
              </p>
              <div
                v-if="usernameCheckResult"
                class="status-tip"
                :class="usernameAvailable ? 'available' : 'unavailable'"
                role="status"
              >
                <el-icon :size="14">
                  <CircleCheck v-if="usernameAvailable" /><CircleClose v-else />
                </el-icon>
                <span>{{ renderMessage(usernameCheckResult) }}</span>
              </div>
              <el-form-item prop="email" :label="t('ui.register.emailLabel')">
                <el-input
                  id="register-email"
                  v-model="formData.email"
                  name="email"
                  type="email"
                  :placeholder="t('ui.login.message011')"
                  autocomplete="email"
                  autocapitalize="none"
                  spellcheck="false"
                />
              </el-form-item>
              <el-form-item prop="password" :label="t('ui.register.passwordLabel')">
                <el-input
                  id="register-password"
                  v-model="formData.password"
                  name="password"
                  type="password"
                  :placeholder="t('ui.sensitiveVerificationDialog.message006')"
                  show-password
                  autocomplete="new-password"
                  aria-describedby="register-password-hint"
                />
              </el-form-item>
              <div id="register-password-hint" class="password-requirements">
                <div
                  v-for="item in requirementItems"
                  :key="item.text"
                  class="requirement-item"
                  :class="{ met: item.met }"
                >
                  <el-icon :size="14">
                    <CircleCheck v-if="item.met" /><CircleClose v-else />
                  </el-icon>
                  <span>{{ item.text }}</span>
                </div>
              </div>
              <div class="step-actions">
                <el-button class="back-btn" :disabled="busy" @click="handleCancel">
                  {{ t('ui.addPasskey.message009') }}
                </el-button>
                <el-button
                  class="next-btn"
                  native-type="submit"
                  :loading="sendLoading"
                  :disabled="busy || (!hasUsableCode && !canResendCode)"
                >
                  {{
                    hasUsableCode
                      ? t('ui.register.continueVerification')
                      : !canResendCode
                        ? t('ui.register.resendCountdown', { seconds: codeCountdown })
                        : t('ui.register.sendAndContinue')
                  }}
                </el-button>
              </div>
            </el-form>
          </div>

          <div v-else key="code" class="step-container">
            <h2 class="step-title">{{ t('ui.register.verifyTitle') }}</h2>
            <p class="step-subtitle">{{ t('ui.changeEmail.message011') }}</p>
            <div class="email-confirm">
              <span class="email-display">{{ formData.email }}</span>
              <el-button link class="change-email-btn" :disabled="busy" @click="backToDetails">
                {{ t('ui.register.editDetails') }}
              </el-button>
            </div>
            <p v-if="!hasUsableCode" class="form-notice" role="alert">
              {{ t('ui.register.codeRequiredAgain') }}
            </p>
            <el-form
              ref="codeFormRef"
              data-testid="code-form"
              :model="formData"
              :rules="codeRules"
              :disabled="busy"
              label-position="top"
              novalidate
              @submit.prevent="handleRegister"
            >
              <el-form-item prop="code" :label="t('ui.register.codeLabel')">
                <el-input
                  id="register-code"
                  v-model="formData.code"
                  name="code"
                  :placeholder="t('ui.sensitiveVerificationDialog.message010')"
                  maxlength="6"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  @input="formData.code = formData.code.replace(/[^\d]/g, '').slice(0, 6)"
                  autofocus
                />
              </el-form-item>
              <div class="code-actions">
                <el-button
                  class="resend-btn"
                  :disabled="busy || !canResendCode"
                  :loading="resendLoading"
                  @click="resendCode"
                >
                  {{
                    canResendCode
                      ? t('ui.sensitiveVerificationDialog.message012')
                      : t('ui.register.resendCountdown', { seconds: codeCountdown })
                  }}
                </el-button>
              </div>
              <div class="step-actions">
                <el-button class="back-btn" :disabled="busy" @click="backToDetails">
                  {{ t('ui.sensitiveVerificationDialog.message007') }}
                </el-button>
                <el-button
                  class="next-btn"
                  native-type="submit"
                  :loading="registerLoading"
                  :disabled="busy || !hasUsableCode"
                >
                  {{ pendingOAuthBind ? t('ui.register.bindSubmit') : t('ui.register.message002') }}
                </el-button>
              </div>
            </el-form>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { t, uiMessage, renderMessage, type LocalizedMessage, LocalizedError } from '@/i18n'
import { ApiError } from '@/utils/apiResult'
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useDark } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { Lock, Lightning, Key, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  bindPendingOAuthAccount,
  checkUsername,
  sendRegisterCode,
  register,
  getPasswordRequirement,
  type OAuthProvider,
  type PasswordRequirement,
} from '@/api/auth'
import { finalizeWebLogin } from '@/utils/desktopBridge'

const router = useRouter()
const route = useRoute()
const detailsFormRef = ref<FormInstance>()
const codeFormRef = ref<FormInstance>()
const step = ref<'details' | 'code'>('details')
const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})
const formData = ref({ username: '', email: '', password: '', code: '' })
// Match the server's defaults until the configured policy has loaded.
const passwordRequirement = ref<PasswordRequirement>({
  minLength: 6,
  maxLength: 66,
  requireUppercase: false,
  requireLowercase: false,
  requireDigits: true,
  requireSpecialChars: false,
  rejectCommonWeakPasswords: true,
  requirementCode: 'PASSWORD_REQUIREMENTS_4',
})
const usernameCheckResult = ref<LocalizedMessage>('')
const usernameAvailable = ref(false)
const detailsError = ref<LocalizedMessage>('')
const sendLoading = ref(false)
const resendLoading = ref(false)
const registerLoading = ref(false)
const accountCreated = ref(false)
const completionError = ref(false)
const busy = computed(
  () => sendLoading.value || resendLoading.value || registerLoading.value || accountCreated.value,
)

// Keep each email's cooldown when returning to edit details or switching addresses.
const verification = ref<Record<string, { resendAt: number; usable: boolean }>>({})
const now = ref(Date.now())
const emailVerification = computed(() => verification.value[formData.value.email.trim()])
const hasUsableCode = computed(() => emailVerification.value?.usable === true)
const codeCountdown = computed(() =>
  Math.max(0, Math.ceil(((emailVerification.value?.resendAt ?? 0) - now.value) / 1000)),
)
const canResendCode = computed(() => codeCountdown.value === 0)
let codeCountdownTimer: ReturnType<typeof setInterval> | undefined

const pendingOAuthBind = computed<{ provider: OAuthProvider; bindToken: string } | null>(() => {
  const provider = route.query.oauthBindProvider
  const bindToken = route.query.oauthBindToken
  if (
    (provider === 'qq' ||
      provider === 'github' ||
      provider === 'microsoft' ||
      provider === 'google') &&
    typeof bindToken === 'string' &&
    bindToken.trim()
  ) {
    return { provider, bindToken: bindToken.trim() }
  }
  return null
})

const requirementItems = computed(() => {
  const req = passwordRequirement.value
  const pwd = formData.value.password
  return [
    {
      text: t('ui.register.message017', { arg0: req.minLength, arg1: req.maxLength }),
      met: pwd.length >= req.minLength && pwd.length <= req.maxLength,
    },
    ...(req.requireUppercase
      ? [{ text: t('ui.register.message018'), met: /[A-Z]/.test(pwd) }]
      : []),
    ...(req.requireLowercase
      ? [{ text: t('ui.register.message019'), met: /[a-z]/.test(pwd) }]
      : []),
    ...(req.requireDigits ? [{ text: t('ui.register.message020'), met: /\d/.test(pwd) }] : []),
    ...(req.requireSpecialChars
      ? [{ text: t('ui.register.message021'), met: /[@$!%*?&]/.test(pwd) }]
      : []),
  ]
})

const detailsRules = computed<FormRules>(() => ({
  username: [
    { required: true, message: t('ui.deleteAccount.message021'), trigger: 'blur' },
    { min: 3, max: 20, message: t('ui.register.message015'), trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_\-\u4e00-\u9fa5]+$/,
      message: t('ui.register.message016'),
      trigger: 'blur',
    },
  ],
  email: [
    { required: true, message: t('ui.login.message069'), trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: t('ui.changeEmail.message016'),
      trigger: 'blur',
    },
  ],
  password: [
    {
      required: true,
      trigger: 'blur',
      validator: (_rule, value: string, callback) => {
        const req = passwordRequirement.value
        let error: LocalizedError | undefined
        if (!value) error = new LocalizedError('ui.sensitiveVerificationDialog.message036')
        else if (value.length < req.minLength || value.length > req.maxLength) {
          error = new LocalizedError('ui.changePassword.message014', {
            arg0: req.minLength,
            arg1: req.maxLength,
          })
        } else if (req.requireUppercase && !/[A-Z]/.test(value))
          error = new LocalizedError('ui.changePassword.message015')
        else if (req.requireLowercase && !/[a-z]/.test(value))
          error = new LocalizedError('ui.changePassword.message016')
        else if (req.requireDigits && !/\d/.test(value))
          error = new LocalizedError('ui.changePassword.message017')
        else if (req.requireSpecialChars && !/[@$!%*?&]/.test(value))
          error = new LocalizedError('ui.changePassword.message018')
        callback(error)
      },
    },
  ],
}))
const codeRules = computed<FormRules>(() => ({
  code: [
    { required: true, message: t('ui.sensitiveVerificationDialog.message037'), trigger: 'blur' },
    {
      pattern: /^\d{6}$/,
      message: t('ui.sensitiveVerificationDialog.message038'),
      trigger: 'blur',
    },
  ],
}))

watch(
  () => formData.value.username,
  () => {
    usernameCheckResult.value = ''
    usernameAvailable.value = false
    detailsError.value = ''
  },
)
watch(
  () => formData.value.email.trim(),
  () => {
    formData.value.code = ''
    detailsError.value = ''
  },
)

onMounted(async () => {
  try {
    passwordRequirement.value = await getPasswordRequirement()
  } catch (error) {
    console.error('Failed to get password requirement:', error)
  }
})
onBeforeUnmount(() => clearInterval(codeCountdownTimer))

const focusCurrentStep = () => {
  const form = step.value === 'details' ? detailsFormRef.value : codeFormRef.value
  const input: HTMLInputElement | undefined = form?.$el.querySelector('input')
  input?.focus()
}
const handleCancel = () => {
  if (!busy.value)
    void router.push({ path: '/login', query: pendingOAuthBind.value ? route.query : {} })
}
const backToDetails = () => {
  if (!busy.value) step.value = 'details'
}
const recordCodeAttempt = (usable = true) => {
  now.value = Date.now()
  verification.value[formData.value.email] = { resendAt: now.value + 60_000, usable }
  formData.value.code = ''
  if (codeCountdownTimer === undefined) {
    codeCountdownTimer = setInterval(() => {
      now.value = Date.now()
    }, 1000)
  }
}
const handleContinue = async () => {
  if (busy.value || step.value !== 'details' || !detailsFormRef.value) return
  sendLoading.value = true
  try {
    formData.value.username = formData.value.username.trim()
    formData.value.email = formData.value.email.trim()
    await detailsFormRef.value.validate()
    const available = await checkUsername(formData.value.username)
    usernameAvailable.value = available
    usernameCheckResult.value = uiMessage(
      available ? 'ui.register.message022' : 'ui.register.usernameCheckUnavailable',
    )
    if (!available) return
    now.value = Date.now()
    if (!hasUsableCode.value) {
      if (!canResendCode.value) return
      await sendRegisterCode({ email: formData.value.email })
      recordCodeAttempt()
      ElMessage.success(t('ui.sensitiveVerificationDialog.message049'))
    }
    detailsError.value = ''
    step.value = 'code'
  } catch (error) {
    // Delivery failed after the server replaced the code and reserved the send quota.
    if (error instanceof ApiError && error.errorCode === 'AUTH_015') recordCodeAttempt(false)
    // Field errors render inline; the request interceptor displays API failures.
    console.error('Continue registration failed:', error)
  } finally {
    sendLoading.value = false
  }
}
const resendCode = async () => {
  now.value = Date.now()
  if (busy.value || !canResendCode.value || step.value !== 'code') return
  resendLoading.value = true
  try {
    await sendRegisterCode({ email: formData.value.email })
    recordCodeAttempt()
    ElMessage.success(t('ui.sensitiveVerificationDialog.message050'))
  } catch (error) {
    if (error instanceof ApiError && error.errorCode === 'AUTH_015') recordCodeAttempt(false)
    console.error('Resend register code failed:', error)
  } finally {
    resendLoading.value = false
  }
}

const recoverRegistrationError = (error: unknown) => {
  if (!(error instanceof ApiError)) return
  const code = error.errorCode
  if (['AUTH_029', 'AUTH_028', 'AUTH_031', 'AUTH_023', 'AUTH_026'].includes(code)) {
    if (emailVerification.value) emailVerification.value.usable = false
    formData.value.code = ''
  }
  if (code === 'AUTH_029') {
    detailsError.value = uiMessage('ui.register.usernameConflict')
    usernameCheckResult.value = ''
    usernameAvailable.value = false
    step.value = 'details'
  } else if (code === 'AUTH_028' || code === 'AUTH_031') {
    detailsError.value = uiMessage('ui.register.accountExists')
    step.value = 'details'
  } else if (
    [
      'AUTH_002',
      'AUTH_007',
      'AUTH_008',
      'AUTH_017',
      'AUTH_019',
      'AUTH_020',
      'AUTH_021',
      'AUTH_022',
    ].includes(code) ||
    code.startsWith('PASSWORD_REQUIREMENTS_')
  ) {
    step.value = 'details'
  }
}
let createdLogin: Parameters<typeof finalizeWebLogin>[0] | undefined
let loginFinalized = false
let bindingAttempted = false
let resultNotified = false
let desktopSynced = false

const completeCreatedAccount = async () => {
  if (!createdLogin) return
  if (!loginFinalized) {
    desktopSynced = await finalizeWebLogin(createdLogin)
    loginFinalized = true
  }
  if (!bindingAttempted) {
    bindingAttempted = true
    if (pendingOAuthBind.value) {
      try {
        await bindPendingOAuthAccount(pendingOAuthBind.value)
      } catch (error) {
        console.error('Bind after registration failed:', error)
        ElMessage.warning(t('ui.register.bindFailed'))
        resultNotified = true
      }
    }
  }
  if (!resultNotified) {
    ElMessage.success(t(desktopSynced ? 'ui.register.message026' : 'ui.register.message027'))
    resultNotified = true
  }
  if (await router.push('/home')) throw new Error('Account navigation did not complete')
}

const retryCompletion = async () => {
  if (!accountCreated.value || registerLoading.value) return
  registerLoading.value = true
  completionError.value = false
  try {
    await completeCreatedAccount()
  } catch (error) {
    completionError.value = true
    console.error('Complete registration failed:', error)
  } finally {
    registerLoading.value = false
  }
}

const handleRegister = async () => {
  if (busy.value || step.value !== 'code' || !hasUsableCode.value || !codeFormRef.value) return
  registerLoading.value = true
  try {
    await codeFormRef.value.validate()
    const response = await register({ ...formData.value })
    // Once created, later binding/navigation failures must never allow registration to replay.
    accountCreated.value = true
    formData.value.password = ''
    createdLogin = {
      accessToken: response.accessToken,
      user: { uuid: response.uuid, username: response.username, email: response.email },
    }
    await completeCreatedAccount()
  } catch (error) {
    if (accountCreated.value) completionError.value = true
    else recoverRegistrationError(error)
    console.error('Register failed:', error)
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
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
  align-items: flex-start;
  justify-content: center;
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  box-sizing: border-box;
  padding: 48px 24px 24px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.login-container.dark {
  background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
}

.login-box {
  width: 100%;
  max-width: 1000px;
  flex-shrink: 0;
  margin-block: auto;
  display: flex;
  background: var(--el-bg-color);
  border-radius: 20px;
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.12),
    0 16px 40px rgba(0, 0, 0, 0.08);
  animation: slideIn 0.6s ease-out;
  backdrop-filter: blur(10px);
  border: 1px solid var(--el-border-color-light);
  overflow: hidden;
  transition: all 2s cubic-bezier(0.4, 0, 0.2, 1);
}

.login-left {
  flex: 0.9;
  min-width: 0;
  padding: 48px 40px;
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
  flex: 1.1;
  min-width: 0;
  padding: 36px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  background: var(--el-bg-color);
  transition: all 2s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Step 过渡动画 */
.step-slide-forward-enter-active,
.step-slide-forward-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.step-slide-forward-enter-from {
  opacity: 0;
  transform: translateX(40px);
}

.step-slide-forward-leave-to {
  opacity: 0;
  transform: translateX(-40px);
}

.step-slide-backward-enter-active,
.step-slide-backward-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.step-slide-backward-enter-from {
  opacity: 0;
  transform: translateX(-40px);
}

.step-slide-backward-leave-to {
  opacity: 0;
  transform: translateX(40px);
}

/* Logo部分 */
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

.login-description .link {
  margin-inline-start: 4px;
}

.login-description {
  font-size: 15px;
  color: var(--el-text-color-regular);
  margin: 0 0 32px 0;
  font-weight: 400;
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

/* 步骤容器 */
.step-container {
  width: 100%;
}

.step-progress {
  margin: 0 0 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.field-hint {
  margin: -8px 0 16px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.form-notice {
  color: var(--el-color-danger);
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 16px;
}

.step-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 8px 0;
  letter-spacing: -0.3px;
}

.step-subtitle {
  font-size: 16px;
  color: var(--el-text-color-regular);
  margin: 0 0 24px 0;
  font-weight: 400;
}

.email-confirm {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.email-display {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.change-email-btn {
  font-size: 12px;
  padding: 0;
}

/* 表单样式 */
:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

:deep(.el-input__wrapper) {
  border: 1.5px solid var(--el-border-color);
  border-radius: 12px;
  background: var(--el-fill-color-light);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 14px 16px;
}

:deep(.el-input__wrapper:hover) {
  border-color: var(--el-border-color-hover);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
  background: var(--el-bg-color);
}

:deep(.el-input__wrapper.is-focus) {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 12px rgba(255, 185, 15, 0.25);
  background: var(--el-bg-color);
}

:deep(.el-input__inner) {
  font-size: 16px;
  color: var(--el-text-color-primary);
}

:deep(.el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder);
}

/* 按钮 */
.step-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 24px;
}

.next-btn,
.back-btn {
  min-width: 100px;
  min-height: 44px;
  height: auto;
  padding: 12px 16px;
  white-space: normal;
  line-height: 1.4;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.next-btn {
  background: var(--el-color-primary);
  border: none;
  color: white;
  flex: 1;
}

.next-btn:hover {
  box-shadow: 0 8px 16px rgba(255, 185, 15, 0.3);
  transform: translateY(-2px);
}

.next-btn:active {
  transform: translateY(0) scale(0.98);
}

.next-btn:disabled {
  background: linear-gradient(135deg, #e5a908 0%, #f5c90f 100%);
  opacity: 0.6;
  box-shadow: none;
  cursor: not-allowed;
}

.back-btn {
  background: var(--el-bg-color);
  border: 1.5px solid var(--el-border-color);
  color: var(--el-text-color-primary);
}

.back-btn:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.back-btn:active {
  transform: scale(0.98);
}

/* 密码要求 */
.password-requirements {
  margin: 12px 0;
  padding: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.requirement-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;
}

.requirement-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 0;
  transition: all 0.2s ease;
}

.requirement-item:last-child {
  margin-bottom: 0;
}

.requirement-item.met {
  color: var(--el-color-success);
}

.requirement-item.met :deep(.el-icon) {
  color: var(--el-color-success);
}

.requirement-item:not(.met) :deep(.el-icon) {
  color: var(--el-text-color-placeholder);
}

/* 用户名状态提示 */
.status-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 8px 0;
  margin-bottom: 16px;
  transition: all 0.2s ease;
}

.status-tip.available {
  color: var(--el-color-success);
}

.status-tip.available :deep(.el-icon) {
  color: var(--el-color-success);
}

.status-tip.unavailable {
  color: var(--el-color-error);
}

.status-tip.unavailable :deep(.el-icon) {
  color: var(--el-color-error);
}

.code-actions {
  display: flex;
  justify-content: flex-end;
  margin: 8px 0 16px;
}

.resend-btn {
  height: 36px;
  border-radius: 8px;
}

.resend-disabled {
  opacity: 0.7;
}

/* 响应式设计 */
@media (max-width: 900px) {
  .login-box {
    max-width: 100%;
    flex-direction: column;
  }

  .login-left {
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-light);
    padding: 24px 32px 16px;
  }

  .login-right {
    padding: 24px 32px;
  }

  .login-title {
    font-size: 28px;
  }

  .step-title {
    font-size: 24px;
  }

  .login-container {
    padding: 48px 16px 20px;
  }

  .logo-section,
  .feature-list,
  .login-left > .login-description:first-of-type {
    display: none;
  }

  .login-description {
    margin-bottom: 0;
    font-size: 13px;
  }
}

@media (max-width: 600px) {
  .login-box {
    border-radius: 12px;
  }

  .login-left {
    padding: 20px 24px 16px;
  }

  .login-right {
    padding: 24px;
  }

  .login-title {
    font-size: 24px;
  }

  .step-title {
    font-size: 20px;
  }

  .step-actions {
    flex-direction: column-reverse;
  }

  .next-btn,
  .back-btn {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .login-left {
    padding-top: 68px;
  }
}
</style>
