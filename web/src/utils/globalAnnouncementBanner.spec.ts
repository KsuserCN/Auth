import { describe, expect, it } from 'vitest'
import { effectScope } from 'vue'
import { setLanguagePreference } from '@/i18n'
import { useGlobalAnnouncementBanner } from './globalAnnouncementBanner'

describe('localized default announcement', () => {
  it('keeps default copy reactive and preserves explicitly entered content', () => {
    localStorage.removeItem('global-announcement-banner:message:v1')
    const scope = effectScope()
    const banner = scope.run(() => useGlobalAnnouncementBanner())!
    expect(banner.effective.message.value).toContain('Beta')
    setLanguagePreference('en')
    expect(banner.effective.message.value).toContain('This system is in beta')
    expect(banner.local.message.value).toBe('')
    banner.local.message.value = '用户自行输入的公告'
    setLanguagePreference('zh-Hans')
    expect(banner.effective.message.value).toBe('用户自行输入的公告')
    scope.stop()
  })
})
