<template>
  <div class="page">
    <el-container class="layout">
      <!-- 顶部导航栏 -->
      <el-header class="navbar">
        <div class="navbar-left">
          <div class="brand">
            <img src="/favicon.ico" class="brand-logo" alt="logo" />
            <span class="brand-text">账号管理</span>
          </div>
        </div>
        <div class="navbar-right">
          <el-dropdown @command="handleThemeCommand" trigger="click">
            <el-button class="icon-btn">
              <el-icon>
                <Sunny v-if="themeMode === 'light'" />
                <Moon v-else-if="themeMode === 'dark'" />
                <Monitor v-else />
              </el-icon>
              <span class="theme-label">外观</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="light">光亮模式</el-dropdown-item>
                <el-dropdown-item command="dark">暗黑模式</el-dropdown-item>
                <el-dropdown-item command="system">跟随浏览器</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-tooltip content="通知" placement="bottom">
            <el-button circle class="icon-btn">
              <el-icon>
                <Bell />
              </el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="帮助" placement="bottom">
            <el-button circle class="icon-btn">
              <el-icon>
                <QuestionFilled />
              </el-icon>
            </el-button>
          </el-tooltip>
          <el-divider direction="vertical" />
          <div class="user-chip">
            <el-avatar :size="32" :src="user?.avatarUrl">
              {{ user?.username?.slice(0, 1) || 'U' }}
            </el-avatar>
            <div class="user-meta">
              <div class="user-name">{{ user?.username || '未登录' }}</div>
              <div class="user-email">{{ user?.email || '—' }}</div>
            </div>
          </div>
        </div>
      </el-header>

      <el-container class="body">
        <!-- 侧边栏 -->
        <el-aside class="sidebar" width="240px">
          <div class="side-title">设置中心</div>
          <el-menu class="side-menu" :default-active="activeMenu" router>
            <el-menu-item index="overview">
              <el-icon>
                <HomeFilled />
              </el-icon>
              <span>概览</span>
            </el-menu-item>
            <el-menu-item index="security">
              <el-icon>
                <Lock />
              </el-icon>
              <span>安全性</span>
            </el-menu-item>
            <el-menu-item index="devices">
              <el-icon>
                <Monitor />
              </el-icon>
              <span>设备与登录</span>
            </el-menu-item>
            <el-menu-item index="privacy">
              <el-icon>
                <DataLine />
              </el-icon>
              <span>隐私与数据</span>
            </el-menu-item>
            <el-menu-item index="preferences">
              <el-icon>
                <Setting />
              </el-icon>
              <span>偏好设置</span>
            </el-menu-item>
          </el-menu>

          <div class="side-footer">
            <el-button class="ghost-btn" plain>
              <el-icon>
                <SwitchButton />
              </el-icon>
              退出登录
            </el-button>
          </div>
        </el-aside>

        <!-- 主内容 -->
        <el-main class="content">
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
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import {
  Bell,
  Cloudy,
  CollectionTag,
  Connection,
  DataLine,
  EditPen,
  HomeFilled,
  Key,
  Lock,
  Monitor,
  Moon,
  QuestionFilled,
  Setting,
  Sunny,
  SwitchButton,
  User as UserIcon
} from '@element-plus/icons-vue'
import { getUserInfo, type User } from '@/api/auth'

const user = ref<User | null>(null)
const loading = ref(true)
const error = ref('')
const activeMenu = ref('overview')
const themeMode = ref<'light' | 'dark' | 'system'>('system')
let themeMedia: MediaQueryList | null = null

// 进度条使用主题色
const progressColor = getComputedStyle(document.documentElement).getPropertyValue('--el-color-primary').trim()

const devices = ref([
  { name: 'MacBook Pro · Chrome', location: '上海', lastSeen: '今天 10:40', status: '当前' },
  { name: 'iPhone 15 · Safari', location: '北京', lastSeen: '昨天 21:12', status: '已验证' },
  { name: 'Windows · Edge', location: '深圳', lastSeen: '2 天前', status: '已验证' }
])

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getUserInfo()
    user.value = res as User
  } catch (err: any) {
    error.value = err?.message || '获取用户信息失败'
  } finally {
    loading.value = false
  }
}

const applyTheme = (mode: 'light' | 'dark' | 'system') => {
  themeMode.value = mode
  const prefersDark = themeMedia?.matches ?? false
  const isDark = mode === 'dark' || (mode === 'system' && prefersDark)
  document.documentElement.classList.toggle('dark', isDark)
}

const handleThemeCommand = (command: 'light' | 'dark' | 'system') => {
  applyTheme(command)
}

const handleThemeMediaChange = () => {
  if (themeMode.value === 'system') {
    applyTheme('system')
  }
}

onMounted(() => {
  load()
  themeMedia = window.matchMedia('(prefers-color-scheme: dark)')
  applyTheme(themeMode.value)
  themeMedia.addEventListener('change', handleThemeMediaChange)
})

onBeforeUnmount(() => {
  if (themeMedia) {
    themeMedia.removeEventListener('change', handleThemeMediaChange)
  }
})
</script>

<style scoped>
:global(html),
:global(body),
:global(#app) {
  height: 100%;
  margin: 0;
  background: var(--el-bg-color-page);
}

.page {
  height: 100%;
  background: var(--el-bg-color-page);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.layout {
  height: 100%;
}

.navbar {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 8px;
}

.brand-text {
  font-size: 16px;
}

.theme-label {
  margin-left: 6px;
  font-size: 13px;
  color: inherit;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-btn {
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.user-email {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.body {
  height: calc(100% - 64px);
}

.sidebar {
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
  padding: 20px 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.side-title {
  padding: 8px 16px 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.8px;
}

.side-menu {
  border-right: none;
  background: transparent;
}

.side-menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  border-radius: 8px;
  margin: 2px 0;
  padding: 0 16px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.side-menu :deep(.el-menu-item .el-icon) {
  font-size: 18px;
  margin-right: 12px;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 500;
}

.side-menu :deep(.el-menu-item:hover) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  transform: translateX(2px);
}

.side-footer {
  margin-top: auto;
  padding: 8px;
}

.ghost-btn {
  width: 100%;
  height: 40px;
  justify-content: center;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-regular);
  font-size: 14px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.ghost-btn:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color);
}

.content {
  padding: 24px;
  background: var(--el-bg-color-page);
  overflow: auto;
}

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

.ghost-btn:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.icon-btn:hover {
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


@media (max-width: 960px) {
  .sidebar {
    width: 200px;
  }
}

@media (max-width: 720px) {
  .navbar {
    padding: 0 16px;
  }

  .user-meta {
    display: none;
  }

  .sidebar {
    display: none;
  }
}
</style>
