<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">安全性</h1>
        <p class="page-subtitle">管理您的账户安全设置和登录认证方式</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="16">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Lock />
            </el-icon>
            <span>两步验证</span>
          </div>
          <div class="security-list">
            <div class="security-item">
              <div class="item-left">
                <div class="item-header">
                  <span class="item-title">邮箱验证码</span>
                </div>
                <p class="item-desc">使用发送到您邮箱的验证码进行登录</p>
              </div>
              <div class="item-right">
                <el-tag type="success">已启用</el-tag>
                <el-button plain size="small">管理</el-button>
              </div>
            </div>
            <el-divider />
            <div class="security-item">
              <div class="item-left">
                <div class="item-header">
                  <span class="item-title">Passkey（生物识别）</span>
                </div>
                <p class="item-desc">使用生物识别或设备密码进行快速安全登录</p>
              </div>
              <div class="item-right">
                <el-tag type="info">未启用</el-tag>
                <el-button type="primary" plain size="small">启用</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Key />
            </el-icon>
            <span>密码管理</span>
          </div>
          <div class="password-info">
            <div class="info-item">
              <span class="info-label">密码强度</span>
              <el-progress :percentage="80" :color="progressColor" />
            </div>
            <div class="info-item">
              <span class="info-label">上次修改</span>
              <span class="info-value">45 天前</span>
            </div>
          </div>
          <el-button type="primary" class="change-pwd-btn">修改密码</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Monitor />
            </el-icon>
            <span>活跃会话</span>
          </div>
          <el-table :data="sessions" class="modern-table" size="small"
            :default-sort="{ prop: 'lastActive', order: 'descending' }">
            <el-table-column prop="device" label="设备" min-width="200">
              <template #default="{ row }">
                <div class="device-info">
                  <el-icon class="device-icon">
                    <Monitor />
                  </el-icon>
                  <span>{{ row.device }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="location" label="位置" min-width="100" />
            <el-table-column prop="lastActive" label="最后活动" min-width="120" sortable />
            <el-table-column label="操作" width="100" align="right">
              <template #default="{ row }">
                <el-popconfirm title="确认退出此会话？" confirm-button-text="确认" cancel-button-text="取消"
                  @confirm="handleSessionLogout(row)">
                  <template #reference>
                    <el-button text type="danger" size="small">退出</el-button>
                  </template>
                </el-popconfirm>
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
import { ElMessage } from 'element-plus'
import {
  Key,
  Lock,
  Monitor,
} from '@element-plus/icons-vue'

const sessions = ref([
  { device: 'MacBook Pro (Chrome)', location: '上海', lastActive: '现在' },
  { device: 'iPhone 15 (Safari)', location: '北京', lastActive: '10 分钟前' },
  { device: 'Windows (Edge)', location: '深圳', lastActive: '2 小时前' },
])

const progressColor = getComputedStyle(document.documentElement).getPropertyValue('--el-color-primary').trim()

const handleSessionLogout = (row: any) => {
  ElMessage.success(`已退出会话：${row.device}`)
  const index = sessions.value.findIndex(s => s.device === row.device)
  if (index > -1) {
    sessions.value.splice(index, 1)
  }
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
  margin-bottom: 16px;
  font-size: 16px;
}

.security-list {
  display: flex;
  flex-direction: column;
}

.security-item {
  padding: 16px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.item-left {
  flex: 1;
}

.item-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 16px;
}

.item-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.item-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.item-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.password-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.info-value {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.change-pwd-btn {
  width: 100%;
}

.row-gap {
  margin-top: 16px;
}

.modern-table {
  border-radius: 8px;
}

.device-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.device-icon {
  color: var(--el-color-primary);
  font-size: 16px;
}

:deep(.el-divider--horizontal) {
  margin: 0;
  background-color: var(--el-border-color-light);
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

:deep(.el-button) {
  transition: all 0.2s ease;
}
</style>
