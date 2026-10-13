<template>
  <main class="official-qr-card">
    <section class="official-qr-info">
      <ApplicationAuthorizationInfo
        v-if="payload"
        :app-name="payload.application.clientName"
        :logo-url="payload.application.logoUrl"
        :redirect-uri="payload.redirectUri"
        :requested-scopes="payload.application.requestedScopes"
        :console-application="payload.application.consoleApplication"
        :subject-name="payload.application.subjectName"
        :subject-verification-type="payload.application.subjectVerificationType"
        :mode="mode"
      />
      <p v-else>{{ t('ui.officialQr.validating') }}</p>
    </section>
    <section class="official-qr-action" aria-labelledby="official-qr-title">
      <button
        class="qr-web-login"
        type="button"
        :aria-label="t('ui.officialQr.webLogin')"
        :title="t('ui.officialQr.webLogin')"
        :disabled="busy || switching"
        :aria-busy="switching"
        @click="webLogin"
      >
        <i
          class="fa-solid"
          :class="switching ? 'fa-spinner qr-spinner' : 'fa-desktop'"
          aria-hidden="true"
        ></i>
      </button>
      <h2 id="official-qr-title">{{ t('ui.officialQr.title') }}</h2>
      <p class="qr-instructions">
        {{ t('ui.applicationQr.instructionsPrefix')
        }}<a
          class="qr-app-link"
          :href="ksuserIosAppStoreUrl"
          target="_blank"
          rel="noopener noreferrer"
          >{{ t('ui.applicationQr.appName') }}</a
        >{{ t('ui.applicationQr.instructionsSuffix') }}
      </p>
      <el-alert
        v-if="error"
        :title="renderMessage(error)"
        type="error"
        :closable="false"
        show-icon
      />
      <div class="qr-stage">
        <div class="qr-status-side">
          <span v-if="state === 'pending'" class="qr-status" role="status" aria-live="polite">{{
            t('ui.applicationQr.waiting')
          }}</span>
          <div
            v-if="state === 'pending'"
            class="qr-countdown"
            role="timer"
            :aria-label="t('ui.applicationQr.countdown', { seconds: remaining })"
          >
            <span class="qr-countdown-number">{{ remaining }}</span>
            <span class="qr-countdown-unit">{{ t('ui.applicationQr.seconds') }}</span>
          </div>
          <div v-if="state === 'pending'" class="qr-countdown-track" aria-hidden="true">
            <span :style="{ width: `${countdownProgress}%` }"></span>
          </div>
          <span v-if="state === 'pending'" class="qr-time-caption">{{
            t('ui.applicationQr.timeLeft')
          }}</span>
        </div>
        <div class="qr-image-frame" :aria-busy="state === 'loading'">
          <img
            v-if="image"
            :src="image"
            :alt="t('ui.officialQr.imageAlt')"
            :class="{ inactive: state !== 'pending' }"
          />
          <div v-if="state !== 'pending'" class="qr-overlay">
            <el-icon v-if="busy" class="qr-spinner" :size="28"><Loading /></el-icon>
            <span role="status" aria-live="polite">{{ statusLabel }}</span>
          </div>
        </div>
        <button
          class="qr-refresh"
          type="button"
          :aria-label="t('ui.officialQr.refresh')"
          :title="t('ui.officialQr.refresh')"
          :disabled="busy || switching"
          @click="start"
        >
          <span class="qr-refresh-icon"
            ><i class="fa-solid fa-rotate-right" aria-hidden="true"></i
          ></span>
        </button>
      </div>
      <p class="qr-note">{{ t('ui.officialQr.confirmNotice') }}</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import ApplicationAuthorizationInfo from '@/components/ApplicationAuthorizationInfo.vue'
import QRCode from 'qrcode'
import { t, renderMessage, type LocalizedMessage } from '@/i18n'
import { localizedError } from '@/utils/apiResult'
import {
  initApplicationQr,
  pollApplicationQr,
  exchangeApplicationQr,
  cancelApplicationQr,
  finishApplicationQrWebLogin,
  type ApplicationAuthorizationMode,
  type ApplicationQrPayload,
} from '@/utils/applicationQr'
import { type OfficialAuthorizeRequest } from '@/api/official'
import { ksuserIosAppStoreUrl } from '@/utils/appStore'

const props = withDefaults(
  defineProps<{ authorization: OfficialAuthorizeRequest; mode?: ApplicationAuthorizationMode }>(),
  { mode: 'official' },
)
const emit = defineEmits<{ webLogin: [] }>()
const payload = ref<ApplicationQrPayload | null>(null)
const image = ref('')
const error = ref<LocalizedMessage>('')
const state = ref<'loading' | 'pending' | 'confirming' | 'expired' | 'error'>('loading')
const remaining = ref(0)
const switching = ref(false)
const busy = computed(() => state.value === 'loading' || state.value === 'confirming')
const statusLabel = computed(() => t(`ui.officialQr.${state.value}`))
const countdownProgress = computed(() =>
  Math.min(
    100,
    Math.max(0, (remaining.value / (payload.value?.challenge.expiresInSeconds || 120)) * 100),
  ),
)
let generation = 0
let disposed = false
let completed = false
let expiresAt = 0
let polling: number | undefined
let countdown: number | undefined
function stop() {
  generation += 1
  window.clearTimeout(polling)
  window.clearInterval(countdown)
}
function current(flow: number) {
  return !disposed && generation === flow
}
function input(value: ApplicationQrPayload) {
  return { challengeId: value.challenge.challengeId, pollToken: value.challenge.pollToken }
}
function fail(failure: unknown) {
  stop()
  state.value = 'error'
  error.value = localizedError(failure, 'ui.officialQr.failure')
}
async function cancel(value: ApplicationQrPayload | null) {
  if (value) await cancelApplicationQr(input(value))
}
async function start() {
  stop()
  const flow = generation
  const previous = payload.value
  payload.value = null
  image.value = ''
  error.value = ''
  state.value = 'loading'
  try {
    await cancel(previous)
    if (!current(flow)) return
    const startedAt = Date.now()
    const value = await initApplicationQr(props.mode, props.authorization)
    if (!current(flow)) {
      void cancel(value).catch(() => {})
      return
    }
    payload.value = value
    const rendered = await QRCode.toDataURL(value.challenge.qrText, {
      width: 232,
      margin: 2,
      errorCorrectionLevel: 'M',
    })
    if (!current(flow)) return
    image.value = rendered
    expiresAt = startedAt + value.challenge.expiresInSeconds * 1000
    state.value = 'pending'
    const tick = () => {
      if (!current(flow)) return
      remaining.value = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000))
      if (!remaining.value) {
        stop()
        state.value = 'expired'
      }
    }
    tick()
    if (!current(flow)) return
    countdown = window.setInterval(tick, 1000)
    void poll(flow, value)
  } catch (failure) {
    if (current(flow)) fail(failure)
  }
}
async function poll(flow: number, value: ApplicationQrPayload) {
  let activeFlow = flow
  try {
    const status = await pollApplicationQr(input(value))
    if (!current(flow)) return
    if (status.status === 'approved' || status.status === 'rejected') {
      stop()
      activeFlow = generation
      state.value = 'confirming'
      const result = await exchangeApplicationQr(input(value))
      if (!current(activeFlow)) return
      if (result.status !== 'completed' || !result.redirectUrl)
        throw new Error(t('ui.officialQr.failure'))
      // Match the registered destination shown for this request before following the server callback.
      const expected = new URL(value.redirectUri)
      const destination = new URL(result.redirectUrl)
      if (destination.origin !== expected.origin || destination.pathname !== expected.pathname)
        throw new Error(t('ui.officialQr.failure'))
      await finishApplicationQrWebLogin(result)
      if (!current(activeFlow)) return
      completed = true
      window.location.replace(result.redirectUrl)
    } else if (status.status === 'pending' && status.expiresInSeconds > 0) {
      expiresAt = Math.min(expiresAt, Date.now() + status.expiresInSeconds * 1000)
      polling = window.setTimeout(() => void poll(flow, value), 2000)
    } else {
      stop()
      state.value = 'expired'
      remaining.value = 0
    }
  } catch (failure) {
    if (current(activeFlow)) fail(failure)
  }
}
async function webLogin() {
  if (busy.value || switching.value) return
  switching.value = true
  stop()
  try {
    await cancel(payload.value)
    payload.value = null
    emit('webLogin')
  } catch (failure) {
    fail(failure)
  } finally {
    switching.value = false
  }
}
watch(
  () => [props.mode, props.authorization],
  () => void start(),
  { immediate: true, deep: true },
)
onBeforeUnmount(() => {
  disposed = true
  stop()
  if (!completed) void cancel(payload.value).catch(() => {})
})
</script>

<style scoped>
.official-qr-card {
  box-sizing: border-box;
  width: min(var(--consent-card-width, 1000px), 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--consent-card-radius, 20px);
  background: var(--el-bg-color);
  box-shadow: var(--consent-card-shadow, 0 8px 24px rgb(0 0 0 / 12%), 0 16px 40px rgb(0 0 0 / 8%));
  overflow: hidden;
}
.official-qr-info {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 56px 48px;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--consent-panel-bg, var(--el-bg-color-overlay));
  color: var(--el-text-color-primary);
}
p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.official-qr-action {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px;
  color: var(--el-text-color-primary);
  text-align: center;
}
h2 {
  margin: 8px 0;
  font-size: 24px;
  line-height: 1.4;
}
.qr-instructions {
  max-width: 360px;
  margin: 12px 0 0;
}
.qr-app-link {
  color: var(--passport-link-color, var(--el-color-primary-dark-2));
  font-weight: 600;
  text-decoration: none;
  transition: color 160ms ease;
}
.qr-app-link:visited {
  color: var(--passport-link-color, var(--el-color-primary-dark-2));
}
.qr-app-link:hover {
  color: var(--passport-link-hover, var(--el-color-primary));
  text-decoration: underline;
  text-underline-offset: 3px;
}
.qr-app-link:focus-visible {
  outline: 2px solid var(--passport-link-color, var(--el-color-primary));
  outline-offset: 3px;
  border-radius: 3px;
}
.official-qr-action :deep(.el-alert) {
  margin-bottom: 16px;
}
.qr-image-frame {
  box-sizing: border-box;
  position: relative;
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: #fff;
}
.qr-image-frame img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.qr-image-frame img.inactive {
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
  color: var(--el-color-primary-dark-2);
  animation: qr-spin 1s linear infinite;
}
.qr-stage {
  display: grid;
  justify-content: center;
  grid-template-columns: 76px minmax(0, 220px) 64px;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 380px;
  margin-top: 24px;
}
.qr-status-side {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-width: 0;
  padding: 0;
  color: var(--el-text-color-secondary);
  font-family: inherit;
  font-size: 11px;
  line-height: 1.5;
  text-align: center;
}
.qr-status {
  display: block;
  max-width: 100%;
  overflow: hidden;
  font-size: 10px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.qr-countdown {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-variant-numeric: tabular-nums;
}
.qr-countdown-number {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.06em;
  line-height: 1;
  color: var(--passport-link-color, var(--el-color-primary-dark-2));
}
.qr-countdown-unit {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1;
}
.qr-countdown-track {
  width: 42px;
  height: 2px;
  overflow: hidden;
  border-radius: 2px;
  background: var(--el-border-color-light);
}
.qr-countdown-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--passport-link-color, var(--el-color-primary-dark-2));
  transition: width 1s linear;
}
.qr-time-caption {
  color: var(--el-text-color-secondary);
  font-size: 10px;
  line-height: 1.2;
}
.qr-refresh {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin: auto;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: rgba(var(--el-color-primary-rgb, 255, 185, 15), 0.1);
  color: var(--passport-link-color, var(--el-color-primary-dark-2));
  cursor: pointer;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}
.qr-refresh-icon {
  display: grid;
  place-items: center;
  font-size: 19px;
}
.qr-refresh:hover:not(:disabled) {
  color: var(--passport-link-hover, var(--el-color-primary));
  background: rgba(var(--el-color-primary-rgb, 255, 185, 15), 0.18);
}
.qr-web-login {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--passport-link-color, var(--el-color-primary-dark-2));
  font-size: 21px;
  cursor: pointer;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}
.qr-web-login:hover:not(:disabled) {
  color: var(--passport-link-hover, var(--el-color-primary));
  background: var(--el-fill-color-light);
}
.qr-refresh:focus-visible,
.qr-web-login:focus-visible {
  outline: 2px solid var(--passport-link-color, var(--el-color-primary));
  outline-offset: 2px;
}
.qr-refresh:disabled,
.qr-web-login:disabled {
  opacity: 0.5;
  cursor: default;
}
.qr-note {
  margin-top: auto;
  padding-top: 24px;
  font-size: 12px;
}
.official-qr-action .qr-note {
  margin: 0;
  padding-top: 20px;
}
@keyframes qr-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 900px) {
  .official-qr-card {
    grid-template-columns: 1fr;
    align-self: flex-start;
    height: max-content;
    min-height: 100%;
    border-radius: 0;
  }
  .official-qr-info {
    padding: 40px 32px;
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-light);
  }
  .official-qr-action {
    padding: 32px;
  }
  .qr-note {
    padding-top: 12px;
  }
}
@media (max-width: 480px) {
  .official-qr-info {
    padding-top: 72px;
  }
  .official-qr-action {
    padding: 32px 16px;
  }
  .qr-stage {
    grid-template-columns: 64px minmax(0, 220px) 48px;
    gap: 8px;
  }
  .qr-status-side {
    font-size: 10px;
  }
  .qr-countdown-number {
    font-size: 19px;
  }
  .qr-countdown-track {
    width: 34px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .qr-spinner {
    animation: none;
  }
}
</style>
