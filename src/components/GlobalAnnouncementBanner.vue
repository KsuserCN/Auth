<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useResizeObserver } from '@vueuse/core'
import { Close } from '@element-plus/icons-vue'

const props = defineProps<{
  message: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const marqueeRef = ref<HTMLElement | null>(null)
const groupRef = ref<HTMLElement | null>(null)

const isOverflowing = ref(false)
const durationSeconds = ref(18)

const updateOverflow = () => {
  const marquee = marqueeRef.value
  const group = groupRef.value
  if (!marquee || !group) return

  const containerWidth = marquee.clientWidth
  const groupWidth = group.scrollWidth
  const nextOverflow = groupWidth > containerWidth + 1

  isOverflowing.value = nextOverflow

  // 速度：约 70px/s，限制在 [12s, 60s]
  const pxPerSecond = 70
  const nextDuration = groupWidth / pxPerSecond
  durationSeconds.value = Math.max(12, Math.min(60, Math.round(nextDuration)))
}

useResizeObserver(marqueeRef, updateOverflow)
useResizeObserver(groupRef, updateOverflow)

onMounted(() => {
  updateOverflow()
  window.addEventListener('resize', updateOverflow, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateOverflow)
})

const trackStyle = computed(() => {
  if (!isOverflowing.value) return undefined
  return {
    '--marquee-duration': `${durationSeconds.value}s`,
  } as Record<string, string>
})

const handleClose = () => emit('close')
</script>

<template>
  <div class="beta-banner" role="status" aria-live="polite">
    <div ref="marqueeRef" class="marquee" :class="{ overflowing: isOverflowing }">
      <div class="track" :class="{ animate: isOverflowing }" :style="trackStyle">
        <div ref="groupRef" class="group">
          <span class="text">{{ props.message }}</span>
          <span class="spacer" aria-hidden="true" />
        </div>
        <div v-if="isOverflowing" class="group" aria-hidden="true">
          <span class="text">{{ props.message }}</span>
          <span class="spacer" />
        </div>
      </div>
    </div>

    <button type="button" class="close-btn" aria-label="关闭公告" @click="handleClose">
      <el-icon>
        <Close />
      </el-icon>
    </button>
  </div>
</template>

<style scoped>
.beta-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 40px;
  z-index: 1999;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  background: var(--el-color-warning-light-9, rgba(255, 214, 102, 0.22));
  border-bottom: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
  box-sizing: border-box;
}

.marquee {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  display: flex;
  justify-content: center;
}

.marquee.overflowing {
  justify-content: flex-start;
}

.track {
  display: flex;
  width: max-content;
  align-items: center;
}

.marquee:not(.overflowing) .track {
  margin: 0 auto;
}

.track.animate {
  animation: marquee var(--marquee-duration, 18s) linear infinite;
  will-change: transform;
}

.beta-banner:hover .track.animate {
  animation-play-state: paused;
}

.group {
  display: inline-flex;
  align-items: center;
}

.text {
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.spacer {
  width: 48px;
  display: inline-block;
}

.close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color, rgba(255, 255, 255, 0.6));
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.close-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

@keyframes marquee {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-50%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .track.animate {
    animation: none;
  }
}
</style>
