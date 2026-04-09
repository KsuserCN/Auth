<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">设备与登录</h1>
        <p class="page-subtitle">查看和管理您已连接的设备</p>
      </div>
      <el-button @click="loadSessions" :loading="loading" :icon="Refresh"> 刷新 </el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Monitor />
            </el-icon>
            <span>已连接的设备</span>
            <el-tag
              v-if="sessions.length > 0"
              type="info"
              effect="plain"
              size="small"
              style="margin-left: 8px"
            >
              {{ sessions.length }} 个会话
            </el-tag>
          </div>

          <el-table
            :data="sessions"
            class="modern-table"
            size="small"
            v-loading="loading"
            :empty-text="loading ? '加载中...' : '暂无设备'"
          >
            <el-table-column label="设备" min-width="220">
              <template #default="{ row }">
                <div class="device-cell">
                  <el-icon class="device-icon" :class="getDeviceIconClass(row.deviceType)">
                    <component :is="getDeviceIcon(row.deviceType)" />
                  </el-icon>
                  <div class="device-details">
                    <div class="device-name">
                      {{ row.browser || '未知浏览器' }}
                      <el-tag
                        v-if="row.current"
                        type="success"
                        size="small"
                        effect="plain"
                        style="margin-left: 8px"
                      >
                        当前
                      </el-tag>
                    </div>
                    <div class="device-os">
                      {{ row.deviceType || '未知设备' }} · {{ row.ipAddress }}
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="ipLocation" label="位置" min-width="120">
              <template #default="{ row }">
                {{ row.ipLocation || '未知' }}
              </template>
            </el-table-column>
            <el-table-column
              label="最后活动"
              min-width="160"
              sortable
              :sort-method="sortByLastSeen"
            >
              <template #default="{ row }">
                {{ formatLastSeen(row.lastSeenAt) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.online ? 'success' : 'info'" size="small" effect="plain">
                  {{ row.online ? '在线' : '离线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.current"
                  text
                  type="danger"
                  size="small"
                  @click="handleRevoke(row)"
                  :loading="revokingIds.has(row.id)"
                >
                  撤销
                </el-button>
                <el-tooltip v-else content="无法撤销当前会话" placement="top">
                  <el-button text type="info" size="small" disabled> 撤销 </el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 危险区域 -->
    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24">
        <el-card class="card danger-card" shadow="never">
          <div class="card-title">
            <el-icon>
              <WarningFilled />
            </el-icon>
            <span>危险区域</span>
          </div>
          <div class="danger-content">
            <div class="danger-info">
              <h3 class="danger-title">从所有设备退出登录</h3>
              <p class="danger-desc">
                这将撤销您在所有设备上的登录状态，包括当前设备。您需要重新登录才能继续使用。
              </p>
            </div>
            <el-button type="danger" plain @click="handleLogoutAll" :loading="logoutAllLoading">
              退出所有设备
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, Cellphone, Iphone, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { getSessions, revokeSession, logoutAll, type SessionItem } from '@/api/auth'
import { useRouter } from 'vue-router'
import { clearAuthSession } from '@/utils/authSession'

const router = useRouter()
const sessions = ref<SessionItem[]>([])
const loading = ref(false)
const revokingIds = ref(new Set<number>())
const logoutAllLoading = ref(false)

// 加载会话列表
const loadSessions = async () => {
  try {
    loading.value = true
    sessions.value = await getSessions()
  } catch (error) {
    console.error('Failed to load sessions:', error)
    ElMessage.error('加载会话列表失败')
  } finally {
    loading.value = false
  }
}

// 撤销会话
const handleRevoke = async (session: SessionItem) => {
  try {
    await ElMessageBox.confirm(`确定要撤销此会话吗？该设备将被强制登出。`, '确认撤销', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    revokingIds.value.add(session.id)
    try {
      await revokeSession(session.id)
      ElMessage.success('会话已撤销')
      await loadSessions()
    } finally {
      revokingIds.value.delete(session.id)
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to revoke session:', error)
      ElMessage.error('撤销会话失败')
      revokingIds.value.delete(session.id)
    }
  }
}

// 获取设备图标
const getDeviceIcon = (deviceType: string | null) => {
  const type = (deviceType || '').toLowerCase()
  if (
    type.includes('android') ||
    type.includes('ios') ||
    type.includes('iphone') ||
    type.includes('mobile')
  ) {
    return Cellphone
  }
  if (type.includes('ipad') || type.includes('tablet')) {
    return Iphone
  }
  return Monitor
}

// 获取设备图标样式类
const getDeviceIconClass = (deviceType: string | null) => {
  const type = (deviceType || '').toLowerCase()
  if (type.includes('android')) return 'device-android'
  if (type.includes('ios') || type.includes('iphone') || type.includes('ipad')) return 'device-ios'
  if (type.includes('windows')) return 'device-windows'
  if (type.includes('mac')) return 'device-mac'
  if (type.includes('linux')) return 'device-linux'
  if (type.includes('bot')) return 'device-bot'
  return ''
}

// 格式化最后活动时间
const formatLastSeen = (lastSeenAt: string) => {
  const date = new Date(lastSeenAt)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 7) return `${days} 天前`

  return date.toLocaleString('zh-CN', {
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
    await ElMessageBox.confirm(
      '确定要从所有设备退出登录吗？这将撤销您在所有设备上的登录状态，包括当前设备。',
      '确认退出',
      {
        confirmButtonText: '确定退出',
        cancelButtonText: '取消',
        type: 'warning',
        distinguishCancelAndClose: true,
      },
    )

    logoutAllLoading.value = true
    try {
      await logoutAll()
      clearAuthSession()
      ElMessage.success('已从所有设备退出登录')
      // 延迟跳转，让用户看到成功消息
      setTimeout(() => {
        router.replace('/login')
      }, 1000)
    } catch (error) {
      console.error('Failed to logout all:', error)
      ElMessage.error('退出失败，请重试')
      logoutAllLoading.value = false
    }
  } catch (error) {
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

.device-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.device-icon {
  color: var(--el-color-primary);
  font-size: 20px;
  flex-shrink: 0;
}

.device-icon.device-android {
  color: #3ddc84;
}

.device-icon.device-ios {
  color: #007aff;
}

.device-icon.device-windows {
  color: #0078d4;
}

.device-icon.device-mac {
  color: #000000;
}

.device-icon.device-linux {
  color: #fcc624;
}

.device-icon.device-bot {
  color: #999999;
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

.danger-card {
  border-color: var(--el-color-danger-light-7);
  background: var(--el-color-danger-light-9);
}

.danger-card .card-title {
  color: var(--el-color-danger);
}

.danger-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.danger-info {
  flex: 1;
}

.danger-title {
  margin: 0 0 8px 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.danger-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

@media (max-width: 768px) {
  .danger-content {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
