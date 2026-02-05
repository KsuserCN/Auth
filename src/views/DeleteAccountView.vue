<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <!-- 左侧：Logo和说明 -->
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">注销账号</h1>
        <p class="login-description">这是一个不可逆的操作，请谨慎选择</p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <WarningFilled />
            </el-icon>
            <span class="feature-text">数据将永久删除</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lock />
            </el-icon>
            <span class="feature-text">安全保护验证</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lightning />
            </el-icon>
            <span class="feature-text">双重确认机制</span>
          </div>
        </div>
      </div>

      <!-- 右侧：表单内容 -->
      <div class="login-right">
        <Transition :name="stepDirection === 'forward' ? 'step-slide-forward' : 'step-slide-backward'" mode="out-in">
          <!-- 第一步：输入用户名确认 -->
          <div v-if="step === 'username'" class="step-container" key="username">
            <h2 class="step-title">确认用户名</h2>
            <p class="step-subtitle">请输入您的用户名以继续</p>

            <div class="warning-box">
              <el-icon class="warning-icon">
                <WarningFilled />
              </el-icon>
              <p>注销后，您的账号和所有数据将被永久删除，无法恢复</p>
            </div>

            <el-form ref="usernameFormRef" :model="usernameInput" :rules="usernameRules" label-position="top">
              <el-form-item prop="username">
                <el-input v-model="usernameInput.username" placeholder="请输入您的用户名" @keyup.enter="goToConfirmStep"
                  autofocus />
              </el-form-item>
            </el-form>

            <div class="step-actions">
              <el-button class="back-btn" @click="handleCancel">取消</el-button>
              <el-button class="next-btn" @click="goToConfirmStep">
                下一步
              </el-button>
            </div>
          </div>

          <!-- 第二步：最终确认 -->
          <div v-else-if="step === 'confirm'" class="step-container" key="confirm">
            <h2 class="step-title">最终确认</h2>
            <p class="step-subtitle">请再次确认您要注销账号</p>

            <div class="confirm-info">
              <div class="info-item">
                <span class="info-label">用户名</span>
                <span class="info-value">{{ usernameInput.username }}</span>
              </div>
            </div>

            <div class="warning-box danger">
              <el-icon class="warning-icon">
                <WarningFilled />
              </el-icon>
              <p>此操作<strong>不可撤销</strong><br>您的账号和所有相关数据将被永久删除。</p>
            </div>

            <el-form ref="confirmTextFormRef" :model="confirmTextInput" :rules="confirmTextRules" label-position="top">
              <el-form-item prop="confirmText">
                <el-input v-model="confirmTextInput.confirmText" placeholder="请输入 我真的不想要我的号辣 以确认"
                  @keyup.enter="handleSubmit" autofocus />
              </el-form-item>
            </el-form>

            <p class="confirm-hint">输入确切的短语 <strong>我真的不想要我的号辣</strong> 来确认账号注销</p>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToUsername">返回</el-button>
              <el-button class="next-btn danger-btn" @click="handleSubmit" :loading="submitLoading"
                :disabled="confirmTextInput.confirmText !== '我真的不想要我的号辣'">
                确认注销
              </el-button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useDark } from '@vueuse/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, Lightning, WarningFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { deleteAccount, checkSensitiveVerification } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

// 表单引用
const usernameFormRef = ref<FormInstance>()
const confirmTextFormRef = ref<FormInstance>()

// 流程步骤
const step = ref<'username' | 'confirm'>('username')
const stepDirection = ref<'forward' | 'backward'>('forward')

const stepOrder = ['username', 'confirm']

const updateStep = (newStep: 'username' | 'confirm') => {
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

// 表单数据
const usernameInput = ref({
  username: '',
})

const confirmTextInput = ref({
  confirmText: '',
})

// 验证规则
const usernameRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 1, message: '用户名不能为空', trigger: 'blur' },
  ],
}

const confirmTextRules: FormRules = {
  confirmText: [
    { required: true, message: '请输入 我真的不想要我的号辣 以确认', trigger: 'blur' },
  ],
}

// 加载状态
const submitLoading = ref(false)

onMounted(async () => {
  await userStore.fetchUserInfo()

  // 进入页面时检查敏感操作验证
  try {
    const status = await checkSensitiveVerification()
    if (!status.verified) {
      ElMessage.warning('请先完成身份验证')
      router.push({
        path: '/sensitive-verification',
        query: { returnTo: '/delete-account' }
      })
    }
  } catch (error) {
    console.error('Check sensitive verification failed:', error)
    router.push({
      path: '/sensitive-verification',
      query: { returnTo: '/delete-account' }
    })
  }

  // 预填用户名
  if (userStore.user?.username) {
    usernameInput.value.username = userStore.user.username
  }
})

const handleCancel = () => {
  router.push('/home/security')
}

const goToConfirmStep = async () => {
  try {
    await usernameFormRef.value?.validate()

    // 验证用户名是否正确
    if (usernameInput.value.username !== userStore.user?.username) {
      ElMessage.error('用户名不正确')
      return
    }

    updateStep('confirm')
  } catch (error) {
    console.error('Validation failed:', error)
  }
}

const backToUsername = () => {
  updateStep('username')
  confirmTextInput.value.confirmText = ''
}

const handleSubmit = async () => {
  try {
    await confirmTextFormRef.value?.validate()

    if (confirmTextInput.value.confirmText !== '我真的不想要我的号辣') {
      ElMessage.error('请输入正确的确认文本')
      return
    }

    // 最后的确认对话框
    await ElMessageBox.confirm(
      '您确定要注销账号吗？此操作不可逆转，所有数据将被永久删除。',
      '最终确认',
      {
        confirmButtonText: '确认注销',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    submitLoading.value = true
    await deleteAccount({ confirmText: '我真的不想要我的号辣' })

    ElMessage.success('账号已注销')
    // 清除用户信息并跳转到登录页
    userStore.user = null
    localStorage.removeItem('accessToken')
    router.push('/login')
  } catch (error: any) {
    if (error?.message === 'Cancel' || error?.message === 'cancel') {
      // 用户取消了确认对话框
      return
    }

    console.error('Delete account failed:', error)
    if (error?.response?.status === 403) {
      ElMessage.error('身份验证已过期，请重新验证')
      router.push({
        path: '/sensitive-verification',
        query: { returnTo: '/delete-account' }
      })
    } else if (error?.response?.status === 400) {
      ElMessage.error(error?.response?.data?.msg || '删除失败，请重试')
    } else {
      ElMessage.error('注销失败，请重试')
    }
  } finally {
    submitLoading.value = false
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

/* 警告框 */
.warning-box {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: #fef3cd;
  border: 1px solid #fde08e;
  border-radius: 8px;
  margin-bottom: 24px;
  color: #856404;
}

.warning-box.danger {
  background: #f8d7da;
  border-color: #f5c6cb;
  color: #721c24;
}

.warning-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 2px;
}

.warning-box p {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
}

/* 确认信息 */
.confirm-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.info-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.info-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

/* 提示文本 */
.confirm-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 8px 0 0 0;
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
  background: var(--el-color-primary);
  opacity: 0.6;
  box-shadow: none;
  cursor: not-allowed;
}

.next-btn.danger-btn {
  background: #dc3545;
}

.next-btn.danger-btn:hover:not(:disabled) {
  background: #c82333;
  box-shadow: 0 8px 16px rgba(220, 53, 69, 0.3);
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
