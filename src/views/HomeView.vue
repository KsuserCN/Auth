<template>
  <h1>Welcome to the Home Page</h1>

  <section class="user">
    <div v-if="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="user">
      <div class="row">
        <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="avatar" class="avatar" />
        <div class="info">
          <div class="field"><span class="label">用户名：</span><span>{{ user.username }}</span></div>
          <div class="field"><span class="label">UUID：</span><span>{{ user.uuid }}</span></div>
          <div class="field"><span class="label">邮箱：</span><span>{{ user.email }}</span></div>
        </div>
      </div>
    </div>
    <div v-else>未获取到用户信息。</div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUserInfo, type User } from '@/api/auth'

const user = ref<User | null>(null)
const loading = ref(true)
const error = ref('')

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

onMounted(() => {
  load()
})
</script>

<style scoped>
.user {
  max-width: 600px;
  margin-top: 16px;
}
.row {
  display: flex;
  gap: 12px;
  align-items: center;
}
.avatar {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
}
.info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field {
  display: flex;
  gap: 8px;
}
.label {
  font-weight: 600;
  width: 80px;
}
.error {
  font-style: italic;
}
</style>
