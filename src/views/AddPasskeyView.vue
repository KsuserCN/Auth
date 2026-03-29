<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <!-- 左侧：Logo和说明 -->
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">添加 Passkey</h1>
        <p class="login-description">使用生物识别或安全密钥快速登录</p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lock />
            </el-icon>
            <span class="feature-text">高度安全</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lightning />
            </el-icon>
            <span class="feature-text">快速验证</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Key />
            </el-icon>
            <span class="feature-text">便捷登录</span>
          </div>
        </div>
      </div>

      <!-- 右侧：表单内容 -->
      <div class="login-right">
        <Transition :name="stepDirection === 'forward' ? 'step-slide-forward' : 'step-slide-backward'" mode="out-in">
          <!-- 第一步：创建凭证 -->
          <div v-if="step === 'create-credential'" class="step-container" key="create-credential">
            <h2 class="step-title">创建 Passkey</h2>
            <p class="step-subtitle">按照浏览器提示完成 Passkey 创建</p>

            <div class="passkey-hint">
              <p>Passkey 使用您设备上的生物识别（如指纹或面部识别）进行身份验证，安全且快速。</p>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="handleCancel">取消</el-button>
              <el-button class="next-btn" @click="handleCreateCredential" :loading="creatingCredential">
                {{ creatingCredential ? '创建中...' : '创建 Passkey' }}
              </el-button>
            </div>
          </div>

          <!-- 第二步：输入名称 -->
          <div v-else-if="step === 'name-input'" class="step-container" key="name-input">
            <h2 class="step-title">设置 Passkey 名称</h2>
            <p class="step-subtitle">为您的 Passkey 取一个便于识别的名称</p>

            <el-form ref="nameFormRef" :model="passkeyInput" :rules="nameRules" label-position="top">
              <el-form-item prop="name">
                <el-input v-model="passkeyInput.name" placeholder="例如：我的 MacBook Pro" maxlength="50" show-word-limit
                  @keyup.enter="handleSubmit" autofocus />
              </el-form-item>
            </el-form>

            <div class="name-hint">
              <p>一个清晰的名称可以帮助您在拥有多个 Passkey 时快速识别。</p>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToCreate">返回</el-button>
              <el-button class="next-btn" @click="handleSubmit" :loading="submitLoading">
                完成
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
import { ElMessage } from 'element-plus'
import { Lock, Lightning, Key } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getPasskeyRegistrationOptions,
  verifyPasskeyRegistration,
} from '@/api/auth'
import { isWebAuthnSupported, createPasskeyCredential, extractRegistrationData } from '@/utils/webauthn'

const router = useRouter()

// 表单引用
const nameFormRef = ref<FormInstance>()

// 流程步骤
const step = ref<'create-credential' | 'name-input'>('create-credential')
const stepDirection = ref<'forward' | 'backward'>('forward')

const stepOrder = ['create-credential', 'name-input']

const updateStep = (newStep: 'create-credential' | 'name-input') => {
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

// Passkey 输入
const passkeyInput = ref({
  name: '',
})

const nameRules: FormRules = {
  name: [
    { required: true, message: '请输入 Passkey 名称', trigger: 'blur' },
    { min: 1, max: 50, message: '名称长度应为1-50个字符', trigger: 'blur' },
  ],
}

// 加载状态
const creatingCredential = ref(false)
const submitLoading = ref(false)

// 凭证存储
let credentialData: PublicKeyCredential | null = null

onMounted(async () => {
  if (!isWebAuthnSupported()) {
    ElMessage.error('当前浏览器不支持 Passkey')
    router.back()
  }
})

const handleCreateCredential = async () => {
  if (creatingCredential.value) return

  try {
    creatingCredential.value = true

    // 1. 获取注册选项（先用空名称）
    const options = await getPasskeyRegistrationOptions({
      passkeyName: 'temp',
      authenticatorType: 'auto',
    })

    // 2. 创建凭证
    const credential = await createPasskeyCredential(options)

    if (!credential) {
      throw new Error('未创建凭证')
    }

    // 3. 存储凭证数据供第二步使用
    credentialData = credential

    ElMessage.success('Passkey 创建成功，请输入名称')

    // 4. 进入第二步
    updateStep('name-input')
    passkeyInput.value.name = ''
  } catch (error: unknown) {
    if (error instanceof Error) {
      if (error.name === 'NotAllowedError') {
        ElMessage.error('用户取消了操作')
        return
      }
      if (error.name === 'NotSupportedError') {
        ElMessage.error('浏览器不支持该操作')
        return
      }
      if (error.name === 'SecurityError') {
        ElMessage.error('安全错误，请检查网络连接')
        return
      }
      console.error('Create passkey failed:', error)
      ElMessage.error(error.message || 'Passkey 创建失败')
    } else {
      console.error('Create passkey failed:', error)
      ElMessage.error('Passkey 创建失败')
    }
  } finally {
    creatingCredential.value = false
  }
}

const handleSubmit = async () => {
  try {
    await nameFormRef.value?.validate()
    submitLoading.value = true

    if (!credentialData) {
      throw new Error('凭证数据丢失')
    }

    // 1. 提取注册数据
    const regData = extractRegistrationData(credentialData)

    // 2. 验证并完成注册
    await verifyPasskeyRegistration({
      ...regData,
      passkeyName: passkeyInput.value.name.trim(),
    })

    ElMessage.success('Passkey 添加成功')

    // 3. 返回登录选项页面
    router.push('/home/login-options')
  } catch (error: unknown) {
    if (error instanceof Error) {
      console.error('Register passkey failed:', error)
      ElMessage.error(error.message || 'Passkey 注册失败')
    } else {
      console.error('Register passkey failed:', error)
      ElMessage.error('Passkey 注册失败')
    }
  } finally {
    submitLoading.value = false
  }
}

const backToCreate = () => {
  updateStep('create-credential')
  credentialData = null
}

const handleCancel = () => {
  router.back()
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
  display: none;
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

/* Passkey 提示 */
.passkey-hint {
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  margin-bottom: 24px;
}

.passkey-hint p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

/* 名称提示 */
.name-hint {
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  margin-bottom: 24px;
}

.name-hint p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

/* 按钮 */
.step-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-bottom: 24px;
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
