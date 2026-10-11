import { effectiveLocale } from '@/i18n'

/** Build a documentation URL for the language currently selected in the app. */
export const documentUrl = (path: string): string => {
  const languagePrefix = effectiveLocale.value === 'en' ? '/en' : ''
  const normalizedPath = path.replace(/^\/+|\/+$/g, '')
  return `https://docs.ksuser.cn${languagePrefix}/${normalizedPath}`
}
