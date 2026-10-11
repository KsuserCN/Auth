import { computed, ref, watch } from 'vue'
import { createI18n } from 'vue-i18n'
import zhHans from './zh-Hans.json'
import en from './en.json'

export type AppLocale = 'zh-Hans' | 'en'
export type LanguagePreference = 'system' | AppLocale
export const LANGUAGE_STORAGE_KEY = 'ksuser:language-preference'

export const isLanguagePreference = (value: unknown): value is LanguagePreference =>
  value === 'system' || value === 'zh-Hans' || value === 'en'

/** The first device language decides the app language; unsupported languages use English. */
export const resolveDeviceLocale = (languages: readonly string[]): AppLocale =>
  /^zh(?:-|$)/i.test(languages[0] || '') ? 'zh-Hans' : 'en'

const readPreference = (): LanguagePreference => {
  try {
    const stored = localStorage.getItem(LANGUAGE_STORAGE_KEY)
    return isLanguagePreference(stored) ? stored : 'system'
  } catch {
    return 'system'
  }
}
const browserLanguages = () =>
  typeof navigator === 'undefined'
    ? []
    : navigator.languages?.length
      ? navigator.languages
      : [navigator.language]

export const languagePreference = ref<LanguagePreference>(readPreference())
const deviceLocale = ref(resolveDeviceLocale(browserLanguages()))
export const effectiveLocale = computed<AppLocale>(() =>
  languagePreference.value === 'system' ? deviceLocale.value : languagePreference.value,
)

const catalogs: Record<AppLocale, Record<string, string>> = {
  'zh-Hans': { ...zhHans },
  en: { ...en },
}
export const i18n = createI18n({
  legacy: false,
  flatJson: true,
  locale: effectiveLocale.value,
  fallbackLocale: 'en',
  messages: catalogs,
  missingWarn: false,
  fallbackWarn: false,
})

export const setLanguagePreference = (value: LanguagePreference) => {
  languagePreference.value = value
  try {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, value)
  } catch {
    /* private storage may be unavailable */
  }
}

watch(
  effectiveLocale,
  (locale) => {
    i18n.global.locale.value = locale
    if (typeof document !== 'undefined') document.documentElement.lang = locale
  },
  { immediate: true, flush: 'sync' },
)

if (typeof window !== 'undefined') {
  window.addEventListener('languagechange', () => {
    deviceLocale.value = resolveDeviceLocale(browserLanguages())
  })
  window.addEventListener('storage', (event) => {
    if (event.key === LANGUAGE_STORAGE_KEY || event.key === null)
      languagePreference.value = readPreference()
  })
}

export const t = (key: string, params: Record<string, unknown> = {}): string => {
  void effectiveLocale.value
  return i18n.global.t(key, params)
}

/** Retained UI copy stores a key/parameters or an API metadata renderer, never a translated string. */
export type MessageDescriptor =
  | { key: string; params?: Record<string, unknown> }
  | { render: () => string }
export type LocalizedMessage = string | MessageDescriptor
export const uiMessage = (key: string, params?: Record<string, unknown>): MessageDescriptor => ({
  key,
  params,
})
export const renderMessage = (value: LocalizedMessage): string => {
  void effectiveLocale.value
  return typeof value === 'string'
    ? value
    : 'key' in value
      ? t(value.key, value.params)
      : value.render()
}
export const intlLocale = computed(() => (effectiveLocale.value === 'zh-Hans' ? 'zh-CN' : 'en-US'))

/** Client validation/helper errors keep their message key when a page retains them. */
export class LocalizedError extends Error {
  readonly localizationKey: string
  readonly localizationParams: Record<string, unknown>
  constructor(key: string, params: Record<string, unknown> = {}) {
    super(t(key, params))
    this.name = 'LocalizedError'
    this.localizationKey = key
    this.localizationParams = params
    Object.defineProperty(this, 'message', {
      configurable: true,
      get: () => t(this.localizationKey, this.localizationParams),
    })
  }
}

export const relativeTime = (value: number, unit: Intl.RelativeTimeFormatUnit): string =>
  new Intl.RelativeTimeFormat(intlLocale.value, { numeric: 'always' }).format(value, unit)
export const durationUnit = (value: number, unit: 'second' | 'minute' | 'hour' | 'day'): string =>
  new Intl.NumberFormat(intlLocale.value, { style: 'unit', unit, unitDisplay: 'long' }).format(
    value,
  )
