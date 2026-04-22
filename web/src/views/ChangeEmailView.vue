<template>
    <div class="login-container" :class="{ dark: isDark }">
        <div class="login-box">
            <!-- 左侧：Logo和说明 -->
            <div class="login-left">
                <div class="logo-section">
                    <img src="/favicon.ico" alt="Logo" class="logo-icon" />
                </div>
                <h1 class="login-title">更改邮箱</h1>
                <p class="login-description">绑定新的邮箱以继续接收安全通知</p>
                <div class="feature-list">
                    <div class="feature-item">
                        <el-icon class="feature-icon" :size="20">
                            <Lock />
                        </el-icon>
                        <span class="feature-text">安全验证</span>
                    </div>
                    <div class="feature-item">
                        <el-icon class="feature-icon" :size="20">
                            <Lightning />
                        </el-icon>
                        <span class="feature-text">快速换绑</span>
                    </div>
                    <div class="feature-item">
                        <el-icon class="feature-icon" :size="20">
                            <Message />
                        </el-icon>
                        <span class="feature-text">邮箱验证</span>
                    </div>
                </div>
            </div>

            <!-- 右侧：表单内容 -->
            <div class="login-right">
                <Transition :name="stepDirection === 'forward' ? 'step-slide-forward' : 'step-slide-backward'"
                    mode="out-in">
                    <!-- 第一步：输入新邮箱 -->
                    <div v-if="step === 'email'" class="step-container" key="email">
                        <h2 class="step-title">输入新邮箱</h2>
                        <p class="step-subtitle">请输入新的邮箱地址</p>

                        <el-form ref="emailFormRef" :model="emailInput" :rules="emailRules" label-position="top">
                            <el-form-item prop="email">
                                <el-input v-model="emailInput.email" type="email" placeholder="新邮箱地址"
                                    autocomplete="email" @keyup.enter="handleSendCode" autofocus />
                            </el-form-item>
                        </el-form>

                        <div class="step-actions">
                            <el-button class="back-btn" @click="handleCancel">取消</el-button>
                            <el-button class="next-btn" @click="handleSendCode" :loading="sendLoading">
                                发送验证码
                            </el-button>
                        </div>
                    </div>

                    <!-- 第二步：输入验证码 -->
                    <div v-else-if="step === 'code'" class="step-container" key="code">
                        <h2 class="step-title">输入验证码</h2>
                        <p class="step-subtitle">验证码已发送至 {{ emailInput.email }}</p>

                        <div class="email-confirm">
                            <span class="email-display">{{ emailInput.email }}</span>
                            <el-button link class="change-email-btn" @click="backToEmail">修改邮箱</el-button>
                        </div>

                        <el-form ref="codeFormRef" :model="codeInput" :rules="codeRules" label-position="top">
                            <el-form-item prop="code">
                                <el-input v-model="codeInput.code" placeholder="输入6位验证码" maxlength="6"
                                    @input="codeInput.code = codeInput.code.replace(/[^\d]/g, '')"
                                    @keyup.enter="handleSubmit" autofocus />
                            </el-form-item>
                        </el-form>

                        <div class="code-actions">
                            <el-button v-if="!canResendCode" disabled class="resend-btn resend-disabled">
                                {{ codeCountdown }}s 后重新发送
                            </el-button>
                            <el-button v-else type="primary" @click="resendCode" class="resend-btn"
                                :loading="resendLoading">
                                重新发送验证码
                            </el-button>
                        </div>

                        <div class="step-actions">
                            <el-button class="back-btn" @click="backToEmail">返回</el-button>
                            <el-button class="next-btn" @click="handleSubmit" :loading="submitLoading">
                                完成换绑
                            </el-button>
                        </div>
                    </div>
                </Transition>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useDark } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { Lock, Lightning, Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { changeEmail, checkSensitiveVerification, sendChangeEmailCode } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

// 表单引用
const emailFormRef = ref<FormInstance>()
const codeFormRef = ref<FormInstance>()

// 流程步骤
const step = ref<'email' | 'code'>('email')
const stepDirection = ref<'forward' | 'backward'>('forward')

const stepOrder = ['email', 'code']

const updateStep = (newStep: 'email' | 'code') => {
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
const emailInput = ref({
    email: '',
})

const codeInput = ref({
    code: '',
})

// 验证规则
const emailRules: FormRules = {
    email: [
        { required: true, message: '请输入新邮箱地址', trigger: 'blur' },
        {
            validator: (_rule, value, callback) => {
                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
                if (!emailRegex.test(value)) {
                    callback(new Error('邮箱格式不正确'))
                } else {
                    callback()
                }
            },
            trigger: 'blur'
        }
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
const submitLoading = ref(false)

// 验证码倒计时
const codeCountdown = ref(0)
const canResendCode = ref(false)
let codeCountdownTimer: number | null = null

onMounted(async () => {
    await userStore.fetchUserInfo()

    // 进入页面时检查敏感操作验证
    try {
        const status = await checkSensitiveVerification()
        if (!status.verified) {
            ElMessage.warning('请先完成身份验证')
            router.push({
                path: '/sensitive-verification',
                query: { returnTo: '/change-email' }
            })
        }
    } catch (error) {
        console.error('Check sensitive verification failed:', error)
        router.push({
            path: '/sensitive-verification',
            query: { returnTo: '/change-email' }
        })
    }
})

onBeforeUnmount(() => {
    cleanupCodeCountdown()
})

const handleCancel = () => {
    router.push('/home/login-options')
}

const backToEmail = () => {
    updateStep('email')
    codeInput.value.code = ''
    cleanupCodeCountdown()
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

        await sendChangeEmailCode({ email: emailInput.value.email })
        ElMessage.success('验证码已发送')

        startCodeCountdown()
        updateStep('code')
    } catch (error: any) {
        console.error('Send change email code failed:', error)
        if (error?.response?.status === 403) {
            ElMessage.warning('请先完成身份验证')
            router.push({
                path: '/sensitive-verification',
                query: { returnTo: '/change-email' }
            })
        }
    } finally {
        sendLoading.value = false
    }
}

const resendCode = async () => {
    if (resendLoading.value) return
    resendLoading.value = true
    try {
        await sendChangeEmailCode({ email: emailInput.value.email })
        ElMessage.success('验证码已重新发送')
        startCodeCountdown()
    } catch (error: any) {
        console.error('Resend change email code failed:', error)
        if (error?.response?.status === 403) {
            ElMessage.warning('请先完成身份验证')
            router.push({
                path: '/sensitive-verification',
                query: { returnTo: '/change-email' }
            })
        }
    } finally {
        resendLoading.value = false
    }
}

const handleSubmit = async () => {
    try {
        await codeFormRef.value?.validate()
        submitLoading.value = true

        const updatedUser = await changeEmail({
            newEmail: emailInput.value.email,
            code: codeInput.value.code,
        })

        ElMessage.success('邮箱更新成功')
        userStore.user = updatedUser
        router.push('/home/login-options')
    } catch (error: any) {
        console.error('Change email failed:', error)
        if (error?.response?.status === 403) {
            ElMessage.warning('请先完成身份验证')
            router.push({
                path: '/sensitive-verification',
                query: { returnTo: '/change-email' }
            })
            return
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
