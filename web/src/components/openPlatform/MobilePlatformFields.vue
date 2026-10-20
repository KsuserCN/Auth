<template>
  <div class="platform-fields">
    <p class="hint">{{ t('ui.openPlatform.mobileCallbackHint') }}</p>
    <el-checkbox v-model="model.iosEnabled">{{ t('ui.openPlatform.iosEnabled') }}</el-checkbox>
    <div v-if="model.iosEnabled" class="platform-section">
      <el-form-item :label="t('ui.openPlatform.bundleId')" required><el-input v-model="model.bundleId" placeholder="com.example.app" maxlength="255" /></el-form-item>
      <el-form-item :label="t('ui.openPlatform.appIdPrefix')" required><el-input v-model="model.appIdPrefix" placeholder="TEAMID1234" maxlength="64" /></el-form-item>
      <el-form-item :label="t('ui.openPlatform.universalLink')" required><el-input v-model="model.universalLink" placeholder="https://login.example.app/ksuser/callback" maxlength="500" /></el-form-item>
      <el-form-item :label="t('ui.openPlatform.urlScheme')"><el-input v-model="model.urlScheme" maxlength="128" /></el-form-item>
      <el-form-item :label="t('ui.openPlatform.appStoreId')"><el-input v-model="model.appStoreId" maxlength="64" /></el-form-item>
    </div>
    <el-checkbox v-model="model.androidEnabled">{{ t('ui.openPlatform.androidEnabled') }}</el-checkbox>
    <el-alert :title="t('ui.openPlatform.androidPending')" type="info" :closable="false" />
    <div v-if="model.androidEnabled" class="platform-section">
      <el-form-item :label="t('ui.openPlatform.packageName')" required><el-input v-model="model.packageName" placeholder="com.example.app" maxlength="255" /></el-form-item>
      <el-form-item :label="t('ui.openPlatform.sha256')" required><el-input v-model="model.fingerprints" type="textarea" :rows="3" :placeholder="t('ui.openPlatform.sha256Hint')" /></el-form-item>
      <el-form-item :label="t('ui.openPlatform.appLink')" required><el-input v-model="model.appLink" placeholder="https://login.example.app/android/callback" maxlength="500" /></el-form-item>
      <el-form-item :label="t('ui.openPlatform.downloadUrl')"><el-input v-model="model.downloadUrl" maxlength="500" /></el-form-item>
    </div>
  </div>
</template>
<script setup lang="ts">
import { t } from '@/i18n'
import type { MobilePlatformDraft } from './mobilePlatforms'
const model = defineModel<MobilePlatformDraft>({ required: true })
</script>
<style scoped>
.hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
.platform-section { padding: 16px; margin: 8px 0 16px; border: 1px solid var(--el-border-color); border-radius: 8px; }
.el-alert { margin: 8px 0 12px; }
</style>
