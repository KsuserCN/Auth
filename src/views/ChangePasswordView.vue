<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <!-- 左侧：Logo和说明 -->
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">修改密码</h1>
        <p class="login-description">设置一个新的安全密码来保护您的账户</p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lock />
            </el-icon>
            <span class="feature-text">安全加密</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lightning />
            </el-icon>
            <span class="feature-text">快速更新</span>
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
        <Transition :name="stepDirection === 'forward' ? 'step-slide-forward' : 'step-slide-backward'" mode="out-in">
          <!-- 第一步：输入新密码 -->
          <div v-if="step === 'new-password'" class="step-container" key="new-password">
            <h2 class="step-title">设置新密码</h2>
            <p class="step-subtitle">{{ passwordRequirement.requirementMessage }}</p>

            <el-form ref="newPasswordFormRef" :model="passwordInput" :rules="newPasswordRules" label-position="top">
              <el-form-item prop="newPassword">
                <el-input v-model="passwordInput.newPassword" type="password" placeholder="输入新密码" show-password
                  @keyup.enter="goToConfirmStep" autocomplete="new-password" autofocus />
              </el-form-item>
            </el-form>

            <div class="password-requirements">
              <div class="requirement-title">密码要求：</div>
              <div v-for="(item, index) in requirementItems" :key="index" class="requirement-item"
                :class="{ met: item.met }">
                <el-icon :size="14">
                  <CircleCheck v-if="item.met" />
                  <CircleClose v-else />
                </el-icon>
                <span>{{ item.text }}</span>
              </div>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="handleCancel">取消</el-button>
              <el-button class="next-btn" @click="goToConfirmStep">
                下一步
              </el-button>
            </div>
          </div>

          <!-- 第二步：确认新密码 -->
          <div v-else-if="step === 'confirm-password'" class="step-container" key="confirm-password">
            <h2 class="step-title">确认新密码</h2>
            <p class="step-subtitle">请再次输入新密码以确认</p>

            <el-form ref="confirmPasswordFormRef" :model="passwordInput" :rules="confirmPasswordRules"
              label-position="top">
              <el-form-item prop="confirmPassword">
                <el-input v-model="passwordInput.confirmPassword" type="password" placeholder="再次输入新密码" show-password
                  @keyup.enter="handleSubmit" autocomplete="new-password" autofocus />
              </el-form-item>
            </el-form>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToNewPassword">返回</el-button>
              <el-button class="next-btn" @click="handleSubmit" :loading="submitLoading">
                修改密码
              </el-button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useDark } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { Lock, Lightning, Key, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { changePassword, checkSensitiveVerification, getPasswordRequirement, type PasswordRequirement } from '@/api/auth'

const router = useRouter()

// 表单引用
const newPasswordFormRef = ref<FormInstance>()
const confirmPasswordFormRef = ref<FormInstance>()

// 流程步骤
const step = ref<'new-password' | 'confirm-password'>('new-password')
const stepDirection = ref<'forward' | 'backward'>('forward')

const stepOrder = ['new-password', 'confirm-password']

const updateStep = (newStep: 'new-password' | 'confirm-password') => {
  const currentIndex = stepOrder.indexOf(step.value)
  const newIndex = stepOrder.indexOf(newStep)
  stepDirection.value = newIndex > currentIndex ? 'forward' : 'backward'
  step.value = newStep
}

// 使用 VueUse 的 useDark 同步暗黑模式
const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light'
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
  requirementMessage: '密码强度不足：需包含大写字母、小写字母、数字'
})

// 密码输入
const passwordInput = ref({
  newPassword: '',
  confirmPassword: '',
})

onMounted(async () => {
  // 获取密码要求
  try {
    passwordRequirement.value = await getPasswordRequirement()
  } catch (error) {
    console.error('Failed to get password requirement:', error)
  }
})

// 自定义验证规则
const validatePassword = (rule: any, value: any, callback: any) => {
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

const validateConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入新密码'))
  } else if (value !== passwordInput.value.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const newPasswordRules: FormRules = {
  newPassword: [
    { required: true, validator: validatePassword, trigger: 'blur' }
  ],
}

const confirmPasswordRules: FormRules = {
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ],
}

// 密码要求检查
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

// 密码要求文本
const requirementItems = computed(() => {
  const req = passwordRequirement.value
  const items = [
    {
      text: `${req.minLength}-${req.maxLength}个字符`,
      met: requirements.value.length
    }
  ]

  if (req.requireUppercase) {
    items.push({
      text: '至少一个大写字母',
      met: requirements.value.hasUpperCase
    })
  }

  if (req.requireLowercase) {
    items.push({
      text: '至少一个小写字母',
      met: requirements.value.hasLowerCase
    })
  }

  if (req.requireDigits) {
    items.push({
      text: '至少一个数字',
      met: requirements.value.hasNumber
    })
  }

  if (req.requireSpecialChars) {
    items.push({
      text: '至少一个特殊字符',
      met: requirements.value.hasSpecialChar
    })
  }

  return items
})

// 加载状态
const submitLoading = ref(false)

const handleCancel = () => {
  router.push('/home/login-options')
}

// 第一步：输入新密码，进入下一步
const goToConfirmStep = async () => {
  try {
    await newPasswordFormRef.value?.validate()
    updateStep('confirm-password')
  } catch {
    // 表单验证失败，Element Plus 会显示错误提示
  }
}

// 返回到新密码输入
const backToNewPassword = () => {
  updateStep('new-password')
  passwordInput.value.confirmPassword = ''
}

const handleSubmit = async () => {
  try {
    await confirmPasswordFormRef.value?.validate()
    submitLoading.value = true

    // 调用修改密码接口
    await changePassword({
      newPassword: passwordInput.value.newPassword
    })

    ElMessage.success('密码修改成功')
    router.push('/home/login-options')
  } catch (error: unknown) {
    console.error('Change password failed:', error)
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
/* 全局样式修复 - 禁用滚动并铺满视口 */
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
  margin: 0 0 32px 0;
  font-weight: 400;
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
