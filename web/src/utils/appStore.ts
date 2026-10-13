import { computed } from 'vue'
import { effectiveLocale } from '@/i18n'

export const ksuserIosAppStoreUrl = computed(() =>
  effectiveLocale.value === 'zh-Hans'
    ? 'https://apps.apple.com/cn/app/ksuser%E5%AE%89%E5%85%A8/id6776157121'
    : 'https://apps.apple.com/us/app/ksuser%E5%AE%89%E5%85%A8/id6776157121',
)
