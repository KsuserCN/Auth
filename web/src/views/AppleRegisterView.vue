<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <aside class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Ksuser" class="logo-icon" />
        </div>
        <h1 class="login-title">{{ t('ui.appleRegister.message001') }}</h1>
        <p class="login-description">{{ t('ui.appleRegister.message002') }}</p>
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20"><Lock /></el-icon>
            <span>{{ t('ui.appleRegister.message003') }}</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20"><Lightning /></el-icon>
            <span>{{ t('ui.appleRegister.message004') }}</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20"><Key /></el-icon>
            <span>{{ t('ui.addPasskey.message005') }}</span>
          </div>
        </div>
      </aside>

      <main class="login-right">
        <div class="step-container">
          <div class="provider-label">
            <i class="fa-brands fa-apple" aria-hidden="true"></i>
            <span>{{ t('ui.appleRegister.message005') }}</span>
          </div>
          <template v-if="mode === 'choice'">
            <h2 class="step-title">{{ t('ui.appleRegister.message006') }}</h2>
            <p class="step-subtitle">{{ t('ui.appleRegister.message007') }}</p>
            <el-alert
              v-if="!pending"
              :title="t('ui.appleRegister.message008')"
              type="warning"
              :closable="false"
            />
            <div v-else class="account-choices">
              <el-button type="primary" @click="bindExisting">{{
                t('ui.appleRegister.message009')
              }}</el-button>
              <p class="field-hint">{{ t('ui.appleRegister.message010') }}</p>
              <el-button :disabled="!pending.canRegister" @click="mode = 'register'">{{
                t('ui.appleRegister.message011')
              }}</el-button>
              <p v-if="pending.emailConflict" class="field-hint">
                {{ t('ui.appleRegister.message012') }}
              </p>
              <p v-else-if="!pending.canRegister" class="field-hint">
                {{ t('ui.appleRegister.message013') }}
              </p>
              <p v-else class="field-hint">{{ t('ui.appleRegister.message014') }}</p>
            </div>
            <el-button class="back-btn" @click="cancel">{{
              t('ui.appleRegister.message015')
            }}</el-button>
          </template>

          <template v-else-if="mode === 'bind'">
            <h2 class="step-title">{{ t('ui.appleRegister.message016') }}</h2>
            <p class="step-subtitle">
              {{ t('ui.appleRegister.message017') }}
            </p>
            <el-alert
              v-if="errorMessage"
              :title="renderMessage(errorMessage)"
              type="error"
              :closable="false"
            />
            <div class="step-actions">
              <el-button class="back-btn" @click="cancel">{{
                t('ui.addPasskey.message009')
              }}</el-button>
              <el-button class="next-btn" :loading="loading" @click="beginBinding">{{
                t('ui.appleRegister.message018')
              }}</el-button>
            </div>
          </template>

          <template v-else>
            <h2 class="step-title">{{ t('ui.appleRegister.message019') }}</h2>
            <p class="step-subtitle">{{ t('ui.appleRegister.message020') }}</p>
            <el-alert
              v-if="!ticket"
              :title="t('ui.appleRegister.message008')"
              type="warning"
              :closable="false"
              class="expired-alert"
            />

            <el-form label-position="top" @submit.prevent="submit">
              <el-form-item :label="t('ui.appleRegister.message021')">
                <el-input
                  v-model="username"
                  maxlength="20"
                  :placeholder="t('ui.appleRegister.message022')"
                  autocomplete="username"
                  :disabled="!ticket"
                />
              </el-form-item>
              <p class="field-hint">{{ t('ui.appleRegister.message023') }}</p>

              <el-checkbox v-model="acceptTerms" class="terms-checkbox" :disabled="!ticket">
                {{ t('ui.appleRegister.message024') }}
              </el-checkbox>

              <div class="step-actions">
                <el-button class="back-btn" :disabled="loading" @click="mode = 'choice'">{{
                  t('ui.appleRegister.message025')
                }}</el-button>
                <el-button
                  class="next-btn"
                  native-type="submit"
                  :loading="loading"
                  :disabled="!ticket || !acceptTerms"
                >
                  {{ t('ui.appleRegister.message026') }}
                </el-button>
              </div>
            </el-form>
          </template>
        </div>
      </main>
    </div>
    <SensitiveVerificationDialog v-model="verificationVisible" @success="completeBinding" />
  </div>
</template>

<script setup lang="ts">
import { localizedError } from '@/utils/apiResult'
import { t, renderMessage, type LocalizedMessage, LocalizedError } from '@/i18n'

import { computed, onMounted, ref } from 'vue'
import { useDark } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lightning, Lock } from '@element-plus/icons-vue'
import {
  bindPendingApple,
  checkSensitiveVerification,
  checkUsername,
  registerPendingApple,
} from '@/api/auth'
import SensitiveVerificationDialog from '@/components/SensitiveVerificationDialog.vue'
import { prepareAppleSignIn, signInWithApple } from '@/utils/appleSignIn'
import {
  clearPendingAppleAccount,
  readPendingAppleAccount,
  requestAppleAccountBinding,
} from '@/utils/appleAccountFlow'
import { getStoredAccessToken } from '@/utils/authSession'
import { finalizeWebLogin } from '@/utils/desktopBridge'
import { consumePostLoginRedirect } from '@/utils/postLoginRedirect'

const router = useRouter()
const route = useRoute()
const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})
const pending = ref(readPendingAppleAccount())
const ticket = computed(() => (pending.value?.canRegister ? pending.value.token : ''))
const mode = ref<'choice' | 'register' | 'bind'>(route.query.mode === 'bind' ? 'bind' : 'choice')
const verificationVisible = ref(false)
const errorMessage = ref<LocalizedMessage>('')
const username = ref('')
const acceptTerms = ref(false)
const loading = ref(false)

onMounted(() => {
  void prepareAppleSignIn().catch(() => {})
  if (mode.value === 'bind' && !getStoredAccessToken()) {
    requestAppleAccountBinding()
    void router.replace({ path: '/login', query: { oauthBindProvider: 'apple' } })
  }
})

const bindExisting = async () => {
  if (!pending.value) return
  requestAppleAccountBinding()
  await router.push({ path: '/login', query: { oauthBindProvider: 'apple' } })
}

const cancel = async () => {
  clearPendingAppleAccount()
  pending.value = null
  await router.replace(
    mode.value === 'bind' ? consumePostLoginRedirect() || '/home/overview' : '/login',
  )
}

const beginBinding = async () => {
  if (loading.value || verificationVisible.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const status = await checkSensitiveVerification()
    if (status.verified) {
      // Authorize from the button click after verification; the provider opens a popup.
      loading.value = false
      await completeBinding()
    } else {
      verificationVisible.value = true
    }
  } catch (error: unknown) {
    errorMessage.value = localizedError(error, 'ui.appleRegister.message027')
  } finally {
    loading.value = false
  }
}

const completeBinding = async () => {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    // A login-purpose Apple ticket cannot be used for binding. Obtain a fresh,
    // authenticated bind-purpose credential, as in the iOS flow.
    const response = await signInWithApple('bind')
    if (!response.needBind || !response.oauthBindToken)
      throw new LocalizedError('ui.appleRegister.message028')
    await bindPendingApple(response.oauthBindToken)
    clearPendingAppleAccount()
    pending.value = null
    ElMessage.success(t('ui.appleRegister.message029'))
    await router.replace(consumePostLoginRedirect() || '/home/overview')
  } catch (error: unknown) {
    errorMessage.value = localizedError(error, 'ui.appleRegister.message030')
    ElMessage.error(renderMessage(errorMessage.value))
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!ticket.value || !acceptTerms.value || loading.value) return
  loading.value = true
  let ticketSubmitted = false
  try {
    const requestedUsername = username.value.trim()
    if (
      requestedUsername &&
      (!/^[a-zA-Z0-9_\-\u4e00-\u9fa5]{3,20}$/.test(requestedUsername) ||
        !(await checkUsername(requestedUsername)))
    ) {
      throw new LocalizedError('ui.appleRegister.message031')
    }
    ticketSubmitted = true
    const result = await registerPendingApple(ticket.value, acceptTerms.value, requestedUsername)
    clearPendingAppleAccount()
    pending.value = null
    if ('challengeId' in result) {
      await router.replace({
        path: '/login',
        query: {
          challengeId: result.challengeId,
          method: result.method,
          methods: result.methods?.join(','),
          mfaFrom: 'apple',
        },
      })
      return
    }
    await finalizeWebLogin({ accessToken: result.accessToken, user: result.user })
    ElMessage.success(t('ui.appleRegister.message032'))
    await router.replace(consumePostLoginRedirect() || '/home/overview')
  } catch (error: unknown) {
    if (ticketSubmitted) {
      // The Apple ticket is single use. A failed attempt requires fresh authorization.
      clearPendingAppleAccount()
      pending.value = null
      ElMessage.error(t('ui.appleRegister.message033'))
    } else {
      ElMessage.error(error instanceof Error ? error.message : t('ui.appleRegister.message034'))
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  width: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.login-container.dark {
  background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
}

.login-box {
  width: 100%;
  max-width: 1000px;
  min-height: 560px;
  display: flex;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 20px;
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.12),
    0 16px 40px rgba(0, 0, 0, 0.08);
  animation: slide-in 0.6s ease-out;
}

.login-left,
.login-right {
  flex: 1 1 50%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-left {
  padding: 60px 48px;
  background: linear-gradient(135deg, #fff8f0, #fffbf5);
  border-right: 1px solid var(--el-border-color-light);
}

.dark .login-left {
  background: var(--el-bg-color-overlay);
}

.login-right {
  padding: 48px;
  background: var(--el-bg-color);
}

.logo-section {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 32px;
}

.logo-icon {
  width: 56px;
  height: 56px;
  display: block;
  border-radius: 14px;
}

.login-title {
  margin: 0 0 12px;
  color: var(--el-text-color-primary);
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.login-description {
  margin: 0 0 32px;
  color: var(--el-text-color-regular);
  font-size: 15px;
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
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.feature-icon {
  color: var(--el-color-primary);
  min-width: 24px;
}

.step-container {
  width: 100%;
}

.account-choices {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.account-choices :deep(.el-button) {
  margin-left: 0;
  min-height: 44px;
}

.account-choices .field-hint {
  margin-bottom: 8px;
}

.provider-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.provider-label i {
  color: var(--el-text-color-primary);
  font-size: 17px;
}

.step-title {
  margin: 0 0 8px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.3px;
}

.step-subtitle {
  margin: 0 0 24px;
  color: var(--el-text-color-regular);
  font-size: 16px;
  line-height: 1.6;
}

.expired-alert {
  margin-bottom: 20px;
}

:deep(.el-form-item) {
  margin-bottom: 8px;
}

:deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  padding: 14px 16px;
  background: var(--el-fill-color-light);
  border: 1.5px solid var(--el-border-color);
  border-radius: 12px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-input__wrapper:hover),
:deep(.el-input__wrapper.is-focus) {
  background: var(--el-bg-color);
  border-color: var(--el-color-primary);
}

:deep(.el-input__inner) {
  font-size: 16px;
}

.field-hint {
  margin: 0 0 22px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.terms-checkbox {
  max-width: 100%;
  height: auto;
  white-space: normal;
}

:deep(.terms-checkbox .el-checkbox__label) {
  white-space: normal;
  line-height: 1.5;
}

.step-actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}

.next-btn,
.back-btn {
  min-width: 100px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
}

.next-btn {
  flex: 1;
  background: var(--el-color-primary);
  border: none;
  color: white;
}

.next-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(255, 185, 15, 0.3);
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

@keyframes slide-in {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 900px) {
  .login-box {
    flex-direction: column;
  }
  .login-left {
    padding: 40px 32px;
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-light);
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
}

@media (max-width: 600px) {
  .login-container {
    padding: 20px;
    align-items: flex-start;
  }
  .login-box {
    border-radius: 12px;
  }
  .login-left,
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
