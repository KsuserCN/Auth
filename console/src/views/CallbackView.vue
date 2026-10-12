<template>
  <div class="callback-page">
    <div class="panel">
      <template v-if="loading"
        ><el-icon class="spinner" :size="32"><Loading /></el-icon>
        <h1>{{ verification ? '正在完成再次验证' : '正在登录管理中心' }}</h1>
        <p class="muted">请稍候，正在验证登录状态。</p></template
      ><template v-else
        ><el-alert :title="error" type="error" :closable="false" show-icon /><el-button
          type="primary"
          @click="router.replace('/login')"
          >返回登录</el-button
        ></template
      >
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { consoleApi, ConsoleApiError } from '@/api/console'
import { useSessionStore } from '@/stores/session'
import { consumeVerificationReturn } from '@/utils/navigation'
const route = useRoute()
const router = useRouter()
const store = useSessionStore()
const verification = computed(() => route.meta.verification === true)
const loading = ref(true)
const error = ref('')
onMounted(async () => {
  const query = { ...route.query }
  // Remove codes and tickets from the address bar before asynchronous exchange.
  window.history.replaceState(window.history.state, '', route.path)
  try {
    if (typeof query.error === 'string')
      throw new ConsoleApiError(query.errorCode?.toString() || 'FORBIDDEN')
    if (typeof query.state !== 'string' || !query.state) throw new ConsoleApiError('BAD_REQUEST')
    const value = verification.value ? query.ticket : query.code
    if (typeof value !== 'string' || !value) throw new ConsoleApiError('BAD_REQUEST')
    store.session = verification.value
      ? await consoleApi.verificationExchange(value, query.state)
      : await consoleApi.exchange(value, query.state)
    await router.replace(verification.value ? consumeVerificationReturn() : '/users')
  } catch (failure) {
    store.clear()
    error.value = failure instanceof Error ? failure.message : '登录失败，请重试'
    loading.value = false
  }
})
</script>
<style scoped>
.callback-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}
.panel {
  max-width: 480px;
  width: 100%;
  text-align: center;
  padding: 45px;
}
h1 {
  font-size: 22px;
  margin-top: 22px;
}
.el-button {
  margin-top: 24px;
}
.spinner {
  color: #ffb90f;
  animation: spin 1.2s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
