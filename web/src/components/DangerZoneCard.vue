<template>
  <el-card class="danger-zone-card" shadow="never">
    <div class="danger-zone-header">
      <span class="danger-zone-icon">
        <el-icon v-if="icon">
          <component :is="icon" />
        </el-icon>
        <slot v-else name="icon" />
      </span>
      <span>{{ title }}</span>
    </div>

    <div class="danger-zone-list">
      <slot />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  title: string
  icon?: Component
}>()
</script>

<style scoped>
.danger-zone-card {
  border-radius: 18px;
  border: 1px solid var(--el-color-danger-light-7);
  background: color-mix(in srgb, var(--el-color-danger) 4%, var(--el-bg-color));
  box-shadow: 0 6px 18px color-mix(in srgb, var(--el-color-danger) 7%, transparent);
}

.danger-zone-card :deep(.el-card__body) {
  padding: 20px;
}

.danger-zone-icon {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 9px;
  background: color-mix(in srgb, var(--el-color-danger) 12%, var(--el-bg-color));
  font-size: 16px;
}

.danger-zone-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 14px;
  font-size: 16px;
  color: var(--el-color-danger);
}

.danger-zone-list {
  display: flex;
  flex-direction: column;
}

::v-slotted(.danger-zone-item) {
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid color-mix(in srgb, var(--el-color-danger) 18%, var(--el-border-color));
  border-radius: 12px;
  background: var(--el-bg-color);
}

::v-slotted(.danger-zone-item + .danger-zone-item) {
  margin-top: 10px;
}

::v-slotted(.danger-zone-item-left) {
  flex: 1;
  min-width: 0;
}

::v-slotted(.danger-zone-item-title) {
  margin: 0 0 5px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-color-danger);
}

::v-slotted(.danger-zone-item-desc) {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

::v-slotted(.danger-zone-item-right) {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .danger-zone-card :deep(.el-card__body) {
    padding: 16px;
  }

  ::v-slotted(.danger-zone-item) {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  ::v-slotted(.danger-zone-item-right) {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
