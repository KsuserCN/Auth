<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">偏好设置</h1>
        <p class="page-subtitle">自定义您的使用偏好和通知设置</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Setting />
            </el-icon>
            <span>色彩主题</span>
          </div>
          <p class="card-desc">为当前设备选择您偏好的色彩主题</p>

          <div class="theme-grid">
            <div v-for="option in themeOptions" :key="option.value" class="theme-option"
              :class="{ active: themeMode === option.value }" @click="themeMode = option.value">
              <div class="option-inner">
                <div class="preview-container">
                  <div class="preview" :class="`preview-${option.value}`">
                    <div class="preview-top"></div>
                    <div class="preview-content">
                      <div class="line line-1"></div>
                      <div class="line line-2"></div>
                    </div>
                  </div>
                </div>
                <div class="option-info">
                  <el-icon class="option-icon">
                    <component :is="option.icon" />
                  </el-icon>
                  <div class="option-text">
                    <div class="option-label">{{ option.label }}</div>
                  </div>
                </div>
              </div>
              <div class="checkmark" v-show="themeMode === option.value">
                <el-icon>
                  <Check />
                </el-icon>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { useStorage } from '@vueuse/core'
import {
  Monitor,
  Sunny,
  Moon,
  Setting,
  Check
} from '@element-plus/icons-vue'

const themeMode = useStorage<'light' | 'dark' | 'system'>('theme-mode', 'system')

const themeOptions = [
  {
    value: 'system',
    label: '跟随浏览器',
    icon: Monitor
  },
  {
    value: 'light',
    label: '浅色',
    icon: Sunny
  },
  {
    value: 'dark',
    label: '深色',
    icon: Moon
  }
]
</script>

<style scoped>
.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 6px 0 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.card-desc {
  margin: 0 0 24px 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.theme-option {
  position: relative;
  cursor: pointer;
  border: 2px solid var(--el-border-color-light);
  border-radius: 12px;
  padding: 16px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: var(--el-bg-color);
}

.theme-option:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.theme-option.active {
  border-color: var(--el-color-primary);
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-bg-color) 100%);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}

.option-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}

.preview-container {
  width: 100%;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.preview {
  width: 100%;
  height: 80px;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.preview-top {
  height: 28px;
  background: var(--el-color-primary);
}

.preview-content {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preview-light {
  background: #ffffff;
}

.preview-light .preview-top {
  background: var(--el-color-primary);
}

.preview-light .line {
  background: #e0e0e0;
}

.preview-dark {
  background: #1f1f1f;
}

.preview-dark .preview-top {
  background: var(--el-color-primary);
}

.preview-dark .line {
  background: #444444;
}

.preview-system {
  background: linear-gradient(90deg, #ffffff 50%, #1f1f1f 50%);
}

.preview-system .preview-top {
  background: var(--el-color-primary);
}

.preview-system .line {
  background: linear-gradient(90deg, #e0e0e0 50%, #444444 50%);
}

.line {
  height: 4px;
  border-radius: 2px;
}

.line-1 {
  width: 100%;
}

.line-2 {
  width: 70%;
}

.option-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 100%;
}

.option-icon {
  font-size: 20px;
  color: var(--el-color-primary);
}

.option-text {
  text-align: center;
}

.option-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.checkmark {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  animation: slideIn 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.checkmark :deep(.el-icon) {
  font-size: 14px;
}

@keyframes slideIn {
  from {
    transform: scale(0) translateX(10px);
    opacity: 0;
  }

  to {
    transform: scale(1) translateX(0);
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .theme-grid {
    grid-template-columns: 1fr;
  }
}
</style>
