import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import LanguageSelector from '@/components/LanguageSelector.vue'
import { clearAuthSession } from '@/utils/authSession'
import { ApiError, localizedError, resolveApiResult, resolveAuditReason } from '@/utils/apiResult'
import { apiResultMessagesByLocale } from '@/generated/apiResultCodes'
import zh from './zh-Hans.json'
import en from './en.json'
import {
  effectiveLocale,
  intlLocale,
  LANGUAGE_STORAGE_KEY,
  languagePreference,
  renderMessage,
  resolveDeviceLocale,
  setLanguagePreference,
  t,
  uiMessage,
} from '.'

afterEach(() => {
  vi.restoreAllMocks()
  setLanguagePreference('zh-Hans')
})

const deviceLanguages = (languages: string[]) => {
  vi.spyOn(navigator, 'languages', 'get').mockReturnValue(languages)
  window.dispatchEvent(new Event('languagechange'))
}

describe('device language and local preferences', () => {
  it.each([
    [['zh-CN'], 'zh-Hans'],
    [['zh-TW'], 'zh-Hans'],
    [['zh-Hans'], 'zh-Hans'],
    [['en-GB'], 'en'],
    [['ja-JP', 'zh-CN'], 'en'],
    [[], 'en'],
  ] as const)('resolves the first preferred language %s', (languages, locale) => {
    expect(resolveDeviceLocale(languages)).toBe(locale)
  })

  it('uses device language, tracks changes, and lets an explicit choice take priority', () => {
    deviceLanguages(['zh-CN'])
    setLanguagePreference('system')
    expect(effectiveLocale.value).toBe('zh-Hans')
    deviceLanguages(['en-US'])
    expect(effectiveLocale.value).toBe('en')
    setLanguagePreference('zh-Hans')
    deviceLanguages(['de-DE'])
    expect(effectiveLocale.value).toBe('zh-Hans')
    setLanguagePreference('system')
    expect(effectiveLocale.value).toBe('en')
    expect(document.documentElement.lang).toBe('en')
    expect(intlLocale.value).toBe('en-US')
  })

  it('persists language independently of the signed-in account', () => {
    setLanguagePreference('en')
    clearAuthSession()
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en')
    expect(languagePreference.value).toBe('en')
  })

  it('accepts changes from another browser tab and rejects invalid preferences', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en')
    window.dispatchEvent(new StorageEvent('storage', { key: LANGUAGE_STORAGE_KEY, newValue: 'en' }))
    expect(languagePreference.value).toBe('en')
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'unsupported')
    window.dispatchEvent(new StorageEvent('storage', { key: LANGUAGE_STORAGE_KEY }))
    expect(languagePreference.value).toBe('system')
    deviceLanguages(['zh-CN'])
    localStorage.removeItem(LANGUAGE_STORAGE_KEY)
    window.dispatchEvent(new StorageEvent('storage', { key: LANGUAGE_STORAGE_KEY }))
    expect(effectiveLocale.value).toBe('zh-Hans')
  })

  it('keeps switching available when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('Storage disabled')
    })
    expect(() => setLanguagePreference('en')).not.toThrow()
    expect(effectiveLocale.value).toBe('en')
  })

  it('updates language from the public/settings selector without remounting', async () => {
    const wrapper = mount(LanguageSelector)
    await wrapper.find('select').setValue('en')
    expect(wrapper.text()).toContain('Use browser language')
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en')
    await wrapper.find('select').setValue('zh-Hans')
    expect(wrapper.text()).toContain('跟随浏览器')
    expect(wrapper.find('select').element.value).toBe('zh-Hans')
    wrapper.unmount()
  })
})

describe('complete catalogs and retained copy', () => {
  it('provides explicit translations and matching parameters for every UI key', () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(zh).sort())
    for (const key of Object.keys(zh) as Array<keyof typeof zh>) {
      expect(en[key].trim(), key).not.toBe('')
      const placeholders = (value: string) =>
        [...value.matchAll(/\{([a-zA-Z0-9_]+)\}/g)].map((m) => m[1]).sort()
      expect(placeholders(en[key]), key).toEqual(placeholders(zh[key]))
    }
  })

  it('renders retained UI and API messages reactively while leaving user text intact', async () => {
    const retained = uiMessage('ui.login.message001')
    const error = new ApiError({ errorCode: 'AUTH_020', params: { min: 8, max: 64 } }, 400)
    const retainedError = localizedError(error, 'ui.apiResult.message001')
    const wrapper = mount({
      setup: () => ({ retained, retainedError, renderMessage }),
      template: '<p>{{ renderMessage(retained) }} / {{ renderMessage(retainedError) }}</p>',
    })
    expect(wrapper.text()).toContain('登录')
    setLanguagePreference('en')
    await nextTick()
    expect(wrapper.text()).toContain('Sign in')
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).not.toMatch(/[\u3400-\u9fff]/)
    expect(error.message).toBe(
      resolveApiResult({ errorCode: 'AUTH_020', params: { min: 8, max: 64 } }, 400),
    )
    expect(renderMessage('用户自己填写的内容')).toBe('用户自己填写的内容')
    wrapper.unmount()
  })

  it('localizes known audit codes at display time and keeps historical audit prose', () => {
    const rawReason = 'AUTH_008'
    setLanguagePreference('en')
    expect(resolveAuditReason(rawReason)).toBe(apiResultMessagesByLocale.en.AUTH_008)
    expect(resolveAuditReason('历史原因：邮箱格式不正确')).toBe('历史原因：邮箱格式不正确')
    setLanguagePreference('zh-Hans')
    expect(resolveAuditReason(rawReason)).toBe(apiResultMessagesByLocale['zh-Hans'].AUTH_008)
  })

  it('uses English safe fallback for unknown codes or missing parameters', () => {
    setLanguagePreference('en')
    expect(resolveApiResult({ errorCode: 'toString' }, 403)).toBe(
      apiResultMessagesByLocale.en.FORBIDDEN,
    )
    expect(resolveApiResult({ errorCode: 'AUTH_020', params: { min: 8 } }, 400)).toBe(
      apiResultMessagesByLocale.en.BAD_REQUEST,
    )
    expect(t('ui.deleteAccount.message018')).toBe('DELETE MY ACCOUNT')
  })
})
