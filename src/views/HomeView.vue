<template>
  <div class="home-container">
    <div class="home-header">
      <h1>欢迎回来</h1>
      <el-button type="primary" @click="handleLogout">退出登录</el-button>
    </div>

    <div class="user-info-card" v-if="userInfo">
      <div class="user-header">
        <div class="user-avatar">
          {{ userInfo.username.charAt(0).toUpperCase() }}
        </div>
        <div class="user-details">
          <h2>{{ userInfo.username }}</h2>
          <p class="user-email">{{ userInfo.email }}</p>
        </div>
      </div>

      <div class="user-info-body">
        <div class="info-item">
          <span class="info-label">用户ID:</span>
          <span class="info-value">{{ userInfo.id }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">用户UUID:</span>
          <span class="info-value">{{ userInfo.uuid }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">邮箱:</span>
          <span class="info-value">{{ userInfo.email }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">用户名:</span>
          <span class="info-value">{{ userInfo.username }}</span>
        </div>
      </div>
    </div>

    <div v-else class="loading">
      <el-spin size="large" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { User } from '@/api/auth'

const router = useRouter()
const userInfo = ref<User | null>(null)

onMounted(() => {
  // 从 sessionStorage 获取用户信息
  const userDataStr = sessionStorage.getItem('user')
  if (userDataStr) {
    userInfo.value = JSON.parse(userDataStr)
  } else {
    // 如果没有用户信息，跳转到登录页
    router.push('/login')
  }
})

const handleLogout = () => {
  // 清除存储的信息
  sessionStorage.removeItem('accessToken')
  sessionStorage.removeItem('user')
  // 跳转到登录页
  router.push('/login')
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  gap: 30px;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: white;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}

.home-header h1 {
  margin: 0;
  font-size: 32px;
  font-weight: 600;
}

.user-info-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
  overflow: hidden;
}

.user-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 40px;
  display: flex;
  align-items: center;
  gap: 30px;
}

.user-avatar {
  width: 80px;
  height: 80px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-details h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.user-email {
  margin: 8px 0 0 0;
  font-size: 14px;
  opacity: 0.9;
}

.user-info-body {
  padding: 30px 40px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
  border-left: 4px solid #667eea;
}

.info-label {
  font-weight: 600;
  color: #333;
  min-width: 100px;
}

.info-value {
  color: #666;
  word-break: break-all;
  text-align: right;
}

.loading {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}
</style>
