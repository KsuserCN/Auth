<template>
  <div class="page">
    <el-container class="layout">
      <!-- 顶部导航栏 -->
      <el-header class="navbar">
        <div class="navbar-left">
          <el-button class="mobile-menu-trigger" circle @click="mobileMenuVisible = true">
            <el-icon><Menu /></el-icon>
          </el-button>
          <div class="brand">
            <img src="/favicon.ico" class="brand-logo" alt="logo" />
            <span class="brand-text">Ksuser 统一认证中心</span>
          </div>
        </div>
        <div class="navbar-right">
          <el-dropdown @command="handleThemeCommand" trigger="click">
            <el-button class="icon-btn">
              <el-icon>
                <Sunny v-if="currentThemeIcon === 'light'" />
                <Moon v-else-if="currentThemeIcon === 'dark'" />
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
          <el-divider direction="vertical" />
          <!-- 加载中：显示骨架屏 -->
          <div v-if="loading" class="user-skeleton">
            <el-skeleton-item variant="text" class="skeleton-avatar"
              style="width: 32px; height: 32px; min-width: 32px" />
            <div class="skeleton-text">
              <el-skeleton-item variant="text" style="width: 80px; height: 16px" />
              <el-skeleton-item variant="text" style="width: 140px; height: 14px" />
            </div>
          </div>
          <!-- 加载完成：显示用户信息 -->
          <div v-else class="user-chip">
            <el-avatar class="rounded-avatar" shape="square" :size="32" :src="user?.avatarUrl">
              {{ user?.username?.slice(0, 1) || 'K' }}
            </el-avatar>
            <div class="user-meta">
              <div class="user-name">
                {{ user?.username || '未登录' }}
                <el-tag v-if="verificationLabel" size="small" effect="plain" :type="verificationTagType"
                  class="verify-tag">
                  {{ verificationLabel }}
                </el-tag>
              </div>
              <div class="user-email">{{ user?.email || '—' }}</div>
            </div>
          </div>
        </div>
      </el-header>

      <el-container class="body">
        <!-- 侧边栏 -->
        <el-aside class="sidebar" width="240px">
          <div class="side-title">设置中心</div>
          <el-menu class="side-menu" :default-active="$route.path" router>
            <el-menu-item
              v-for="item in menuItems"
              :key="item.path"
              :index="item.path"
              :to="item.path"
            >
              <el-icon>
                <component :is="item.icon" />
              </el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </el-menu>

          <div class="side-footer">
            <MobileBridgeQrSidebarAction />
            <el-button class="logout-btn" type="danger" plain :loading="logoutLoading" :disabled="logoutLoading"
              @click="handleLogout">
              <el-icon>
                <SwitchButton />
              </el-icon>
              <span>退出登录</span>
            </el-button>
          </div>
        </el-aside>

        <!-- 主内容 -->
        <el-main class="content">
          <router-view />
        </el-main>
      </el-container>
    </el-container>

    <el-drawer
      v-model="mobileMenuVisible"
      class="mobile-nav-drawer"
      direction="ltr"
      size="78%"
      :with-header="false"
    >
      <div class="side-title mobile-side-title">设置中心</div>
      <el-menu
        class="side-menu mobile-side-menu"
        :default-active="currentPath"
        @select="handleMobileMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon>
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <div class="side-footer mobile-side-footer">
        <MobileBridgeQrSidebarAction />
        <el-button
          class="logout-btn"
          type="danger"
          plain
          :loading="logoutLoading"
          :disabled="logoutLoading"
          @click="handleLogout"
        >
          <el-icon>
            <SwitchButton />
          </el-icon>
          <span>退出登录</span>
        </el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useDark, useStorage } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import {
  Connection,
  DataLine,
  HomeFilled,
  Key,
  Lock,
  Menu,
  Monitor,
  Moon,
  Setting,
  Sunny,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { logout } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'
import { clearAuthSession } from '@/utils/authSession'
import MobileBridgeQrSidebarAction from '@/components/MobileBridgeQrSidebarAction.vue'

const userStore = useUserStore()
const { user, loading } = storeToRefs(userStore)
const logoutLoading = ref(false)
const router = useRouter()
const route = useRoute()
const mobileMenuVisible = ref(false)

const menuItems = [
  { path: '/home/overview', label: '概览', icon: HomeFilled },
  { path: '/home/profile', label: '基本信息', icon: User },
  { path: '/home/security', label: '安全性', icon: Lock },
  { path: '/home/login-options', label: '登录选项', icon: Key },
  { path: '/home/devices', label: '设备与登录', icon: Monitor },
  { path: '/home/privacy', label: '隐私与数据', icon: DataLine },
  { path: '/home/preferences', label: '偏好设置', icon: Setting },
  { path: '/home/open-platform', label: '开放平台', icon: Connection },
]

const currentPath = computed(() => route.path)

// 使用 VueUse 的 useDark 和 useStorage 实现持久化主题
const themeMode = useStorage<'light' | 'dark' | 'system'>('theme-mode', 'system')
const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})

// 计算当前主题图标
const currentThemeIcon = computed(() => {
  if (themeMode.value === 'light') return 'light'
  if (themeMode.value === 'dark') return 'dark'
  return 'system'
})

const verificationLabel = computed(() => {
  const type = user.value?.verificationType
  if (type === 'admin') return '管理员'
  if (type === 'enterprise') return '企业认证'
  if (type === 'personal') return '个人认证'
  return ''
})

const verificationTagType = computed(() => {
  const type = user.value?.verificationType
  if (type === 'admin') return 'danger'
  if (type === 'enterprise') return 'warning'
  if (type === 'personal') return 'success'
  return 'info'
})

const handleThemeCommand = (command: 'light' | 'dark' | 'system') => {
  themeMode.value = command
}

const handleMobileMenuSelect = (path: string) => {
  mobileMenuVisible.value = false
  if (path !== route.path) {
    router.push(path)
  }
}

const handleLogout = async () => {
  try {
    logoutLoading.value = true

    // 调用退出登录接口
    await logout()

    // 清除用户信息
    userStore.clearUser()

    clearAuthSession()

    ElMessage.success('退出登录成功')

    // 跳转到登录页面
    router.push('/login')
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err)
    ElMessage.error(message || '退出登录失败')
    logoutLoading.value = false
  }
}

// 监听主题模式变化并应用
watch(
  themeMode,
  (mode) => {
    if (mode === 'system') {
      // 跟随系统，让 useDark 自动处理
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
      isDark.value = prefersDark
    } else {
      // 手动设置
      isDark.value = mode === 'dark'
    }
  },
  { immediate: true },
)

onMounted(() => {
  userStore.fetchUserInfo()
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
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
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
  min-width: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mobile-menu-trigger {
  display: none;
  border: 1px solid var(--el-border-color-light);
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
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-email {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.verify-tag {
  transform: translateY(-1px);
}

.user-skeleton {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
  animation: pulse 2s infinite;
}

.user-skeleton :deep(.el-skeleton__item) {
  background: var(--el-skeleton-color, rgba(0, 0, 0, 0.06));
}

:root.dark .user-skeleton :deep(.el-skeleton__item) {
  background: rgba(255, 255, 255, 0.12);
}

.skeleton-avatar {
  border-radius: 6px !important;
}

.skeleton-text {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.skeleton-text :deep(.el-skeleton__item) {
  background: var(--el-skeleton-color, rgba(0, 0, 0, 0.06));
  border-radius: 4px;
}

:root.dark .skeleton-text :deep(.el-skeleton__item) {
  background: rgba(255, 255, 255, 0.12);
}

@keyframes pulse {

  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.6;
  }
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
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mobile-side-title {
  padding: 4px 8px 8px;
}

.mobile-side-footer {
  padding: 8px 0 0;
}

.logout-btn {
  width: 100%;
  height: 40px;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.logout-btn :deep(.el-icon) {
  margin-right: 8px;
  font-size: 18px;
}

.content {
  padding: 24px;
  background: var(--el-bg-color-page);
  overflow: auto;
}

:deep(.el-button) {
  transition:
    background 0.2s ease,
    color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

:deep(.el-button):not(.is-disabled):hover {
  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.08);
}

.icon-btn:hover {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.rounded-avatar :deep(.el-avatar__img) {
  border-radius: 6px !important;
}

@media (max-width: 960px) {
  .sidebar {
    width: 200px;
  }
}

@media (max-width: 720px) {
  .navbar {
    padding: 0 16px;
    height: 56px;
  }

  .body {
    height: calc(100% - 56px);
  }

  .mobile-menu-trigger {
    display: inline-flex;
  }

  .brand-text {
    font-size: 14px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .user-meta {
    display: none;
  }

  .sidebar {
    display: none;
  }

  .content {
    padding: 12px;
  }

  .navbar-right :deep(.el-divider) {
    margin: 0 2px;
  }

  .navbar-right {
    gap: 8px;
  }

  .theme-label {
    display: none;
  }

  :deep(.mobile-nav-drawer .el-drawer__body) {
    display: flex;
    flex-direction: column;
    padding: 12px;
  }

  .mobile-side-menu {
    border-right: none;
  }

  .mobile-side-footer {
    margin-top: auto;
  }
}

@media (max-width: 480px) {
  .brand-text {
    display: none;
  }
}
</style>
