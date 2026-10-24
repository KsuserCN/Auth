import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import QRCode from 'qrcode'
import DownloadsView from './DownloadsView.vue'
import { setLanguagePreference } from '@/i18n'
import { mobileDownloadPageUrl } from '@/utils/appDownloads'

const qrMock = vi.hoisted(() => ({ toDataURL: vi.fn<() => Promise<string>>() }))
vi.mock('qrcode', () => ({ default: qrMock }))
const release = {
  versionCode: 42,
  versionName: '2.3.0',
  apkUrl: 'https://cdn.example.com/auth-42.apk',
  sha256: 'b'.repeat(64),
  releaseNotes: 'A release from the manifest',
}
const wrappers: ReturnType<typeof mount>[] = []
const render = () => {
  const wrapper = mount(DownloadsView, { global: { stubs: { RouterLink: RouterLinkStub } } })
  wrappers.push(wrapper)
  return wrapper
}
const jsonResponse = () =>
  new Response(JSON.stringify(release), { headers: { 'Content-Type': 'application/json' } })
const clipboardDescriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard')

beforeEach(() => {
  qrMock.toDataURL.mockReset().mockResolvedValue('data:image/png;base64,fixture')
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse()))
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  document.documentElement.classList.remove('dark')
  vi.restoreAllMocks()
  if (clipboardDescriptor) Object.defineProperty(navigator, 'clipboard', clipboardDescriptor)
  else Reflect.deleteProperty(navigator, 'clipboard')
  vi.unstubAllGlobals()
  setLanguagePreference('zh-Hans')
})

describe('mobile app download page', () => {
  it.each([
    ['Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 Mobile', 'android'],
    ['Mozilla/5.0 (Linux; Android 15; Tablet) AppleWebKit/537.36', 'android'],
    ['Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X)', 'ios'],
    ['Mozilla/5.0 (iPad; CPU OS 19_0 like Mac OS X)', 'ios'],
    ['Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', 'ios'],
    ['Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'ios'],
    ['', 'ios'],
  ])('shows the actual %s device preview for platform %s', async (userAgent, platform) => {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(userAgent)
    const wrapper = render()
    await flushPromises()
    const screenshot = wrapper.get('img.app-screenshot')
    expect(screenshot.attributes('src')).toBe(`/images/app-preview/${platform}-light.png`)
    expect(screenshot.attributes('alt')).toContain(platform === 'android' ? 'Android' : 'iOS')
  })

  it('follows the existing site theme when choosing real screenshots', async () => {
    document.documentElement.classList.add('dark')
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('img.app-screenshot').attributes('src')).toContain('-dark.png')
    document.documentElement.classList.remove('dark')
    await flushPromises()
    expect(wrapper.get('img.app-screenshot').attributes('src')).toContain('-light.png')
    document.documentElement.classList.add('dark')
    await flushPromises()
    expect(wrapper.get('img.app-screenshot').attributes('src')).toContain('-dark.png')
  })

  it('uses the published manifest for version, download URL, hash and release notes', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('[data-testid="android-download"]').attributes('href')).toBe(release.apkUrl)
    expect(wrapper.text()).toContain('2.3.0')
    expect(wrapper.text()).toContain('构建 42')
    expect(wrapper.get('.apk-hash').text()).toContain(release.sha256)
    expect(wrapper.get('.release-notes').text()).toBe(release.releaseNotes)
    expect(QRCode.toDataURL).toHaveBeenCalledWith(mobileDownloadPageUrl, expect.any(Object))
  })

  it('keeps iOS available after a manifest failure and supports retrying Android', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response('<html>SPA</html>', { headers: { 'Content-Type': 'text/html' } }),
    )
    const wrapper = render()
    await flushPromises()
    expect(wrapper.find('[data-testid="android-download"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="ios-download"]').attributes('href')).toContain(
      'apps.apple.com/cn/',
    )
    expect(wrapper.text()).toContain('暂时无法获取 Android 下载信息')
    await wrapper.get('button.android-button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="android-download"]').attributes('href')).toBe(release.apkUrl)
  })

  it('switches page copy, title, documentation and App Store region without refetching', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('select').setValue('en')
    expect(wrapper.get('h1').text()).toContain('Account security,')
    expect(document.title).toBe('Download Ksuser Auth · Official mobile app')
    expect(wrapper.get('[data-testid="ios-download"]').attributes('href')).toContain(
      'apps.apple.com/us/',
    )
    expect(wrapper.get('footer a[href*="agreement/privacy"]').attributes('href')).toContain(
      '/en/agreement/privacy',
    )
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('copies the canonical public download page, not a login or authorization URL', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.copy-download-link').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('https://auth.ksuser.cn/downloads')
    expect(wrapper.get('.copy-download-link').text()).toBe('链接已复制')
  })

  it('offers a manual link when clipboard access fails', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockRejectedValue(new Error('Denied')) },
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.copy-download-link').trigger('click')
    await flushPromises()
    expect(wrapper.get('.copy-failure').text()).toContain('请手动复制下方链接')
    expect(wrapper.get('.canonical-link').attributes('href')).toBe(mobileDownloadPageUrl)
  })

  it('cancels the public release request when leaving the page', async () => {
    let signal: AbortSignal | undefined
    vi.mocked(fetch).mockImplementation(
      (_url, options) =>
        new Promise((_accept, reject) => {
          signal = options?.signal as AbortSignal
          signal.addEventListener('abort', () => reject(new Error('Aborted')))
        }),
    )
    const wrapper = render()
    expect(signal?.aborted).toBe(false)
    wrapper.unmount()
    wrappers.pop()
    await flushPromises()
    expect(signal?.aborted).toBe(true)
  })
})
