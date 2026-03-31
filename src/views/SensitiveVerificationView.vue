<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <!-- 左侧：Logo和说明 -->
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">敏感操作验证</h1>
        <p class="login-description">为了保护您的账户安全，请先完成身份验证</p>
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
            <span class="feature-text">快速验证</span>
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
          <!-- 第一步：选择验证方式 -->
          <div v-if="step === 'method'" class="step-container" key="method">
            <h2 class="step-title">选择验证方式</h2>
            <p class="step-subtitle">选择一种方式来验证您的身份</p>

            <div class="method-list">
              <!-- 密码验证 -->
              <div class="method-option" @click="!methodSelecting && selectMethod('password')"
                :class="{ 'is-disabled': methodSelecting || !isMethodSelectable('password') }">
                <el-icon class="method-icon" :size="28">
                  <Lock />
                </el-icon>
                <div class="method-info">
                  <h3>使用密码验证</h3>
                  <p>输入您的登录密码</p>
                </div>
                <el-icon class="method-arrow" v-if="!methodSelecting">
                  <ArrowRight />
                </el-icon>
                <el-icon class="method-arrow loading-icon" v-else>
                  <Loading />
                </el-icon>
              </div>

              <!-- 邮箱验证码验证 -->
              <div class="method-option" @click="!methodSelecting && selectMethod('email-code')"
                :class="{ 'is-disabled': methodSelecting || !isMethodSelectable('email-code') }">
                <el-icon class="method-icon" :size="28">
                  <Message />
                </el-icon>
                <div class="method-info">
                  <h3>使用验证码验证</h3>
                  <p>将验证码发送到您的邮箱</p>
                </div>
                <el-icon class="method-arrow" v-if="!methodSelecting">
                  <ArrowRight />
                </el-icon>
                <el-icon class="method-arrow loading-icon" v-else>
                  <Loading />
                </el-icon>
              </div>

              <!-- Passkey 验证 -->
              <div v-if="isMethodAvailable('passkey')" class="method-option"
                @click="!methodSelecting && selectMethod('passkey')"
                :class="{ 'is-disabled': methodSelecting || !isMethodSelectable('passkey') }">
                <el-icon class="method-icon" :size="28">
                  <Key />
                </el-icon>
                <div class="method-info">
                  <h3>使用 Passkey 验证</h3>
                  <p>使用生物识别或安全密钥</p>
                </div>
                <el-icon class="method-arrow" v-if="!methodSelecting">
                  <ArrowRight />
                </el-icon>
                <el-icon class="method-arrow loading-icon" v-else>
                  <Loading />
                </el-icon>
              </div>

              <!-- TOTP 验证 -->
              <div class="method-option" @click="!methodSelecting && selectMethod('totp')"
                :class="{ 'is-disabled': methodSelecting || !isMethodSelectable('totp') }">
                <el-icon class="method-icon" :size="28">
                  <Key />
                </el-icon>
                <div class="method-info">
                  <h3>使用 TOTP 验证</h3>
                  <p>输入身份验证器中的 6 位验证码，或输入 8 位恢复码</p>
                </div>
                <el-icon class="method-arrow" v-if="!methodSelecting">
                  <ArrowRight />
                </el-icon>
                <el-icon class="method-arrow loading-icon" v-else>
                  <Loading />
                </el-icon>
              </div>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="handleCancel">取消</el-button>
            </div>
          </div>

          <!-- 第二步：密码验证 -->
          <div v-else-if="step === 'password'" class="step-container" key="password">
            <h2 class="step-title">输入密码</h2>
            <p class="step-subtitle">请输入您的登录密码以验证身份</p>

            <div v-if="canChooseSensitiveMethod()" class="switch-method-section" @click="backToMethod">
              <el-icon class="switch-method-icon">
                <Refresh />
              </el-icon>
              <span>选择其他验证方式</span>
            </div>

            <el-form ref="passwordFormRef" :model="passwordInput" :rules="passwordRules" label-position="top">
              <el-form-item prop="password">
                <el-input v-model="passwordInput.password" type="password" placeholder="密码" show-password
                  @keyup.enter="handlePasswordVerify" autocomplete="current-password" autofocus />
              </el-form-item>
            </el-form>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToMethod">返回</el-button>
              <el-button class="next-btn" @click="handlePasswordVerify" :loading="passwordLoading">
                验证
              </el-button>
            </div>
          </div>

          <!-- 第二步：邮箱验证码验证 -->
          <div v-else-if="step === 'email-code'" class="step-container" key="email-code">
            <h2 class="step-title">输入验证码</h2>
            <p class="step-subtitle">验证码已发送至 {{ userEmail }}</p>

            <div v-if="canChooseSensitiveMethod()" class="switch-method-section" @click="backToMethod">
              <el-icon class="switch-method-icon">
                <Refresh />
              </el-icon>
              <span>选择其他验证方式</span>
            </div>

            <el-form ref="codeFormRef" :model="codeInput" :rules="codeRules" label-position="top">
              <el-form-item prop="code">
                <el-input v-model="codeInput.code" placeholder="输入6位验证码" maxlength="6"
                  @input="codeInput.code = codeInput.code.replace(/[^\d]/g, '')" @keyup.enter="handleCodeVerify"
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
              <el-button class="next-btn" @click="handleCodeVerify" :loading="codeLoading">
                验证
              </el-button>
            </div>
          </div>

          <!-- 第二步：Passkey 验证 -->
          <div v-else-if="step === 'passkey'" class="step-container" key="passkey">
            <h2 class="step-title">Passkey 验证</h2>
            <p class="step-subtitle">使用生物识别或安全密钥进行身份验证</p>

            <div v-if="canChooseSensitiveMethod()" class="switch-method-section" @click="backToMethod">
              <el-icon class="switch-method-icon">
                <Refresh />
              </el-icon>
              <span>选择其他验证方式</span>
            </div>

            <div class="passkey-hint">
              <p>点击"验证身份"按钮，然后按照浏览器提示完成验证</p>
            </div>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToMethod">返回</el-button>
              <el-button class="next-btn" @click="handlePasskeyVerify" :loading="passkeyLoading">
                {{ passkeyLoading ? '验证中...' : '验证身份' }}
              </el-button>
            </div>
          </div>

          <!-- 第二步：TOTP 验证 -->
          <div v-else-if="step === 'totp'" class="step-container" key="totp">
            <h2 class="step-title">TOTP 验证</h2>
            <p class="step-subtitle">请选择一种方式完成 TOTP 验证</p>

            <div class="totp-mode-switch" role="tablist" aria-label="验证码类型选择">
              <button type="button" class="totp-mode-chip" :class="{ active: totpMode === 'totp' }"
                :aria-pressed="totpMode === 'totp'" @click="setTotpMode('totp')">
                <span class="chip-title">动态码</span>
                <span class="chip-meta">推荐</span>
              </button>
              <button type="button" class="totp-mode-chip" :class="{ active: totpMode === 'recovery' }"
                :aria-pressed="totpMode === 'recovery'" @click="setTotpMode('recovery')">
                <span class="chip-title">恢复码</span>
                <span class="chip-meta">应急</span>
              </button>
            </div>

            <div v-if="canChooseSensitiveMethod()" class="switch-method-section" @click="backToMethod">
              <el-icon class="switch-method-icon">
                <Refresh />
              </el-icon>
              <span>选择其他验证方式</span>
            </div>

            <el-form ref="totpFormRef" :model="totpInput" :rules="totpRules" label-position="top">
              <el-form-item prop="code">
                <el-input v-model="totpInput.code" :placeholder="totpMode === 'totp' ? '输入6位动态码' : '输入8位大写字母恢复码'"
                  :maxlength="totpMode === 'totp' ? 6 : 8" @input="handleTotpInput" @keyup.enter="handleTotpVerify"
                  autofocus />
              </el-form-item>
            </el-form>

            <p class="totp-mode-hint">{{ totpModeHint }}</p>

            <div class="step-actions">
              <el-button class="back-btn" @click="backToMethod">返回</el-button>
              <el-button class="next-btn" @click="handleTotpVerify" :loading="totpLoading">
                验证
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
import { ArrowRight, Lock, Lightning, Key, Message, Loading, Refresh } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  checkSensitiveVerification,
  verifySensitiveOperation,
  sendSensitiveVerificationCode,
  getPasskeySensitiveVerificationOptions,
  verifyPasskeySensitiveOperation
} from '@/api/auth'
import { isWebAuthnSupported, getPasskeyCredential, extractAuthenticationData } from '@/utils/webauthn'

const router = useRouter()
const userStore = useUserStore()

// 表单引用
const passwordFormRef = ref<FormInstance>()
const codeFormRef = ref<FormInstance>()
const totpFormRef = ref<FormInstance>()

// 流程步骤
const step = ref<'method' | 'password' | 'email-code' | 'passkey' | 'totp'>('method')
const stepDirection = ref<'forward' | 'backward'>('forward')

const stepOrder = ['method', 'password', 'email-code', 'passkey', 'totp']

const updateStep = (newStep: 'method' | 'password' | 'email-code' | 'passkey' | 'totp') => {
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

// 用户邮箱
const userEmail = computed(() => userStore.user?.email || '—')

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

const totpMode = ref<'totp' | 'recovery'>('totp')

const totpModeHint = computed(() => {
  if (totpMode.value === 'totp') {
    return '请输入身份验证器应用中的 6 位数字动态码'
  }

  return '请输入 8 位大写字母恢复码（A-Z）'
})

const totpRules = {
  code: [
    { required: true, message: '请输入验证码或恢复码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: (err?: Error) => void) => {
        const v = (value || '').trim()
        if (totpMode.value === 'totp' && !/^[0-9]{6}$/.test(v)) {
          callback(new Error('请输入 6 位数字动态码'))
          return
        }

        if (totpMode.value === 'recovery' && !/^[A-Z]{8}$/.test(v)) {
          callback(new Error('请输入 8 位大写字母恢复码'))
          return
        }

        if (totpMode.value !== 'totp' && totpMode.value !== 'recovery') {
          callback(new Error('请选择验证方式'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// 加载状态
const passwordLoading = ref(false)
const codeLoading = ref(false)
const passkeyLoading = ref(false)
const totpLoading = ref(false)
const methodSelecting = ref(false)

const availableMethods = ref<Array<'password' | 'email-code' | 'passkey' | 'totp'>>([
  'password',
  'email-code',
  'passkey',
  'totp',
])

// Passkey 支持检测
const isPasskeySupported = ref(false)

// 验证码倒计时
const codeCountdown = ref(0)
const canResendCode = ref(false)
let codeCountdownTimer: number | null = null

const ensureAuthenticated = () => {
  const token = sessionStorage.getItem('accessToken')
  if (!token) {
    ElMessage.warning('请先登录')
    router.replace({
      path: '/login',
      query: { returnTo: router.currentRoute.value.fullPath },
    })
    return false
  }
  return true
}

onMounted(async () => {
  if (!ensureAuthenticated()) return
  await userStore.fetchUserInfo()
  isPasskeySupported.value = isWebAuthnSupported()

  try {
    const status = await checkSensitiveVerification()

    if (status.verified && status.remainingSeconds > 0) {
      const returnTo = router.currentRoute.value.query.returnTo as string || '/home/login-options'
      router.push(returnTo)
      return
    }

    const methods = status.methods ?? ['password', 'email-code', 'passkey', 'totp']
    availableMethods.value = methods.filter(
      (item): item is 'password' | 'email-code' | 'passkey' | 'totp' =>
        item === 'password' || item === 'email-code' || item === 'passkey' || item === 'totp',
    )

    const preferredMethod = status.preferredMethod
    const defaultMethod = [preferredMethod, ...availableMethods.value].find((item) =>
      item && isMethodSelectable(item as 'password' | 'email-code' | 'passkey' | 'totp'),
    ) as 'password' | 'email-code' | 'passkey' | 'totp' | undefined

    if (defaultMethod) {
      await selectMethod(defaultMethod)
    }
  } catch (error) {
    console.error('Load sensitive verification preference failed:', error)
  }
})

const isMethodAvailable = (method: 'password' | 'email-code' | 'passkey' | 'totp') => {
  return availableMethods.value.includes(method)
}

const isMethodSelectable = (method: 'password' | 'email-code' | 'passkey' | 'totp') => {
  if (!isMethodAvailable(method)) return false
  if (method === 'passkey' && !isPasskeySupported.value) return false
  return true
}

const canChooseSensitiveMethod = () => {
  const selectableCount = ['password', 'email-code', 'passkey', 'totp'].filter((item) =>
    isMethodSelectable(item as 'password' | 'email-code' | 'passkey' | 'totp'),
  ).length

  return selectableCount > 1
}

// 选择验证方式
const selectMethod = async (method: 'password' | 'email-code' | 'passkey' | 'totp') => {
  if (methodSelecting.value) return
  if (!isMethodSelectable(method)) {
    ElMessage.error('当前不可使用该验证方式')
    return
  }

  methodSelecting.value = true

  try {
    if (method === 'password') {
      await new Promise((resolve) => setTimeout(resolve, 200))
      updateStep('password')
    } else if (method === 'email-code') {
      await sendCode()
    } else if (method === 'passkey') {
      await new Promise((resolve) => setTimeout(resolve, 200))
      updateStep('passkey')
    } else if (method === 'totp') {
      await new Promise((resolve) => setTimeout(resolve, 200))
      updateStep('totp')
    }
  } finally {
    methodSelecting.value = false
  }
}

const backToMethod = () => {
  updateStep('method')
  codeInput.value.code = ''
  totpInput.value.code = ''
  totpMode.value = 'totp'
  cleanupCodeCountdown()
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

const handleCancel = () => {
  router.back()
}

// 密码验证
const handlePasswordVerify = async () => {
  if (!ensureAuthenticated()) return
  try {
    await passwordFormRef.value?.validate()
    passwordLoading.value = true

    await verifySensitiveOperation({
      method: 'password',
      password: passwordInput.value.password
    })

    ElMessage.success('验证成功')

    // 获取返回地址
    const returnTo = router.currentRoute.value.query.returnTo as string || '/home/login-options'
    router.push(returnTo)
  } catch (error: unknown) {
    console.error('Password verify failed:', error)
  } finally {
    passwordLoading.value = false
  }
}

// 发送验证码
const sendCode = async () => {
  if (!ensureAuthenticated()) return
  try {
    await sendSensitiveVerificationCode()
    ElMessage.success('验证码已发送')
    updateStep('email-code')
    codeInput.value.code = ''
    startCodeCountdown()
  } catch (error: unknown) {
    console.error('Send code failed:', error)
  }
}

const resendCode = async () => {
  if (!ensureAuthenticated()) return
  try {
    codeLoading.value = true
    await sendSensitiveVerificationCode()
    ElMessage.success('验证码已重新发送')
    codeInput.value.code = ''
    startCodeCountdown()
  } catch (error: unknown) {
    console.error('Resend code failed:', error)
  } finally {
    codeLoading.value = false
  }
}

// 验证码验证
const handleCodeVerify = async () => {
  if (!ensureAuthenticated()) return
  try {
    await codeFormRef.value?.validate()
    codeLoading.value = true

    await verifySensitiveOperation({
      method: 'email-code',
      code: codeInput.value.code
    })

    ElMessage.success('验证成功')

    // 获取返回地址
    const returnTo = router.currentRoute.value.query.returnTo as string || '/home/login-options'
    router.push(returnTo)
  } catch (error: unknown) {
    console.error('Code verify failed:', error)
  } finally {
    codeLoading.value = false
  }
}

// TOTP 验证
const handleTotpVerify = async () => {
  if (!ensureAuthenticated()) return
  try {
    await totpFormRef.value?.validate()
    totpLoading.value = true

    const normalizedInput = totpInput.value.code.trim()

    await verifySensitiveOperation(
      totpMode.value === 'recovery'
        ? {
          method: 'totp',
          recoveryCode: normalizedInput.toUpperCase(),
        }
        : {
          method: 'totp',
          code: normalizedInput,
        },
    )

    ElMessage.success('验证成功')

    const returnTo = router.currentRoute.value.query.returnTo as string || '/home/login-options'
    router.push(returnTo)
  } catch (error: unknown) {
    console.error('TOTP verify failed:', error)
  } finally {
    totpLoading.value = false
  }
}

// 倒计时功能
const startCodeCountdown = () => {
  cleanupCodeCountdown()
  codeCountdown.value = 60
  canResendCode.value = false

  codeCountdownTimer = window.setInterval(() => {
    codeCountdown.value--
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

onBeforeUnmount(() => {
  cleanupCodeCountdown()
})

// Passkey 验证
const handlePasskeyVerify = async () => {
  if (!ensureAuthenticated()) return
  if (passkeyLoading.value) return
  if (!isPasskeySupported.value) {
    ElMessage.error('当前浏览器不支持 Passkey')
    return
  }

  try {
    passkeyLoading.value = true

    // 1. 获取 Passkey 敏感操作验证选项
    const options = await getPasskeySensitiveVerificationOptions()

    // 2. 调用浏览器 WebAuthn API 进行认证
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

    // 3. 提取认证数据
    const authData = extractAuthenticationData(credential)

    // 4. 验证凭证
    await verifyPasskeySensitiveOperation(options.challengeId, authData)

    ElMessage.success('验证成功，有效期15分钟')

    // 5. 获取返回地址并跳转
    const returnTo = router.currentRoute.value.query.returnTo as string || '/home/login-options'
    router.push(returnTo)
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
      console.error('Passkey verify failed:', error)
    } else {
      console.error('Passkey verify failed:', error)
    }
  } finally {
    passkeyLoading.value = false
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

/* 选择其他验证方式容器 */
.switch-method-section {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  margin: 12px 0 24px 0;
  background: linear-gradient(135deg, rgba(255, 185, 15, 0.05) 0%, rgba(255, 185, 15, 0.02) 100%);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.switch-method-section:hover {
  background: linear-gradient(135deg, rgba(255, 185, 15, 0.08) 0%, rgba(255, 185, 15, 0.04) 100%);
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 12px rgba(255, 185, 15, 0.12);
}

.switch-method-section:active {
  transform: scale(0.99);
}

.switch-method-icon {
  color: var(--el-color-primary);
  font-size: 18px;
  flex-shrink: 0;
}

.switch-method-btn {
  margin: 0;
  padding: 0;
  color: var(--el-color-primary);
  font-size: 14px;
  font-weight: 500;
  height: auto;
  line-height: 1.5;
}

.switch-method-btn:hover {
  color: var(--el-color-primary);
}

.totp-mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 12px;
}

.totp-mode-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-lighter);
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.totp-mode-chip:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
}

.totp-mode-chip.active {
  border-color: var(--el-color-primary);
  background: rgba(255, 185, 15, 0.08);
  box-shadow: 0 0 0 2px rgba(255, 185, 15, 0.12);
}

.chip-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.chip-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 999px;
  padding: 2px 8px;
}

.totp-mode-chip.active .chip-meta {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

.totp-mode-hint {
  margin: 2px 0 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
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

  .method-list {
    gap: 8px;
  }

  .method-option {
    padding: 12px;
  }
}
</style>
