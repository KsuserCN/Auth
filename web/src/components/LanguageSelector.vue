<script setup lang="ts">
import { computed } from 'vue'
import { languagePreference, setLanguagePreference, t, type LanguagePreference } from '@/i18n'

defineProps<{ compact?: boolean }>()
const preference = computed({
  get: () => languagePreference.value,
  set: (value: LanguagePreference) => setLanguagePreference(value),
})
</script>

<template>
  <label class="language-selector" :class="{ compact }">
    <span>{{ t('language.label') }}</span>
    <select v-model="preference" :aria-label="t('language.label')">
      <option value="system">{{ t('language.system') }}</option>
      <option value="zh-Hans">简体中文</option>
      <option value="en">English</option>
    </select>
  </label>
</template>

<style scoped>
.language-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--el-text-color-primary);
}
.language-selector select {
  min-width: 160px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font: inherit;
}
.language-selector.compact {
  padding: 6px 10px;
  border-radius: 10px;
  background: var(--el-bg-color);
  font-size: 13px;
  box-shadow: 0 2px 12px #00000012;
}
.language-selector.compact select {
  min-width: 120px;
  padding: 4px 8px;
}
@media (max-width: 480px) {
  .language-selector.compact > span {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
  }
}
</style>
