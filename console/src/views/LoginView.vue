<template>
  <div class="login-page">
    <main class="login-card">
      <section class="login-brand-panel" aria-label="Ksuser Console 管理中心">
        <div class="brand-lockup">
          <span class="brand-icon-wrap"><img class="login-brand" src="/favicon.ico" alt="" /></span>
          <span class="brand-wordmark">Ksuser<span>CONSOLE</span></span>
        </div>

        <div class="brand-copy">
          <span class="eyebrow"><span class="eyebrow-dot"></span> ADMINISTRATION</span>
          <h1>管理中心</h1>
          <p>管理 Ksuser 服务与账户。<br />使用 Ksuser 通行证安全登录。</p>
        </div>

        <div class="access-note">
          <span class="access-mark" aria-hidden="true">✓</span>
          <span>
            <strong>超级管理员专用</strong>
            <small>通过身份验证后进入管理面板</small>
          </span>
        </div>
      </section>

      <section
        class="login-action-panel"
        :class="{ 'scan-mode': method === 'qr' }"
        aria-labelledby="login-title"
      >
        <div class="login-action">
          <div class="login-status"><span class="status-dot"></span> Ksuser 安全认证</div>
          <h2 id="login-title">登录管理中心</h2>
          <p class="action-description">
            <template v-if="method === 'qr'"
              >使用
              <a
                class="app-store-link"
                href="https://apps.apple.com/cn/app/ksuser%E5%AE%89%E5%85%A8/id6776157121"
                target="_blank"
                rel="noopener noreferrer"
                >Ksuser安全 App</a
              >
              扫码，在手机上确认登录。</template
            >
            <template v-else>前往 Ksuser 通行证完成身份验证，即可继续。</template>
          </p>

          <div class="login-methods" aria-label="登录方式">
            <button
              type="button"
              :class="{ active: method === 'qr' }"
              :aria-pressed="method === 'qr'"
              :disabled="busy"
              @click="selectMethod('qr')"
            >
              App 扫码登录
            </button>
            <button
              type="button"
              :class="{ active: method === 'passport' }"
              :aria-pressed="method === 'passport'"
              :disabled="busy"
              @click="selectMethod('passport')"
            >
              通行证登录
            </button>
          </div>

          <el-alert
            v-if="error"
            class="login-alert"
            :title="error"
            type="error"
            :closable="false"
            show-icon
          />

          <div v-if="method === 'qr'" class="qr-login">
            <div class="qr-frame" :aria-busy="qrState === 'loading'">
              <img
                v-if="qrImage"
                :src="qrImage"
                class="qr-image"
                alt="使用 Ksuser安全 App 扫描此二维码登录 Console"
                :class="{ inactive: qrState !== 'pending' }"
              />
              <div v-if="qrState !== 'pending'" class="qr-overlay">
                <el-icon
                  v-if="qrState === 'loading' || qrState === 'confirming'"
                  class="qr-spinner"
                  :size="28"
                  ><Loading
                /></el-icon>
                <span>{{ qrStatusText }}</span>
                <el-button
                  v-if="['expired', 'rejected', 'error'].includes(qrState)"
                  type="primary"
                  size="small"
                  @click="startQrLogin"
                  >刷新二维码</el-button
                >
              </div>
            </div>
            <p class="qr-status" role="status" aria-live="polite">
              {{
                qrState === 'pending' ? `等待手机确认 · ${remainingSeconds} 秒后过期` : qrStatusText
              }}
            </p>
            <p class="qr-help">点击 App 屏幕右上角的扫码图标<br />核对登录网站与账号后确认</p>
            <button
              v-if="qrState === 'pending'"
              type="button"
              class="qr-refresh"
              :disabled="busy"
              @click="startQrLogin"
            >
              刷新二维码
            </button>
          </div>

          <el-button
            v-else
            class="login-button"
            type="primary"
            size="large"
            :loading="loading"
            @click="login"
          >
            <span>使用 Ksuser 通行证登录</span>
            <span class="button-arrow" aria-hidden="true">↗</span>
          </el-button>

          <a
            v-if="method === 'passport'"
            class="passport-link"
            :href="passportUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            前往 Ksuser 通行证 <span aria-hidden="true">↗</span>
          </a>

          <div class="security-note">
            {{
              method === 'qr'
                ? '仅超级管理员可登录。请只确认你本人发起的登录请求。'
                : '登录将在 Ksuser 通行证完成，Console 不会索要你的密码。'
            }}
          </div>
        </div>
      </section>
    </main>
    <footer class="login-footer"><span>Ksuser</span><i>·</i> console.ksuser.cn</footer>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { consoleApi, ConsoleApiError, type ConsoleQrChallenge } from '@/api/console'
import { useSessionStore } from '@/stores/session'
import { apiResultMessagesByLocale } from '@/generated/apiResultCodes'
const route = useRoute()
const router = useRouter()
const store = useSessionStore()
const loading = ref(false)
const localError = ref('')
const method = ref<'qr' | 'passport'>('qr')
const switching = ref(false)
const qrState = ref<'loading' | 'pending' | 'confirming' | 'expired' | 'rejected' | 'error'>(
  'loading',
)
const qrImage = ref('')
const remainingSeconds = ref(0)
let generation = 0
let mounted = true
let completed = false
let expiresAt = 0
let pollTimer: number | undefined
let countdownTimer: number | undefined
let activeChallenge: ConsoleQrChallenge | undefined
const busy = computed(
  () =>
    loading.value ||
    switching.value ||
    (method.value === 'qr' && ['loading', 'confirming'].includes(qrState.value)),
)
const qrStatusText = computed(
  () =>
    ({
      loading: '正在生成二维码',
      pending: '等待手机确认',
      confirming: '手机已确认，正在登录',
      expired: '二维码已过期',
      rejected: '本次登录未获批准',
      error: '二维码暂时不可用',
    })[qrState.value],
)
const error = computed(
  () =>
    localError.value ||
    (typeof route.query.error === 'string'
      ? apiResultMessagesByLocale['zh-Hans'][route.query.error] || '登录失败，请重新登录'
      : ''),
)
const passportUrl = import.meta.env.VITE_PASSPORT_BASE_URL || 'https://auth.ksuser.cn'
function stopQr() {
  generation += 1
  window.clearTimeout(pollTimer)
  window.clearInterval(countdownTimer)
  pollTimer = undefined
  countdownTimer = undefined
}
function current(flow: number) {
  return mounted && method.value === 'qr' && generation === flow
}
function failQr(failure: unknown) {
  stopQr()
  qrState.value = 'error'
  localError.value =
    failure instanceof ConsoleApiError ? failure.message : '无法连接服务器，请刷新二维码重试'
}
async function startQrLogin() {
  stopQr()
  const flow = generation
  qrState.value = 'loading'
  qrImage.value = ''
  activeChallenge = undefined
  localError.value = ''
  const startedAt = Date.now()
  try {
    const challenge = await consoleApi.initQrLogin()
    if (!current(flow)) {
      void consoleApi.cancelQrLogin(challenge.challengeId).catch(() => {})
      return
    }
    activeChallenge = challenge
    const image = await QRCode.toDataURL(challenge.qrText, {
      width: 232,
      margin: 2,
      errorCorrectionLevel: 'M',
    })
    if (!current(flow)) return
    qrImage.value = image
    expiresAt = startedAt + challenge.expiresInSeconds * 1000
    remainingSeconds.value = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000))
    qrState.value = 'pending'
    if (remainingSeconds.value === 0) {
      stopQr()
      qrState.value = 'expired'
      return
    }
    countdownTimer = window.setInterval(() => {
      if (!current(flow)) return
      remainingSeconds.value = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000))
      if (remainingSeconds.value === 0) {
        stopQr()
        qrState.value = 'expired'
      }
    }, 1000)
    void pollQr(flow, challenge)
  } catch (failure) {
    if (current(flow)) failQr(failure)
  }
}
async function pollQr(flow: number, challenge: ConsoleQrChallenge) {
  let activeFlow = flow
  try {
    const status = await consoleApi.pollQrLogin(challenge.challengeId, challenge.pollToken)
    if (!current(flow)) return
    if (status.status === 'approved') {
      stopQr()
      const exchangeFlow = generation
      activeFlow = exchangeFlow
      qrState.value = 'confirming'
      const session = await consoleApi.exchangeQrLogin(challenge.challengeId, challenge.pollToken)
      if (!current(exchangeFlow)) return
      store.session = session
      completed = true
      await router.replace('/users')
    } else if (status.status === 'pending' && status.expiresInSeconds > 0) {
      expiresAt = Math.min(expiresAt, Date.now() + status.expiresInSeconds * 1000)
      pollTimer = window.setTimeout(() => void pollQr(flow, challenge), 2000)
    } else {
      stopQr()
      remainingSeconds.value = 0
      qrState.value = status.status === 'rejected' ? 'rejected' : 'expired'
    }
  } catch (failure) {
    if (current(activeFlow)) failQr(failure)
  }
}
async function selectMethod(value: 'qr' | 'passport') {
  if (method.value === value || busy.value) return
  stopQr()
  switching.value = true
  const previous = method.value
  method.value = value
  localError.value = ''
  try {
    if (previous === 'qr') await consoleApi.cancelQrLogin(activeChallenge?.challengeId)
  } catch {
    // Login start also invalidates the previous browser-bound QR request.
  } finally {
    switching.value = false
  }
  if (value === 'qr' && mounted) await startQrLogin()
}
const login = async () => {
  if (loading.value || switching.value) return
  loading.value = true
  localError.value = ''
  try {
    window.location.assign((await consoleApi.login()).authorizationUrl)
  } catch (error) {
    localError.value = error instanceof ConsoleApiError ? error.message : '无法连接服务器，请重试'
    loading.value = false
  }
}
onMounted(() => void startQrLogin())
onBeforeUnmount(() => {
  mounted = false
  stopQr()
  if (!completed && method.value === 'qr' && activeChallenge)
    void consoleApi.cancelQrLogin(activeChallenge.challengeId).catch(() => {})
})
</script>
<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  isolation: isolate;
  padding: 32px 24px 24px;
  background:
    radial-gradient(ellipse at 82% 10%, var(--console-accent-glow), transparent 34%),
    radial-gradient(ellipse at 0% 100%, var(--console-accent-soft), transparent 40%),
    var(--console-page-bg);
}
.login-card {
  width: min(100%, 960px);
  min-height: 540px;
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  overflow: hidden;
  background: var(--console-surface);
  border: 1px solid var(--console-border);
  border-radius: 24px;
  box-shadow: 0 28px 80px rgb(24 35 50 / 10%);
  animation: card-enter 520ms cubic-bezier(0.2, 0.7, 0.2, 1) both;
}
.login-brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 44px clamp(30px, 5vw, 56px);
  border-right: 1px solid var(--console-border);
  background: linear-gradient(150deg, var(--console-accent-soft), var(--console-surface) 76%);
}
.brand-lockup {
  display: flex;
  align-items: center;
  gap: 13px;
}
.brand-icon-wrap {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 1px solid var(--console-accent-border);
  border-radius: 15px;
  background: var(--console-surface);
  box-shadow: 0 6px 18px rgb(128 92 0 / 8%);
}
.login-brand {
  width: 34px;
  height: 34px;
  object-fit: contain;
}
.brand-wordmark {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--console-text);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.3px;
}
.brand-wordmark span {
  color: var(--console-link);
  font-size: 10px;
  font-weight: 650;
  letter-spacing: 2px;
}
.brand-copy {
  margin: auto 0;
  padding: 54px 0 62px;
}
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--console-link);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}
.eyebrow-dot,
.status-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--brand-yellow);
  box-shadow: 0 0 0 4px var(--console-accent-glow);
}
h1 {
  margin: 20px 0 14px;
  color: var(--console-text);
  font-size: clamp(34px, 4vw, 42px);
  font-weight: 720;
  letter-spacing: -1.3px;
}
.brand-copy p {
  margin: 0;
  color: var(--console-text-secondary);
  font-size: 15px;
  line-height: 1.9;
}
.access-note {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 15px 16px;
  border: 1px solid var(--console-accent-border);
  border-radius: 13px;
  background: var(--console-surface);
}
.access-mark {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 10px;
  background: var(--console-accent-soft);
  color: var(--console-link);
  font-size: 15px;
  font-weight: 700;
}
.access-note > span:last-child {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.access-note strong {
  color: var(--console-text);
  font-size: 12px;
  font-weight: 650;
}
.access-note small {
  color: var(--console-text-muted);
  font-size: 11px;
}
.login-action-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 52px clamp(34px, 6vw, 68px);
  background: var(--console-surface);
}
.login-action {
  width: 100%;
  max-width: 380px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.login-action-panel.scan-mode {
  padding-top: 32px;
  padding-bottom: 32px;
}
.scan-mode h2 {
  margin-top: 20px;
}
.scan-mode .action-description {
  margin-bottom: 18px;
}
.scan-mode .login-methods {
  margin-bottom: 16px;
}
.scan-mode .security-note {
  margin-top: 20px;
  padding-top: 14px;
}
.login-status {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--console-text-muted);
  font-size: 12px;
  font-weight: 550;
}
.login-status .status-dot {
  width: 6px;
  height: 6px;
  box-shadow: none;
}
h2 {
  margin: 26px 0 9px;
  color: var(--console-text);
  font-size: 27px;
  font-weight: 680;
  letter-spacing: -0.7px;
}
.action-description {
  margin: 0 0 28px;
  color: var(--console-text-secondary);
  font-size: 14px;
  line-height: 1.8;
}
.app-store-link {
  color: var(--console-link);
  font-weight: 600;
  transition: color 160ms ease;
}
.app-store-link:visited {
  color: var(--console-link);
}
.app-store-link:hover {
  color: var(--console-link-hover);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.app-store-link:focus-visible {
  outline: 2px solid var(--console-link);
  outline-offset: 3px;
  border-radius: 3px;
}
.login-alert {
  width: 100%;
  margin: -5px 0 18px;
  border-radius: 10px;
}
.login-methods {
  width: 100%;
  display: flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: 22px;
  background: var(--console-accent-soft);
  border-radius: 11px;
}
.login-methods button {
  flex: 1;
  min-height: 38px;
  padding: 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--console-text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.login-methods button.active {
  border-color: var(--console-border);
  background: var(--console-surface);
  color: var(--console-link);
  font-weight: 650;
  box-shadow: 0 2px 6px rgb(24 35 50 / 5%);
}
.login-methods button:disabled {
  cursor: wait;
}
.login-methods button:focus-visible,
.qr-refresh:focus-visible {
  outline: 2px solid var(--console-link);
  outline-offset: 3px;
}
.qr-login {
  width: 100%;
  text-align: center;
}
.qr-frame {
  width: 220px;
  height: 220px;
  max-width: 100%;
  margin: 0 auto;
  position: relative;
  overflow: hidden;
  display: grid;
  place-items: center;
  border: 1px solid var(--console-border);
  border-radius: 16px;
  background: #fff;
}
.qr-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.qr-image.inactive {
  opacity: 0.08;
  filter: blur(3px);
}
.qr-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #475569;
  font-size: 13px;
}
.qr-spinner {
  color: #b78300;
  animation: qr-spin 1s linear infinite;
}
.qr-status {
  margin: 13px 0 5px;
  color: var(--console-text-secondary);
  font-size: 12px;
}
.qr-help {
  margin: 0;
  color: var(--console-text-muted);
  font-size: 12px;
  line-height: 1.6;
}
.qr-refresh {
  margin-top: 5px;
  padding: 4px 8px;
  border: 0;
  background: none;
  color: var(--console-link);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
@keyframes qr-spin {
  to {
    transform: rotate(360deg);
  }
}
.login-action > .login-button {
  width: 100%;
  height: 54px;
  margin: 0;
  border-radius: 11px;
  font-size: 15px;
  font-weight: 650;
  transition:
    transform 160ms ease,
    box-shadow 160ms ease,
    background-color 160ms ease;
}
.login-action > .login-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgb(204 148 12 / 24%);
}
.button-arrow {
  margin-left: 14px;
  font-size: 20px;
  line-height: 1;
}
.passport-link {
  width: 100%;
  margin-top: 21px;
  color: var(--console-text-secondary);
  font-size: 13px;
  font-weight: 550;
  text-align: center;
  transition: color 160ms ease;
}
.passport-link:hover {
  color: var(--console-link);
}
.passport-link span {
  margin-left: 4px;
  color: var(--console-text-muted);
}
.security-note {
  width: 100%;
  margin-top: 36px;
  padding-top: 17px;
  border-top: 1px solid var(--console-border);
  color: var(--console-text-muted);
  font-size: 11px;
  line-height: 1.7;
  text-align: center;
}
.login-footer {
  margin-top: 27px;
  color: var(--console-text-muted);
  font-size: 12px;
  letter-spacing: 0.15px;
}
.login-footer span {
  color: var(--console-text-secondary);
  font-weight: 600;
}
.login-footer i {
  margin: 0 5px;
  color: var(--console-text-subtle);
  font-style: normal;
}
@keyframes card-enter {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (max-width: 760px) {
  .login-page {
    padding: 28px 20px 22px;
  }
  .login-card {
    width: min(100%, 560px);
    min-height: 0;
    grid-template-columns: minmax(0, 1fr);
  }
  .login-brand-panel {
    gap: 30px;
    padding: 30px 34px 27px;
    border-right: 0;
    border-bottom: 1px solid var(--console-border);
  }
  .brand-copy {
    padding: 0;
  }
  h1 {
    margin: 13px 0 8px;
    font-size: 30px;
  }
  .brand-copy p {
    font-size: 13px;
    line-height: 1.7;
  }
  .access-note {
    padding: 12px 13px;
  }
  .login-action-panel {
    padding: 36px 34px 38px;
  }
  h2 {
    margin-top: 19px;
  }
  .security-note {
    margin-top: 28px;
  }
}
@media (max-width: 480px) {
  .login-page {
    min-height: 100vh;
    min-height: 100dvh;
    justify-content: flex-start;
    padding: 18px 14px;
  }
  .login-card {
    border-radius: 18px;
  }
  .login-brand-panel {
    gap: 23px;
    padding: 23px 22px;
  }
  .brand-icon-wrap {
    width: 42px;
    height: 42px;
    border-radius: 13px;
  }
  .login-brand {
    width: 30px;
    height: 30px;
  }
  .brand-wordmark {
    font-size: 16px;
  }
  h1 {
    font-size: 28px;
  }
  .login-action-panel {
    padding: 28px 22px 30px;
  }
  h2 {
    font-size: 23px;
  }
  .action-description {
    margin-bottom: 22px;
    font-size: 13px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .login-card,
  .qr-spinner {
    animation: none;
  }
  .login-action > .login-button,
  .passport-link {
    transition: none;
  }
}
</style>
