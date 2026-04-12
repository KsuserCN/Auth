<template>
  <el-card class="danger-zone-card" shadow="never">
    <div class="danger-zone-header">
      <el-icon v-if="icon">
        <component :is="icon" />
      </el-icon>
      <slot v-else name="icon" />
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
  border-radius: 16px;
  border: 1px solid var(--el-color-danger-light-7);
  background: var(--el-color-danger-light-9);
}

.danger-zone-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 16px;
  font-size: 16px;
  color: var(--el-color-danger);
}

.danger-zone-list {
  display: flex;
  flex-direction: column;
}

::v-slotted(.danger-zone-item) {
  padding: 16px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

::v-slotted(.danger-zone-item + .danger-zone-item) {
  border-top: 1px solid var(--el-color-danger-light-7);
}

::v-slotted(.danger-zone-item-left) {
  flex: 1;
  min-width: 0;
}

::v-slotted(.danger-zone-item-title) {
  margin: 0 0 8px 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

::v-slotted(.danger-zone-item-title.danger) {
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
  ::v-slotted(.danger-zone-item) {
    flex-direction: column;
    align-items: flex-start;
  }

  ::v-slotted(.danger-zone-item-right) {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
