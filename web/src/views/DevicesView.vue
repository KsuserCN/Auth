<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">{{ t('ui.mainLayout.message013') }}</h1>
        <p class="page-subtitle">{{ t('ui.devices.message001') }}</p>
      </div>
      <el-button @click="loadSessions" :loading="loading" :icon="Refresh">
        {{ t('ui.devices.message002') }}
      </el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Monitor />
            </el-icon>
            <span>{{ t('ui.devices.message003') }}</span>
            <el-tag
              v-if="sessions.length > 0"
              type="info"
              effect="plain"
              size="small"
              style="margin-left: 8px"
            >
              {{ sessions.length }} {{ t('ui.devices.message004') }}
            </el-tag>
          </div>

          <el-skeleton :loading="loading" animated>
            <template #template>
              <div class="table-skeleton">
                <div class="table-skeleton-header">
                  <el-skeleton-item variant="text" class="skeleton-head-lg" />
                  <el-skeleton-item variant="text" class="skeleton-head-sm" />
                  <el-skeleton-item variant="text" class="skeleton-head-md" />
                  <el-skeleton-item variant="text" class="skeleton-head-xs" />
                </div>
                <div v-for="index in 4" :key="index" class="table-skeleton-row">
                  <div class="table-skeleton-device">
                    <el-skeleton-item variant="circle" class="skeleton-device-icon" />
                    <div class="table-skeleton-device-meta">
                      <el-skeleton-item variant="text" class="skeleton-device-title" />
                      <el-skeleton-item variant="text" class="skeleton-device-subtitle" />
                    </div>
                  </div>
                  <el-skeleton-item variant="text" class="skeleton-cell-sm" />
                  <el-skeleton-item variant="text" class="skeleton-cell-md" />
                  <el-skeleton-item variant="text" class="skeleton-cell-xs" />
                </div>
              </div>
            </template>
            <template #default>
              <el-table
                :data="sessions"
                class="modern-table"
                size="small"
                :empty-text="t('ui.devices.message005')"
              >
                <el-table-column :label="t('ui.devices.message006')" min-width="220">
                  <template #default="{ row }">
                    <div class="device-cell">
                      <div class="device-logo" :class="getDeviceLogoTone(row)" aria-hidden="true">
                        <div class="device-logo-slot">
                          <el-icon
                            v-if="getClientKind(row) === 'desktop-app'"
                            class="device-app-icon"
                            :aria-label="t('ui.devices.message007')"
                          >
                            <Monitor />
                          </el-icon>
                          <el-icon
                            v-else-if="getClientKind(row) === 'mobile-app'"
                            class="device-app-icon"
                            :aria-label="t('ui.devices.message008')"
                          >
                            <Cellphone />
                          </el-icon>
                          <i
                            v-else-if="getBrowserLogoClass(row)"
                            class="device-fa-icon"
                            :class="getBrowserLogoClass(row)!"
                            aria-hidden="true"
                          ></i>
                        </div>
                        <div class="device-logo-slot">
                          <i
                            v-if="getSystemLogoClass(row)"
                            class="device-fa-icon device-fa-icon--secondary"
                            :class="getSystemLogoClass(row)!"
                            aria-hidden="true"
                          ></i>
                        </div>
                      </div>

                      <div class="device-details">
                        <div class="device-name">
                          {{ getClientDisplayName(row) }}
                          <el-tag
                            v-if="row.current"
                            type="success"
                            size="small"
                            effect="plain"
                            style="margin-left: 8px"
                          >
                            {{ t('ui.devices.message009') }}
                          </el-tag>
                        </div>
                        <div class="device-os">
                          {{ getSystemDisplayName(row) }} · {{ row.ipAddress }}
                        </div>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column
                  prop="ipLocation"
                  :label="t('ui.accountRecoveryCard.message015')"
                  min-width="120"
                >
                  <template #default="{ row }">
                    {{ row.ipLocation || t('ui.devices.message010') }}
                  </template>
                </el-table-column>
                <el-table-column
                  :label="t('ui.devices.message011')"
                  min-width="160"
                  sortable
                  :sort-method="sortByLastSeen"
                >
                  <template #default="{ row }">
                    {{ formatLastSeen(row.lastSeenAt) }}
                  </template>
                </el-table-column>
                <el-table-column :label="t('ui.devices.message012')" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.online ? 'success' : 'info'" size="small" effect="plain">
                      {{ row.online ? t('ui.devices.message013') : t('ui.devices.message014') }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="t('ui.devices.message015')" width="140" align="right">
                  <template #default="{ row }">
                    <el-button text type="primary" size="small" @click="openDetails(row)">
                      {{ t('ui.devices.message016') }}
                    </el-button>
                    <el-button
                      v-if="!row.current"
                      text
                      type="danger"
                      size="small"
                      @click="handleRevoke(row)"
                      :loading="revokingIds.has(row.id)"
                    >
                      {{ t('ui.devices.message017') }}
                    </el-button>
                    <el-tooltip v-else :content="t('ui.devices.message018')" placement="top">
                      <el-button text type="info" size="small" disabled>
                        {{ t('ui.devices.message017') }}
                      </el-button>
                    </el-tooltip>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </el-skeleton>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="detailsVisible"
      :title="t('ui.devices.message019')"
      width="520px"
      align-center
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="t('ui.devices.message020')">
          {{
            getClientKind(detailsSession) === 'desktop-app'
              ? t('ui.devices.message007')
              : getClientKind(detailsSession) === 'mobile-app'
                ? t('ui.devices.message008')
                : t('ui.devices.message021')
          }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('ui.devices.message022')">
          {{ detailsSession ? getClientDisplayName(detailsSession) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('ui.devices.message023')">
          {{ getClientVersion(detailsSession) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('ui.devices.message024')">
          {{ getSystemDisplayName(detailsSession) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('ui.devices.message025')">
          {{ getSystemVersion(detailsSession) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="IP">
          {{ detailsSession?.ipAddress || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('ui.accountRecoveryCard.message015')">
          {{ detailsSession?.ipLocation || t('ui.devices.message010') }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('ui.devices.message026')">
          {{ detailsSession?.lastSeenAt ? formatLastSeen(detailsSession.lastSeenAt) : '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailsVisible = false">{{
          t('ui.accountRecoveryCard.message017')
        }}</el-button>
      </template>
    </el-dialog>

    <!-- 危险区域 -->
    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24">
        <DangerZoneCard :title="t('ui.devices.message027')" :icon="WarningFilled">
          <div class="danger-zone-item">
            <div class="danger-zone-item-left">
              <div class="danger-zone-item-title">{{ t('ui.devices.message028') }}</div>
              <p class="danger-zone-item-desc">
                {{ t('ui.devices.message029') }}
              </p>
            </div>
            <div class="danger-zone-item-right">
              <el-button type="danger" plain @click="handleLogoutAll" :loading="logoutAllLoading">
                {{ t('ui.devices.message030') }}
              </el-button>
            </div>
          </div>
        </DangerZoneCard>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { localizedMessageBox } from '@/utils/localizedMessageBox'
import { intlLocale, relativeTime, t, uiMessage } from '@/i18n'

import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Monitor, Cellphone, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { getSessions, revokeSession, logoutAll, type SessionItem } from '@/api/auth'
import { useRouter } from 'vue-router'
import { clearAuthSession } from '@/utils/authSession'
import DangerZoneCard from '@/components/DangerZoneCard.vue'

const router = useRouter()
const sessions = ref<SessionItem[]>([])
const loading = ref(false)
const revokingIds = ref(new Set<number>())
const logoutAllLoading = ref(false)

const detailsVisible = ref(false)
const detailsSession = ref<SessionItem | null>(null)

// 加载会话列表
const loadSessions = async () => {
  try {
    loading.value = true
    sessions.value = await getSessions()
  } catch (error) {
    console.error('Failed to load sessions:', error)
    ElMessage.error(t('ui.devices.message031'))
  } finally {
    loading.value = false
  }
}

// 撤销会话
const handleRevoke = async (session: SessionItem) => {
  try {
    await localizedMessageBox.confirm(
      uiMessage('ui.devices.message032'),
      uiMessage('ui.devices.message033'),
      {
        confirmButtonText: uiMessage('ui.devices.message034'),
        cancelButtonText: uiMessage('ui.addPasskey.message009'),
        type: 'warning',
      },
    )

    revokingIds.value.add(session.id)
    try {
      await revokeSession(session.id)
      ElMessage.success(t('ui.devices.message035'))
      await loadSessions()
    } finally {
      revokingIds.value.delete(session.id)
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to revoke session:', error)
      ElMessage.error(t('ui.devices.message036'))
      revokingIds.value.delete(session.id)
    }
  }
}

const openDetails = (session: SessionItem) => {
  detailsSession.value = session
  detailsVisible.value = true
}

type ClientKind = 'desktop-app' | 'mobile-app' | 'web'

const getClientKind = (session: SessionItem | null | undefined): ClientKind => {
  const ua = (session?.userAgent || '').toLowerCase()
  if (ua.includes('ksuserauthdesktop')) return 'desktop-app'
  if (ua.includes('ksuserauthmobile')) return 'mobile-app'
  return 'web'
}

const getDeviceLogoTone = (session: SessionItem) => {
  const kind = getClientKind(session)
  if (kind === 'desktop-app') return 'tone-desktop'
  if (kind === 'mobile-app') return 'tone-mobile'
  return 'tone-web'
}

const getBrowserDisplayName = (session: SessionItem | null | undefined) => {
  const raw = (session?.browser || session?.userAgent || '').toLowerCase()
  if (raw.includes('edge') || raw.includes('edg/')) return 'Edge'
  if (raw.includes('firefox')) return 'Firefox'
  if (raw.includes('opera') || raw.includes('opr/')) return 'Opera'
  if (raw.includes('chrome')) return 'Chrome'
  if (raw.includes('safari')) return 'Safari'
  if (raw.includes('ie') || raw.includes('trident')) return 'Internet Explorer'
  return session?.browser || t('ui.devices.message037')
}

const getBrowserVersion = (session: SessionItem | null | undefined) => {
  const ua = session?.userAgent || ''
  const lower = ua.toLowerCase()
  const pick = (re: RegExp) => {
    const m = ua.match(re)
    return m?.[1] || null
  }

  if (lower.includes('edg/')) return pick(/Edg\/([0-9.]+)/)
  if (lower.includes('opr/')) return pick(/OPR\/([0-9.]+)/)
  if (lower.includes('firefox/')) return pick(/Firefox\/([0-9.]+)/)
  if (lower.includes('chrome/') && !lower.includes('edg/') && !lower.includes('opr/')) {
    return pick(/Chrome\/([0-9.]+)/)
  }
  // Safari 的真实版本通常在 Version/x.y.z
  if (lower.includes('safari/') && lower.includes('version/') && !lower.includes('chrome/')) {
    return pick(/Version\/([0-9.]+)/)
  }
  return null
}

const getSystemDisplayName = (session: SessionItem | null | undefined) => {
  const ua = (session?.userAgent || session?.deviceType || '').toLowerCase()
  if (ua.includes('android')) return 'Android'
  if (ua.includes('iphone') || ua.includes('ipad') || ua.includes('ios')) return 'iOS'
  if (ua.includes('windows')) return 'Windows'
  if (ua.includes('mac os') || ua.includes('macintosh') || ua.includes('mac')) return 'macOS'
  if (ua.includes('linux') || ua.includes('x11')) return 'Linux'
  if (ua.includes('cros') || ua.includes('chromeos')) return 'ChromeOS'
  if (getClientKind(session) === 'desktop-app') return t('ui.devices.message007')
  if (getClientKind(session) === 'mobile-app') return t('ui.devices.message008')
  return session?.deviceType || t('ui.devices.message038')
}

const getSystemVersion = (session: SessionItem | null | undefined) => {
  const ua = session?.userAgent || ''
  const lower = ua.toLowerCase()
  const pick = (re: RegExp) => {
    const m = ua.match(re)
    return m?.[1] || null
  }

  if (lower.includes('windows nt')) return pick(/Windows NT ([0-9.]+)/)
  if (lower.includes('android')) return pick(/Android ([0-9.]+)/)
  if (lower.includes('iphone') || lower.includes('ipad')) {
    const m = ua.match(/OS ([0-9_]+)/)
    return m?.[1] ? m[1].split('_').join('.') : null
  }
  if (lower.includes('mac os x')) {
    const m = ua.match(/Mac OS X ([0-9_]+)/)
    return m?.[1] ? m[1].split('_').join('.') : null
  }
  return null
}

const getBrowserLogoClass = (session: SessionItem) => {
  const value = (session.browser || session.userAgent || '').toLowerCase()
  if (value.includes('edge') || value.includes('edg/')) return 'fa-brands fa-edge'
  if (value.includes('firefox')) return 'fa-brands fa-firefox'
  if (value.includes('opera') || value.includes('opr/')) return 'fa-brands fa-opera'
  // Safari UA 里也可能带 Chrome，所以尽量先排除 Chrome/Edge/Opera
  if (
    value.includes('safari') &&
    !value.includes('chrome') &&
    !value.includes('edg/') &&
    !value.includes('opr/')
  ) {
    return 'fa-brands fa-safari'
  }
  if (value.includes('chrome')) return 'fa-brands fa-chrome'
  if (value.includes('ie') || value.includes('trident')) return 'fa-brands fa-internet-explorer'
  return null
}

const getSystemLogoClass = (session: SessionItem) => {
  const device = (session.userAgent || session.deviceType || '').toLowerCase()
  if (device.includes('windows')) return 'fa-brands fa-windows'
  if (device.includes('android')) return 'fa-brands fa-android'
  // macOS / iPhone / iPad 统一用 Apple
  if (device.includes('iphone') || device.includes('ipad') || device.includes('ios'))
    return 'fa-brands fa-apple'
  if (device.includes('mac os') || device.includes('macintosh') || device.includes('mac'))
    return 'fa-brands fa-apple'
  return null
}

const getClientDisplayName = (session: SessionItem) => {
  const kind = getClientKind(session)
  if (kind === 'desktop-app') return t('ui.devices.message007')
  if (kind === 'mobile-app') return t('ui.devices.message008')
  return getBrowserDisplayName(session)
}

const getClientVersion = (session: SessionItem | null | undefined) => {
  if (!session) return null
  const kind = getClientKind(session)
  if (kind === 'web') return getBrowserVersion(session)

  const ua = session.userAgent || ''
  if (kind === 'desktop-app') {
    const m =
      ua.match(/KsuserAuthDesktop\/([0-9.]+)/i) || ua.match(/KsuserAuthDesktop\s+([0-9.]+)/i)
    return m?.[1] || null
  }
  if (kind === 'mobile-app') {
    const m = ua.match(/KsuserAuthMobile\/([0-9.]+)/i) || ua.match(/KsuserAuthMobile\s+([0-9.]+)/i)
    return m?.[1] || null
  }
  return null
}

// 格式化最后活动时间
const formatLastSeen = (lastSeenAt: string) => {
  const date = new Date(lastSeenAt)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return t('ui.devices.message039')
  if (minutes < 60) return relativeTime(-minutes, 'minute')
  if (hours < 24) return relativeTime(-hours, 'hour')
  if (days < 7) return relativeTime(-days, 'day')

  return date.toLocaleString(intlLocale.value, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// 排序方法
const sortByLastSeen = (a: SessionItem, b: SessionItem) => {
  return new Date(b.lastSeenAt).getTime() - new Date(a.lastSeenAt).getTime()
}

// 从所有设备退出登录
const handleLogoutAll = async () => {
  try {
    await localizedMessageBox.confirm(
      uiMessage('ui.devices.message043'),
      uiMessage('ui.devices.message044'),
      {
        confirmButtonText: uiMessage('ui.devices.message045'),
        cancelButtonText: uiMessage('ui.addPasskey.message009'),
        type: 'warning',
        distinguishCancelAndClose: true,
      },
    )

    logoutAllLoading.value = true
    try {
      await logoutAll()
      clearAuthSession()
      ElMessage.success(t('ui.devices.message046'))
      // 延迟跳转，让用户看到成功消息
      setTimeout(() => {
        router.replace('/login')
      }, 1000)
    } catch (error) {
      console.error('Failed to logout all:', error)
      ElMessage.error(t('ui.devices.message047'))
      logoutAllLoading.value = false
    }
  } catch (_error) {
    // 用户取消操作
  }
}

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 6px 0 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 16px;
  font-size: 16px;
}

.modern-table {
  border-radius: 8px;
}

.table-skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.table-skeleton-header,
.table-skeleton-row {
  display: grid;
  grid-template-columns: minmax(220px, 2.4fr) minmax(100px, 1fr) minmax(130px, 1.1fr) 84px;
  gap: 16px;
  align-items: center;
}

.table-skeleton-header {
  padding-bottom: 8px;
}

.table-skeleton-row {
  padding: 14px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.table-skeleton-device {
  display: flex;
  align-items: center;
  gap: 12px;
}

.table-skeleton-device-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.skeleton-device-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.skeleton-head-lg,
.skeleton-device-title {
  width: 72%;
  height: 14px;
}

.skeleton-head-md,
.skeleton-cell-md {
  width: 78%;
  height: 14px;
}

.skeleton-head-sm,
.skeleton-cell-sm {
  width: 64%;
  height: 14px;
}

.skeleton-head-xs,
.skeleton-cell-xs {
  width: 52px;
  height: 14px;
}

.skeleton-device-subtitle {
  width: 88%;
  height: 12px;
}

.device-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.device-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-shrink: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
}

.device-logo.tone-desktop {
  color: var(--el-color-primary);
}

.device-logo.tone-mobile {
  color: var(--el-color-success);
}

.device-logo.tone-web {
  color: var(--el-text-color-primary);
}

.device-app-icon {
  font-size: 22px;
}

.device-logo-slot {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.device-fa-icon {
  font-size: 18px;
  line-height: 1;
}

.device-fa-icon--secondary {
  opacity: 0.75;
  font-size: 16px;
}

.device-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.device-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.device-os {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.badge :deep(.el-badge__content) {
  background: var(--el-color-primary);
  border: none;
}

:deep(.el-table) {
  background: transparent;
}

:deep(.el-table__header) {
  background: var(--el-fill-color-light);
}

:deep(.el-table td) {
  border-color: var(--el-border-color-light);
}

.row-gap {
  margin-top: 16px;
}

@media (max-width: 768px) {
  .table-skeleton-header,
  .table-skeleton-row {
    grid-template-columns: 1.8fr 0.9fr 1fr;
  }

  .table-skeleton-header > :last-child,
  .table-skeleton-row > :last-child {
    display: none;
  }
}
</style>
