import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import {
  decideNativeAuthorization,
  getNativeAuthorizationContext,
  readNativeAuthorizationTicket,
  validatedNativeAuthorizationReturn,
} from './nativeAuthorization'

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: mocks }))
const ticket = 'a'.repeat(32)
const callback = 'https://client.example/ksuser/callback'

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockResolvedValue({ data: { ticket } })
  mocks.post.mockResolvedValue({ data: { completed: true, returnUrl: callback } })
})

describe('native authorization web boundary', () => {
  it('uses the frozen ticket for context and decision without receiving SDK credentials', async () => {
    await getNativeAuthorizationContext(ticket)
    const input = { ticket, approve: true, grantMode: 'TIME_LIMITED' as const, grantTtlSeconds: 3600 }
    await decideNativeAuthorization(input)
    expect(mocks.get).toHaveBeenCalledWith('/auth/native-authorization/context', { params: { ticket } })
    expect(mocks.post).toHaveBeenCalledWith('/auth/native-authorization/decide', input)
  })

  it('rejects malformed and duplicate ticket query parameters', () => {
    expect(readNativeAuthorizationTicket(ticket)).toBe(ticket)
    for (const value of ['', 'invalid', [ticket, ticket], undefined, 'a'.repeat(33)]) {
      expect(readNativeAuthorizationTicket(value)).toBeNull()
    }
  })

  it.each(['code=server-code&state=original', 'error=access_denied&state=original'])('allows a standard response at the exact registered callback: %s', (query) => {
    const returnUrl = callback + '?' + query
    expect(validatedNativeAuthorizationReturn({ completed: true, returnUrl }, callback)).toBe(returnUrl)
  })

  it.each([
    'https://client.example.evil.test/ksuser/callback?code=c&state=s',
    'https://client.example/other?code=c&state=s',
    'http://client.example/ksuser/callback?code=c&state=s',
    callback + '?code=c&state=s#fragment',
    callback + '?code=c&code=other&state=s',
    callback + '?code=c&error=denied&state=s',
    callback + '?code=c&state=s&state=other',
    callback + '?code=c',
    callback + '?code=&state=s',
    'https://user:password@client.example/ksuser/callback?code=c&state=s',
  ])('blocks a modified or ambiguous callback: %s', (returnUrl) => {
    expect(validatedNativeAuthorizationReturn({ completed: true, returnUrl }, callback)).toBeNull()
  })

  it('refuses a registered callback that already contains response parameters', () => {
    expect(validatedNativeAuthorizationReturn({ completed: true, returnUrl: callback + '?code=c&state=s' }, callback + '?state=s')).toBeNull()
  })

  it('associates only the app entry path, leaving browser consent and return paths on the web', () => {
    const aasa = JSON.parse(readFileSync('public/.well-known/apple-app-site-association', 'utf8'))
    const paths = aasa.applinks.details.flatMap((entry: { components: { '/': string }[] }) => entry.components.map((component) => component['/']))
    expect(paths).toContain('/app/sdk-authorize')
    expect(paths).not.toContain('/app/sdk-authorize/web')
    expect(paths).not.toContain('/app/authorize-return')
    expect(paths).not.toContain('/app/sdk-authorize/*')
  })
})
