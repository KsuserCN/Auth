<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watchEffect } from 'vue'
import { RouterLink } from 'vue-router'
import { useMutationObserver } from '@vueuse/core'
import { ArrowDown, ArrowRight, Key, Monitor, FullScreen } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { t } from '@/i18n'
import LanguageSelector from '@/components/LanguageSelector.vue'
import { ksuserIosAppStoreUrl } from '@/utils/appStore'
import { documentUrl } from '@/utils/documentLinks'
import {
  fetchAndroidRelease,
  mobileDownloadPageUrl,
  type AndroidDownloadRelease,
} from '@/utils/appDownloads'

const release = ref<AndroidDownloadRelease | null>(null)
const loading = ref(true)
const qrImage = ref('')
const copyState = ref<'idle' | 'copied' | 'failed'>('idle')
const previewPlatform = /Android/i.test(navigator.userAgent) ? 'android' : 'ios'
const previewPlatformName = previewPlatform === 'android' ? 'Android' : 'iOS'
const previewDark = ref(document.documentElement.classList.contains('dark'))
// Follow the site's actual theme, including changes made on other Web pages.
useMutationObserver(
  document.documentElement,
  () => {
    previewDark.value = document.documentElement.classList.contains('dark')
  },
  { attributes: true, attributeFilter: ['class'] },
)
const previewSrc = computed(
  () => `/images/app-preview/${previewPlatform}-${previewDark.value ? 'dark' : 'light'}.png`,
)
const previousTitle = document.title
const pageTitle = computed(() => t('ui.downloads.pageTitle'))
watchEffect(() => {
  document.title = pageTitle.value
})
let active = true
let controller: AbortController | undefined
let copyTimeout: ReturnType<typeof setTimeout> | undefined

const features = [
  { name: 'login', icon: Key, number: '01' },
  { name: 'authorize', icon: FullScreen, number: '02' },
  { name: 'sessions', icon: Monitor, number: '03' },
] as const
const faqs = ['install', 'account', 'update'] as const

async function loadRelease() {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  release.value = null
  const timeout = setTimeout(() => current.abort(), 12000)
  try {
    const result = await fetchAndroidRelease(current.signal)
    if (active && controller === current) release.value = result
  } catch {
    // The public download page stays usable when the Android release is unavailable.
  } finally {
    clearTimeout(timeout)
    if (active && controller === current) loading.value = false
  }
}

async function copyLink() {
  clearTimeout(copyTimeout)
  try {
    await navigator.clipboard.writeText(mobileDownloadPageUrl)
    if (active) copyState.value = 'copied'
  } catch {
    if (active) copyState.value = 'failed'
  }
  if (active)
    copyTimeout = setTimeout(() => {
      copyState.value = 'idle'
    }, 3500)
}

onMounted(() => {
  void loadRelease()
  void QRCode.toDataURL(mobileDownloadPageUrl, {
    width: 240,
    margin: 2,
    errorCorrectionLevel: 'M',
    color: { dark: '#303133', light: '#ffffff' },
  })
    .then((image) => {
      if (active) qrImage.value = image
    })
    .catch(() => {
      /* The text link remains available. */
    })
})
onBeforeUnmount(() => {
  active = false
  controller?.abort()
  clearTimeout(copyTimeout)
  if (document.title === pageTitle.value) document.title = previousTitle
})
</script>

<template>
  <div class="downloads-page">
    <a class="skip-link" href="#get-app">{{ t('ui.downloads.skip') }}</a>
    <header class="downloads-header">
      <RouterLink class="download-brand" to="/login" :aria-label="t('ui.downloads.webLogin')">
        <img src="/images/ksuser-app-icon.png" alt="" width="28" height="28" />
        <span>{{ t('ui.downloads.brand') }}</span>
      </RouterLink>
      <nav class="downloads-nav" :aria-label="t('ui.downloads.navigation')">
        <a class="features-nav" href="#features">{{ t('ui.downloads.featuresNav') }}</a>
        <RouterLink class="web-login-link" to="/login"
          >{{ t('ui.downloads.webLogin') }} <ArrowRight
        /></RouterLink>
        <LanguageSelector compact />
      </nav>
    </header>

    <main>
      <section class="download-hero download-container" aria-labelledby="download-heading">
        <div class="hero-copy">
          <p class="download-eyebrow"><span></span>{{ t('ui.downloads.eyebrow') }}</p>
          <h1 id="download-heading">
            {{ t('ui.downloads.headline') }}<br /><span>{{
              t('ui.downloads.headlineAccent')
            }}</span>
          </h1>
          <p class="hero-description">{{ t('ui.downloads.description') }}</p>
          <div class="hero-downloads">
            <a
              v-if="release"
              class="download-button android-button"
              :href="release.apkUrl"
              download="Ksuser-Auth.apk"
              data-testid="android-download"
            >
              <i class="fa-brands fa-android" aria-hidden="true"></i>
              <span>{{ t('ui.downloads.androidDownload') }}<small>Android 7.0+</small></span>
              <ArrowDown />
            </a>
            <button
              v-else
              class="download-button android-button"
              type="button"
              :disabled="loading"
              @click="loadRelease"
            >
              <i class="fa-brands fa-android" aria-hidden="true"></i>
              <span
                >{{ t(loading ? 'ui.downloads.loading' : 'ui.downloads.retry')
                }}<small>Android</small></span
              >
              <ArrowDown />
            </button>
            <a
              class="download-button ios-button"
              :href="ksuserIosAppStoreUrl"
              target="_blank"
              rel="noopener noreferrer"
              data-testid="ios-download"
            >
              <i class="fa-brands fa-apple" aria-hidden="true"></i>
              <span>{{ t('ui.downloads.iosDownload') }}<small>App Store</small></span>
              <ArrowRight />
            </a>
          </div>
          <p class="release-status" role="status" aria-live="polite">
            <template v-if="release"
              ><span class="status-dot"></span
              >{{
                t('ui.downloads.currentVersion', {
                  version: release.versionName,
                  build: release.versionCode,
                })
              }}</template
            >
            <template v-else>{{
              t(loading ? 'ui.downloads.loadingHint' : 'ui.downloads.unavailable')
            }}</template>
          </p>
          <a class="explore-link" href="#features">{{ t('ui.downloads.explore') }} <ArrowDown /></a>
        </div>

        <div class="hero-visual">
          <img
            class="app-screenshot"
            :src="previewSrc"
            :alt="t('ui.downloads.previewAlt', { platform: previewPlatformName })"
            :width="previewPlatform === 'android' ? 1080 : 1206"
            :height="previewPlatform === 'android' ? 2400 : 2622"
            fetchpriority="high"
          />
          <p class="preview-caption">
            {{ t('ui.downloads.previewCaption', { platform: previewPlatformName }) }}
          </p>
        </div>
      </section>

      <section
        id="features"
        class="download-features download-container"
        aria-labelledby="features-heading"
      >
        <div class="section-intro">
          <p class="section-kicker">{{ t('ui.downloads.featureEyebrow') }}</p>
          <h2 id="features-heading">{{ t('ui.downloads.featureHeading') }}</h2>
        </div>
        <div class="feature-columns">
          <article v-for="feature in features" :key="feature.name" class="download-feature">
            <div class="feature-top">
              <span class="feature-symbol"><component :is="feature.icon" /></span
              ><span class="feature-number">{{ feature.number }}</span>
            </div>
            <h3>{{ t(`ui.downloads.${feature.name}Title`) }}</h3>
            <p>{{ t(`ui.downloads.${feature.name}Description`) }}</p>
          </article>
        </div>
      </section>

      <section id="get-app" class="get-app-section" aria-labelledby="get-app-heading">
        <div class="download-container">
          <div class="section-intro">
            <p class="section-kicker">{{ t('ui.downloads.getAppEyebrow') }}</p>
            <h2 id="get-app-heading">{{ t('ui.downloads.getAppHeading') }}</h2>
            <p>{{ t('ui.downloads.getAppDescription') }}</p>
          </div>
          <div class="download-options">
            <article class="platform-option android-option">
              <div class="platform-heading">
                <i class="fa-brands fa-android" aria-hidden="true"></i>
                <div>
                  <h3>Android</h3>
                  <p>{{ t('ui.downloads.androidRequirements') }}</p>
                </div>
                <span class="platform-tag">APK</span>
              </div>
              <template v-if="release">
                <p class="platform-version">
                  {{ release.versionName }}
                  <span>{{ t('ui.downloads.build', { build: release.versionCode }) }}</span>
                </p>
                <a class="platform-download" :href="release.apkUrl" download="Ksuser-Auth.apk"
                  >{{ t('ui.downloads.androidDownload') }} <ArrowDown
                /></a>
                <details v-if="release.releaseNotes" class="release-detail">
                  <summary>{{ t('ui.downloads.releaseNotes') }}</summary>
                  <p class="release-notes">{{ release.releaseNotes }}</p>
                </details>
                <details class="release-detail">
                  <summary>{{ t('ui.downloads.verification') }}</summary>
                  <p>{{ t('ui.downloads.verificationHint') }}</p>
                  <code class="apk-hash">SHA-256<br />{{ release.sha256 }}</code>
                </details>
              </template>
              <div v-else class="platform-unavailable" aria-live="polite">
                <p>{{ t(loading ? 'ui.downloads.loadingHint' : 'ui.downloads.unavailable') }}</p>
                <button type="button" :disabled="loading" @click="loadRelease">
                  {{ t(loading ? 'ui.downloads.loading' : 'ui.downloads.retry') }}
                </button>
              </div>
            </article>
            <article class="platform-option ios-option">
              <div class="platform-heading">
                <i class="fa-brands fa-apple" aria-hidden="true"></i>
                <div>
                  <h3>iPhone · iPad</h3>
                  <p>{{ t('ui.downloads.iosRequirements') }}</p>
                </div>
                <span class="platform-tag">iOS</span>
              </div>
              <p class="store-description">{{ t('ui.downloads.iosDescription') }}</p>
              <a
                class="platform-download"
                :href="ksuserIosAppStoreUrl"
                target="_blank"
                rel="noopener noreferrer"
                >{{ t('ui.downloads.iosDownload') }} <ArrowRight
              /></a>
              <p class="platform-note">{{ t('ui.downloads.iosNote') }}</p>
            </article>
            <aside class="download-qr" :aria-label="t('ui.downloads.qrTitle')">
              <img
                v-if="qrImage"
                :src="qrImage"
                :alt="t('ui.downloads.qrAlt')"
                width="120"
                height="120"
              />
              <div v-else class="qr-placeholder"><FullScreen /></div>
              <h3>{{ t('ui.downloads.qrTitle') }}</h3>
              <p>{{ t('ui.downloads.qrDescription') }}</p>
              <button class="copy-download-link" type="button" @click="copyLink">
                {{ t(copyState === 'copied' ? 'ui.downloads.copied' : 'ui.downloads.copyLink') }}
              </button>
              <span v-if="copyState === 'failed'" class="copy-failure" role="status">{{
                t('ui.downloads.copyFailed')
              }}</span>
              <a class="canonical-link" :href="mobileDownloadPageUrl">auth.ksuser.cn/downloads</a>
            </aside>
          </div>
        </div>
      </section>

      <section class="download-faq download-container" aria-labelledby="faq-heading">
        <div class="section-intro">
          <p class="section-kicker">{{ t('ui.downloads.faqEyebrow') }}</p>
          <h2 id="faq-heading">{{ t('ui.downloads.faqHeading') }}</h2>
        </div>
        <div class="faq-list">
          <details v-for="faq in faqs" :key="faq">
            <summary>
              {{ t(`ui.downloads.${faq}Question`) }}<span aria-hidden="true">+</span>
            </summary>
            <p>{{ t(`ui.downloads.${faq}Answer`) }}</p>
          </details>
        </div>
      </section>
    </main>

    <footer class="downloads-footer download-container">
      <span>© {{ new Date().getFullYear() }} {{ t('ui.downloads.brand') }}</span>
      <div>
        <a :href="documentUrl('agreement/user')" target="_blank" rel="noopener noreferrer">{{
          t('ui.downloads.terms')
        }}</a
        ><a :href="documentUrl('agreement/privacy')" target="_blank" rel="noopener noreferrer">{{
          t('ui.downloads.privacy')
        }}</a
        ><RouterLink to="/login">{{ t('ui.downloads.webLogin') }}</RouterLink>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.downloads-page {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  scroll-behavior: smooth;
}
.downloads-page *,
.downloads-page *::before,
.downloads-page *::after {
  box-sizing: border-box;
}
.downloads-page a {
  color: inherit;
  text-decoration: none;
}
.downloads-page button {
  font: inherit;
  cursor: pointer;
}
.downloads-page button:disabled {
  cursor: wait;
  opacity: 0.65;
}
.downloads-page :is(a, button, summary):focus-visible {
  outline: 3px solid var(--el-color-primary);
  outline-offset: 5px;
}
.downloads-page svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}
.download-container {
  width: min(1180px, calc(100% - 80px));
  margin-inline: auto;
}
.skip-link {
  position: fixed;
  top: -80px;
  left: 16px;
  z-index: 40;
  padding: 12px 20px;
  background: var(--el-color-primary);
  color: var(--el-color-black) !important;
  border-radius: 8px;
}
.skip-link:focus {
  top: 12px;
}
.downloads-header {
  position: sticky;
  top: 0;
  z-index: 10;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 64px;
  padding: 12px max(24px, calc((100% - 1180px) / 2));
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}
.download-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 16px;
  white-space: nowrap;
}
.download-brand img {
  border-radius: 8px;
}
.downloads-nav {
  display: flex;
  align-items: center;
  gap: 28px;
  font-size: 13px;
}
.features-nav {
  color: var(--el-text-color-secondary) !important;
}
.web-login-link {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.web-login-link svg {
  width: 15px;
  height: 15px;
}
.downloads-nav :deep(.language-label) {
  display: none;
}
.downloads-nav :deep(.language-selector.compact) {
  padding: 0;
  box-shadow: none;
  background: transparent;
}
.downloads-nav :deep(.language-selector.compact .language-select) {
  width: 136px;
}
.downloads-nav :deep(select) {
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
  border-color: var(--el-border-color-light);
}
.download-hero {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  align-items: center;
  gap: 48px;
  padding-block: 74px 68px;
}
.hero-copy {
  padding-bottom: 12px;
}
.download-eyebrow {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 27px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1.2px;
}
.download-eyebrow > span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-color-primary);
}
.hero-copy h1 {
  margin: 0;
  font-size: clamp(42px, 4.7vw, 64px);
  line-height: 1.28;
  font-weight: 700;
  letter-spacing: -2px;
}
.hero-copy h1 > span {
  color: var(--el-color-primary);
}
.hero-description {
  max-width: 430px;
  margin: 31px 0;
  font-size: 16px;
  line-height: 1.95;
  color: var(--el-text-color-secondary);
}
.hero-downloads {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.download-button {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 65px;
  padding: 12px 17px;
  border: 1px solid transparent;
  border-radius: 12px;
  transition:
    transform 0.18s,
    box-shadow 0.18s;
}
.download-button:hover {
  transform: translateY(-2px);
  box-shadow: var(--el-box-shadow-light);
}
.download-button > i {
  font-size: 26px;
}
.download-button > span {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
  font-weight: 650;
}
.download-button small {
  font-size: 10px;
  font-weight: 400;
  opacity: 0.75;
}
.download-button > svg {
  width: 17px;
  height: 17px;
  margin-left: 5px;
}
.android-button {
  background: var(--el-color-primary);
  color: var(--el-color-black) !important;
}
.ios-button {
  background: var(--el-bg-color);
  border-color: var(--el-border-color-light);
}
.release-status {
  min-height: 36px;
  display: flex;
  align-items: center;
  gap: 7px;
  max-width: 450px;
  margin: 13px 0 24px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.status-dot {
  width: 5px;
  height: 5px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--el-color-success);
}
.explore-link {
  display: inline-flex;
  gap: 9px;
  align-items: center;
  font-size: 12px;
}
.explore-link svg {
  width: 14px;
  height: 14px;
}
.hero-visual {
  position: relative;
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  padding: 0 20px;
  min-width: 0;
}
.app-screenshot {
  display: block;
  width: 258px;
  max-width: 100%;
  height: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 24px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
}
.preview-caption {
  margin: 18px 0 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.download-features,
.download-faq {
  padding-block: 70px 80px;
  scroll-margin-top: 85px;
}
.download-features {
  border-top: 1px solid var(--el-border-color-light);
}
.section-kicker {
  margin: 0 0 14px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--el-text-color-secondary);
}
.section-intro h2 {
  margin: 0;
  font-size: clamp(25px, 2.7vw, 34px);
  line-height: 1.4;
  letter-spacing: -0.8px;
}
.section-intro > p:not(.section-kicker) {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.8;
  margin: 15px 0 0;
}
.feature-columns {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 32px;
}
.download-feature {
  padding: 24px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
}
.feature-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.feature-symbol {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  background: var(--el-fill-color-light);
  border-radius: 12px;
}
.feature-number {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
.download-feature h3 {
  margin: 20px 0 10px;
  font-size: 17px;
  font-weight: 650;
}
.download-feature p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.9;
}
.get-app-section {
  padding-block: 68px 72px;
  background: var(--el-bg-color-page);
  border-block: 1px solid var(--el-border-color-light);
  scroll-margin-top: 75px;
}
.download-options {
  display: grid;
  grid-template-columns: 1fr 1fr 0.78fr;
  gap: 20px;
  margin-top: 36px;
  align-items: start;
}
.platform-option {
  padding: 26px;
  min-height: 282px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
}
.platform-heading {
  display: flex;
  gap: 12px;
  align-items: center;
}
.platform-heading > i {
  font-size: 27px;
}
.platform-heading h3 {
  margin: 0;
  font-size: 17px;
}
.platform-heading p {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.platform-tag {
  margin-left: auto;
  border: 1px solid var(--el-border-color-light);
  padding: 4px 6px;
  border-radius: 5px;
  font-size: 9px;
  color: var(--el-text-color-secondary);
}
.platform-version {
  margin: 25px 0 20px;
  font-size: 22px;
  font-weight: 650;
}
.platform-version > span {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
  margin-left: 5px;
}
.platform-download {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 15px;
  border-radius: 9px;
  font-size: 12px;
  font-weight: 650;
  background: var(--el-color-primary);
  color: var(--el-color-black) !important;
}
.platform-download svg {
  width: 16px;
  height: 16px;
}
.ios-option .platform-download {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary) !important;
}
.store-description {
  min-height: 37px;
  margin: 23px 0 20px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}
.platform-note {
  margin: 17px 0 0;
  font-size: 10px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.release-detail {
  margin-top: 17px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.release-detail summary {
  cursor: pointer;
  line-height: 1.7;
}
.release-detail p {
  margin: 10px 0 0;
  line-height: 1.8;
}
.release-notes {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.apk-hash {
  display: block;
  margin-top: 10px;
  line-height: 1.7;
  font-size: 10px;
  overflow-wrap: anywhere;
}
.platform-unavailable {
  padding-top: 24px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}
.platform-unavailable button {
  color: var(--el-text-color-primary);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  padding: 10px 15px;
}
.download-qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 4px 8px;
}
.download-qr > img {
  width: 120px;
  height: 120px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-light);
}
.qr-placeholder {
  display: grid;
  place-items: center;
  width: 120px;
  height: 120px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
}
.download-qr h3 {
  font-size: 14px;
  margin: 15px 0 7px;
}
.download-qr p {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
  margin: 0;
}
.copy-download-link {
  margin-top: 14px;
  padding: 7px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 7px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font-size: 11px !important;
}
.canonical-link {
  margin-top: 11px;
  font-size: 10px;
  color: var(--el-text-color-secondary) !important;
  overflow-wrap: anywhere;
}
.copy-failure {
  margin-top: 8px;
  font-size: 10px;
  color: var(--el-text-color-secondary);
}
.download-faq {
  display: grid;
  grid-template-columns: 0.8fr 1.5fr;
  gap: 70px;
}
.faq-list > details {
  border-bottom: 1px solid var(--el-border-color-light);
}
.faq-list > details:first-child {
  border-top: 1px solid var(--el-border-color-light);
}
.faq-list summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  list-style: none;
  padding: 22px 0;
  font-size: 14px;
  cursor: pointer;
}
.faq-list summary::-webkit-details-marker {
  display: none;
}
.faq-list summary > span {
  font-size: 20px;
  color: var(--el-text-color-secondary);
}
.faq-list details[open] summary > span {
  transform: rotate(45deg);
}
.faq-list p {
  margin: -4px 28px 24px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.9;
}
.downloads-footer {
  display: flex;
  justify-content: space-between;
  gap: 25px;
  padding-block: 25px 30px;
  border-top: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.downloads-footer > div {
  display: flex;
  flex-wrap: wrap;
  gap: 23px;
}
@media (min-width: 1500px) {
  .download-hero {
    padding-block: 90px;
  }
}
@media (max-width: 1050px) {
  .download-container {
    width: calc(100% - 64px);
  }
  .downloads-header {
    padding-inline: 32px;
  }
  .download-hero {
    gap: 20px;
  }
  .hero-copy h1 {
    font-size: 48px;
  }
  .hero-downloads {
    gap: 9px;
  }
  .download-button {
    padding: 12px;
  }
  .download-button > span {
    font-size: 12px;
  }
  .download-button > svg {
    display: none;
  }
  .feature-columns {
    gap: 30px;
  }
  .download-options {
    grid-template-columns: 1fr 1fr;
  }
  .download-qr {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: 100px 1fr;
    text-align: left;
    align-items: center;
    column-gap: 24px;
    padding: 15px 0 0;
  }
  .download-qr > img,
  .qr-placeholder {
    grid-column: 1;
    grid-row: 1 / 6;
    width: 100px;
    height: 100px;
  }
  .download-qr h3,
  .download-qr p,
  .download-qr button,
  .download-qr .canonical-link,
  .copy-failure {
    grid-column: 2;
    justify-self: start;
    margin-top: 0;
  }
  .download-qr button {
    margin-top: 7px;
  }
  .download-qr .canonical-link {
    margin-top: 6px;
  }
}
@media (max-width: 720px) {
  .download-container {
    width: calc(100% - 40px);
  }
  .downloads-header {
    padding: 13px 20px;
    gap: 10px;
  }
  .download-brand {
    font-size: 16px;
    gap: 8px;
  }
  .download-brand img {
    width: 28px;
    height: 28px;
  }
  .downloads-nav {
    gap: 14px;
  }
  .features-nav {
    display: none;
  }
  .downloads-nav :deep(.language-selector.compact .language-select) {
    width: 110px;
  }
  .downloads-nav :deep(.language-selector.compact .language-select select) {
    height: 34px;
    font-size: 11px;
    padding-left: 8px;
  }
  .web-login-link {
    font-size: 11px;
  }
  .web-login-link svg {
    display: none;
  }
  .download-hero {
    grid-template-columns: 1fr;
    padding-block: 46px 40px;
    gap: 42px;
  }
  .hero-copy {
    text-align: center;
  }
  .download-eyebrow {
    justify-content: center;
    margin-bottom: 22px;
    font-size: 10px;
  }
  .hero-copy h1 {
    font-size: clamp(38px, 9.3vw, 52px);
    letter-spacing: -1px;
  }
  .hero-description {
    margin: 24px auto;
    font-size: 14px;
    line-height: 1.9;
  }
  .hero-downloads {
    justify-content: center;
  }
  .download-button {
    padding: 12px 15px;
    min-height: 62px;
  }
  .download-button > span {
    font-size: 12px;
  }
  .release-status {
    justify-content: center;
    margin: 13px auto 20px;
    font-size: 10px;
  }
  .hero-visual {
    padding-inline: 20px;
    width: 100%;
    max-width: 440px;
    margin-inline: auto;
  }
  .app-screenshot {
    width: 242px;
  }
  .download-features,
  .download-faq {
    padding-block: 43px 48px;
  }
  .feature-columns {
    grid-template-columns: 1fr;
    gap: 27px;
    margin-top: 28px;
  }
  .download-feature h3 {
    margin-top: 14px;
  }
  .get-app-section {
    padding-block: 43px 45px;
  }
  .download-options {
    grid-template-columns: 1fr;
    margin-top: 26px;
    gap: 16px;
  }
  .platform-option {
    padding: 23px;
    min-height: auto;
  }
  .download-qr {
    grid-column: auto;
    padding-top: 14px;
  }
  .download-faq {
    grid-template-columns: 1fr;
    gap: 26px;
  }
  .downloads-footer {
    flex-direction: column;
    gap: 17px;
    padding-block: 23px;
  }
}
@media (max-width: 380px) {
  .downloads-header {
    padding-inline: 13px;
  }
  .download-brand {
    font-size: 14px;
  }
  .downloads-nav {
    gap: 9px;
  }
  .download-button {
    padding: 11px;
    gap: 9px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .downloads-page {
    scroll-behavior: auto;
  }
  .download-button {
    transition: none;
  }
}
</style>
