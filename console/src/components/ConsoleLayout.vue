<template>
  <div class="console-shell">
    <aside class="sidebar">
      <RouterLink to="/users" class="brand"
        ><img class="brand-icon" src="https://static.ksuser.cn/favicon.png" alt="Ksuser 标志" />
        <span>Ksuser<span class="brand-sub">Console</span></span></RouterLink
      >
      <span class="nav-caption">管理中心</span>
      <nav>
        <RouterLink to="/users"
          ><el-icon><User /></el-icon>用户管理</RouterLink
        ><RouterLink to="/applications"
          ><el-icon><Grid /></el-icon>官方应用</RouterLink
        ><RouterLink to="/user-applications"
          ><el-icon><Collection /></el-icon>认证用户应用</RouterLink
        ><RouterLink to="/unionid-applications"
          ><el-icon><Connection /></el-icon>UnionID 审核</RouterLink
        ><RouterLink to="/audit"
          ><el-icon><Document /></el-icon>操作记录</RouterLink
        >
      </nav>
      <div class="sidebar-note"><span class="status-dot"></span>超级管理员专用</div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <span>Ksuser 通行证 · 管理中心</span>
        <div class="account">
          <el-dropdown trigger="click" @command="handleThemeCommand">
            <el-button class="theme-button" text aria-label="切换主题">
              <el-icon>
                <Sunny v-if="currentThemeIcon === 'light'" />
                <Moon v-else-if="currentThemeIcon === 'dark'" />
                <Monitor v-else />
              </el-icon>
              <span>主题</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="light">浅色</el-dropdown-item>
                <el-dropdown-item command="dark">暗黑</el-dropdown-item>
                <el-dropdown-item command="system">跟随系统</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-avatar
            class="account-avatar"
            :size="30"
            :src="store.session?.user.avatarUrl?.trim() || undefined"
            >{{ avatarFallback(store.session?.user.username) }}</el-avatar
          ><span>{{ store.session?.user.username }}</span
          ><el-tag size="small" type="primary" effect="plain">超级管理员</el-tag
          ><el-button text :loading="loggingOut" @click="logout">退出</el-button>
        </div>
      </header>
      <main><RouterView /></main>
    </div>
  </div>
  <ConsoleSensitiveVerificationDialog />
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Grid, Collection, Connection, Document, Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { consoleApi } from '@/api/console'
import { avatarFallback } from '@/utils/avatar'
import { useSessionStore } from '@/stores/session'
import { clearConsoleNavigation } from '@/utils/navigation'
import ConsoleSensitiveVerificationDialog from '@/components/ConsoleSensitiveVerificationDialog.vue'
import { currentThemeIcon, setThemeMode, type ConsoleThemeMode } from '@/utils/theme'
const store = useSessionStore()
const router = useRouter()
const loggingOut = ref(false)
const handleThemeCommand = (mode: ConsoleThemeMode) => setThemeMode(mode)
const logout = async () => {
  loggingOut.value = true
  try {
    await consoleApi.logout()
    clearConsoleNavigation()
    store.clear()
    await router.replace('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退出失败')
  } finally {
    loggingOut.value = false
  }
}
</script>
<style scoped>
.console-shell {
  display: flex;
  min-height: 100vh;
}
.sidebar {
  width: 226px;
  flex-shrink: 0;
  background: var(--console-surface);
  border-right: 1px solid var(--console-border);
  padding: 32px 18px;
  display: flex;
  flex-direction: column;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--console-text);
  font-size: 23px;
  font-weight: 700;
  margin: 0 10px 45px;
}
.brand-icon {
  width: 38px;
  height: 38px;
  object-fit: contain;
  flex-shrink: 0;
}
.brand-sub {
  display: block;
  font-size: 12px;
  letter-spacing: 1px;
  font-weight: 450;
  color: var(--console-text-secondary);
  margin-top: 2px;
}
.nav-caption {
  font-size: 11px;
  color: var(--console-text-subtle);
  margin: 0 15px 12px;
}
nav {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
nav a {
  color: var(--console-nav-text);
  border-radius: 9px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 14px;
}
nav a:hover {
  background: var(--console-surface-hover);
}
nav a.router-link-active {
  background: var(--console-nav-active-bg);
  color: var(--console-nav-active-text);
  font-weight: 600;
}
.sidebar-note {
  margin: auto 12px 0;
  padding-top: 30px;
  font-size: 11px;
  color: var(--console-text-muted);
  display: flex;
  gap: 7px;
  align-items: center;
}
.status-dot {
  width: 6px;
  height: 6px;
  background: #ffb90f;
  border-radius: 50%;
}
.main-shell {
  flex: 1;
  min-width: 0;
}
.topbar {
  height: 76px;
  background: var(--console-surface);
  border-bottom: 1px solid var(--console-border);
  padding: 0 35px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--console-text-secondary);
  font-size: 13px;
}
.account {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--console-text);
}
.theme-button {
  gap: 6px;
  color: var(--console-text-secondary);
}
.theme-button:hover {
  color: var(--console-text);
}
.account-avatar {
  color: var(--console-link);
  background-color: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
  font-weight: 600;
  flex-shrink: 0;
}
main {
  padding: 35px;
  max-width: 1500px;
  margin: 0 auto;
}
@media (max-width: 850px) {
  .sidebar {
    width: 170px;
    padding: 28px 10px;
  }
  main {
    padding: 22px;
  }
  .topbar {
    padding: 0 22px;
  }
  .topbar > span {
    display: none;
  }
}
@media (max-width: 600px) {
  .console-shell {
    display: block;
  }
  .sidebar {
    width: 100%;
    padding: 14px 16px;
    border-right: 0;
    border-bottom: 1px solid var(--console-border);
  }
  .brand {
    font-size: 19px;
    margin: 0 0 12px;
  }
  .nav-caption,
  .sidebar-note {
    display: none;
  }
  nav {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 4px;
  }
  nav a {
    padding: 10px;
    font-size: 13px;
  }
  .topbar {
    height: 58px;
  }
  main {
    padding: 18px;
  }
}
</style>
