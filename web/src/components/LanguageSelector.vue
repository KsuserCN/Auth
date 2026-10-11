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
    <span class="language-label">{{ t('language.label') }}</span>
    <span class="language-select">
      <select v-model="preference" :aria-label="t('language.label')">
        <option value="system">{{ t('language.system') }}</option>
        <option value="zh-Hans">简体中文</option>
        <option value="en">English</option>
      </select>
    </span>
  </label>
</template>

<style scoped>
.language-selector {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--el-text-color-primary);
}
.language-label {
  font-size: 14px;
  line-height: 1.4;
}
.language-select {
  position: relative;
  display: inline-flex;
  width: 220px;
  max-width: 100%;
}
.language-select::after {
  position: absolute;
  top: 50%;
  right: 15px;
  width: 8px;
  height: 8px;
  border-right: 1.5px solid var(--el-text-color-secondary);
  border-bottom: 1.5px solid var(--el-text-color-secondary);
  content: '';
  pointer-events: none;
  transform: translateY(-70%) rotate(45deg);
}
.language-select select {
  width: 100%;
  height: 40px;
  padding: 0 38px 0 12px;
  box-sizing: border-box;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.4;
  appearance: none;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.language-select select:hover {
  border-color: var(--el-color-primary);
}
.language-select select:focus-visible {
  outline: none;
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}
.language-selector.compact {
  padding: 6px 10px;
  border-radius: 10px;
  background: var(--el-bg-color);
  font-size: 13px;
  box-shadow: 0 2px 12px #00000012;
}
.language-selector.compact .language-label {
  font-size: 13px;
}
.language-selector.compact .language-select {
  width: 170px;
}
.language-selector.compact .language-select select {
  height: 32px;
  padding-left: 9px;
  font-size: 13px;
}
.language-selector.compact .language-select::after {
  right: 12px;
  width: 7px;
  height: 7px;
}
@media (max-width: 480px) {
  .language-selector.compact .language-label {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
  }
}
</style>
