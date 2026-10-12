<template>
  <main class="console-verification">
    <el-card class="verification-card">
      <h1>{{ t('ui.console.verifyTitle') }}</h1>
      <p>{{ t('ui.console.verifyIntro') }}</p>
      <el-skeleton v-if="initializing" :rows="3" animated />
      <el-alert v-else-if="invalid" :title="t('ui.console.verifyInvalid')" type="error" :closable="false" />
      <el-form v-else @submit.prevent="verify">
        <el-form-item :label="t('ui.sensitiveVerification.message002')">
          <el-radio-group v-model="method" :disabled="loading">
            <el-radio-button v-if="methods.includes('password')" value="password">{{ t('ui.sensitiveVerification.message005') }}</el-radio-button>
            <el-radio-button v-if="methods.includes('passkey')" value="passkey">Passkey</el-radio-button>
            <el-radio-button v-if="methods.includes('totp')" value="totp">TOTP</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="method === 'password'" :label="t('ui.sensitiveVerificationDialog.message006')">
          <el-input v-model="password" type="password" autocomplete="current-password" show-password :disabled="loading" />
        </el-form-item>
        <template v-if="method === 'totp'">
          <el-checkbox v-model="recovery" :disabled="loading">{{ t('ui.console.verifyRecovery') }}</el-checkbox>
          <el-form-item :label="recovery ? t('ui.console.verifyRecovery') : 'TOTP'">
            <el-input v-model="code" :maxlength="recovery ? 8 : 6" autocomplete="one-time-code" :disabled="loading" />
          </el-form-item>
        </template>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-button type="primary" native-type="submit" :loading="loading" :disabled="!method">{{ t('ui.console.verifyContinue') }}</el-button>
      </el-form>
      <el-button class="return-button" @click="cancel">{{ t('ui.console.verifyCancel') }}</el-button>
    </el-card>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { t } from '@/i18n'
import { getStoredAccessToken } from '@/utils/authSession'
import { checkSensitiveVerification, getPasskeySensitiveVerificationOptions, verifyPasskeySensitiveOperation, verifySensitiveOperation } from '@/api/auth'
import { getConsoleVerificationContext, completeConsoleVerification } from '@/api/consoleVerification'
import { extractAuthenticationData, getPasskeyCredential, isWebAuthnSupported } from '@/utils/webauthn'

const route = useRoute()
const router = useRouter()
const initializing = ref(true)
const invalid = ref(false)
const loading = ref(false)
const error = ref('')
const method = ref<'password' | 'passkey' | 'totp' | ''>('')
const methods = ref<string[]>([])
const password = ref('')
const code = ref('')
const recovery = ref(false)
const requestId = typeof route.query.request_id === 'string' ? route.query.request_id : ''
const consoleOrigin = import.meta.env.DEV ? 'http://localhost:5174' : 'https://console.ksuser.cn'

onMounted(async () => {
  if (!getStoredAccessToken()) {
    await router.replace({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  try {
    if (!requestId) throw new Error('Missing request')
    const context = await getConsoleVerificationContext(requestId)
    const status = await checkSensitiveVerification()
    // A general verified flag deliberately does not satisfy a Console challenge.
    methods.value = (status.methods ?? []).filter((item) =>
      (item === 'password' && context.hasPassword) || item === 'totp' || (item === 'passkey' && isWebAuthnSupported()),
    )
    const preferred = status.preferredMethod
    method.value = (preferred && methods.value.includes(preferred) ? preferred : methods.value[0] ?? '') as typeof method.value
    if (!method.value) invalid.value = true
  } catch {
    invalid.value = true
  } finally {
    initializing.value = false
  }
})

async function verify() {
  if (loading.value || !method.value) return
  loading.value = true
  error.value = ''
  try {
    if (method.value === 'password') {
      await verifySensitiveOperation({ method: 'password', password: password.value, consoleVerificationId: requestId })
    } else if (method.value === 'totp') {
      await verifySensitiveOperation(recovery.value
        ? { method: 'totp', recoveryCode: code.value.toUpperCase(), consoleVerificationId: requestId }
        : { method: 'totp', code: code.value, consoleVerificationId: requestId })
    } else {
      const options = await getPasskeySensitiveVerificationOptions()
      const credential = await getPasskeyCredential(options)
      if (!credential) throw new Error(t('ui.sensitiveVerificationDialog.message052'))
      await verifyPasskeySensitiveOperation(options.challengeId, extractAuthenticationData(credential), requestId)
    }
    password.value = ''
    code.value = ''
    const result = await completeConsoleVerification(requestId)
    const url = new URL(result.redirectUrl)
    if (url.origin !== consoleOrigin || url.pathname !== '/auth/verification-callback') throw new Error(t('ui.console.verifyInvalid'))
    window.location.assign(url.toString())
  } catch (cause) {
    if (cause instanceof Error && ['NotAllowedError', 'AbortError'].includes(cause.name)) {
      error.value = t('ui.sensitiveVerificationDialog.message053')
    } else {
      error.value = cause instanceof Error ? cause.message : t('ui.console.verifyInvalid')
    }
  } finally {
    loading.value = false
  }
}

function cancel() { window.location.assign(consoleOrigin) }
</script>

<style scoped>
.console-verification { min-height: 100dvh; display: grid; place-items: center; padding: 24px; background: var(--el-fill-color-light); }
.verification-card { width: min(100%, 560px); }
h1 { margin: 0 0 16px; font-size: 24px; }
p { line-height: 1.7; color: var(--el-text-color-secondary); margin-bottom: 24px; }
.el-alert { margin: 16px 0; }
.return-button { margin: 20px 0 0; }
</style>
