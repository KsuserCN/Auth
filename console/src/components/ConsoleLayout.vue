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
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Grid, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { consoleApi } from '@/api/console'
import { avatarFallback } from '@/utils/avatar'
import { useSessionStore } from '@/stores/session'
import { clearConsoleNavigation } from '@/utils/navigation'
const store = useSessionStore()
const router = useRouter()
const loggingOut = ref(false)
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
  background: #fff;
  border-right: 1px solid #e4e8ee;
  padding: 32px 18px;
  display: flex;
  flex-direction: column;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #182332;
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
  color: #647386;
  margin-top: 2px;
}
.nav-caption {
  font-size: 11px;
  color: #88929e;
  margin: 0 15px 12px;
}
nav {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
nav a {
  color: #5e6b7a;
  border-radius: 9px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 14px;
}
nav a:hover {
  background: #f5f7f8;
}
nav a.router-link-active {
  background: #fff5d6;
  color: #805c00;
  font-weight: 600;
}
.sidebar-note {
  margin: auto 12px 0;
  padding-top: 30px;
  font-size: 11px;
  color: #7b8490;
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
  background: #fff;
  border-bottom: 1px solid #e4e8ee;
  padding: 0 35px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #647386;
  font-size: 13px;
}
.account {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #182332;
}
.account-avatar {
  color: #805c00;
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
    border-bottom: 1px solid #e4e8ee;
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
