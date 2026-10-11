<template>
  <div class="overview-page">
    <div class="content-header card-like">
      <div class="header-main">
        <h1 class="page-title">{{ t('ui.mainLayout.message009') }}</h1>
        <p class="page-subtitle">{{ t('ui.home.message001') }}</p>
      </div>
      <el-button class="primary-btn" :loading="refreshing" @click="refreshOverview">
        <el-icon>
          <Refresh />
        </el-icon>
        {{ t('ui.devices.message002') }}
      </el-button>
    </div>

    <el-skeleton v-if="initialLoading" :rows="5" animated class="skeleton" />

    <template v-else>
      <el-card class="hero-card" shadow="never">
        <div class="hero-top">
          <div>
            <div class="hero-greeting">{{ user?.username || t('ui.home.message002') }}</div>
            <div class="hero-meta">{{ user?.email || t('ui.home.message003') }}</div>
          </div>
          <el-tag :type="securityLevelTagType" effect="light" round>
            {{ t('ui.home.message004') }}{{ securityLevelLabel }}
          </el-tag>
        </div>
        <div class="hero-score-row">
          <div class="hero-score">{{ securityScore }}</div>
          <div class="hero-score-text">
            <div class="hero-score-title">{{ t('ui.home.message005') }}</div>
            <div class="hero-score-desc">{{ securityHint }}</div>
          </div>
          <el-button class="hero-action-btn" type="primary" @click="goTo('/home/security')">
            {{ t('ui.home.message006') }}
          </el-button>
        </div>
      </el-card>

      <el-row :gutter="16" class="section-row">
        <el-col :xs="24" :lg="14">
          <el-card class="action-card" shadow="never">
            <div class="section-title">{{ t('ui.home.message007') }}</div>
            <div class="check-list">
              <div v-for="item in securityChecklist" :key="item.title" class="check-item">
                <div class="check-left">
                  <el-icon class="check-icon" :class="item.done ? 'ok' : 'todo'">
                    <CircleCheckFilled v-if="item.done" />
                    <WarningFilled v-else />
                  </el-icon>
                  <div>
                    <div class="check-title">{{ item.title }}</div>
                    <div class="check-desc">{{ item.desc }}</div>
                  </div>
                </div>
                <el-button class="check-action-btn" text type="primary" @click="goTo(item.path)">
                  {{ t('ui.home.message008') }}
                  <el-icon>
                    <ArrowRight />
                  </el-icon>
                </el-button>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="10">
          <el-card class="action-card" shadow="never">
            <div class="section-title">{{ t('ui.home.message009') }}</div>
            <div class="shortcut-grid">
              <button class="shortcut-item" @click="goTo('/home/profile')">
                <el-icon>
                  <User />
                </el-icon>
                <span>{{ t('ui.mainLayout.message010') }}</span>
              </button>
              <button class="shortcut-item" @click="goTo('/home/security')">
                <el-icon>
                  <Lock />
                </el-icon>
                <span>{{ t('ui.mainLayout.message011') }}</span>
              </button>
              <button class="shortcut-item" @click="goTo('/home/login-options')">
                <el-icon>
                  <Link />
                </el-icon>
                <span>{{ t('ui.mainLayout.message012') }}</span>
              </button>
              <button class="shortcut-item" @click="goTo('/home/devices')">
                <el-icon>
                  <Monitor />
                </el-icon>
                <span>{{ t('ui.mainLayout.message013') }}</span>
              </button>
              <button class="shortcut-item" @click="goTo('/home/privacy')">
                <el-icon>
                  <DataLine />
                </el-icon>
                <span>{{ t('ui.mainLayout.message014') }}</span>
              </button>
              <button class="shortcut-item" @click="goTo('/home/preferences')">
                <el-icon>
                  <Setting />
                </el-icon>
                <span>{{ t('ui.mainLayout.message015') }}</span>
              </button>
              <button
                v-if="canCreateApps"
                class="shortcut-item"
                @click="goTo('/home/open-platform')"
              >
                <el-icon>
                  <Connection />
                </el-icon>
                <span>{{ t('ui.mainLayout.message016') }}</span>
              </button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-alert v-if="errorText" type="warning" :closable="false" class="error-banner" show-icon>
        {{ renderMessage(errorText) }}
      </el-alert>
    </template>

    <footer class="overview-footer">
      <div class="footer-left">
        <a :href="documentUrl('agreement/user')" target="_blank" rel="noopener noreferrer">
          {{ t('ui.home.message010') }}
        </a>
        <a
          :href="documentUrl('agreement/privacy')"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ t('ui.home.message011') }}
        </a>
        <a
          :href="documentUrl('agreement/third-party-information-sharing')"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ t('ui.home.message012') }}
        </a>
      </div>
      <div class="footer-right">
        <span>Ksuser Auth 2025-{{ currentYear }}</span>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
          {{ t('ui.home.message013') }}
        </a>
        <a
          class="beian-link with-img"
          href="https://beian.mps.gov.cn/#/query/webSearch?code=31011502403952"
          target="_blank"
          rel="noopener noreferrer"
        >
          <img
            class="beian-icon"
            src="https://static.ksuser.cn/gongan.png"
            :alt="t('ui.home.message014')"
            loading="lazy"
            decoding="async"
          />
          <span>{{ t('ui.home.message015') }}</span>
        </a>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { t, uiMessage, renderMessage, type LocalizedMessage } from '@/i18n'
import { documentUrl } from '@/utils/documentLinks'

import { computed, onMounted, ref } from 'vue'
import {
  ArrowRight,
  CircleCheckFilled,
  Connection,
  DataLine,
  Link,
  Lock,
  Monitor,
  Refresh,
  Setting,
  User,
  WarningFilled,
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { getPasskeyList, getTotpStatus } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'

const router = useRouter()
const userStore = useUserStore()
const { user } = storeToRefs(userStore)
const canCreateApps = computed(() =>
  ['personal', 'enterprise', 'admin'].includes(user.value?.verificationType ?? ''),
)

const initialLoading = ref(true)
const refreshing = ref(false)
const errorText = ref<LocalizedMessage>('')
const currentYear = new Date().getFullYear()

const passkeyCount = ref(0)
const totpEnabled = ref(false)

const mfaEnabled = computed(() => Boolean(user.value?.settings?.mfaEnabled))

const securityScore = computed(() => {
  let score = 0
  if (mfaEnabled.value) score += 35
  if (passkeyCount.value > 0) score += 25
  if (totpEnabled.value) score += 20
  if (user.value?.settings?.detectUnusualLogin) score += 10
  if (user.value?.settings?.notifySensitiveActionEmail) score += 10
  return score
})

const securityLevelLabel = computed(() => {
  if (securityScore.value >= 80) return t('ui.home.message016')
  if (securityScore.value >= 50) return t('ui.home.message017')
  return t('ui.home.message018')
})

const securityLevelTagType = computed(() => {
  if (securityScore.value >= 80) return 'success'
  if (securityScore.value >= 50) return 'warning'
  return 'danger'
})

const securityHint = computed(() => {
  if (securityScore.value >= 80) return t('ui.home.message019')
  if (securityScore.value >= 50) return t('ui.home.message020')
  return t('ui.home.message021')
})

const securityChecklist = computed(() => {
  return [
    {
      title: t('ui.home.message022'),
      desc: mfaEnabled.value ? t('ui.home.message023') : t('ui.home.message024'),
      done: mfaEnabled.value,
      path: '/home/security',
    },
    {
      title: t('ui.home.message025'),
      desc:
        passkeyCount.value > 0
          ? t('ui.home.message026', { arg0: passkeyCount.value })
          : t('ui.home.message027'),
      done: passkeyCount.value > 0,
      path: '/home/login-options',
    },
    {
      title: t('ui.home.message028'),
      desc: totpEnabled.value ? t('ui.home.message029') : t('ui.home.message030'),
      done: totpEnabled.value,
      path: '/home/login-options',
    },
  ]
})

const loadOverview = async () => {
  errorText.value = ''

  try {
    await userStore.fetchUserInfo()

    const [passkeysRes, totpStatusRes] = await Promise.allSettled([
      getPasskeyList(),
      getTotpStatus(),
    ])

    let hasPartialError = false

    if (passkeysRes.status === 'fulfilled') {
      passkeyCount.value = passkeysRes.value.length
    } else {
      hasPartialError = true
    }

    if (totpStatusRes.status === 'fulfilled') {
      totpEnabled.value = totpStatusRes.value.enabled
    } else {
      hasPartialError = true
    }

    if (hasPartialError) {
      errorText.value = uiMessage('ui.home.message031')
    }
  } catch (error) {
    console.error('Load overview failed:', error)
    errorText.value = uiMessage('ui.home.message032')
  }
}

const refreshOverview = async () => {
  refreshing.value = true
  await loadOverview()
  refreshing.value = false
}

const goTo = (path: string) => {
  router.push(path)
}

onMounted(async () => {
  await loadOverview()
  initialLoading.value = false
})
</script>

<style scoped>
.overview-page {
  --surface-soft: var(--el-fill-color-light);
  --surface-subtle: var(--el-fill-color-extra-light);
  --brand-soft: var(--el-color-primary-light-9);
  --brand-border: var(--el-color-primary-light-7);
  --hero-score-bg: linear-gradient(
    140deg,
    var(--el-color-primary) 0%,
    var(--el-color-primary-light-3) 100%
  );
  --hero-score-border: color-mix(in srgb, var(--el-color-primary) 28%, transparent);
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 1400px;
  min-height: calc(100vh - 112px);
  margin: 0 auto;
  padding-bottom: 8px;
}

.card-like {
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  background: var(--el-bg-color);
  padding: 16px 18px;
  box-shadow: 0 4px 14px color-mix(in srgb, var(--el-text-color-primary) 6%, transparent);
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.header-main {
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.primary-btn {
  background: color-mix(in srgb, var(--el-color-primary) 88%, #000 12%);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-weight: 600;
  box-shadow: 0 8px 18px color-mix(in srgb, var(--el-color-primary) 24%, transparent);
}

.primary-btn:hover {
  color: #fff;
  transform: translateY(-1px);
  background: color-mix(in srgb, var(--el-color-primary) 82%, #000 18%);
  box-shadow: 0 10px 24px color-mix(in srgb, var(--el-color-primary) 30%, transparent);
}

.hero-card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  overflow: hidden;
}

.hero-card :deep(.el-card__body) {
  padding: 20px 22px;
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.hero-greeting {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.hero-meta {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.hero-score-row {
  margin-top: 16px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
}

.hero-score {
  min-width: 62px;
  height: 62px;
  padding: 0 12px;
  border-radius: 14px;
  background: var(--hero-score-bg);
  border: 1px solid var(--hero-score-border);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.hero-score-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.hero-score-desc {
  margin-top: 3px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hero-action-btn {
  border-radius: 10px;
  font-weight: 600;
  padding-left: 16px;
  padding-right: 16px;
}

.action-card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
  overflow: hidden;
}

.action-card:hover {
  border-color: var(--brand-border);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--el-color-primary) 12%, transparent);
  transform: translateY(-1px);
}

.action-card :deep(.el-card__body) {
  padding: 18px;
}

.section-row {
  margin-top: 0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 14px;
}

.check-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.check-item {
  width: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.check-item:hover {
  border-color: var(--brand-border);
  background: var(--surface-subtle);
}

.check-left {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.check-icon {
  margin-top: 1px;
  font-size: 16px;
}

.check-icon.ok {
  color: var(--el-color-success);
}

.check-icon.todo {
  color: var(--el-color-warning);
}

.check-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.check-desc {
  margin-top: 3px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.check-action-btn {
  align-self: center;
  border-radius: 10px;
  padding: 0 8px;
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
}

.shortcut-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
  font-weight: 600;
}

.shortcut-item:hover {
  border-color: var(--brand-border);
  background: var(--surface-subtle);
  transform: translateY(-1px);
}

.shortcut-item :deep(.el-icon) {
  width: 22px;
  height: 22px;
  border-radius: 8px;
  background: var(--brand-soft);
  display: grid;
  place-items: center;
  color: var(--el-color-primary);
}

.error-banner,
.skeleton {
  margin-top: 0;
}

.overview-footer {
  margin-top: auto;
  padding: 14px 0 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.8;
  background: var(--el-bg-color-page);
}

.footer-left,
.footer-right {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.footer-right {
  justify-content: flex-end;
  text-align: right;
}

.overview-footer a {
  color: inherit;
  text-decoration: none;
}

.overview-footer a:hover {
  color: var(--el-text-color-regular);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.beian-link.with-img {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.beian-icon {
  width: 14px;
  height: 14px;
  object-fit: contain;
}

@media (max-width: 992px) {
  .content-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .primary-btn {
    width: 100%;
  }

  .hero-top {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-score-row {
    grid-template-columns: 1fr;
  }

  .hero-score {
    width: fit-content;
  }

  .hero-action-btn,
  .check-action-btn {
    width: 100%;
  }

  .overview-footer {
    flex-direction: column;
    gap: 8px;
    padding-top: 12px;
  }

  .footer-right {
    justify-content: flex-start;
    text-align: left;
  }
}

@media (max-width: 720px) {
  .overview-footer {
    margin-top: auto;
  }
}
</style>

<style>
html.dark .overview-page,
:root.dark .overview-page {
  --brand-soft: rgba(255, 185, 15, 0.08);
  --brand-border: rgba(255, 185, 15, 0.3);
  --hero-score-bg: #ffb90f;
  --hero-score-border: rgba(255, 185, 15, 0.45);
}
</style>
