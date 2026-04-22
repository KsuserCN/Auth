<template>
  <div class="bridge-launch">
    <div class="bridge-launch-card">
      <h1>正在尝试打开 Ksuser App</h1>
      <p>如果没有自动切换到 App，页面会返回登录页并保留当前登录请求。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const isTrustedReturnUrl = (value: string): boolean => {
  try {
    const url = new URL(value)
    const hostname = url.hostname.toLowerCase()
    return (
      url.protocol === 'https:' &&
      (hostname === 'auth.ksuser.cn' || hostname === 'www.ksuser.cn' || hostname.endsWith('.ksuser.cn'))
    ) || (url.protocol === 'http:' && hostname === 'localhost')
  } catch {
    return false
  }
}

onMounted(() => {
  const challengeId = typeof route.query.challengeId === 'string' ? route.query.challengeId.trim() : ''
  const returnUrl = typeof route.query.returnUrl === 'string' ? route.query.returnUrl.trim() : ''

  let fallback = `${window.location.origin}/login?mobileBridgeFallback=1`
  if (challengeId) {
    fallback += `&mobileBridgeChallengeId=${encodeURIComponent(challengeId)}`
  }

  if (returnUrl && isTrustedReturnUrl(returnUrl)) {
    try {
      const target = new URL(returnUrl)
      target.searchParams.set('mobileBridgeFallback', '1')
      window.location.replace(target.toString())
      return
    } catch {
      // noop
    }
  }

  window.location.replace(fallback)
})
</script>

<style scoped>
.bridge-launch {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at top, rgba(255, 185, 15, 0.18), transparent 45%),
    linear-gradient(180deg, #fffaf1 0%, #fff 100%);
}

.bridge-launch-card {
  max-width: 420px;
  padding: 28px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 48px rgba(33, 30, 12, 0.12);
  text-align: center;
}

.bridge-launch-card h1 {
  margin: 0 0 12px;
  font-size: 24px;
}

.bridge-launch-card p {
  margin: 0;
  color: #5f6368;
  line-height: 1.6;
}
</style>
