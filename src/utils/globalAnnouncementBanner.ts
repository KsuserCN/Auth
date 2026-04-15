import { computed, type ComputedRef, type Ref } from 'vue'
import { useStorage } from '@vueuse/core'
import { parseEnvBoolean, parseEnvNumber } from './env'

export type GlobalAnnouncementBannerSettings = {
  env: {
    enabled: boolean
    locked: boolean
    message: string
    heightPx: number
    dismissible: boolean
  }
  local: {
    enabled: Ref<boolean>
    message: Ref<string>
    heightPx: Ref<number>
    dismissible: Ref<boolean>
    dismissed: Ref<boolean>
  }
  effective: {
    enabled: ComputedRef<boolean>
    message: ComputedRef<string>
    heightPx: ComputedRef<number>
    dismissible: ComputedRef<boolean>
    visible: ComputedRef<boolean>
  }
  actions: {
    dismiss: () => void
    resetDismissed: () => void
    resetToEnvDefaults: () => void
  }
}

const DEFAULT_MESSAGE =
  '当前系统正在Beta测试阶段，如遇到数据丢失/服务不稳定为正常情况，请勿上传真实信息'

const STORAGE_KEYS = {
  enabled: 'global-announcement-banner:enabled:v1',
  message: 'global-announcement-banner:message:v1',
  heightPx: 'global-announcement-banner:heightPx:v1',
  dismissible: 'global-announcement-banner:dismissible:v1',
  dismissed: 'global-announcement-banner:dismissed:v1',
} as const

export const useGlobalAnnouncementBanner = (): GlobalAnnouncementBannerSettings => {
  const envEnabled = parseEnvBoolean(import.meta.env.VITE_GLOBAL_BANNER_ENABLED, true)
  const envLocked = parseEnvBoolean(import.meta.env.VITE_GLOBAL_BANNER_LOCKED, false)
  const envMessage =
    (import.meta.env.VITE_GLOBAL_BANNER_MESSAGE as string | undefined) || DEFAULT_MESSAGE
  const envHeightPx = Math.max(0, parseEnvNumber(import.meta.env.VITE_GLOBAL_BANNER_HEIGHT, 40))
  const envDismissible = parseEnvBoolean(import.meta.env.VITE_GLOBAL_BANNER_DISMISSIBLE, true)

  const localEnabled = useStorage<boolean>(STORAGE_KEYS.enabled, true)
  const localMessage = useStorage<string>(STORAGE_KEYS.message, envMessage)
  const localHeightPx = useStorage<number>(STORAGE_KEYS.heightPx, envHeightPx)
  const localDismissible = useStorage<boolean>(STORAGE_KEYS.dismissible, envDismissible)
  const localDismissed = useStorage<boolean>(STORAGE_KEYS.dismissed, false)

  const effectiveEnabled = computed(() => envEnabled && (envLocked ? true : localEnabled.value))
  const effectiveMessage = computed(() => (envLocked ? envMessage : localMessage.value))
  const effectiveHeightPx = computed(() => {
    const raw = envLocked ? envHeightPx : localHeightPx.value
    return Math.max(0, Math.round(raw))
  })
  const effectiveDismissible = computed(() => (envLocked ? envDismissible : localDismissible.value))
  const effectiveVisible = computed(() => {
    if (!effectiveEnabled.value) return false
    if (!effectiveDismissible.value) return true
    return !localDismissed.value
  })

  const resetToEnvDefaults = () => {
    localEnabled.value = true
    localMessage.value = envMessage
    localHeightPx.value = envHeightPx
    localDismissible.value = envDismissible
  }

  return {
    env: {
      enabled: envEnabled,
      locked: envLocked,
      message: envMessage,
      heightPx: envHeightPx,
      dismissible: envDismissible,
    },
    local: {
      enabled: localEnabled,
      message: localMessage,
      heightPx: localHeightPx,
      dismissible: localDismissible,
      dismissed: localDismissed,
    },
    effective: {
      enabled: effectiveEnabled,
      message: effectiveMessage,
      heightPx: effectiveHeightPx,
      dismissible: effectiveDismissible,
      visible: effectiveVisible,
    },
    actions: {
      dismiss: () => {
        if (!effectiveDismissible.value) return
        localDismissed.value = true
      },
      resetDismissed: () => {
        localDismissed.value = false
      },
      resetToEnvDefaults,
    },
  }
}
