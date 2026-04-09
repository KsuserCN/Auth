<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <!-- 左侧：Logo和说明 -->
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">创建账户</h1>
        <p class="login-description">开始您的安全之旅，创建一个新账户</p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lock />
            </el-icon>
            <span class="feature-text">安全保护</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lightning />
            </el-icon>
            <span class="feature-text">快速开始</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Key />
            </el-icon>
            <span class="feature-text">密码强度</span>
          </div>
        </div>
      </div>

      <!-- 右侧：表单内容 -->
      <div class="login-right">
        <Transition
          :name="stepDirection === 'forward' ? 'step-slide-forward' : 'step-slide-backward'"
          mode="out-in"
        >
          <!-- 第一步：用户名 -->
          <div v-if="step === 'username'" class="step-container" key="username">
            <h2 class="step-title">选择用户名</h2>
            <p class="step-subtitle">3-20个字符，支持中文、字母、数字、下划线和连字符</p>

            <el-form
              ref="usernameFormRef"
              :model="formData"
              :rules="usernameRules"
              label-position="top"
            >
              <el-form-item prop="username">
                <el-input
                  v-model="formData.username"
                  placeholder="用户名"
                  @keyup.enter="goToPassword"
                  autocomplete="off"
                  autofocus
                />
              </el-form-item>
            </el-form>

            <div
              v-if="usernameCheckResult"
              class="status-tip"
              :class="usernameAvailable ? 'available' : 'unavailable'"
            >
              <el-icon :size="14">
                <CircleCheck v-if="usernameAvailable" />
                <CircleClose v-else />
              </el-icon>
              <span>{{ usernameCheckResult }}</span>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="handleCancel">取消</el-button>
              <el-button class="next-btn" @click="goToPassword" :loading="usernameLoading">
                下一步
              </el-button>
            </div>
          </div>

          <!-- 第二步：密码 -->
          <div v-else-if="step === 'password'" class="step-container" key="password">
            <h2 class="step-title">设置密码</h2>
            <p class="step-subtitle">{{ passwordRequirement.requirementMessage }}</p>

            <el-form
              ref="passwordFormRef"
              :model="formData"
              :rules="passwordRules"
              label-position="top"
            >
              <el-form-item prop="password">
                <el-input
                  v-model="formData.password"
                  type="password"
                  placeholder="输入密码"
                  show-password
                  @keyup.enter="goToPasswordConfirm"
                  autocomplete="new-password"
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
              <el-button class="back-btn" @click="backToUsername">返回</el-button>
              <el-button class="next-btn" @click="goToPasswordConfirm"> 下一步 </el-button>
            </div>
          </div>

          <!-- 第三步：确认密码 -->
          <div
            v-else-if="step === 'password-confirm'"
            class="step-container"
            key="password-confirm"
          >
            <h2 class="step-title">确认密码</h2>
            <p class="step-subtitle">请再次输入密码以确认</p>

            <el-form
              ref="passwordConfirmFormRef"
              :model="formData"
              :rules="passwordConfirmRules"
              label-position="top"
            >
              <el-form-item prop="passwordConfirm">
                <el-input
                  v-model="formData.passwordConfirm"
                  type="password"
                  placeholder="再次输入密码"
                  show-password
                  @keyup.enter="goToEmail"
                  autocomplete="new-password"
                  autofocus
                />
              </el-form-item>
            </el-form>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToPassword">返回</el-button>
              <el-button class="next-btn" @click="goToEmail"> 下一步 </el-button>
            </div>
          </div>

          <!-- 第四步：邮箱 -->
          <div v-else-if="step === 'email'" class="step-container" key="email">
            <h2 class="step-title">输入邮箱</h2>
            <p class="step-subtitle">用于接收验证码和系统通知</p>

            <el-form ref="emailFormRef" :model="formData" :rules="emailRules" label-position="top">
              <el-form-item prop="email">
                <el-input
                  v-model="formData.email"
                  type="email"
                  placeholder="邮箱地址"
                  @keyup.enter="handleSendCode"
                  autocomplete="email"
                  autofocus
                />
              </el-form-item>
            </el-form>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToPasswordConfirm">返回</el-button>
              <el-button class="next-btn" @click="handleSendCode" :loading="sendLoading">
                发送验证码
              </el-button>
            </div>
          </div>

          <!-- 第五步：验证码 -->
          <div v-else-if="step === 'code'" class="step-container" key="code">
            <h2 class="step-title">输入验证码</h2>
            <p class="step-subtitle">验证码已发送至 {{ formData.email }}</p>

            <div class="email-confirm">
              <span class="email-display">{{ formData.email }}</span>
              <el-button link class="change-email-btn" @click="backToEmail">修改邮箱</el-button>
            </div>

            <el-form ref="codeFormRef" :model="formData" :rules="codeRules" label-position="top">
              <el-form-item prop="code">
                <el-input
                  v-model="formData.code"
                  placeholder="输入6位验证码"
                  maxlength="6"
                  @input="formData.code = formData.code.replace(/[^\d]/g, '')"
                  @keyup.enter="handleRegister"
                  autofocus
                />
              </el-form-item>
            </el-form>

            <div class="code-actions">
              <el-button v-if="!canResendCode" disabled class="resend-btn resend-disabled">
                {{ codeCountdown }}s 后重新发送
              </el-button>
              <el-button
                v-else
                type="primary"
                @click="resendCode"
                class="resend-btn"
                :loading="resendLoading"
              >
                重新发送验证码
              </el-button>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToEmail">返回</el-button>
              <el-button class="next-btn" @click="handleRegister" :loading="registerLoading">
                创建账户
              </el-button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useDark } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { Lock, Lightning, Key, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  checkUsername,
  sendRegisterCode,
  register,
  getPasswordRequirement,
  type PasswordRequirement,
} from '@/api/auth'
import { finalizeWebLogin } from '@/utils/desktopBridge'

const router = useRouter()

// 表单引用
const usernameFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const passwordConfirmFormRef = ref<FormInstance>()
const emailFormRef = ref<FormInstance>()
const codeFormRef = ref<FormInstance>()

// 流程步骤
const step = ref<'username' | 'password' | 'password-confirm' | 'email' | 'code'>('username')
const stepDirection = ref<'forward' | 'backward'>('forward')

const stepOrder = ['username', 'password', 'password-confirm', 'email', 'code']

const updateStep = (newStep: 'username' | 'password' | 'password-confirm' | 'email' | 'code') => {
  const currentIndex = stepOrder.indexOf(step.value)
  const newIndex = stepOrder.indexOf(newStep)
  stepDirection.value = newIndex > currentIndex ? 'forward' : 'backward'
  step.value = newStep
}

// 使用 VueUse 的 useDark 同步暗黑模式
const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})

// 表单数据
const formData = ref({
  username: '',
  password: '',
  passwordConfirm: '',
  email: '',
  code: '',
})

// 密码要求
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

// 用户名检查
const usernameLoading = ref(false)
const usernameCheckResult = ref('')
const usernameAvailable = ref(false)

// 密码验证规则
const validatePassword = (rule: any, value: any, callback: any) => {
  const req = passwordRequirement.value

  if (value === '') {
    callback(new Error('请输入密码'))
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

const validatePasswordConfirm = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== formData.value.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

// 表单验证规则
const usernameRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3-20个字符之间', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_\-\u4e00-\u9fa5]+$/,
      message: '用户名只能包含中文、字母、数字、下划线和连字符',
      trigger: 'blur',
    },
  ],
}

const passwordRules: FormRules = {
  password: [{ required: true, validator: validatePassword, trigger: 'blur' }],
}

const passwordConfirmRules: FormRules = {
  passwordConfirm: [{ required: true, validator: validatePasswordConfirm, trigger: 'blur' }],
}

const emailRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        if (!emailRegex.test(value)) {
          callback(new Error('邮箱格式不正确'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

const codeRules: FormRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码应为6位数字', trigger: 'blur' },
  ],
}

// 加载状态
const sendLoading = ref(false)
const resendLoading = ref(false)
const registerLoading = ref(false)

// 验证码倒计时
const codeCountdown = ref(0)
const canResendCode = ref(false)
let codeCountdownTimer: number | null = null

// 密码要求检查
const requirementItems = computed(() => {
  const req = passwordRequirement.value
  const pwd = formData.value.password
  const items: Array<{ text: string; met: boolean }> = []

  if (req.minLength || req.maxLength) {
    items.push({
      text: `长度 ${req.minLength}-${req.maxLength} 个字符`,
      met: pwd.length >= req.minLength && pwd.length <= req.maxLength,
    })
  }

  if (req.requireUppercase) {
    items.push({
      text: '至少包含一个大写字母',
      met: /[A-Z]/.test(pwd),
    })
  }

  if (req.requireLowercase) {
    items.push({
      text: '至少包含一个小写字母',
      met: /[a-z]/.test(pwd),
    })
  }

  if (req.requireDigits) {
    items.push({
      text: '至少包含一个数字',
      met: /\d/.test(pwd),
    })
  }

  if (req.requireSpecialChars) {
    items.push({
      text: '至少包含一个特殊字符',
      met: /[!@#$%^&*(),.?":{}|<>]/.test(pwd),
    })
  }

  return items
})

onMounted(async () => {
  try {
    passwordRequirement.value = await getPasswordRequirement()
  } catch (error) {
    console.error('Failed to get password requirement:', error)
  }
})

onBeforeUnmount(() => {
  cleanupCodeCountdown()
})

const handleCancel = () => {
  router.push('/login')
}

// 用户名检查
const checkUsernameAvailability = async () => {
  if (!formData.value.username) {
    usernameCheckResult.value = ''
    return false
  }

  usernameLoading.value = true
  try {
    const available = await checkUsername(formData.value.username)
    usernameAvailable.value = available
    usernameCheckResult.value = available ? '用户名可用' : '用户名已被使用'
    return available
  } catch (error) {
    usernameCheckResult.value = '检查失败'
    usernameAvailable.value = false
    return false
  } finally {
    usernameLoading.value = false
  }
}

// 导航函数
const goToPassword = async () => {
  try {
    await usernameFormRef.value?.validate()

    // 点击下一步时检查用户名是否可用
    const available = await checkUsernameAvailability()
    if (!available) {
      ElMessage.error('用户名不可用，请更换')
      return
    }

    updateStep('password')
  } catch {
    // 验证失败
  }
}

const backToUsername = () => {
  updateStep('username')
}

const goToPasswordConfirm = async () => {
  try {
    await passwordFormRef.value?.validate()
    updateStep('password-confirm')
  } catch {
    // 验证失败
  }
}

const backToPassword = () => {
  updateStep('password')
}

const goToEmail = async () => {
  try {
    await passwordConfirmFormRef.value?.validate()
    updateStep('email')
  } catch {
    // 验证失败
  }
}

const backToPasswordConfirm = () => {
  updateStep('password-confirm')
}

const backToEmail = () => {
  updateStep('email')
  formData.value.code = ''
  cleanupCodeCountdown()
}

// 验证码相关
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
  if (codeCountdownTimer) {
    clearInterval(codeCountdownTimer)
    codeCountdownTimer = null
  }
  if (codeCountdown.value <= 0) {
    canResendCode.value = true
  }
}

const handleSendCode = async () => {
  try {
    await emailFormRef.value?.validate()
    sendLoading.value = true

    await sendRegisterCode({ email: formData.value.email })
    ElMessage.success('验证码已发送')

    startCodeCountdown()
    updateStep('code')
  } catch (error: any) {
    console.error('Send register code failed:', error)
  } finally {
    sendLoading.value = false
  }
}

const resendCode = async () => {
  if (resendLoading.value) return
  resendLoading.value = true
  try {
    await sendRegisterCode({ email: formData.value.email })
    ElMessage.success('验证码已重新发送')
    startCodeCountdown()
  } catch (error: any) {
    console.error('Resend register code failed:', error)
  } finally {
    resendLoading.value = false
  }
}

const handleRegister = async () => {
  try {
    await codeFormRef.value?.validate()
    registerLoading.value = true

    const response = await register({
      username: formData.value.username,
      email: formData.value.email,
      password: formData.value.password,
      code: formData.value.code,
    })

    const desktopSynced = await finalizeWebLogin({
      accessToken: response.accessToken,
      user: {
        uuid: response.uuid,
        username: response.username,
        email: response.email,
      },
    })

    ElMessage.success(desktopSynced ? '注册成功，已同步到桌面端' : '注册成功')
    // 直接跳转到首页
    router.push('/home')
  } catch (error: any) {
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
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  overflow: hidden;
}

.login-container.dark {
  background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
}

.login-box {
  width: 100%;
  max-width: 1000px;
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
  padding: 48px 48px;
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
  margin-bottom: 12px;
}

.email-display {
  font-size: 13px;
  color: var(--el-text-color-secondary);
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
  height: 44px;
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
  margin: 20px 0;
  padding: 16px;
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
  margin-bottom: 8px;
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
    border-bottom: 1px solid #f0f0f0;
    padding: 40px 32px;
  }

  .login-right {
    padding: 40px 32px;
  }

  .login-title {
    font-size: 28px;
  }

  .step-title {
    font-size: 24px;
  }

  .login-container {
    padding: 20px;
  }
}

@media (max-width: 600px) {
  .login-box {
    border-radius: 12px;
  }

  .login-left {
    padding: 32px 24px;
  }

  .login-right {
    padding: 32px 24px;
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
</style>
