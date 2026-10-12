import { computed, ref } from 'vue'

export type ConsoleThemeMode = 'light' | 'dark' | 'system'

const THEME_MODE_KEY = 'theme-mode'
const THEME_PREFERENCE_KEY = 'theme-preference'

function readThemeMode(): ConsoleThemeMode {
  if (typeof window === 'undefined') return 'system'
  const stored = window.localStorage.getItem(THEME_MODE_KEY)
  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system'
}

export const themeMode = ref<ConsoleThemeMode>(readThemeMode())
export const currentThemeIcon = computed(() => themeMode.value)

const systemTheme =
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null

function applyTheme() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return
  const dark =
    themeMode.value === 'dark' || (themeMode.value === 'system' && systemTheme?.matches === true)
  document.documentElement.classList.toggle('dark', dark)
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light'
  window.localStorage.setItem(THEME_PREFERENCE_KEY, dark ? 'dark' : 'light')
}

export function setThemeMode(mode: ConsoleThemeMode) {
  themeMode.value = mode
  if (typeof window === 'undefined') return
  window.localStorage.setItem(THEME_MODE_KEY, mode)
  applyTheme()
}

applyTheme()

if (typeof window !== 'undefined') {
  if (window.localStorage.getItem(THEME_MODE_KEY) === null) {
    window.localStorage.setItem(THEME_MODE_KEY, themeMode.value)
  }

  systemTheme?.addEventListener('change', () => {
    if (themeMode.value === 'system') applyTheme()
  })

  window.addEventListener('storage', (event) => {
    if (event.key !== THEME_MODE_KEY) return
    const mode = event.newValue
    if (mode === 'light' || mode === 'dark' || mode === 'system') {
      themeMode.value = mode
      applyTheme()
    }
  })
}
