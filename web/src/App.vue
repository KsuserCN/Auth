<script setup lang="ts">
import { t } from '@/i18n'

import { RouterView } from 'vue-router'
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import LanguageSelector from './components/LanguageSelector.vue'
import LocalizedMessageBox from './components/LocalizedMessageBox.vue'
import { effectiveLocale } from './i18n'
import { useStorage } from '@vueuse/core'
import GlobalAnnouncementBanner from './components/GlobalAnnouncementBanner.vue'

const bannerMessage = computed(() => t('ui.app.message001'))

const route = useRoute()
const elementLocale = computed(() => (effectiveLocale.value === 'zh-Hans' ? zhCn : en))
const showPublicLanguageSelector = computed(() => !route.path.startsWith('/home'))

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
    <GlobalAnnouncementBanner v-if="bannerVisible" :message="bannerMessage" @close="closeBanner" />
    <div class="app-content">
      <el-config-provider :message="messageConfig" :locale="elementLocale">
        <div v-if="showPublicLanguageSelector" class="public-language-selector">
          <LanguageSelector compact />
        </div>
        <RouterView />
        <LocalizedMessageBox />
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

.public-language-selector {
  position: fixed;
  top: calc(var(--global-banner-height) + 10px);
  right: 12px;
  z-index: 20;
}

.app-content {
  width: 100%;
  height: calc(100vh - var(--global-banner-height));
  height: calc(100dvh - var(--global-banner-height));
  margin-top: var(--global-banner-height);
  overflow: hidden;
}

@media (max-width: 720px) {
  .public-language-selector {
    position: fixed;
    top: calc(var(--global-banner-height) + 10px);
    right: 12px;
    z-index: 20;
  }

  .app-content {
    overflow: auto;
  }
}
</style>
