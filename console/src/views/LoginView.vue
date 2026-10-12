<template>
  <div class="login-page">
    <div class="login-card">
      <img class="login-brand" src="https://static.ksuser.cn/favicon.png" alt="Ksuser 标志" />
      <span class="eyebrow">KSUSER CONSOLE</span>
      <h1>管理中心</h1>
      <p>使用 Ksuser 通行证安全登录。<br />仅超级管理员可访问管理面板。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon /><el-button
        type="primary"
        size="large"
        :loading="loading"
        @click="login"
        >使用 Ksuser 通行证登录 <span aria-hidden="true">↗</span></el-button
      ><a :href="passportUrl" target="_blank" rel="noopener noreferrer">前往 Ksuser 通行证</a>
    </div>
    <div class="login-footer">Ksuser · console.ksuser.cn</div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { consoleApi, ConsoleApiError } from '@/api/console'
import { apiResultMessagesByLocale } from '@/generated/apiResultCodes'
const route = useRoute()
const loading = ref(false)
const localError = ref('')
const error = computed(
  () =>
    localError.value ||
    (typeof route.query.error === 'string'
      ? apiResultMessagesByLocale['zh-Hans'][route.query.error] || '登录失败，请重新登录'
      : ''),
)
const passportUrl = import.meta.env.VITE_PASSPORT_BASE_URL || 'https://auth.ksuser.cn'
const login = async () => {
  loading.value = true
  localError.value = ''
  try {
    window.location.assign((await consoleApi.login()).authorizationUrl)
  } catch (error) {
    localError.value = error instanceof ConsoleApiError ? error.message : '无法连接服务器，请重试'
    loading.value = false
  }
}
</script>
<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 75% 20%, var(--console-accent-glow), transparent 45%),
    var(--console-page-bg);
  padding: 24px;
}
.login-card {
  width: 440px;
  max-width: 100%;
  background: var(--console-surface);
  border: 1px solid var(--console-border);
  padding: 48px;
  border-radius: 20px;
  box-shadow: 0 12px 45px rgb(24 35 50 / 4%);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 20px;
}
.login-brand {
  width: 58px;
  height: 58px;
  object-fit: contain;
  margin-bottom: 4px;
}
.eyebrow {
  color: var(--console-link);
  font-size: 11px;
  letter-spacing: 2px;
}
h1 {
  font-size: 31px;
}
p {
  line-height: 1.9;
  color: var(--console-text-secondary);
  margin: -6px 0 6px;
  font-size: 14px;
}
.login-card > .el-button {
  width: 100%;
  margin: 6px 0 2px;
}
.el-button span {
  margin-left: 15px;
}
.login-card > a,
.login-footer {
  font-size: 12px;
  color: var(--console-text-muted);
}
.login-footer {
  margin-top: 32px;
}
</style>
