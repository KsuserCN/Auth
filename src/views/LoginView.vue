<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <!-- 左侧：Logo和说明 -->
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">登录</h1>
        <p class="login-description">使用多种验证方式登录您的Ksuser账户</p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lock />
            </el-icon>
            <span class="feature-text">安全认证</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lightning />
            </el-icon>
            <span class="feature-text">快速登录</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Key />
            </el-icon>
            <span class="feature-text">多种方式</span>
          </div>
        </div>
      </div>

      <!-- 右侧：表单内容 -->
      <div class="login-right">
        <Transition :name="stepDirection === 'forward' ? 'step-slide-forward' : 'step-slide-backward'" mode="out-in">
          <!-- 第一步：邮箱输入 -->
          <div v-if="step === 'email'" class="step-container" key="email">
            <h2 class="step-title">开始登录</h2>
            <p class="step-subtitle">输入您的邮箱地址</p>

            <el-form ref="emailFormRef" :model="emailInput" :rules="emailRules" label-position="top">
              <el-form-item prop="email">
                <el-input v-model="emailInput.email" type="email" placeholder="邮箱地址" @keyup.enter="goToNextStep"
                  autocomplete="username email" autofocus />
              </el-form-item>
            </el-form>

            <div class="step-actions">
              <el-button class="next-btn" @click="goToNextStep" :loading="validateLoading">
                下一步
              </el-button>
            </div>

            <div class="create-account">
              还没有账号？<router-link to="/register" class="link">创建账号</router-link>
            </div>
          </div>

          <!-- 第二步：选择登录方式 -->
          <div v-else-if="step === 'method'" class="step-container" key="method"
            @keyup.enter="!methodSelecting && selectMethod('password')">
            <h2 class="step-title">选择登录方式</h2>

            <div class="email-confirm">
              <span class="email-display">{{ emailInput.email }}</span>
              <el-button link class="change-email-btn" @click="updateStep('email')">不是您的邮箱？</el-button>
            </div>

            <div class="method-list">
              <!-- 密码登录 -->
              <div class="method-option" @click="!methodSelecting && selectMethod('password')"
                :class="{ 'is-disabled': methodSelecting }">
                <el-icon class="method-icon" :size="28">
                  <Lock />
                </el-icon>
                <div class="method-info">
                  <h3>使用密码登录</h3>
                  <p>输入您的密码</p>
                </div>
                <el-icon class="method-arrow" v-if="!methodSelecting">
                  <ArrowRight />
                </el-icon>
                <el-icon class="method-arrow loading-icon" v-else>
                  <Loading />
                </el-icon>
              </div>

              <!-- 邮箱验证码登录 -->
              <div class="method-option" @click="!methodSelecting && selectMethod('email')"
                :class="{ 'is-disabled': methodSelecting }">
                <el-icon class="method-icon" :size="28">
                  <Message />
                </el-icon>
                <div class="method-info">
                  <h3>使用验证码登录</h3>
                  <p>将验证码发送到您的邮箱</p>
                </div>
                <el-icon class="method-arrow" v-if="!methodSelecting">
                  <ArrowRight />
                </el-icon>
                <el-icon class="method-arrow loading-icon" v-else>
                  <Loading />
                </el-icon>
              </div>

              <!-- Passkey 登录 -->
            </div>
          </div>

          <!-- 第三步：密码登录 -->
          <div v-else-if="step === 'password'" class="step-container" key="password">
            <h2 class="step-title">输入密码</h2>

            <div class="email-confirm">
              <span class="email-display">{{ emailInput.email }}</span>
              <el-button link class="change-email-btn" @click="backToMethod">回到上一页</el-button>
            </div>

            <el-form ref="passwordFormRef" :model="passwordInput" :rules="passwordRules" label-position="top">
              <el-form-item prop="password">
                <el-input v-model="passwordInput.password" type="password" placeholder="密码" show-password
                  @keyup.enter="handlePasswordLogin" autocomplete="current-password" autofocus />
              </el-form-item>
            </el-form>

            <router-link to="/forgot-password" class="forgot-link">忘记密码？</router-link>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToMethod">返回</el-button>
              <el-button class="next-btn" @click="handlePasswordLogin" :loading="passwordLoading">
                登录
              </el-button>
            </div>
          </div>

          <!-- 第三步：邮箱验证码登录 -->
          <div v-else-if="step === 'email-code'" class="step-container" key="email-code">
            <h2 class="step-title">输入验证码</h2>

            <div class="email-confirm">
              <span class="email-display">{{ emailInput.email }}</span>
              <el-button link class="change-email-btn" @click="backToMethod">回到上一页</el-button>
            </div>

            <el-form ref="codeFormRef" :model="codeInput" :rules="codeRules" label-position="top">
              <el-form-item prop="code">
                <el-input v-model="codeInput.code" placeholder="输入6位验证码" maxlength="6"
                  @input="codeInput.code = codeInput.code.replace(/[^\d]/g, '')" @keyup.enter="handleEmailCodeLogin"
                  autofocus />
              </el-form-item>
            </el-form>

            <div class="code-actions">
              <el-button v-if="!canResendCode" disabled class="resend-btn resend-disabled">
                {{ codeCountdown }}s 后重新发送
              </el-button>
              <el-button v-else type="primary" @click="resendCode" class="resend-btn">重新发送验证码</el-button>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToMethod">返回</el-button>
              <el-button class="next-btn" @click="handleEmailCodeLogin" :loading="codeLoading">
                登录
              </el-button>
            </div>
          </div>

          <!-- 第四步：TOTP 验证 -->
          <div v-else-if="step === 'totp'" class="step-container" key="totp">
            <h2 class="step-title">验证身份</h2>
            <p class="step-subtitle">请输入您的 MFA 验证码</p>

            <el-form ref="totpFormRef" :model="totpInput" :rules="totpRules" label-position="top">
              <el-form-item prop="code">
                <el-input v-model="totpInput.code" placeholder="输入6位验证码" maxlength="6"
                  @input="totpInput.code = totpInput.code.replace(/[^\d]/g, '')" @keyup.enter="handleTotpVerify"
                  autofocus />
              </el-form-item>
            </el-form>

            <p class="step-subtitle">使用 TOTP 应用中的验证码，或输入您的回复码</p>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToMFASource">返回</el-button>
              <el-button class="next-btn" @click="handleTotpVerify" :loading="totpLoading">
                验证
              </el-button>
            </div>
          </div>

        </Transition>

        <div v-if="step === 'email'" class="extra-login">
          <div class="extra-title">其他登录方式</div>
          <div class="extra-actions">
            <div class="extra-icons">
              <button class="icon-btn" @click="handleUnsupportedLogin('微信')" aria-label="微信登录" type="button">
                <i class="fa-brands fa-weixin" aria-hidden="true"></i>
              </button>
              <button class="icon-btn" @click="handleUnsupportedLogin('qq')" aria-label="QQ 登录" type="button">
                <i class="fa-brands fa-qq" aria-hidden="true"></i>
              </button>
              <button class="icon-btn" @click="handleUnsupportedLogin('Github')" aria-label="Github 登录" type="button">
                <i class="fa-brands fa-github" aria-hidden="true"></i>
              </button>
              <button class="icon-btn" @click="handleUnsupportedLogin('微软')" aria-label="微软登录" type="button">
                <i class="fa-brands fa-microsoft" aria-hidden="true"></i>
              </button>
            </div>
            <el-button class="extra-btn" :loading="passkeyLoading" :disabled="!isPasskeySupported"
              @click="handlePasskeyLogin">
              {{ isPasskeySupported ? 'Passkey 登录' : 'Passkey 不可用' }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useDark } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { ArrowRight, Lock, Lightning, Key, Message, Loading } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  passwordLogin,
  sendLoginCode,
  loginWithCode,
  getPasskeyAuthenticationOptions,
  verifyPasskeyAuthentication,
  verifyTOTPForLogin,
  type MFAChallenge,
  type LoginResponse
} from '@/api/auth'
import { isWebAuthnSupported, getPasskeyCredential, extractAuthenticationData } from '@/utils/webauthn'

// 表单引用
const emailFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const codeFormRef = ref<FormInstance>()
const totpFormRef = ref<FormInstance>()

// 路由
const router = useRouter()

// 流程步骤
const step = ref<'email' | 'method' | 'password' | 'email-code' | 'totp'>('email')
const stepDirection = ref<'forward' | 'backward'>('forward')

const stepOrder = ['email', 'method', 'password', 'email-code', 'totp']

const updateStep = (newStep: 'email' | 'method' | 'password' | 'email-code' | 'totp') => {
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

// 邮箱输入
const emailInput = ref({
  email: '',
})

const emailRules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
}

// 密码输入
const passwordInput = ref({
  password: '',
})

const passwordRules = {
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// 验证码输入
const codeInput = ref({
  code: '',
})

const codeRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码应为6位数字', trigger: 'blur' },
  ],
}

// TOTP 验证码输入
const totpInput = ref({
  code: '',
})

const totpRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码应为6位数字', trigger: 'blur' },
  ],
}

// 加载状态
const validateLoading = ref(false)
const passwordLoading = ref(false)
const codeLoading = ref(false)
const passkeyLoading = ref(false)
const methodSelecting = ref(false)
const totpLoading = ref(false)

// MFA 相关状态
const mfaChallenge = ref<MFAChallenge | null>(null)
const mfaSource = ref<'password' | 'email-code' | 'passkey' | null>(null)

// 验证码倒计时
const codeCountdown = ref(0)
const canResendCode = ref(false)
let codeCountdownTimer: number | null = null

// Passkey 支持检测
const isPasskeySupported = ref(false)

onMounted(() => {
  isPasskeySupported.value = isWebAuthnSupported()
})

// ===== 第一步：邮箱验证 =====

const goToNextStep = async () => {
  try {
    validateLoading.value = true
    await emailFormRef.value?.validate()
    updateStep('method')
  } catch {
    // 表单验证失败，Element Plus 会显示错误提示
  } finally {
    validateLoading.value = false
  }
}

// ===== 第二步：选择登录方式 =====

const selectMethod = async (method: 'password' | 'email') => {
  if (methodSelecting.value) return

  methodSelecting.value = true

  try {
    if (method === 'password') {
      await new Promise((resolve) => setTimeout(resolve, 200))
      updateStep('password')
    } else if (method === 'email') {
      await sendCode()
    }
  } finally {
    methodSelecting.value = false
  }
}

const backToMethod = () => {
  updateStep('method')
  codeInput.value.code = ''
  cleanupCodeCountdown()
}

// ===== 第三步：密码登录 =====

const handlePasswordLogin = async () => {
  try {
    await passwordFormRef.value?.validate()
    passwordLoading.value = true

    // 调用密码登录接口
    const response = await passwordLogin({
      email: emailInput.value.email,
      password: passwordInput.value.password
    })

    // 检查是否需要 MFA 验证
    if ('challengeId' in response) {
      // 需要 MFA 验证
      mfaChallenge.value = response as MFAChallenge
      mfaSource.value = 'password'
      ElMessage.info('需要进行 MFA 验证')
      updateStep('totp')
    } else {
      // 直接登录成功
      const loginResp = response as LoginResponse
      sessionStorage.setItem('accessToken', loginResp.accessToken)
      if (loginResp.user) {
        sessionStorage.setItem('user', JSON.stringify(loginResp.user))
      }
      ElMessage.success('登录成功')
      router.push('/home')
    }
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('Password login failed:', error)
  } finally {
    passwordLoading.value = false
  }
}

// ===== 第三步：邮箱验证码登录 =====

const sendCode = async () => {
  try {
    // 调用发送登录验证码接口
    await sendLoginCode({
      email: emailInput.value.email
    })

    ElMessage.success('验证码已发送')
    updateStep('email-code')
    codeInput.value.code = ''
    startCodeCountdown()
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('Send code failed:', error)
  }
}

const resendCode = async () => {
  try {
    codeLoading.value = true

    // 调用发送登录验证码接口
    await sendLoginCode({
      email: emailInput.value.email
    })

    ElMessage.success('验证码已重新发送')
    codeInput.value.code = ''
    startCodeCountdown()
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('Resend code failed:', error)
  } finally {
    codeLoading.value = false
  }
}

const startCodeCountdown = () => {
  canResendCode.value = false
  codeCountdown.value = 60
  cleanupCodeCountdown()

  codeCountdownTimer = window.setInterval(() => {
    codeCountdown.value--
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
}

const handleEmailCodeLogin = async () => {
  try {
    await codeFormRef.value?.validate()
    codeLoading.value = true

    // 调用邮箱验证码登录接口
    const response = await loginWithCode({
      email: emailInput.value.email,
      code: codeInput.value.code
    })

    // 检查是否需要 MFA 验证
    if ('challengeId' in response) {
      // 需要 MFA 验证
      mfaChallenge.value = response as MFAChallenge
      mfaSource.value = 'email-code'
      ElMessage.info('需要进行 MFA 验证')
      updateStep('totp')
    } else {
      // 直接登录成功
      const loginResp = response as LoginResponse
      sessionStorage.setItem('accessToken', loginResp.accessToken)
      if (loginResp.user) {
        sessionStorage.setItem('user', JSON.stringify(loginResp.user))
      }
      ElMessage.success('登录成功')
      router.push('/home')
    }
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('Email code login failed:', error)
  } finally {
    codeLoading.value = false
  }
}

// ===== 第三步：Passkey登录 =====

const handlePasskeyLogin = async () => {
  if (passkeyLoading.value) return
  if (!isPasskeySupported.value) {
    ElMessage.error('当前浏览器不支持 Passkey')
    return
  }

  try {
    passkeyLoading.value = true

    // 1. 获取 Passkey 认证选项
    const options = await getPasskeyAuthenticationOptions()

    // 2. 调用浏览器 WebAuthn API 进行认证
    const credential = await getPasskeyCredential({
      challenge: options.challenge,
      timeout: options.timeout,
      rpId: options.rpId,
      userVerification: options.userVerification,
    })

    if (!credential) {
      throw new Error('未获取到凭证')
    }

    // 3. 提取认证数据
    const authData = extractAuthenticationData(credential)

    // 4. 验证凭证
    const response = await verifyPasskeyAuthentication(options.challengeId, authData)

    // 检查是否需要 MFA 验证
    if ('challengeId' in response) {
      // 需要 MFA 验证
      mfaChallenge.value = response as MFAChallenge
      mfaSource.value = 'passkey'
      ElMessage.info('需要进行 MFA 验证')
      updateStep('totp')
    } else {
      // 直接登录成功
      const loginResp = response as LoginResponse
      sessionStorage.setItem('accessToken', loginResp.accessToken)
      if (loginResp.user) {
        sessionStorage.setItem('user', JSON.stringify(loginResp.user))
      }
      ElMessage.success('Passkey 登录成功')
      router.push('/home')
    }
  } catch (error: unknown) {
    // 处理不同类型的错误
    if (error instanceof Error) {
      if (error.name === 'NotAllowedError') {
        ElMessage.error('用户取消了认证')
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
      if (error.name === 'InvalidStateError') {
        ElMessage.error('当前账号没有可用的 Passkey')
        return
      }
      console.error('Passkey login failed:', error)
    } else {
      console.error('Passkey login failed:', error)
    }

    ElMessage.error('Passkey 登录失败，请重试')
  } finally {
    passkeyLoading.value = false
  }
}

const handleUnsupportedLogin = (provider: string) => {
  ElMessage.info(`${provider.toUpperCase()} 登录 - 暂不支持`)
}

// ===== 第四步：TOTP 验证 =====

const handleTotpVerify = async () => {
  try {
    await totpFormRef.value?.validate()
    totpLoading.value = true

    if (!mfaChallenge.value) {
      ElMessage.error('MFA 挑战信息丢失')
      return
    }

    // 调用 TOTP 验证接口
    const response = await verifyTOTPForLogin({
      challengeId: mfaChallenge.value.challengeId,
      code: totpInput.value.code
    })

    // 存储 Access Token 到 sessionStorage
    sessionStorage.setItem('accessToken', response.accessToken)

    // 如果返回了用户信息，也一并存储
    if (response.user) {
      sessionStorage.setItem('user', JSON.stringify(response.user))
    }

    ElMessage.success('MFA 验证成功，登录完成')

    // 跳转到首页
    router.push('/home')
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('TOTP verification failed:', error)
  } finally {
    totpLoading.value = false
  }
}

const backToMFASource = () => {
  const source = mfaSource.value
  mfaChallenge.value = null
  mfaSource.value = null
  totpInput.value.code = ''

  // 根据 MFA 来源返回到对应的页面
  switch (source) {
    case 'password':
      updateStep('password')
      break
    case 'email-code':
      updateStep('email-code')
      break
    case 'passkey':
      // Passkey 登录没有中间步骤，直接返回到 email
      emailInput.value.email = ''
      updateStep('email')
      break
    default:
      updateStep('email')
  }
}

// ===== 生命周期 =====

const handleKeyPress = (e: KeyboardEvent) => {
  const target = e.target as HTMLElement
  const isInput = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA'

  if (step.value === 'method' && e.key === 'Enter' && !isInput && !methodSelecting.value) {
    e.preventDefault()
    selectMethod('password')
  }
}

onMounted(() => {
  isPasskeySupported.value = Boolean(window.PublicKeyCredential)
  window.addEventListener('keypress', handleKeyPress)
})

onBeforeUnmount(() => {
  cleanupCodeCountdown()
  window.removeEventListener('keypress', handleKeyPress)
})
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

@keyframes fadeIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

/* Vue Transition - Fade */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
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

/* 邮箱确认 */
.email-confirm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32px;
  padding: 16px 20px;
  background: var(--el-fill-color-light);
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
}

.email-display {
  font-size: 16px;
  color: var(--el-text-color-primary);
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.change-email-btn,
.change-email-btn:hover {
  color: var(--el-color-primary);
  font-weight: 500;
  flex-shrink: 0;
  margin-left: 16px;
}

.code-hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}

/* 登录方式列表 */
.method-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.method-option {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: var(--el-fill-color-light);
  border: 1.5px solid var(--el-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.method-option:hover {
  background: var(--el-bg-color);
  border-color: var(--el-color-primary);
  box-shadow: 0 8px 16px rgba(255, 185, 15, 0.15);
  transform: translateY(-2px);
}

.method-option:active {
  transform: scale(0.98);
}

.method-option.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: none;
}

.loading-icon {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.method-icon {
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.method-info {
  flex: 1;
}

.method-info h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.method-info p {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.method-arrow {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  font-size: 20px;
}

/* 验证码操作 */
.code-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  width: 100%;
}

.resend-btn {
  flex: 1;
  font-size: 14px;
  height: 44px;
  border-radius: 10px;
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.resend-btn.resend-disabled {
  background: var(--el-fill-color);
  color: var(--el-text-color-placeholder);
  border: 1.5px solid var(--el-border-color);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
  cursor: not-allowed;
}

.resend-btn:not(.resend-disabled) {
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  color: white;
  border: none;
  box-shadow: 0 4px 12px rgba(255, 185, 15, 0.2);
}

.resend-btn:not(.resend-disabled):hover {
  box-shadow: 0 8px 16px rgba(255, 185, 15, 0.3);
  transform: translateY(-2px);
}

.resend-btn:not(.resend-disabled):active {
  transform: translateY(0) scale(0.98);
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

/* 链接 */
.forgot-link {
  display: inline-block;
  font-size: 14px;
  color: var(--el-color-primary);
  text-decoration: none;
  margin-bottom: 24px;
  transition: all 0.2s;
}

.forgot-link:hover {
  color: #ffd700;
  text-decoration: underline;
}

.create-account {
  text-align: center;
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin-top: 24px;
}

.create-account .link {
  color: var(--el-color-primary);
  text-decoration: none;
  font-weight: 500;
  margin-left: 4px;
  transition: all 0.2s;
}

.create-account .link:hover {
  color: #ffd700;
  text-decoration: underline;
}

/* 输入框样式 */
:deep(.el-input__wrapper) {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

:deep(.el-input:focus-within .el-input__wrapper) {
  box-shadow:
    0 0 0 2px rgba(255, 185, 15, 0.1),
    0 1px 8px rgba(255, 185, 15, 0.2);
  border-color: var(--el-color-primary);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 2px rgba(255, 185, 15, 0.1),
    0 1px 8px rgba(255, 185, 15, 0.2);
  border-color: var(--el-color-primary);
}

:deep(.el-input input) {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-input input::placeholder) {
  color: #d3d3d3;
  transition: color 0.3s ease;
}

:deep(.el-input input:focus::placeholder) {
  color: #ccc;
}

/* Form Item样式 */
:deep(.el-form-item) {
  transition: all 0.3s ease;
  margin-bottom: 20px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #202124;
  transition: color 0.3s ease;
}

/* Passkey 提示 */
.passkey-hint {
  background: var(--el-fill-color-light);
  border: 1.5px solid var(--el-border-color);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
}

.passkey-hint p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

/* 额外登录方式 */
.extra-login {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}

.extra-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}

.extra-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.extra-icons {
  display: flex;
  gap: 16px;
  align-items: center;
}

.icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.3s ease;
}

.icon-btn:hover {
  opacity: 0.8;
}

.icon-btn i {
  font-size: 28px;
  color: var(--el-text-color-secondary);
  opacity: 0.7;
  transition: opacity 0.3s ease, color 0.3s ease;
}

.icon-btn:hover i {
  opacity: 0.9;
  color: var(--el-color-primary);
}

.dark .icon-btn i {
  opacity: 0.85;
}

.extra-btn {
  height: 40px;
  border-radius: 10px;
  font-weight: 500;
  white-space: nowrap;
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

  .login-description {
    margin-bottom: 24px;
  }

  .feature-list {
    gap: 12px;
  }

  .feature-item {
    font-size: 13px;
  }

  .feature-icon {
    font-size: 18px;
  }

  .step-actions {
    flex-direction: column-reverse;
  }

  .next-btn,
  .back-btn {
    width: 100%;
  }

  .email-confirm {
    flex-direction: column;
    align-items: flex-start;
  }

  .change-email-btn {
    margin-left: 0;
    margin-top: 12px;
  }

  .method-list {
    gap: 8px;
  }

  .method-option {
    padding: 12px;
  }

  .method-icon {
    font-size: 24px;
  }

  .method-info h3 {
    font-size: 14px;
  }

  .method-info p {
    font-size: 12px;
  }
}
</style>
