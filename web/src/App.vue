<script setup lang="ts">
import { RouterView } from 'vue-router'
import { computed, watchEffect } from 'vue'
import { useStorage } from '@vueuse/core'
import GlobalAnnouncementBanner from './components/GlobalAnnouncementBanner.vue'

const bannerMessage =
  '当前系统正在Beta测试阶段，如遇数据丢失/页面出错/服务不稳定属于正常情况，请各位用户在测试版期间请勿上传真实信息'

const bannerDismissed = useStorage('global-beta-banner-dismissed:v1', false)
const bannerVisible = computed(() => !bannerDismissed.value)
const bannerHeight = computed(() => (bannerVisible.value ? '40px' : '0px'))
const messageConfig = computed(() => ({
  offset: bannerVisible.value ? 56 : 16,
}))

const closeBanner = () => {
  bannerDismissed.value = true
}

watchEffect(() => {
  document.documentElement.style.setProperty('--global-banner-height', bannerHeight.value)
})
</script>

<template>
  <div class="app-shell" :style="{ '--global-banner-height': bannerHeight }">
    <GlobalAnnouncementBanner v-if="bannerVisible" :message ="bannerMessage" @close="closeBanner" />
    <div class="app-content">
      <el-config-provider :message="messageConfig">
        <RouterView />
      </el-config-provider>
    </div>
  </div>
</template>

<style>
.app-shell {
  --global-banner-height: 0px;
  width: 100%;
  height: 100vh;
  height: 100dvh;
}

.app-content {
  width: 100%;
  height: calc(100vh - var(--global-banner-height));
  height: calc(100dvh - var(--global-banner-height));
  margin-top: var(--global-banner-height);
  overflow: hidden;
}

@media (max-width: 720px) {
  .app-content {
    overflow: auto;
  }
}
</style>
