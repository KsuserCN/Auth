<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">设备与登录</h1>
        <p class="page-subtitle">查看和管理您已连接的设备</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Monitor />
            </el-icon>
            <span>已连接的设备</span>
          </div>

          <el-table :data="devices" class="modern-table" size="small">
            <el-table-column label="设备" min-width="200">
              <template #default="{ row }">
                <div class="device-cell">
                  <el-icon class="device-icon">
                    <Monitor />
                  </el-icon>
                  <div class="device-details">
                    <div class="device-name">{{ row.name }}</div>
                    <div class="device-os">{{ row.os }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="location" label="位置" min-width="100" />
            <el-table-column prop="lastActive" label="最后活动" min-width="120" sortable />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-badge :value="row.status" type="success" class="badge" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="right">
              <template #default>
                <el-button text type="primary" size="small">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Monitor } from '@element-plus/icons-vue'

const devices = ref([
  { name: 'MacBook Pro', os: 'macOS 14.2', location: '上海', lastActive: '现在', status: '活跃' },
  { name: 'iPhone 15 Pro', os: 'iOS 17.2', location: '北京', lastActive: '30 分钟前', status: '活跃' },
  { name: 'iPad Air', os: 'iPadOS 17.2', location: '深圳', lastActive: '2 小时前', status: '活跃' },
])
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
</style>
