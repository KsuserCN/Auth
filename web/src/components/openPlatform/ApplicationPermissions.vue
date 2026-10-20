<template>
  <div class="permissions">
    <section class="permission-row">
      <h3>{{ t('ui.openPlatform.loginPermission') }}</h3>
      <p>{{ t('ui.openPlatform.loginPermissionHint') }}</p>
      <el-tag>{{ t('ui.openPlatform.loginAvailable') }}</el-tag>
    </section>
    <section class="permission-row">
      <div class="permission-heading"><h3>UnionID</h3><el-tag :type="app.unionidStatus === 'approved' ? 'success' : 'info'">{{ t(`ui.openPlatform.unionid_${app.unionidStatus || 'none'}`) }}</el-tag></div>
      <p>{{ t('ui.openPlatform.unionidHint') }}</p>
      <p v-if="app.unionidReason"><strong>{{ t('ui.openPlatform.unionidReason') }}：</strong>{{ app.unionidReason }}</p>
      <p v-if="app.unionidReviewReason"><strong>{{ t('ui.openPlatform.unionidReviewReason') }}：</strong>{{ app.unionidReviewReason }}</p>
      <el-button v-if="!['pending', 'approved'].includes(app.unionidStatus || 'none')" type="primary" @click="applying = true">{{ t('ui.openPlatform.applyUnionid') }}</el-button>
      <el-button v-else-if="app.unionidStatus === 'pending'" :loading="saving" @click="close">{{ t('ui.openPlatform.withdrawUnionid') }}</el-button>
      <el-popconfirm v-else :title="t('ui.openPlatform.unionidCloseConfirm')" @confirm="close">
        <template #reference><el-button type="danger" :loading="saving">{{ t('ui.openPlatform.closeUnionid') }}</el-button></template>
      </el-popconfirm>
      <div v-if="applying" class="application-form">
        <el-input v-model="reason" type="textarea" :rows="3" maxlength="1000" show-word-limit :placeholder="t('ui.openPlatform.unionidReasonHint')" :aria-label="t('ui.openPlatform.unionidReason')" />
        <div class="application-actions"><el-button @click="applying = false">{{ t('ui.addPasskey.message009') }}</el-button><el-button type="primary" :loading="saving" @click="apply">{{ t('ui.openPlatform.submitApplication') }}</el-button></div>
      </div>
    </section>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { t } from '@/i18n'
import { applyApplicationUnionid, closeApplicationUnionid, type OAuth2App } from '@/api/oauth2'
const props = defineProps<{ app: OAuth2App }>()
const emit = defineEmits<{ updated: [app: OAuth2App] }>()
const applying = ref(false)
const saving = ref(false)
const reason = ref('')
watch(() => props.app.appId, () => { applying.value = false; reason.value = '' })
async function apply() {
  if (!reason.value.trim()) { ElMessage.warning(t('ui.openPlatform.unionidReasonRequired')); return }
  saving.value = true
  try { emit('updated', await applyApplicationUnionid(props.app.appId, reason.value.trim())); applying.value = false; reason.value = ''; ElMessage.success(t('ui.openPlatform.unionidSubmitted')) }
  finally { saving.value = false }
}
async function close() {
  saving.value = true
  try { emit('updated', await closeApplicationUnionid(props.app.appId)); ElMessage.success(t('ui.openPlatform.unionidClosed')) }
  finally { saving.value = false }
}
</script>
<style scoped>
.permission-row { padding: 18px 0; border-bottom: 1px solid var(--el-border-color); }
.permission-row h3 { margin: 0; font-size: 15px; }
.permission-row p { color: var(--el-text-color-secondary); line-height: 1.7; font-size: 13px; overflow-wrap: anywhere; }
.permission-heading, .application-actions { display: flex; align-items: center; gap: 12px; }
.application-form { margin-top: 16px; }
.application-actions { justify-content: flex-end; margin-top: 12px; }
</style>
