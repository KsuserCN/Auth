<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">隐私与数据</h1>
        <p class="page-subtitle">管理您的数据、隐私和授权应用</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Share />
            </el-icon>
            <span>授权应用</span>
          </div>
          <p class="card-desc">您已授权以下应用访问您的账户信息</p>

          <div class="app-list">
            <div v-for="app in apps" :key="app.id" class="app-item">
              <div class="app-left">
                <div class="app-avatar">
                  <el-avatar :size="48" :src="app.avatar" />
                </div>
                <div class="app-info">
                  <div class="app-name">{{ app.name }}</div>
                  <div class="app-perm">已访问：{{ app.permissions }}</div>
                  <div class="app-time">授权于：{{ app.authorizedAt }}</div>
                </div>
              </div>
              <el-button text type="danger" size="small">撤销</el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Download />
            </el-icon>
            <span>数据管理</span>
          </div>
          <p class="card-desc">导出或删除您的账户数据</p>

          <div class="data-actions">
            <el-alert title="数据安全" type="warning" description="请妥善保管已导出的数据，不要与他人分享。" :closable="false"
              class="action-alert" />

            <div class="action-item">
              <div class="action-info">
                <div class="action-title">下载您的数据</div>
                <div class="action-desc">获取您账户的完整数据备份（JSON 格式）</div>
              </div>
              <el-button type="primary" plain>下载</el-button>
            </div>

            <el-divider />

            <div class="action-item">
              <div class="action-info">
                <div class="action-title">删除账户</div>
                <div class="action-desc danger">此操作不可撤销，请谨慎选择</div>
              </div>
              <el-popconfirm title="确认删除账户？" description="删除后所有数据将被永久清除，此操作无法撤销。" confirm-button-text="我已理解，删除"
                cancel-button-text="取消" @confirm="handleDeleteAccount">
                <template #reference>
                  <el-button type="danger" plain>删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Share, Download } from '@element-plus/icons-vue'

const apps = ref([
  { id: 1, name: 'GitHub', avatar: 'https://avatars.githubusercontent.com/u/1?v=4', permissions: '用户信息、邮箱', authorizedAt: '2024-01-15' },
  { id: 2, name: 'Google', avatar: 'https://www.google.com/favicon.ico', permissions: '用户信息、日历', authorizedAt: '2024-01-10' },
])

const handleDeleteAccount = () => {
  ElMessage.warning('账户删除功能需要额外验证，请查收您的邮件')
}
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
  margin-bottom: 8px;
  font-size: 16px;
}

.card-desc {
  margin: 0 0 16px 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.app-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.app-item {
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: all 0.2s ease;
}

.app-item:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary-light-5);
}

.app-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.app-avatar :deep(.el-avatar) {
  border-radius: 8px;
}

.app-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.app-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.app-perm {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.app-time {
  font-size: 11px;
  color: var(--el-text-color-disabled);
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
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.action-info {
  flex: 1;
}

.action-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
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
</style>
