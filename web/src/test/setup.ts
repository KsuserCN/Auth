import { beforeEach } from 'vitest'
import { setLanguagePreference } from '@/i18n'

// Existing behavior tests assert Chinese copy explicitly rather than depending on jsdom’s locale.
beforeEach(() => {
  setLanguagePreference('zh-Hans')
})
