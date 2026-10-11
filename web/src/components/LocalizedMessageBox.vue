<script setup lang="ts">
import { computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { effectiveLocale, renderMessage, t } from '@/i18n'
import {
  activeMessageBox,
  messageBoxInput,
  messageBoxValidationAttempted,
  confirmMessageBox,
  cancelMessageBox,
  messageBoxClosed,
} from '@/utils/localizedMessageBox'

const promptError = computed(() => {
  void effectiveLocale.value
  const current = activeMessageBox.value
  if (!current || current.mode !== 'prompt' || !messageBoxValidationAttempted.value) return ''
  const result = current.options.inputValidator?.(messageBoxInput.value) ?? true
  return result === true
    ? ''
    : result === false
      ? t('ui.addPasskey.message016')
      : renderMessage(result)
})
const confirm = () => {
  messageBoxValidationAttempted.value = true
  if (!promptError.value) confirmMessageBox()
}
const close = (done: () => void) => {
  cancelMessageBox('close')
  done()
}
</script>

<template>
  <el-dialog
    :model-value="activeMessageBox !== null"
    :title="activeMessageBox ? renderMessage(activeMessageBox.title) : ''"
    width="min(420px, calc(100vw - 32px))"
    :before-close="close"
    class="localized-message-box"
    @closed="messageBoxClosed"
  >
    <template v-if="activeMessageBox">
      <div class="message-body">
        <el-icon v-if="activeMessageBox.options.type === 'warning'" class="warning-icon"
          ><WarningFilled
        /></el-icon>
        <p>{{ renderMessage(activeMessageBox.message) }}</p>
      </div>
      <el-input
        v-if="activeMessageBox.mode === 'prompt'"
        v-model="messageBoxInput"
        :placeholder="
          activeMessageBox.options.inputPlaceholder
            ? renderMessage(activeMessageBox.options.inputPlaceholder)
            : ''
        "
        :aria-label="renderMessage(activeMessageBox.message)"
        :aria-invalid="Boolean(promptError)"
        autofocus
        @input="messageBoxValidationAttempted = true"
        @keyup.enter="confirm"
      />
      <p v-if="promptError" class="input-error" role="alert">{{ promptError }}</p>
    </template>
    <template #footer>
      <div v-if="activeMessageBox" class="dialog-actions">
        <el-button @click="cancelMessageBox()">{{
          activeMessageBox.options.cancelButtonText
            ? renderMessage(activeMessageBox.options.cancelButtonText)
            : t('ui.addPasskey.message009')
        }}</el-button>
        <el-button type="primary" @click="confirm">{{
          activeMessageBox.options.confirmButtonText
            ? renderMessage(activeMessageBox.options.confirmButtonText)
            : t('ui.devices.message034')
        }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.message-body {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.message-body p {
  margin: 0 0 16px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.warning-icon {
  color: var(--el-color-warning);
  font-size: 24px;
  flex-shrink: 0;
  margin-top: 2px;
}
.input-error {
  color: var(--el-color-danger);
  margin: 8px 0 0;
  font-size: 13px;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}
.dialog-actions .el-button {
  margin-left: 0;
  max-width: 100%;
  height: auto;
  min-height: 32px;
  white-space: normal;
  padding-block: 8px;
}
</style>
