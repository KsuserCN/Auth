<template>
  <div class="platform-details">
    <section v-for="platform in configured" :key="platform" class="platform-card">
      <div class="platform-heading"><h3>{{ platform === 'ios' ? 'iOS' : 'Android' }}</h3><el-tag :type="app.platforms?.[platform]?.verificationStatus === 'verified' ? 'success' : 'info'">{{ t(`ui.openPlatform.verification_${app.platforms?.[platform]?.verificationStatus || 'unverified'}`) }}</el-tag><el-button :loading="verifying === platform" :disabled="!!verifying" @click="verify(platform)">{{ t('ui.openPlatform.verify') }}</el-button></div>
      <el-alert v-if="platform === 'android'" :title="t('ui.openPlatform.androidPending')" type="info" :closable="false" />
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="field in fields(platform)" :key="field.label" :label="field.label"><code>{{ field.value || '—' }}</code></el-descriptions-item>
        <el-descriptions-item v-if="app.platforms?.[platform]?.verifiedAt" :label="t('ui.openPlatform.verifiedAt')">{{ app.platforms?.[platform]?.verifiedAt }}</el-descriptions-item>
      </el-descriptions>
      <p v-if="app.platforms?.[platform]?.verificationMessage" role="status">{{ app.platforms?.[platform]?.verificationMessage }}</p>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { t } from '@/i18n'
import { verifyApplicationPlatform, type OAuth2App, type MobilePlatform } from '@/api/oauth2'
const props = defineProps<{ app: OAuth2App }>()
const emit = defineEmits<{ updated: [app: OAuth2App] }>()
const verifying = ref<MobilePlatform | ''>('')
const configured = computed(() => (['ios', 'android'] as const).filter((platform) => props.app.platforms?.[platform]))
function fields(platform: MobilePlatform) {
  const ios = props.app.platforms?.ios
  const android = props.app.platforms?.android
  return platform === 'ios' ? [
    { label: t('ui.openPlatform.bundleId'), value: ios?.bundleId }, { label: t('ui.openPlatform.appIdPrefix'), value: ios?.appIdPrefix },
    { label: t('ui.openPlatform.universalLink'), value: ios?.universalLink }, { label: t('ui.openPlatform.urlScheme'), value: ios?.urlScheme }, { label: t('ui.openPlatform.appStoreId'), value: ios?.appStoreId },
  ] : [{ label: t('ui.openPlatform.packageName'), value: android?.packageName }, { label: t('ui.openPlatform.sha256'), value: android?.sha256CertFingerprints.join('\n') }, { label: t('ui.openPlatform.appLink'), value: android?.appLink }, { label: t('ui.openPlatform.downloadUrl'), value: android?.downloadUrl }]
}
async function verify(platform: MobilePlatform) {
  verifying.value = platform
  try { emit('updated', await verifyApplicationPlatform(props.app.appId, platform)); ElMessage.success(t('ui.openPlatform.verifyCompleted')) }
  finally { verifying.value = '' }
}
</script>
<style scoped>
.platform-card { padding: 16px 0; }
.platform-heading { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.platform-heading h3 { margin: 0 auto 0 0; }
code { overflow-wrap: anywhere; white-space: pre-wrap; }
p { color: var(--el-text-color-secondary); font-size: 13px; }
.el-alert { margin-bottom: 16px; }
</style>
