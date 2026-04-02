<template>
    <el-dialog :model-value="modelValue" title="敏感操作验证" width="560px" :close-on-click-modal="true" @close="handleClose">
        <div v-if="initializing" class="loading-panel">
            <el-skeleton :rows="4" animated />
        </div>

        <div v-else-if="step === 'method'" class="panel">
            <p class="subtitle">请选择一种方式验证身份</p>
            <div class="method-list">
                <button v-for="method in allMethods" :key="method" type="button" class="method-item"
                    :disabled="methodSelecting || !isMethodSelectable(method)" @click="selectMethod(method)">
                    <div class="method-title">{{ methodLabelMap[method] }}</div>
                    <div class="method-desc">{{ methodDescMap[method] }}</div>
                </button>
            </div>
        </div>

        <div v-else-if="step === 'password'" class="panel">
            <p class="subtitle">请输入登录密码</p>
            <el-form ref="passwordFormRef" :model="passwordInput" :rules="passwordRules" label-position="top">
                <el-form-item prop="password">
                    <el-input v-model="passwordInput.password" type="password" show-password placeholder="输入密码"
                        autocomplete="current-password" @keyup.enter="handlePasswordVerify" />
                </el-form-item>
            </el-form>
            <div class="actions">
                <el-button @click="backToMethod">返回</el-button>
                <el-button type="primary" :loading="passwordLoading" @click="handlePasswordVerify">验证</el-button>
            </div>
        </div>

        <div v-else-if="step === 'email-code'" class="panel">
            <p class="subtitle">验证码已发送到您的邮箱</p>
            <el-form ref="codeFormRef" :model="codeInput" :rules="codeRules" label-position="top">
                <el-form-item prop="code">
                    <el-input v-model="codeInput.code" placeholder="输入6位验证码" maxlength="6"
                        @input="codeInput.code = codeInput.code.replace(/[^\\d]/g, '')"
                        @keyup.enter="handleCodeVerify" />
                </el-form-item>
            </el-form>
            <div class="code-actions">
                <el-button v-if="!canResendCode" disabled>{{ codeCountdown }}s 后可重发</el-button>
                <el-button v-else @click="resendCode">重新发送验证码</el-button>
            </div>
            <div class="actions">
                <el-button @click="backToMethod">返回</el-button>
                <el-button type="primary" :loading="codeLoading" @click="handleCodeVerify">验证</el-button>
            </div>
        </div>

        <div v-else-if="step === 'passkey'" class="panel">
            <p class="subtitle">请按照浏览器提示完成 Passkey 验证</p>
            <div class="actions">
                <el-button @click="backToMethod">返回</el-button>
                <el-button type="primary" :loading="passkeyLoading" @click="handlePasskeyVerify">
                    {{ passkeyLoading ? '验证中...' : '验证身份' }}
                </el-button>
            </div>
        </div>

        <div v-else-if="step === 'totp'" class="panel">
            <p class="subtitle">请输入动态码或恢复码</p>
            <div class="totp-mode-switch" role="tablist" aria-label="TOTP 模式切换">
                <button type="button" class="chip" :class="{ active: totpMode === 'totp' }"
                    @click="setTotpMode('totp')">
                    动态码
                </button>
                <button type="button" class="chip" :class="{ active: totpMode === 'recovery' }"
                    @click="setTotpMode('recovery')">
                    恢复码
                </button>
            </div>
            <el-form ref="totpFormRef" :model="totpInput" :rules="totpRules" label-position="top">
                <el-form-item prop="code">
                    <el-input v-model="totpInput.code" :placeholder="totpMode === 'totp' ? '输入6位动态码' : '输入8位恢复码'"
                        :maxlength="totpMode === 'totp' ? 6 : 8" @input="handleTotpInput"
                        @keyup.enter="handleTotpVerify" />
                </el-form-item>
            </el-form>
            <div class="actions">
                <el-button @click="backToMethod">返回</el-button>
                <el-button type="primary" :loading="totpLoading" @click="handleTotpVerify">验证</el-button>
            </div>
        </div>
    </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
    checkSensitiveVerification,
    getPasskeySensitiveVerificationOptions,
    sendSensitiveVerificationCode,
    verifyPasskeySensitiveOperation,
    verifySensitiveOperation,
} from '@/api/auth'
import { extractAuthenticationData, getPasskeyCredential, isWebAuthnSupported } from '@/utils/webauthn'

const props = defineProps<{
    modelValue: boolean
}>()

const emit = defineEmits<{
    (e: 'update:modelValue', value: boolean): void
    (e: 'success'): void
    (e: 'cancel'): void
}>()

const allMethods: Array<'password' | 'email-code' | 'passkey' | 'totp'> = [
    'password',
    'email-code',
    'passkey',
    'totp',
]

const methodLabelMap: Record<'password' | 'email-code' | 'passkey' | 'totp', string> = {
    password: '密码验证',
    'email-code': '邮箱验证码',
    passkey: 'Passkey 验证',
    totp: 'TOTP 验证',
}

const methodDescMap: Record<'password' | 'email-code' | 'passkey' | 'totp', string> = {
    password: '输入登录密码',
    'email-code': '使用邮箱验证码',
    passkey: '使用生物识别或安全密钥',
    totp: '输入动态码或恢复码',
}

const step = ref<'method' | 'password' | 'email-code' | 'passkey' | 'totp'>('method')
const initializing = ref(false)
const methodSelecting = ref(false)
const availableMethods = ref<Array<'password' | 'email-code' | 'passkey' | 'totp'>>([
    'password',
    'email-code',
    'passkey',
    'totp',
])
const passkeySupported = ref(false)

const passwordFormRef = ref<FormInstance>()
const codeFormRef = ref<FormInstance>()
const totpFormRef = ref<FormInstance>()

const passwordInput = ref({ password: '' })
const codeInput = ref({ code: '' })
const totpInput = ref({ code: '' })
const totpMode = ref<'totp' | 'recovery'>('totp')

const passwordRules = {
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const codeRules = {
    code: [
        { required: true, message: '请输入验证码', trigger: 'blur' },
        { len: 6, message: '验证码应为6位数字', trigger: 'blur' },
    ],
}

const totpRules = computed(() => ({
    code: [
        { required: true, message: '请输入验证码或恢复码', trigger: 'blur' },
        {
            validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
                const v = (value || '').trim()
                if (totpMode.value === 'totp' && !/^[0-9]{6}$/.test(v)) {
                    callback(new Error('请输入 6 位数字动态码'))
                    return
                }

                if (totpMode.value === 'recovery' && !/^[A-Z]{8}$/.test(v)) {
                    callback(new Error('请输入 8 位大写字母恢复码'))
                    return
                }

                callback()
            },
            trigger: 'blur',
        },
    ],
}))

const passwordLoading = ref(false)
const codeLoading = ref(false)
const passkeyLoading = ref(false)
const totpLoading = ref(false)

const codeCountdown = ref(0)
const canResendCode = ref(false)
let codeCountdownTimer: number | null = null

const isMethodSelectable = (method: 'password' | 'email-code' | 'passkey' | 'totp') => {
    if (!availableMethods.value.includes(method)) return false
    if (method === 'passkey' && !passkeySupported.value) return false
    return true
}

const setVisible = (value: boolean) => {
    emit('update:modelValue', value)
}

const resetState = () => {
    step.value = 'method'
    initializing.value = false
    passwordInput.value.password = ''
    codeInput.value.code = ''
    totpInput.value.code = ''
    totpMode.value = 'totp'
    cleanupCodeCountdown()
}

const handleClose = () => {
    emit('cancel')
    setVisible(false)
    resetState()
}

const initDialog = async () => {
    initializing.value = true
    passkeySupported.value = isWebAuthnSupported()

    try {
        const status = await checkSensitiveVerification()
        if (status.verified && status.remainingSeconds > 0) {
            emit('success')
            setVisible(false)
            return
        }

        const methods = status.methods ?? allMethods
        const sanitizedMethods = methods.filter(
            (item): item is 'password' | 'email-code' | 'passkey' | 'totp' => allMethods.includes(item),
        )

        // 如果后端仅返回单一偏好方式，仍保留前端多方式选择，避免用户被锁死在一种方式。
        availableMethods.value = sanitizedMethods.length > 1 ? sanitizedMethods : allMethods

        // 默认进入偏好方式，同时保留返回入口让用户切换其他方式。
        const preferredMethod = status.preferredMethod
        const defaultMethod = [preferredMethod, ...availableMethods.value].find(
            (item) => !!item && isMethodSelectable(item as 'password' | 'email-code' | 'passkey' | 'totp'),
        ) as 'password' | 'email-code' | 'passkey' | 'totp' | undefined

        if (defaultMethod) {
            await selectMethod(defaultMethod)
            return
        }

        step.value = 'method'
    } catch (error) {
        console.error('Load sensitive verification preference failed:', error)
        ElMessage.error('加载验证方式失败，请稍后重试')
    } finally {
        initializing.value = false
    }
}

watch(
    () => props.modelValue,
    async (visible) => {
        if (visible) {
            resetState()
            await initDialog()
            return
        }

        resetState()
    },
)

const selectMethod = async (method: 'password' | 'email-code' | 'passkey' | 'totp') => {
    if (methodSelecting.value) return
    if (!isMethodSelectable(method)) {
        ElMessage.error('当前不可使用该验证方式')
        return
    }

    methodSelecting.value = true
    try {
        if (method === 'email-code') {
            await sendCode()
        } else {
            step.value = method
        }
    } finally {
        methodSelecting.value = false
    }
}

const backToMethod = () => {
    step.value = 'method'
    codeInput.value.code = ''
    totpInput.value.code = ''
    totpMode.value = 'totp'
    cleanupCodeCountdown()
}

const verifySuccess = () => {
    ElMessage.success('验证成功')
    emit('success')
    setVisible(false)
}

const handlePasswordVerify = async () => {
    try {
        await passwordFormRef.value?.validate()
        passwordLoading.value = true
        await verifySensitiveOperation({
            method: 'password',
            password: passwordInput.value.password,
        })
        verifySuccess()
    } catch (error) {
        console.error('Password verify failed:', error)
    } finally {
        passwordLoading.value = false
    }
}

const sendCode = async () => {
    await sendSensitiveVerificationCode()
    step.value = 'email-code'
    codeInput.value.code = ''
    startCodeCountdown()
    ElMessage.success('验证码已发送')
}

const resendCode = async () => {
    try {
        codeLoading.value = true
        await sendSensitiveVerificationCode()
        codeInput.value.code = ''
        startCodeCountdown()
        ElMessage.success('验证码已重新发送')
    } catch (error) {
        console.error('Resend code failed:', error)
    } finally {
        codeLoading.value = false
    }
}

const handleCodeVerify = async () => {
    try {
        await codeFormRef.value?.validate()
        codeLoading.value = true
        await verifySensitiveOperation({ method: 'email-code', code: codeInput.value.code })
        verifySuccess()
    } catch (error) {
        console.error('Code verify failed:', error)
    } finally {
        codeLoading.value = false
    }
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

const handleTotpVerify = async () => {
    try {
        await totpFormRef.value?.validate()
        totpLoading.value = true

        const normalizedInput = totpInput.value.code.trim()
        await verifySensitiveOperation(
            totpMode.value === 'recovery'
                ? { method: 'totp', recoveryCode: normalizedInput.toUpperCase() }
                : { method: 'totp', code: normalizedInput },
        )

        verifySuccess()
    } catch (error) {
        console.error('TOTP verify failed:', error)
    } finally {
        totpLoading.value = false
    }
}

const handlePasskeyVerify = async () => {
    if (passkeyLoading.value) return
    if (!passkeySupported.value) {
        ElMessage.error('当前浏览器不支持 Passkey')
        return
    }

    try {
        passkeyLoading.value = true
        const options = await getPasskeySensitiveVerificationOptions()
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

        const authData = extractAuthenticationData(credential)
        await verifyPasskeySensitiveOperation(options.challengeId, authData)
        verifySuccess()
    } catch (error) {
        if (error instanceof Error && error.name === 'NotAllowedError') {
            ElMessage.error('用户取消了认证')
            return
        }
        console.error('Passkey verify failed:', error)
    } finally {
        passkeyLoading.value = false
    }
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
    if (codeCountdownTimer !== null) {
        clearInterval(codeCountdownTimer)
        codeCountdownTimer = null
    }
}

onBeforeUnmount(() => {
    cleanupCodeCountdown()
})
</script>

<style scoped>
.loading-panel {
    min-height: 180px;
    display: flex;
    align-items: center;
}

.panel {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.subtitle {
    margin: 0;
    color: var(--el-text-color-secondary);
}

.method-list {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
}

.method-item {
    text-align: left;
    border: 1px solid var(--el-border-color);
    border-radius: 10px;
    background: var(--el-fill-color-blank);
    padding: 12px;
    cursor: pointer;
}

.method-item:disabled {
    opacity: 0.55;
    cursor: not-allowed;
}

.method-title {
    font-weight: 600;
    color: var(--el-text-color-primary);
}

.method-desc {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
}

.actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 4px;
}

.code-actions {
    display: flex;
    justify-content: flex-start;
}

.totp-mode-switch {
    display: flex;
    gap: 8px;
}

.chip {
    border: 1px solid var(--el-border-color);
    border-radius: 999px;
    background: transparent;
    padding: 6px 12px;
    cursor: pointer;
}

.chip.active {
    border-color: var(--el-color-primary);
    color: var(--el-color-primary);
}

@media (max-width: 640px) {
    .method-list {
        grid-template-columns: 1fr;
    }
}
</style>
