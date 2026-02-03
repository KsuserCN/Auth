<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">账号概览</h1>
        <p class="page-subtitle">集中管理您的账户、设备与安全设置</p>
      </div>
      <el-button class="primary-btn">
        <el-icon>
          <EditPen />
        </el-icon>
        编辑资料
      </el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12" :lg="8">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <UserIcon />
            </el-icon>
            <span>个人资料</span>
          </div>
          <el-descriptions :column="1" class="desc">
            <el-descriptions-item label="用户名">
              {{ user?.username || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              {{ user?.email || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="UUID">
              {{ user?.uuid || '—' }}
            </el-descriptions-item>
          </el-descriptions>
          <div class="card-actions">
            <el-button class="primary-ghost" plain>管理资料</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12" :lg="8">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Lock />
            </el-icon>
            <span>安全状态</span>
          </div>
          <el-statistic :value="92" suffix="%" class="stat" />
          <el-progress :percentage="92" :stroke-width="10" :color="progressColor" />
          <div class="hint">建议开启双重验证以提升安全级别</div>
          <div class="card-actions">
            <el-button class="primary-ghost" plain>查看建议</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12" :lg="8">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Key />
            </el-icon>
            <span>登录方式</span>
          </div>
          <div class="tag-list">
            <el-tag type="warning" effect="light">密码</el-tag>
            <el-tag type="warning" effect="light">邮箱验证码</el-tag>
            <el-tag type="warning" effect="light">Passkey</el-tag>
          </div>
          <div class="hint">最近 30 天无异常登录</div>
          <div class="card-actions">
            <el-button class="primary-ghost" plain>管理方式</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24" :lg="16">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Monitor />
            </el-icon>
            <span>设备与会话</span>
          </div>
          <el-table :data="devices" class="device-table" size="small">
            <el-table-column prop="name" label="设备" />
            <el-table-column prop="location" label="位置" />
            <el-table-column prop="lastSeen" label="最近活动" />
            <el-table-column prop="status" label="状态">
              <template #default="scope">
                <el-badge :value="scope.row.status" type="warning" class="badge" />
              </template>
            </el-table-column>
          </el-table>
          <div class="card-actions">
            <el-button class="primary-ghost" plain>管理设备</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Cloudy />
            </el-icon>
            <span>存储与同步</span>
          </div>
          <div class="storage">
            <div class="storage-value">
              <span class="storage-amount">8.4</span>
              <span class="storage-unit">GB / 15 GB</span>
            </div>
            <el-progress :percentage="56" :stroke-width="10" :color="progressColor" />
            <div class="hint">本周上传 1.2 GB</div>
          </div>
          <div class="card-actions">
            <el-button class="primary-ghost" plain>升级套餐</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24" :md="12">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <CollectionTag />
            </el-icon>
            <span>最近活动</span>
          </div>
          <el-timeline>
            <el-timeline-item type="warning" timestamp="今天 10:40">
              新设备登录验证成功
            </el-timeline-item>
            <el-timeline-item type="warning" timestamp="昨天 21:12">
              修改了安全选项
            </el-timeline-item>
            <el-timeline-item type="warning" timestamp="2 天前">
              启用了邮箱验证码登录
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Connection />
            </el-icon>
            <span>隐私与数据</span>
          </div>
          <div class="hint">我们建议您定期检查数据权限与授权应用</div>
          <el-button class="primary-ghost" plain>查看授权应用</el-button>
          <el-divider />
          <div class="hint">数据下载与删除请求可在此处理</div>
          <el-button class="primary-ghost" plain>管理数据</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-alert v-if="error" type="warning" :closable="false" class="error-banner" show-icon>
      {{ error }}
    </el-alert>

    <el-skeleton v-if="loading" :rows="4" animated class="skeleton" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  Cloudy,
  CollectionTag,
  Connection,
  EditPen,
  Key,
  Lock,
  Monitor,
  User as UserIcon
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'

const userStore = useUserStore()
const { user, loading, error } = storeToRefs(userStore)

const devices = ref([
  { name: 'MacBook Pro · Chrome', location: '上海', lastSeen: '今天 10:40', status: '当前' },
  { name: 'iPhone 15 · Safari', location: '北京', lastSeen: '昨天 21:12', status: '已验证' },
  { name: 'Windows · Edge', location: '深圳', lastSeen: '2 天前', status: '已验证' }
])

// 进度条使用主题色
const progressColor = getComputedStyle(document.documentElement).getPropertyValue('--el-color-primary').trim()

onMounted(() => {
  // 如果 store 中还没有用户信息，则获取
  if (!user.value) {
    userStore.fetchUserInfo()
  }
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

.primary-btn {
  background: linear-gradient(135deg,
      var(--el-color-primary) 0%,
      var(--el-color-primary-light-3) 100%);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-weight: 600;
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
  margin-bottom: 12px;
}

.desc {
  margin-bottom: 8px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.primary-ghost {
  border-radius: 10px;
  border: 1px solid var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

:deep(.el-button) {
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

:deep(.el-button):not(.is-disabled):hover {
  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.08);
}

.primary-btn:hover {
  background: linear-gradient(135deg,
      var(--el-color-primary-dark-2) 0%,
      var(--el-color-primary-light-3) 100%);
  color: #fff;
}

.primary-ghost:hover {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.stat {
  margin-bottom: 8px;
}

.hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tag-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.row-gap {
  margin-top: 16px;
}

.device-table :deep(.el-table__inner-wrapper) {
  border-radius: 12px;
}

.badge :deep(.el-badge__content) {
  background: var(--el-color-primary);
  border: none;
}

.storage {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.storage-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.storage-amount {
  font-size: 28px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.storage-unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.error-banner {
  margin-top: 16px;
}

.skeleton {
  margin-top: 12px;
}
</style>
