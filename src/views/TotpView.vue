<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <!-- 左侧：Logo和说明 -->
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">启用 TOTP</h1>
        <p class="login-description">使用身份验证器应用保护您的账户</p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lock />
            </el-icon>
            <span class="feature-text">双因素认证</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lightning />
            </el-icon>
            <span class="feature-text">额外保护</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Key />
            </el-icon>
            <span class="feature-text">恢复码</span>
          </div>
        </div>
      </div>

      <!-- 右侧：表单内容 -->
      <div class="login-right">
        <!-- TOTP 状态检查 -->
        <div v-if="statusLoading" class="loading-container">
          <el-skeleton :rows="4" animated />
        </div>

        <!-- 如果已启用，显示提示 -->
        <div v-else-if="totpEnabled" class="step-container">
          <h2 class="step-title">TOTP 已启用</h2>
          <p class="step-subtitle">您的账户已启用两步验证</p>
          <div class="step-actions">
            <el-button class="back-btn" @click="handleCancel">返回</el-button>
          </div>
        </div>

        <!-- 设置流程 -->
        <Transition :name="stepDirection === 'forward' ? 'step-slide-forward' : 'step-slide-backward'" mode="out-in"
          v-else>
          <!-- 第一步：扫码或输入密钥 -->
          <div v-if="step === 'qrcode'" class="step-container" key="qrcode">
            <h2 class="step-title">扫码或输入密钥</h2>
            <p class="step-subtitle">在身份验证器应用中扫描此二维码</p>

            <div class="qrcode-section">
              <canvas ref="qrcodeCanvas" class="qrcode-canvas" v-show="qrcodeReady"></canvas>
              <el-skeleton v-show="qrcodeLoading" :rows="8" animated />
              <div v-if="qrcodeError" class="qrcode-error">
                <el-icon class="qrcode-error-icon">
                  <WarningFilled />
                </el-icon>
                <span>{{ qrcodeError }}</span>
              </div>
            </div>

            <div class="secret-section">
              <div class="secret-label">或者手动输入此密钥：</div>
              <div class="secret-display">
                <span class="secret-text">{{ setupOptions?.secret || '' }}</span>
                <el-button type="primary" link @click="copySecret" size="small">复制</el-button>
              </div>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="handleCancel">取消</el-button>
              <el-button class="next-btn" @click="goToVerifyStep">下一步</el-button>
            </div>
          </div>

          <!-- 第二步：验证码确认 -->
          <div v-else-if="step === 'verify'" class="step-container" key="verify">
            <h2 class="step-title">验证码确认</h2>
            <p class="step-subtitle">请输入身份验证器应用中显示的 6 位动态码</p>

            <el-form ref="verifyFormRef" :model="verifyInput" :rules="verifyRules" label-position="top">
              <el-form-item prop="verificationCode">
                <el-input v-model="verifyInput.verificationCode" placeholder="输入6位动态码" maxlength="6"
                  @input="verifyInput.verificationCode = verifyInput.verificationCode.replace(/[^\d]/g, '').slice(0, 6)"
                  @keydown.enter.prevent="goToRecoveryStep" autofocus />
              </el-form-item>
            </el-form>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToQrcode">返回</el-button>
              <el-button class="next-btn" @click="goToRecoveryStep" :loading="verifyLoading" :disabled="verifyLoading">
                下一步
              </el-button>
            </div>
          </div>

          <!-- 第三步：保存恢复码 -->
          <div v-else-if="step === 'recovery'" class="step-container" key="recovery">
            <h2 class="step-title">保存恢复码</h2>
            <p class="step-subtitle">请妥善保存这些恢复码，以便在丢失访问权限时恢复账户</p>

            <div class="recovery-codes-section">
              <div class="recovery-codes-list">
                <div v-for="(code, index) in setupOptions?.recoveryCodes" :key="index" class="recovery-code-item">
                  {{ code }}
                </div>
              </div>
              <div class="recovery-actions">
                <el-button @click="copyRecoveryCodes" size="small">复制所有</el-button>
                <el-button @click="downloadRecoveryCodes" size="small">下载</el-button>
              </div>
            </div>

            <el-checkbox v-model="recoveryCodesSaved" label="我已妥善保存恢复码" />

            <div class="step-actions">
              <el-button class="back-btn" @click="backToVerify" :disabled="!recoveryCodesSaved">返回</el-button>
              <el-button class="next-btn" @click="handleComplete" :loading="completeLoading"
                :disabled="!recoveryCodesSaved">
                完成设置
              </el-button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useDark } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { Lock, Lightning, Key, WarningFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { checkSensitiveVerification, getTotpStatus, getTotpRegistrationOptions, verifyTotpRegistration } from '@/api/auth'

const router = useRouter()
const verifyFormRef = ref<FormInstance>()

// 流程步骤
const step = ref<'qrcode' | 'verify' | 'recovery'>('qrcode')
const stepDirection = ref<'forward' | 'backward'>('forward')

const stepOrder = ['qrcode', 'verify', 'recovery']

const updateStep = (newStep: 'qrcode' | 'verify' | 'recovery') => {
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

// TOTP 状态
const statusLoading = ref(true)
const totpEnabled = ref(false)

// TOTP 设置
const setupOptions = ref<{ secret: string; qrCodeUrl: string; recoveryCodes: string[] } | null>(null)
const qrcodeCanvas = ref<HTMLCanvasElement | null>(null)
const qrcodeReady = ref(false)
const qrcodeRendering = ref(false)
const qrcodeLoading = ref(true)
const qrcodeError = ref('')

// 验证码输入
const verifyInput = ref({
  verificationCode: ''
})

const verifyRules: FormRules = {
  verificationCode: [
    { required: true, message: '请输入 6 位动态码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: (err?: Error) => void) => {
        const v = (value || '').trim()
        if (!/^[0-9]{6}$/.test(v)) {
          callback(new Error('请输入6位动态码'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 加载和提交状态
const verifyLoading = ref(false)
const completeLoading = ref(false)
const recoveryCodesSaved = ref(false)

// 初始化
onMounted(async () => {
  try {
    // 检查敏感操作验证
    const verificationStatus = await checkSensitiveVerification()
    if (!verificationStatus.verified) {
      ElMessage.info('需要验证身份')
      router.push({
        path: '/sensitive-verification',
        query: { returnTo: router.currentRoute.value.fullPath }
      })
      return
    }

    // 获取 TOTP 状态
    const status = await getTotpStatus()
    totpEnabled.value = status.enabled

    if (!totpEnabled.value) {
      const options = await getTotpRegistrationOptions()
      setupOptions.value = options
    }
  } catch (error) {
    console.error('Failed to fetch TOTP status or setup options:', error)
    ElMessage.error('加载失败')
  } finally {
    statusLoading.value = false
  }
})

const renderQrCode = async () => {
  if (qrcodeRendering.value) return
  qrcodeError.value = ''
  qrcodeLoading.value = true
  qrcodeReady.value = false
  if (!setupOptions.value?.qrCodeUrl) {
    qrcodeError.value = '二维码数据缺失，请刷新后重试'
    qrcodeLoading.value = false
    return
  }
  if (!qrcodeCanvas.value) return
  qrcodeRendering.value = true
  try {
    await nextTick()
    await QRCode.toCanvas(qrcodeCanvas.value, setupOptions.value.qrCodeUrl, {
      errorCorrectionLevel: 'H',
      width: 256,
      margin: 1,
      color: {
        dark: '#000000',
        light: '#FFFFFF'
      }
    })
    qrcodeReady.value = true
  } catch (error) {
    console.error('Failed to generate QR code:', error)
    qrcodeError.value = '生成二维码失败，请刷新重试'
  } finally {
    qrcodeLoading.value = false
    qrcodeRendering.value = false
  }
}

watch([statusLoading, totpEnabled, setupOptions, step, qrcodeCanvas], async () => {
  if (!statusLoading.value && !totpEnabled.value && step.value === 'qrcode') {
    await renderQrCode()
  }
}, { immediate: true, flush: 'post' })

const handleCancel = () => {
  router.push('/home/login-options')
}

// 步骤导航
const goToVerifyStep = async () => {
  updateStep('verify')
}

const backToQrcode = () => {
  updateStep('qrcode')
  verifyInput.value.verificationCode = ''
}

const goToRecoveryStep = async () => {
  if (verifyLoading.value) return
  verifyLoading.value = true
  try {
    await verifyFormRef.value?.validate()

    if (!setupOptions.value?.recoveryCodes?.length) {
      ElMessage.error('恢复码缺失，请重新获取 TOTP 注册选项')
      return
    }

    // 验证 TOTP 注册
    await verifyTotpRegistration({
      code: verifyInput.value.verificationCode.trim(),
      recoveryCodes: setupOptions.value.recoveryCodes,
    })

    updateStep('recovery')
  } catch (error: unknown) {
    console.error('Verify TOTP registration failed:', error)
  } finally {
    verifyLoading.value = false
  }
}

const backToVerify = () => {
  updateStep('verify')
  recoveryCodesSaved.value = false
}

const handleComplete = async () => {
  try {
    completeLoading.value = true
    ElMessage.success('TOTP 已成功启用')
    router.push('/home/login-options')
  } catch (error) {
    console.error('Complete TOTP setup failed:', error)
  } finally {
    completeLoading.value = false
  }
}

// 工具函数
const copySecret = async () => {
  if (!setupOptions.value) return
  try {
    await navigator.clipboard.writeText(setupOptions.value.secret)
    ElMessage.success('密钥已复制')
  } catch (error) {
    console.error('Failed to copy secret:', error)
    ElMessage.error('复制失败')
  }
}

const copyRecoveryCodes = async () => {
  if (!setupOptions.value?.recoveryCodes) return
  try {
    const text = setupOptions.value.recoveryCodes.join('\n')
    await navigator.clipboard.writeText(text)
    ElMessage.success('恢复码已复制')
  } catch (error) {
    console.error('Failed to copy recovery codes:', error)
    ElMessage.error('复制失败')
  }
}

const downloadRecoveryCodes = () => {
  if (!setupOptions.value?.recoveryCodes) return
  const element = document.createElement('a')
  const text = setupOptions.value.recoveryCodes.join('\n')
  element.setAttribute('href', `data:text/plain;charset=utf-8,${encodeURIComponent(text)}`)
  element.setAttribute('download', `recovery-codes-${new Date().toISOString().split('T')[0]}.txt`)
  element.style.display = 'none'
  document.body.appendChild(element)
  element.click()
  document.body.removeChild(element)
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

/* 二维码部分 */
.qrcode-section {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  margin: 24px 0;
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
}

.qrcode-canvas {
  max-width: 100%;
  height: auto;
}

.qrcode-error {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-color-danger);
  font-size: 13px;
  padding: 8px 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  margin-top: 12px;
}

.qrcode-error-icon {
  color: var(--el-color-danger);
}

/* 密钥部分 */
.secret-section {
  margin: 24px 0;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.secret-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;
}

.secret-display {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--el-bg-color);
  border-radius: 6px;
  border: 1px solid var(--el-border-color);
}

.secret-text {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
  color: var(--el-text-color-primary);
  word-break: break-all;
  flex: 1;
}

/* 恢复码部分 */
.recovery-codes-section {
  margin: 24px 0;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.recovery-codes-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 16px;
  max-height: 200px;
  overflow-y: auto;
}

.recovery-code-item {
  padding: 8px 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  color: var(--el-text-color-primary);
  text-align: center;
}

.recovery-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
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

/* Checkbox 样式 */
:deep(.el-checkbox) {
  margin: 16px 0;
}

:deep(.el-checkbox__label) {
  color: var(--el-text-color-regular);
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

/* 加载容器 */
.loading-container {
  width: 100%;
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

  .recovery-codes-list {
    grid-template-columns: 1fr;
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

  .recovery-codes-list {
    grid-template-columns: 1fr;
  }
}
</style>
