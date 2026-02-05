<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">登录选项</h1>
        <p class="page-subtitle">管理您的登录凭据和认证方式</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Lock />
            </el-icon>
            <span>账户信息</span>
          </div>
          <div class="info-list">
            <!-- 邮箱 -->
            <div class="info-row" @click="handleChangeEmail">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Message />
                </el-icon>
                <span class="row-label">邮箱</span>
              </div>
              <div class="row-right">
                <span class="row-value">{{ userEmail }}</span>
                <el-icon class="row-arrow">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>

            <!-- 密码 -->
            <div class="info-row" @click="handleChangePassword">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Key />
                </el-icon>
                <span class="row-label">密码</span>
              </div>
              <div class="row-right">
                <span class="row-value">••••••••</span>
                <el-icon class="row-arrow">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  Key,
  Lock,
  Message,
  ArrowRight,
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const userEmail = computed(() => userStore.user?.email || '—')

onMounted(async () => {
  await userStore.fetchUserInfo()
})

const handleChangeEmail = () => {
  ElMessage.info('邮箱更改功能开发中')
}

const handleChangePassword = () => {
  router.push('/change-password')
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

/* 信息列表样式 */
.info-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 0 -20px;
  padding: 0;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  border-bottom: 1px solid var(--el-border-color-light);
  user-select: none;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row:hover {
  background-color: var(--el-fill-color-light);
}

.row-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.row-icon {
  font-size: 18px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.row-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  min-width: 80px;
}

.row-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: flex-end;
}

.row-value {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: right;
  flex: 1;
  word-break: break-all;
}

.row-arrow {
  font-size: 16px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.info-row:hover .row-arrow {
  color: var(--el-color-primary);
}
</style>
