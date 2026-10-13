<template>
  <main class="verification-page">
    <section class="verification-card">
      <img class="brand-icon" src="https://static.ksuser.cn/favicon.png" alt="Ksuser 标志" />
      <span class="eyebrow">KSUSER CONSOLE</span>
      <h1>验证管理员身份</h1>
      <p class="intro">请使用此 Ksuser 账号已设置的验证方式。验证完成后，请返回原页面再次确认操作。</p>

      <div v-if="initializing" v-loading="true" class="loading-panel" />
      <el-alert v-else-if="invalid" :title="error || '验证请求已失效，请返回管理面板重新发起。'" type="error" :closable="false" show-icon />
      <template v-else>
        <el-radio-group v-model="method" class="method-picker" :disabled="loading" @change="error = ''">
          <el-radio-button v-if="context?.hasPassword" value="password">密码</el-radio-button>
          <el-radio-button v-if="context?.hasPasskey && passkeySupported" value="passkey">Passkey</el-radio-button>
          <el-radio-button v-if="context?.hasTotp" value="totp">TOTP</el-radio-button>
        </el-radio-group>

        <form class="verification-form" @submit.prevent="verify">
          <el-form-item v-if="method === 'password'" label="账号密码">
            <el-input
              v-model="password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入 Ksuser 账号密码"
              :disabled="loading"
              @input="error = ''"
              @keyup.enter="verify"
            />
          </el-form-item>

          <template v-else-if="method === 'totp'">
            <el-checkbox v-model="recovery" :disabled="loading" @change="error = ''">使用恢复码</el-checkbox>
            <el-form-item :label="recovery ? '恢复码' : '身份验证器代码'">
              <el-input
                v-model="code"
                :maxlength="recovery ? 8 : 6"
                autocomplete="one-time-code"
                :placeholder="recovery ? '输入 8 位恢复码' : '输入 6 位动态验证码'"
                :disabled="loading"
                @input="error = ''"
                @keyup.enter="verify"
              />
            </el-form-item>
          </template>

          <p v-else-if="method === 'passkey'" class="method-hint">
            使用此账号的 Passkey 完成验证。浏览器会显示 Ksuser 的安全验证提示。
          </p>

          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
          <el-button class="submit-button" type="primary" native-type="submit" :loading="loading" :disabled="!method">
            {{ method === 'passkey' ? '使用 Passkey 验证' : '验证并返回管理面板' }}
          </el-button>
        </form>
      </template>

      <el-button class="cancel-button" text :disabled="initializing" @click="cancel">取消并返回</el-button>
    </section>
    <footer>Ksuser · console.ksuser.cn</footer>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { consoleApi, type ConsolePasskeyCredential, type ConsoleVerificationContext } from '@/api/console'
import { consumeVerificationReturn } from '@/utils/navigation'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const store = useSessionStore()
const requestId = typeof route.query.request_id === 'string' ? route.query.request_id : ''
const initializing = ref(true)
const invalid = ref(false)
const loading = ref(false)
const error = ref('')
const context = ref<ConsoleVerificationContext | null>(null)
const method = ref<'password' | 'passkey' | 'totp' | ''>('')
const password = ref('')
const code = ref('')
const recovery = ref(false)
const passkeySupported = typeof window !== 'undefined' && typeof window.PublicKeyCredential === 'function'
const abortController = ref<AbortController | null>(null)

const hasAvailableMethod = computed(() => Boolean(
  context.value && (context.value.hasPassword || context.value.hasTotp || (context.value.hasPasskey && passkeySupported)),
))

onMounted(async () => {
  try {
    if (!requestId) throw new Error('验证请求已失效，请返回管理面板重新发起。')
    context.value = await consoleApi.verificationContext(requestId)
    method.value = context.value.hasPassword
      ? 'password'
      : context.value.hasPasskey && passkeySupported
        ? 'passkey'
        : context.value.hasTotp
          ? 'totp'
          : ''
    if (!hasAvailableMethod.value) throw new Error('当前账号没有可用的验证方式，请先在 Ksuser 通行证中设置密码、Passkey 或 TOTP。')
  } catch (cause) {
    invalid.value = true
    error.value = cause instanceof Error ? cause.message : '无法加载身份验证信息，请返回管理面板重试。'
  } finally {
    initializing.value = false
  }
})

async function verify() {
  if (loading.value || !method.value || !requestId) return
  loading.value = true
  error.value = ''
  try {
    if (method.value === 'password') {
      store.session = await consoleApi.verifyPassword(requestId, password.value)
      password.value = ''
    } else if (method.value === 'totp') {
      store.session = await consoleApi.verifyTotp(requestId, recovery.value
        ? { recoveryCode: code.value.trim().toUpperCase() }
        : { code: code.value.trim() })
      code.value = ''
    } else {
      store.session = await verifyPasskey()
    }
    await router.replace(consumeVerificationReturn())
  } catch (cause) {
    error.value = verificationError(cause)
  } finally {
    loading.value = false
    abortController.value = null
  }
}

async function verifyPasskey() {
  if (!passkeySupported) throw new Error('当前浏览器不支持 Passkey。')
  const options = await consoleApi.passkeyVerificationOptions(requestId)
  const controller = new AbortController()
  abortController.value = controller
  const credential = await navigator.credentials.get({
    publicKey: {
      challenge: decodeBase64Url(options.challenge),
      timeout: Number(options.timeout),
      rpId: options.rpId,
      userVerification: options.userVerification as UserVerificationRequirement,
    },
    signal: controller.signal,
  })
  if (!credential || !(credential instanceof PublicKeyCredential)) throw new Error('未获取到 Passkey 凭证。')
  const assertion = credential.response as AuthenticatorAssertionResponse
  const data: ConsolePasskeyCredential = {
    credentialRawId: encodeBase64Url(credential.rawId),
    clientDataJSON: encodeBase64Url(assertion.clientDataJSON),
    authenticatorData: encodeBase64Url(assertion.authenticatorData),
    signature: encodeBase64Url(assertion.signature),
  }
  return consoleApi.verifyPasskey(requestId, options.challengeId, data)
}

function verificationError(cause: unknown) {
  if (cause instanceof Error && ['NotAllowedError', 'AbortError'].includes(cause.name))
    return 'Passkey 验证已取消，可以重新尝试或切换其他验证方式。'
  if (cause instanceof Error && cause.name === 'SecurityError')
    return '此 Passkey 暂时无法用于管理面板。请切换密码或 TOTP；如持续出现，请检查浏览器兼容性和 auth.ksuser.cn 的关联域名配置。'
  return cause instanceof Error ? cause.message : '身份验证失败，请重试。'
}

function cancel() {
  abortController.value?.abort()
  void router.replace(consumeVerificationReturn())
}

onBeforeUnmount(() => abortController.value?.abort())

function decodeBase64Url(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const bytes = Uint8Array.from(atob(padded), (character) => character.charCodeAt(0))
  return bytes.buffer
}

function encodeBase64Url(value: ArrayBuffer): string {
  const bytes = new Uint8Array(value)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}
</script>

<style scoped>
.verification-page {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 75% 20%, var(--console-accent-glow), transparent 45%),
    var(--console-page-bg);
}
.verification-card {
  width: min(100%, 480px);
  padding: 40px;
  border: 1px solid var(--console-border);
  border-radius: 20px;
  background: var(--console-surface);
  box-shadow: 0 12px 45px rgb(24 35 50 / 4%);
}
.brand-icon {
  display: block;
  width: 48px;
  height: 48px;
  margin-bottom: 18px;
}
.eyebrow {
  color: var(--console-link);
  font-size: 11px;
  letter-spacing: 2px;
}
h1 {
  margin: 14px 0 0;
  font-size: 26px;
}
.intro,
.method-hint {
  color: var(--console-text-secondary);
  font-size: 14px;
  line-height: 1.8;
}
.intro {
  margin: 12px 0 24px;
}
.loading-panel {
  height: 120px;
}
.method-picker {
  display: flex;
  width: 100%;
  margin-bottom: 24px;
}
.method-picker :deep(.el-radio-button) {
  flex: 1;
}
.method-picker :deep(.el-radio-button__inner) {
  width: 100%;
}
.verification-form {
  display: grid;
  gap: 16px;
}
.method-hint {
  margin: 0;
}
.submit-button {
  width: 100%;
}
.cancel-button {
  width: 100%;
  margin-top: 18px;
}
footer {
  position: fixed;
  bottom: 20px;
  color: var(--console-text-muted);
  font-size: 12px;
}
@media (max-width: 520px) {
  .verification-card {
    padding: 28px 22px;
  }
}
</style>
