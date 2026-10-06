<template>
  <main class="authorization-return">
    <div class="return-card">
      <img src="/favicon.ico" alt="Ksuser" width="48" height="48" />
      <h1>{{ error ? '无法继续授权' : '授权已确认' }}</h1>
      <p>{{ error || '正在返回应用，请稍候…' }}</p>
      <el-button v-if="error && credentials" type="primary" :loading="busy" @click="complete"
        >重试</el-button
      >
    </div>
  </main>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  readMobileAuthorizationReturn,
  consumeMobileAuthorization,
  continueMobileAuthorization,
} from '@/utils/mobileAuthorization'
const credentials = readMobileAuthorizationReturn(window.location.hash)
// Remove the secret before loading any external application resources.
window.history.replaceState(window.history.state, '', window.location.pathname)
const error = ref('')
const busy = ref(false)
const complete = async () => {
  if (!credentials) {
    error.value = '授权回跳参数无效，请返回原应用重新发起授权。'
    return
  }
  busy.value = true
  error.value = ''
  try {
    if (!continueMobileAuthorization(await consumeMobileAuthorization(credentials))) {
      error.value = '授权尚未完成，请返回 Ksuser App 确认。'
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '请求已完成或过期，请返回原应用重新发起授权。'
  } finally {
    busy.value = false
  }
}
onMounted(complete)
</script>
<style scoped>
.authorization-return {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: radial-gradient(ellipse at top, #fff2d0, #f7f8fa 65%);
}
.return-card {
  max-width: 420px;
  padding: 40px 28px;
  border-radius: 28px;
  text-align: center;
  background: #fff;
  box-shadow: 0 20px 70px #38280a14;
}
h1 {
  font-size: 24px;
  color: #28221a;
}
p {
  line-height: 1.7;
  color: #68615a;
}
@media (prefers-color-scheme: dark) {
  .authorization-return {
    background: #141414;
  }
  .return-card {
    background: #242424;
  }
  h1 {
    color: #faf5e9;
  }
  p {
    color: #beb9b0;
  }
}
</style>
