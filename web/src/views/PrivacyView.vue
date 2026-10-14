<template>
  <div class="privacy-page">
    <div class="content-header">
      <div>
        <h1 class="page-title">{{ t('ui.mainLayout.message014') }}</h1>
        <p class="page-subtitle">{{ t('ui.privacy.message001') }}</p>
      </div>
    </div>

    <el-alert
      v-if="!canCreateApps"
      type="info"
      :closable="false"
      show-icon
      class="app-access-banner"
    >
      {{ t('ui.privacy.message002') }}
      <a href="https://www.ksuser.cn/#/contact" target="_blank" rel="noopener noreferrer">{{
        t('ui.privacy.message003')
      }}</a>
      {{ t('ui.privacy.message004') }}
    </el-alert>

    <div v-if="loading || officialLoading" class="overview-grid">
      <el-card v-for="index in 2" :key="index" class="overview-card" shadow="never">
        <div class="privacy-overview-skeleton">
          <el-skeleton-item variant="text" class="privacy-overview-label" />
          <el-skeleton-item variant="text" class="privacy-overview-value" />
          <el-skeleton-item variant="text" class="privacy-overview-desc" />
        </div>
      </el-card>
    </div>
    <div v-else class="overview-grid">
      <el-card class="overview-card" shadow="never">
        <div class="overview-card-head">
          <span class="overview-icon external"
            ><el-icon><Share /></el-icon></span
          ><span class="overview-label">{{ t('ui.privacy.message007') }}</span>
        </div>
        <div class="overview-value">{{ oauthApps.length }}</div>
        <div class="overview-desc">{{ t('ui.privacy.message008') }}</div>
      </el-card>

      <el-card class="overview-card" shadow="never">
        <div class="overview-card-head">
          <span class="overview-icon official"
            ><el-icon><Lock /></el-icon></span
          ><span class="overview-label">{{ t('ui.official.privacyTitle') }}</span>
        </div>
        <div class="overview-value">{{ officialApps.length }}</div>
        <div class="overview-desc">{{ t('ui.official.privacyDescription') }}</div>
      </el-card>
    </div>

    <el-row v-if="loading" :gutter="16">
      <el-col v-for="index in 2" :key="index" :xs="24" :lg="12" :xl="8">
        <el-card class="card" shadow="never">
          <div class="privacy-card-skeleton">
            <div class="privacy-card-skeleton-head">
              <el-skeleton-item variant="circle" class="privacy-title-icon" />
              <div class="privacy-title-copy">
                <el-skeleton-item variant="text" class="privacy-title-line" />
                <el-skeleton-item variant="text" class="privacy-title-subline" />
              </div>
            </div>
            <div v-for="row in 3" :key="row" class="privacy-app-skeleton">
              <div class="privacy-app-skeleton-main">
                <el-skeleton-item variant="circle" class="privacy-app-avatar" />
                <div class="privacy-app-copy">
                  <el-skeleton-item variant="text" class="privacy-app-title" />
                  <el-skeleton-item variant="text" class="privacy-app-meta" />
                </div>
              </div>
              <div class="privacy-app-actions">
                <el-skeleton-item variant="button" class="privacy-action-btn" />
                <el-skeleton-item variant="button" class="privacy-action-btn" />
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row v-else :gutter="16">
      <el-col :xs="24" :lg="12" :xl="8">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon><Share /></el-icon>
            <span>{{ t('ui.privacy.message007') }}</span>
            <el-tag class="app-count" size="small" effect="plain">{{ oauthApps.length }}</el-tag>
          </div>
          <p class="card-desc">{{ t('ui.privacy.message018') }}</p>

          <div class="list-toolbar">
            <el-input
              v-if="oauthApps.length > PAGE_SIZE"
              v-model="oauthSearch"
              class="app-search"
              :placeholder="t('ui.privacy.message019')"
              clearable
              :prefix-icon="Search"
              @input="oauthPage = 1"
            />
            <span v-if="oauthApps.length" class="list-count"
              >{{ filteredOauthApps.length }} {{ t('ui.privacy.message011') }}</span
            >
          </div>
          <div v-if="pagedOauthApps.length" class="app-list">
            <div v-for="app in pagedOauthApps" :key="app.appId" class="app-item">
              <div class="app-left">
                <el-avatar
                  :size="48"
                  :src="app.logoUrl"
                  :class="['app-avatar', { 'app-avatar--image': !!app.logoUrl }]"
                >
                  {{ app.appName.slice(0, 1).toUpperCase() }}
                </el-avatar>
                <div class="app-info">
                  <div class="app-name-row">
                    <span class="app-name">{{ app.appName }}</span>
                    <el-tag size="small" type="info" effect="plain">{{
                      creatorNameLabel(app.creatorName)
                    }}</el-tag>
                    <el-tag size="small" effect="plain">{{ grantModeLabel(app.grantMode) }}</el-tag>
                    <el-tag
                      v-if="app.creatorVerificationType"
                      size="small"
                      :type="verificationTagType(app.creatorVerificationType)"
                      effect="plain"
                    >
                      {{ verificationLabel(app.creatorVerificationType) }}
                    </el-tag>
                  </div>
                  <div class="app-meta">
                    {{ t('ui.privacy.message012') }}{{ formatDateTime(app.lastAuthorizedAt) }}
                  </div>
                  <div v-if="app.grantMode === 'TIME_LIMITED' && app.expiresAt" class="app-meta">
                    {{ t('ui.privacy.message013') }}{{ formatDateTime(app.expiresAt) }}
                  </div>
                </div>
              </div>

              <div class="app-actions">
                <el-button size="small" @click="openOauthDetailDialog(app.appId)">{{
                  t('ui.devices.message016')
                }}</el-button>
                <el-popconfirm
                  :title="t('ui.privacy.message020')"
                  :description="t('ui.privacy.message021')"
                  :confirm-button-text="t('ui.devices.message017')"
                  :cancel-button-text="t('ui.addPasskey.message009')"
                  @confirm="handleRevokeOauth(app.appId)"
                >
                  <template #reference>
                    <el-button
                      type="danger"
                      plain
                      size="small"
                      :loading="revokingId === `oauth:${app.appId}`"
                    >
                      {{ t('ui.devices.message017') }}
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </div>

          <el-empty
            v-else
            :description="
              oauthApps.length ? t('ui.privacy.message016') : t('ui.privacy.message022')
            "
          />
          <el-pagination
            v-if="filteredOauthApps.length > PAGE_SIZE"
            v-model:current-page="oauthPage"
            class="app-pagination"
            :page-size="PAGE_SIZE"
            :total="filteredOauthApps.length"
            layout="prev, pager, next"
            small
            background
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12" :xl="8">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon><Monitor /></el-icon>
            <span>{{ t('ui.official.privacyTitle') }}</span>
            <el-tag class="app-count" size="small" effect="plain">{{ officialApps.length }}</el-tag>
          </div>
          <p class="card-desc">{{ t('ui.official.privacyDescription') }}</p>
          <div v-if="officialLoading" class="privacy-card-skeleton">
            <el-skeleton :rows="2" animated />
          </div>
          <el-alert v-else-if="officialError" type="error" :closable="false" show-icon>
            {{ officialError }}
            <el-button link type="primary" @click="loadOfficialAuthorizations">{{
              t('ui.devices.message035')
            }}</el-button>
          </el-alert>
          <template v-else>
            <div v-if="officialApps.length > PAGE_SIZE" class="list-toolbar">
              <el-input
                v-model="officialSearch"
                class="app-search"
                :placeholder="t('ui.official.privacySearch')"
                clearable
                :prefix-icon="Search"
                @input="officialPage = 1"
              />
            </div>
            <div v-if="pagedOfficialApps.length" class="app-list">
              <div v-for="app in pagedOfficialApps" :key="app.clientId" class="app-item">
                <div class="app-left">
                  <el-avatar :size="48" :src="app.logoUrl" class="app-avatar">{{
                    app.clientName.slice(0, 1).toUpperCase()
                  }}</el-avatar>
                  <div class="app-info">
                    <div class="app-name-row">
                      <span class="app-name">{{ app.clientName }}</span
                      ><el-tag type="success" size="small">{{ t('ui.official.privacyTitle') }}</el-tag>
                    </div>
                    <div class="app-meta mono">{{ app.clientId }}</div>
                    <div class="scope-list">
                      <el-tag v-for="scope in app.scopes" :key="scope" size="small" effect="plain">{{
                        officialScopeLabel(scope)
                      }}</el-tag>
                    </div>
                    <div class="app-meta">
                      {{ t('ui.privacy.message012') }}{{ formatDateTime(app.lastAuthorizedAt) }}
                    </div>
                  </div>
                </div>
                <el-popconfirm
                  :title="t('ui.official.privacyRevoke')"
                  :confirm-button-text="t('ui.devices.message017')"
                  :cancel-button-text="t('ui.addPasskey.message009')"
                  @confirm="handleRevokeOfficial(app.clientId)"
                >
                  <template #reference
                    ><el-button
                      type="danger"
                      plain
                      size="small"
                      :loading="revokingId === `official:${app.clientId}`"
                      >{{ t('ui.devices.message017') }}</el-button
                    ></template
                  >
                </el-popconfirm>
              </div>
            </div>
            <el-empty
              v-else
              :description="
                officialApps.length ? t('ui.privacy.message016') : t('ui.official.privacyEmpty')
              "
            />
            <el-pagination
              v-if="filteredOfficialApps.length > PAGE_SIZE"
              v-model:current-page="officialPage"
              class="app-pagination"
              :page-size="PAGE_SIZE"
              :total="filteredOfficialApps.length"
              layout="prev, pager, next"
              small
              background
            />
          </template>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="oauthDetailDialogVisible"
      class="detail-dialog"
      :title="t('ui.privacy.message023')"
      width="720px"
      @closed="resetOauthDetailDialog"
    >
      <div v-if="oauthDetailApp" class="detail-shell">
        <div class="detail-head">
          <div class="detail-title">
            <el-avatar
              :size="56"
              :src="oauthDetailApp.logoUrl"
              :class="[
                'app-avatar',
                'detail-avatar',
                { 'app-avatar--image': !!oauthDetailApp.logoUrl },
              ]"
            >
              {{ oauthDetailApp.appName.slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div>
              <div class="detail-name">{{ oauthDetailApp.appName }}</div>
              <div class="detail-sub">
                {{ t('ui.privacy.message012')
                }}{{ formatDateTime(oauthDetailApp.lastAuthorizedAt) }}
              </div>
            </div>
          </div>
          <div class="detail-tags">
            <el-tag size="small" type="info" effect="plain">
              {{ creatorNameLabel(oauthDetailApp.creatorName) }}
            </el-tag>
            <el-tag
              v-if="oauthDetailApp.creatorVerificationType"
              size="small"
              :type="verificationTagType(oauthDetailApp.creatorVerificationType)"
              effect="plain"
            >
              {{ verificationLabel(oauthDetailApp.creatorVerificationType) }}
            </el-tag>
          </div>
        </div>

        <el-descriptions class="detail-desc" :column="1" border>
          <el-descriptions-item :label="t('ui.privacy.message024')">{{
            extractWebsite(oauthDetailApp.redirectUri)
          }}</el-descriptions-item>
          <el-descriptions-item :label="t('ui.privacy.message028')">{{
            oauthDetailApp.contactInfo
          }}</el-descriptions-item>
          <el-descriptions-item :label="t('ui.privacy.message025')">{{
            formatDateTime(oauthDetailApp.authorizedAt)
          }}</el-descriptions-item>
          <el-descriptions-item :label="t('ui.oAuthAuthorize.message019')">
            {{ grantModeLabel(oauthDetailApp.grantMode) }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="oauthDetailApp.grantMode === 'TIME_LIMITED' && oauthDetailApp.expiresAt"
            :label="t('ui.privacy.message026')"
          >
            {{ formatDateTime(oauthDetailApp.expiresAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message037')">
            <div class="scope-list">
              <el-tag
                v-for="scope in oauthDetailApp.scopes"
                :key="`${oauthDetailApp.appId}-${scope}`"
                size="small"
                effect="plain"
              >
                {{ oauthScopeLabel(scope) }}
              </el-tag>
            </div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else :description="t('ui.privacy.message027')" />
    </el-dialog>

    <el-card class="card data-card" shadow="never">
      <div class="card-title">
        <el-icon><Download /></el-icon>
        <span>{{ t('ui.privacy.message029') }}</span>
      </div>
      <p class="card-desc">{{ t('ui.privacy.message030') }}</p>

      <div class="data-actions">
        <el-alert
          :title="t('ui.privacy.message031')"
          type="warning"
          :description="t('ui.privacy.message032')"
          :closable="false"
          class="action-alert"
        />

        <div class="action-item">
          <div class="action-info">
            <div class="action-title">{{ t('ui.privacy.message033') }}</div>
            <div class="action-desc">{{ t('ui.privacy.message034') }}</div>
          </div>
          <el-button type="primary" plain :loading="downloading" @click="handleDownloadData">
            {{ t('ui.privacy.message035') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <div class="danger-zone-section">
      <DangerZoneCard :title="t('ui.devices.message027')" :icon="WarningFilled">
        <div class="danger-zone-item">
          <div class="danger-zone-item-left">
            <div class="danger-zone-item-title danger">{{ t('ui.privacy.message036') }}</div>
            <p class="danger-zone-item-desc">{{ t('ui.privacy.message037') }}</p>
          </div>
          <div class="danger-zone-item-right">
            <el-popconfirm
              :title="t('ui.privacy.message038')"
              :description="t('ui.privacy.message037')"
              :confirm-button-text="t('ui.privacy.message039')"
              :cancel-button-text="t('ui.addPasskey.message009')"
              @confirm="handleDeleteAccount"
            >
              <template #reference>
                <el-button type="danger" plain>{{ t('ui.loginOptions.message020') }}</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </DangerZoneCard>
    </div>

    <SensitiveVerificationDialog
      v-model="sensitiveDialogVisible"
      @success="handleSensitiveVerificationSuccess"
      @cancel="handleSensitiveVerificationCancel"
    />
  </div>
</template>

<script setup lang="ts">
import { intlLocale, t } from '@/i18n'

import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Lock, Monitor, Search, Share, WarningFilled } from '@element-plus/icons-vue'
import type { VerificationType } from '@/api/auth'
import {
  getOAuth2Authorizations,
  revokeOAuth2Authorization,
  type AuthorizationGrantMode,
  type OAuth2AuthorizedApp,
  type OAuth2Scope,
} from '@/api/oauth2'
import { checkSensitiveVerification } from '@/api/auth'
import {
  getOfficialAuthorizations,
  revokeOfficialAuthorization,
  officialScopeLabel,
  type OfficialAuthorization,
} from '@/api/official'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'
import { downloadPrivacyExportFile, fetchPrivacyExportData } from '@/api/privacyExport'
import SensitiveVerificationDialog from '@/components/SensitiveVerificationDialog.vue'
import DangerZoneCard from '@/components/DangerZoneCard.vue'

const loading = ref(false)
const { user } = storeToRefs(useUserStore())
const canCreateApps = computed(
  () =>
    ['ADMIN', 'SUPER_ADMIN'].includes(user.value?.systemRole ?? '') ||
    ['personal', 'enterprise'].includes(
      user.value?.developerVerificationType ?? user.value?.verificationType ?? '',
    ),
)
const officialApps = ref<OfficialAuthorization[]>([])
const officialLoading = ref(true)
const officialError = ref('')
const officialSearch = ref('')
const officialPage = ref(1)
const filteredOfficialApps = computed(() => {
  const query = officialSearch.value.trim().toLocaleLowerCase()
  return officialApps.value.filter((app) =>
    `${app.clientName} ${app.clientId}`.toLocaleLowerCase().includes(query),
  )
})
const pagedOfficialApps = computed(() =>
  filteredOfficialApps.value.slice(
    (officialPage.value - 1) * PAGE_SIZE,
    officialPage.value * PAGE_SIZE,
  ),
)
const revokingId = ref('')
const oauthApps = ref<OAuth2AuthorizedApp[]>([])
const PAGE_SIZE = 5
const oauthSearch = ref('')
const oauthPage = ref(1)
const filteredOauthApps = computed(() => {
  const query = oauthSearch.value.trim().toLocaleLowerCase()
  if (!query) return oauthApps.value
  return oauthApps.value.filter((app) =>
    `${app.appName} ${app.creatorName ?? ''} ${app.contactInfo ?? ''}`
      .toLocaleLowerCase()
      .includes(query),
  )
})
const pagedOauthApps = computed(() =>
  filteredOauthApps.value.slice((oauthPage.value - 1) * PAGE_SIZE, oauthPage.value * PAGE_SIZE),
)
const downloading = ref(false)
const sensitiveDialogVisible = ref(false)
const oauthDetailDialogVisible = ref(false)
const oauthDetailAppId = ref('')
let pendingSensitiveAction: (() => Promise<void>) | null = null

const oauthScopeLabel = (scope: OAuth2Scope) => {
  if (scope === 'email') return t('ui.login.message011')
  return t('ui.openPlatform.message050')
}

const creatorNameLabel = (value?: string) => {
  const normalized = value?.trim()
  return normalized ? normalized : 'Third-party'
}

const grantModeLabel = (mode?: AuthorizationGrantMode) => {
  if (mode === 'ONE_TIME') return t('ui.privacy.message040')
  if (mode === 'TIME_LIMITED') return t('ui.privacy.message041')
  return t('ui.privacy.message042')
}

const verificationLabel = (type?: VerificationType) => {
  if (type === 'admin') return t('ui.mainLayout.message017')
  if (type === 'enterprise') return t('ui.mainLayout.message018')
  if (type === 'personal') return t('ui.mainLayout.message019')
  return t('ui.openPlatform.message070')
}

const verificationTagType = (type?: VerificationType) => {
  if (type === 'admin') return 'danger'
  if (type === 'enterprise') return 'warning'
  if (type === 'personal') return 'success'
  return 'info'
}

const formatDateTime = (value: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(intlLocale.value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

const extractWebsite = (redirectUri: string) => {
  const raw = redirectUri?.trim() || ''
  if (!raw) return '-'
  try {
    const url = new URL(raw)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return '-'
    return url.host || '-'
  } catch {
    return '-'
  }
}

const oauthDetailApp = computed(() => {
  const appId = oauthDetailAppId.value
  if (!appId) return null
  return oauthApps.value.find((item) => item.appId === appId) ?? null
})

const openOauthDetailDialog = (appId: string) => {
  oauthDetailAppId.value = appId
  oauthDetailDialogVisible.value = true
}

const resetOauthDetailDialog = () => {
  oauthDetailAppId.value = ''
}

const loadAuthorizations = async () => {
  loading.value = true
  try {
    oauthApps.value = await getOAuth2Authorizations()
    oauthPage.value = Math.min(
      oauthPage.value,
      Math.max(1, Math.ceil(filteredOauthApps.value.length / PAGE_SIZE)),
    )
    if (oauthDetailDialogVisible.value && oauthDetailAppId.value && !oauthDetailApp.value) {
      resetOauthDetailDialog()
      oauthDetailDialogVisible.value = false
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('ui.privacy.message043'))
  } finally {
    loading.value = false
  }
}

const loadOfficialAuthorizations = async () => {
  officialLoading.value = true
  officialError.value = ''
  try {
    officialApps.value = await getOfficialAuthorizations()
  } catch (error) {
    officialError.value =
      error instanceof Error ? error.message : t('ui.official.privacyLoadFailed')
  } finally {
    officialLoading.value = false
  }
}

const handleRevokeOfficial = async (clientId: string) => {
  revokingId.value = `official:${clientId}`
  try {
    await revokeOfficialAuthorization(clientId)
    officialApps.value = officialApps.value.filter((app) => app.clientId !== clientId)
    officialPage.value = Math.min(
      officialPage.value,
      Math.max(1, Math.ceil(filteredOfficialApps.value.length / PAGE_SIZE)),
    )
    ElMessage.success(t('ui.official.privacyRevokeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('ui.privacy.message047'))
  } finally {
    revokingId.value = ''
  }
}

const handleRevokeOauth = async (appId: string) => {
  revokingId.value = `oauth:${appId}`
  try {
    await revokeOAuth2Authorization(appId)
    oauthApps.value = oauthApps.value.filter((item) => item.appId !== appId)
    oauthPage.value = Math.min(
      oauthPage.value,
      Math.max(1, Math.ceil(filteredOauthApps.value.length / PAGE_SIZE)),
    )
    ElMessage.success(t('ui.privacy.message044'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('ui.privacy.message045'))
  } finally {
    revokingId.value = ''
  }
}

const runWithSensitiveVerification = async (action: () => Promise<void>) => {
  try {
    const status = await checkSensitiveVerification()
    if (status.verified) {
      await action()
      return
    }

    pendingSensitiveAction = action
    sensitiveDialogVisible.value = true
    ElMessage.info(t('ui.mobileBridgeQrSidebarAction.message007'))
  } catch {
    pendingSensitiveAction = action
    sensitiveDialogVisible.value = true
    ElMessage.info(t('ui.mobileBridgeQrSidebarAction.message007'))
  }
}

const executeDataDownload = async () => {
  downloading.value = true
  try {
    const payload = await fetchPrivacyExportData()
    downloadPrivacyExportFile(payload)
    ElMessage.success(t('ui.privacy.message048'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('ui.privacy.message049'))
  } finally {
    downloading.value = false
  }
}

const handleDownloadData = async () => {
  if (downloading.value) return
  await runWithSensitiveVerification(executeDataDownload)
}

const handleSensitiveVerificationSuccess = async () => {
  const action = pendingSensitiveAction
  pendingSensitiveAction = null
  if (!action) return

  try {
    await action()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('ui.privacy.message050'))
  }
}

const handleSensitiveVerificationCancel = () => {
  pendingSensitiveAction = null
}

const handleDeleteAccount = () => {
  ElMessage.warning(t('ui.privacy.message051'))
}

onMounted(() => {
  void loadAuthorizations()
  void loadOfficialAuthorizations()
})
</script>

<style scoped>
.privacy-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  --privacy-card-bg: var(--el-fill-color-blank);
  --privacy-item-bg: var(--el-fill-color-light);
  --privacy-item-hover-shadow: 0 12px 24px rgba(15, 23, 42, 0.06);
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.app-access-banner a {
  color: var(--el-color-primary);
  font-weight: 600;
  text-decoration: none;
}

.app-access-banner a:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.overview-card,
.card {
  border-radius: 18px;
  border: 1px solid var(--el-border-color-light);
  background: var(--privacy-card-bg);
}

.overview-card :deep(.el-card__body),
.card :deep(.el-card__body) {
  background: transparent;
}

.overview-card {
  position: relative;
  overflow: hidden;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.overview-card:hover {
  border-color: var(--el-color-primary-light-7);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  transform: translateY(-2px);
}

.overview-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.overview-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 17px;
}

.overview-icon.internal {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.overview-icon.external {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.overview-icon.official {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.overview-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.overview-value {
  margin-top: 8px;
  font-size: 34px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.overview-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.privacy-overview-skeleton,
.privacy-card-skeleton {
  display: flex;
  flex-direction: column;
}

.privacy-overview-skeleton {
  gap: 10px;
}

.privacy-overview-label {
  width: 96px;
  height: 14px;
}

.privacy-overview-value {
  width: 54px;
  height: 36px;
}

.privacy-overview-desc {
  width: 80%;
  height: 14px;
}

.privacy-card-skeleton {
  gap: 14px;
}

.privacy-card-skeleton-head,
.privacy-app-skeleton,
.privacy-app-skeleton-main,
.privacy-app-actions {
  display: flex;
  align-items: center;
}

.privacy-card-skeleton-head {
  gap: 10px;
}

.privacy-title-copy,
.privacy-app-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.privacy-title-icon,
.privacy-app-avatar {
  flex-shrink: 0;
}

.privacy-title-icon {
  width: 20px;
  height: 20px;
}

.privacy-title-line {
  width: 120px;
  height: 16px;
}

.privacy-title-subline {
  width: 260px;
  max-width: 80%;
  height: 14px;
}

.privacy-app-skeleton {
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--privacy-item-bg);
}

.privacy-app-avatar {
  width: 48px;
  height: 48px;
}

.privacy-app-copy {
  min-width: 0;
}

.privacy-app-title {
  width: 180px;
  max-width: 100%;
  height: 16px;
}

.privacy-app-meta {
  width: 220px;
  max-width: 100%;
  height: 13px;
}

.privacy-app-actions {
  gap: 8px;
  flex-shrink: 0;
}

.privacy-action-btn {
  width: 58px;
  height: 28px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.app-count {
  margin-left: auto;
  min-width: 26px;
  justify-content: center;
  border-radius: 999px;
}

.card-desc {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.detail-tags {
  display: flex;
  gap: 8px;
  align-items: center;
}

.app-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 560px;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 2px 4px 2px 0;
  scrollbar-width: thin;
  scrollbar-color: var(--el-border-color) transparent;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  margin: 0 0 10px;
}

.app-search {
  max-width: 280px;
}

.app-search :deep(.el-input__wrapper) {
  min-height: 36px;
  border-radius: 10px;
}

.list-count {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.app-pagination {
  justify-content: center;
  margin-top: 14px;
}

.app-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: var(--privacy-item-bg);
  transition:
    border-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.app-item:hover {
  border-color: var(--el-color-primary-light-5);
  transform: translateY(-1px);
  box-shadow: var(--privacy-item-hover-shadow);
}

.app-left {
  display: flex;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.app-avatar {
  flex-shrink: 0;
  border-radius: 12px;
  background: linear-gradient(135deg, #1f9d6c, #3dbb88);
  color: #fff;
  font-weight: 700;
}

.app-avatar--image {
  background: transparent;
  color: var(--el-text-color-primary);
  border: 1px solid var(--el-border-color-lighter);
}

.app-avatar :deep(img) {
  object-fit: contain;
  background: transparent;
}

.app-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.app-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.app-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.scope-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.app-meta {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.detail-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.detail-avatar {
  border-radius: 14px;
}

.detail-name {
  font-size: 16px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.detail-sub {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.detail-desc :deep(.el-descriptions__label) {
  width: 120px;
}

.data-card {
  margin-top: 4px;
}

.data-card {
  box-shadow: 0 4px 14px color-mix(in srgb, var(--el-text-color-primary) 4%, transparent);
}

.danger-zone-section {
  margin-top: 16px;
}

.data-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-alert {
  margin: 0;
}

.action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--privacy-item-bg);
}

.action-info {
  flex: 1;
}

.action-title {
  margin-bottom: 4px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.action-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.action-desc.danger {
  color: var(--el-color-danger);
}

:deep(.el-divider--horizontal) {
  margin: 8px 0;
}

@media (max-width: 900px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .app-item,
  .action-item {
    flex-direction: column;
    align-items: stretch;
  }

  .app-actions {
    justify-content: flex-start;
  }

  .app-list {
    max-height: 460px;
  }

  .app-search {
    max-width: none;
    flex: 1;
  }
}
</style>

<style>
html.dark .privacy-page .app-item,
:root.dark .privacy-page .app-item {
  box-shadow: none;
}

html.dark .privacy-page .app-item:hover,
:root.dark .privacy-page .app-item:hover {
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.3);
}

html.dark .privacy-page .el-loading-mask,
:root.dark .privacy-page .el-loading-mask {
  background-color: rgba(0, 0, 0, 0.45) !important;
}
</style>
