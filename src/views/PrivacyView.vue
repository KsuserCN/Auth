<template>
  <div class="privacy-page">
    <div class="content-header">
      <div>
        <h1 class="page-title">访问授权</h1>
        <p class="page-subtitle">管理已授权应用及其可访问的账号信息</p>
      </div>
    </div>

    <div v-if="loading" class="overview-grid">
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
        <div class="overview-label">Ksuser 应用</div>
        <div class="overview-value">{{ ssoApps.length }}</div>
        <div class="overview-desc">Ksuser内部应用，用于证明您的身份</div>
      </el-card>

      <el-card class="overview-card" shadow="never">
        <div class="overview-label">第三方应用</div>
        <div class="overview-value">{{ oauthApps.length }}</div>
        <div class="overview-desc">第三方外部应用，用于第三方网站登录</div>
      </el-card>
    </div>

    <el-row v-if="loading" :gutter="16">
      <el-col v-for="index in 2" :key="index" :xs="24" :lg="12">
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
      <el-col :xs="24" :lg="12">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon><Monitor /></el-icon>
            <span>Ksuser 应用</span>
          </div>
          <p class="card-desc">已授权后再次发起同范围授权时会自动回调</p>

          <div v-if="ssoApps.length" class="app-list">
            <div v-for="app in ssoApps" :key="app.clientId" class="app-item">
              <div class="app-left">
                <el-avatar
                  :size="48"
                  :src="app.logoUrl"
                  :class="['app-avatar', { 'app-avatar--image': !!app.logoUrl }]"
                >
                  {{ app.clientName.slice(0, 1).toUpperCase() }}
                </el-avatar>
                <div class="app-info">
                  <div class="app-name-row">
                    <span class="app-name">{{ app.clientName }}</span>
                    <el-tag size="small" type="success" effect="plain">Ksuser</el-tag>
                    <el-tag size="small" effect="plain">{{ grantModeLabel(app.grantMode) }}</el-tag>
                  </div>
                  <div class="app-meta">最近授权：{{ formatDateTime(app.lastAuthorizedAt) }}</div>
                  <div v-if="app.grantMode === 'TIME_LIMITED' && app.expiresAt" class="app-meta">
                    授权有效期至：{{ formatDateTime(app.expiresAt) }}
                  </div>
                </div>
              </div>

              <div class="app-actions">
                <el-button size="small" @click="openSsoDetailDialog(app.clientId)">详情</el-button>
                <el-popconfirm
                  title="撤销该 Ksuser 应用授权？"
                  description="撤销后，下次登录该应用需要重新确认授权。"
                  confirm-button-text="撤销"
                  cancel-button-text="取消"
                  @confirm="handleRevokeSso(app.clientId)"
                >
                  <template #reference>
                    <el-button type="danger" plain size="small" :loading="revokingId === `sso:${app.clientId}`">
                      撤销
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </div>

          <el-empty v-else description="暂无已授权的 Ksuser 应用" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon><Share /></el-icon>
            <span>第三方应用</span>
          </div>
          <p class="card-desc">这些应用通过 Ksuser OAuth2.0 访问您的公开资料，撤销后将无法继续使用现有授权</p>

          <div v-if="oauthApps.length" class="app-list">
            <div v-for="app in oauthApps" :key="app.appId" class="app-item">
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
                    <el-tag size="small" type="info" effect="plain">{{ creatorNameLabel(app.creatorName) }}</el-tag>
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
                  <div class="app-meta">最近授权：{{ formatDateTime(app.lastAuthorizedAt) }}</div>
                  <div v-if="app.grantMode === 'TIME_LIMITED' && app.expiresAt" class="app-meta">
                    授权有效期至：{{ formatDateTime(app.expiresAt) }}
                  </div>
                </div>
              </div>

              <div class="app-actions">
                <el-button size="small" @click="openOauthDetailDialog(app.appId)">详情</el-button>
                <el-popconfirm
                  title="撤销该第三方应用授权？"
                  description="撤销后，该应用需要重新请求授权才能再次访问您的账号信息。"
                  confirm-button-text="撤销"
                  cancel-button-text="取消"
                  @confirm="handleRevokeOauth(app.appId)"
                >
                  <template #reference>
                    <el-button type="danger" plain size="small" :loading="revokingId === `oauth:${app.appId}`">
                      撤销
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </div>

          <el-empty v-else description="暂无已授权的第三方应用" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="ssoDetailDialogVisible"
      class="detail-dialog"
      title="应用详情"
      width="720px"
      @closed="resetSsoDetailDialog"
    >
      <div v-if="ssoDetailApp" class="detail-shell">
        <div class="detail-head">
          <div class="detail-title">
            <el-avatar
              :size="56"
              :src="ssoDetailApp.logoUrl"
              :class="['app-avatar', 'detail-avatar', { 'app-avatar--image': !!ssoDetailApp.logoUrl }]"
            >
              {{ ssoDetailApp.clientName.slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div>
              <div class="detail-name">{{ ssoDetailApp.clientName }}</div>
              <div class="detail-sub">最近授权：{{ formatDateTime(ssoDetailApp.lastAuthorizedAt) }}</div>
            </div>
          </div>
          <el-tag size="small" type="success" effect="plain">Ksuser</el-tag>
        </div>

        <el-descriptions class="detail-desc" :column="1" border>
          <el-descriptions-item label="网站">{{ extractWebsite(ssoDetailApp.redirectUri) }}</el-descriptions-item>
          <el-descriptions-item label="首次授权">{{ formatDateTime(ssoDetailApp.authorizedAt) }}</el-descriptions-item>
          <el-descriptions-item label="授权方式">
            {{ grantModeLabel(ssoDetailApp.grantMode) }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="ssoDetailApp.grantMode === 'TIME_LIMITED' && ssoDetailApp.expiresAt"
            label="授权有效期至"
          >
            {{ formatDateTime(ssoDetailApp.expiresAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="权限范围">
            <div class="scope-list">
              <el-tag
                v-for="scope in ssoDetailApp.scopes"
                :key="`${ssoDetailApp.clientId}-${scope}`"
                size="small"
                effect="plain"
              >
                {{ ssoScopeLabel(scope) }}
              </el-tag>
            </div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else description="应用不存在或已被撤销" />
    </el-dialog>

    <el-dialog
      v-model="oauthDetailDialogVisible"
      class="detail-dialog"
      title="应用详情"
      width="720px"
      @closed="resetOauthDetailDialog"
    >
      <div v-if="oauthDetailApp" class="detail-shell">
        <div class="detail-head">
          <div class="detail-title">
            <el-avatar
              :size="56"
              :src="oauthDetailApp.logoUrl"
              :class="['app-avatar', 'detail-avatar', { 'app-avatar--image': !!oauthDetailApp.logoUrl }]"
            >
              {{ oauthDetailApp.appName.slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div>
              <div class="detail-name">{{ oauthDetailApp.appName }}</div>
              <div class="detail-sub">最近授权：{{ formatDateTime(oauthDetailApp.lastAuthorizedAt) }}</div>
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
          <el-descriptions-item label="网站">{{ extractWebsite(oauthDetailApp.redirectUri) }}</el-descriptions-item>
          <el-descriptions-item label="开发者">{{ oauthDetailApp.contactInfo }}</el-descriptions-item>
          <el-descriptions-item label="首次授权">{{ formatDateTime(oauthDetailApp.authorizedAt) }}</el-descriptions-item>
          <el-descriptions-item label="授权方式">
            {{ grantModeLabel(oauthDetailApp.grantMode) }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="oauthDetailApp.grantMode === 'TIME_LIMITED' && oauthDetailApp.expiresAt"
            label="授权有效期至"
          >
            {{ formatDateTime(oauthDetailApp.expiresAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="权限范围">
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
      <el-empty v-else description="应用不存在或已被撤销" />
    </el-dialog>

	    <el-card class="card data-card" shadow="never">
	      <div class="card-title">
	        <el-icon><Download /></el-icon>
	        <span>数据管理</span>
	      </div>
      <p class="card-desc">导出或删除您的账户数据</p>

      <div class="data-actions">
        <el-alert
          title="数据安全"
          type="warning"
          description="请妥善保管已导出的数据，不要与他人分享。"
          :closable="false"
          class="action-alert"
        />

	        <div class="action-item">
	          <div class="action-info">
	            <div class="action-title">下载您的数据</div>
	            <div class="action-desc">获取您账户的完整数据备份（JSON 格式）</div>
	          </div>
	          <el-button type="primary" plain :loading="downloading" @click="handleDownloadData">
	            下载
	          </el-button>
	        </div>
	      </div>
	    </el-card>

	    <div class="danger-zone-section">
	      <DangerZoneCard title="危险操作区域" :icon="WarningFilled">
	        <div class="danger-zone-item">
	          <div class="danger-zone-item-left">
	            <div class="danger-zone-item-title danger">删除账户</div>
	            <p class="danger-zone-item-desc">删除后所有数据将被永久清除，此操作无法撤销。</p>
	          </div>
	          <div class="danger-zone-item-right">
	            <el-popconfirm
	              title="确认删除账户？"
	              description="删除后所有数据将被永久清除，此操作无法撤销。"
	              confirm-button-text="我已理解，删除"
	              cancel-button-text="取消"
	              @confirm="handleDeleteAccount"
	            >
	              <template #reference>
	                <el-button type="danger" plain>删除</el-button>
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
	import { computed, onMounted, ref } from 'vue'
	import { ElMessage } from 'element-plus'
	import { Download, Monitor, Share, WarningFilled } from '@element-plus/icons-vue'
import type { VerificationType } from '@/api/auth'
import {
  getOAuth2Authorizations,
  revokeOAuth2Authorization,
  type AuthorizationGrantMode,
  type OAuth2AuthorizedApp,
  type OAuth2Scope,
} from '@/api/oauth2'
import {
  getSSOAuthorizations,
  revokeSSOAuthorization,
  type SSOAuthorizedClient,
  type SSOScope,
} from '@/api/sso'
import { checkSensitiveVerification } from '@/api/auth'
import { downloadPrivacyExportFile, fetchPrivacyExportData } from '@/api/privacyExport'
	import SensitiveVerificationDialog from '@/components/SensitiveVerificationDialog.vue'
	import DangerZoneCard from '@/components/DangerZoneCard.vue'

const loading = ref(false)
const revokingId = ref('')
const oauthApps = ref<OAuth2AuthorizedApp[]>([])
const ssoApps = ref<SSOAuthorizedClient[]>([])
const downloading = ref(false)
const sensitiveDialogVisible = ref(false)
const ssoDetailDialogVisible = ref(false)
const oauthDetailDialogVisible = ref(false)
const ssoDetailClientId = ref('')
const oauthDetailAppId = ref('')
let pendingSensitiveAction: (() => Promise<void>) | null = null

const oauthScopeLabel = (scope: OAuth2Scope) => {
  if (scope === 'email') return '邮箱地址'
  return '昵称与头像'
}

const ssoScopeLabel = (scope: SSOScope) => {
  if (scope === 'openid') return '基础身份标识'
  if (scope === 'email') return '邮箱地址'
  return '昵称与头像'
}

const creatorNameLabel = (value?: string) => {
  const normalized = value?.trim()
  return normalized ? normalized : 'Third-party'
}

const grantModeLabel = (mode?: AuthorizationGrantMode) => {
  if (mode === 'ONE_TIME') return '一次性'
  if (mode === 'TIME_LIMITED') return '限时'
  return '长期'
}

const verificationLabel = (type?: VerificationType) => {
  if (type === 'admin') return '管理员'
  if (type === 'enterprise') return '企业认证'
  if (type === 'personal') return '个人认证'
  return '未认证'
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
  return new Intl.DateTimeFormat('zh-CN', {
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

const ssoDetailApp = computed(() => {
  const clientId = ssoDetailClientId.value
  if (!clientId) return null
  return ssoApps.value.find((item) => item.clientId === clientId) ?? null
})

const oauthDetailApp = computed(() => {
  const appId = oauthDetailAppId.value
  if (!appId) return null
  return oauthApps.value.find((item) => item.appId === appId) ?? null
})

const openSsoDetailDialog = (clientId: string) => {
  ssoDetailClientId.value = clientId
  ssoDetailDialogVisible.value = true
}

const openOauthDetailDialog = (appId: string) => {
  oauthDetailAppId.value = appId
  oauthDetailDialogVisible.value = true
}

const resetSsoDetailDialog = () => {
  ssoDetailClientId.value = ''
}

const resetOauthDetailDialog = () => {
  oauthDetailAppId.value = ''
}

const loadAuthorizations = async () => {
  loading.value = true
  try {
    const [oauth, sso] = await Promise.all([getOAuth2Authorizations(), getSSOAuthorizations()])
    oauthApps.value = oauth
    ssoApps.value = sso
    if (ssoDetailDialogVisible.value && ssoDetailClientId.value && !ssoDetailApp.value) {
      resetSsoDetailDialog()
      ssoDetailDialogVisible.value = false
    }
    if (oauthDetailDialogVisible.value && oauthDetailAppId.value && !oauthDetailApp.value) {
      resetOauthDetailDialog()
      oauthDetailDialogVisible.value = false
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载授权应用失败')
  } finally {
    loading.value = false
  }
}

const handleRevokeOauth = async (appId: string) => {
  revokingId.value = `oauth:${appId}`
  try {
    await revokeOAuth2Authorization(appId)
    oauthApps.value = oauthApps.value.filter((item) => item.appId !== appId)
    ElMessage.success('第三方应用授权已撤销')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤销第三方应用失败')
  } finally {
    revokingId.value = ''
  }
}

const handleRevokeSso = async (clientId: string) => {
  revokingId.value = `sso:${clientId}`
  try {
    await revokeSSOAuthorization(clientId)
    ssoApps.value = ssoApps.value.filter((item) => item.clientId !== clientId)
    ElMessage.success('Ksuser 应用授权已撤销')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤销 Ksuser 应用失败')
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
    ElMessage.info('需要验证身份')
  } catch (error) {
    pendingSensitiveAction = action
    sensitiveDialogVisible.value = true
    ElMessage.info('需要验证身份')
  }
}

const executeDataDownload = async () => {
  downloading.value = true
  try {
    const payload = await fetchPrivacyExportData()
    downloadPrivacyExportFile(payload)
    ElMessage.success('数据已下载')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败，请稍后重试')
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
    ElMessage.error(error instanceof Error ? error.message : '执行失败，请稍后重试')
  }
}

const handleSensitiveVerificationCancel = () => {
  pendingSensitiveAction = null
}

const handleDeleteAccount = () => {
  ElMessage.warning('账户删除功能需要额外验证，请查收您的邮件')
}

onMounted(() => {
  void loadAuthorizations()
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

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
  gap: 12px;
}

.app-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: var(--privacy-item-bg);
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
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
