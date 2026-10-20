import request from '@/utils/request'
import type { AuthorizationGrantMode } from '@/api/oauth2'

export interface NativeAuthorizationContext {
  ticket: string
  nativeAuthorization: true
  appName: string
  logoUrl?: string | null
  contactInfo?: string | null
  redirectUri: string
  requestedScopes: string[]
  alreadyAuthorized: boolean
  existingGrantMode: AuthorizationGrantMode
  expiresInSeconds: number
  officialApplication: boolean
  consoleApplication: boolean
  subjectName?: string | null
  subjectVerificationType?: string | null
}

export interface NativeAuthorizationReturn {
  returnUrl: string
  completed: true
}

export const readNativeAuthorizationTicket = (value: unknown): string | null =>
  typeof value === 'string' && /^[A-Za-z0-9_-]{32}$/.test(value) ? value : null

export const getNativeAuthorizationContext = async (
  ticket: string,
): Promise<NativeAuthorizationContext> =>
  (await request.get('/auth/native-authorization/context', { params: { ticket } })).data

export const decideNativeAuthorization = async (input: {
  ticket: string
  approve: boolean
  grantMode: AuthorizationGrantMode
  grantTtlSeconds?: number
}): Promise<NativeAuthorizationReturn> =>
  (await request.post('/auth/native-authorization/decide', input)).data

/** Match the registered callback before adding the server's OAuth response query. */
export const validatedNativeAuthorizationReturn = (
  result: NativeAuthorizationReturn,
  registeredRedirectUri: string,
): string | null => {
  try {
    if (result.completed !== true) return null
    const registered = new URL(registeredRedirectUri)
    const callback = new URL(result.returnUrl)
    if (
      registered.protocol !== 'https:' || registered.username || registered.password ||
      registered.search || registered.hash || callback.username || callback.password || callback.hash
    ) return null
    const destination = new URL(callback)
    destination.search = ''
    if (destination.href !== registered.href) return null
    const query = callback.searchParams
    if (query.getAll('state').length !== 1 || !query.get('state')) return null
    const codes = query.getAll('code')
    const errors = query.getAll('error')
    if (!((codes.length === 1 && !!codes[0] && errors.length === 0) ||
      (errors.length === 1 && !!errors[0] && codes.length === 0))) return null
    return callback.href
  } catch {
    return null
  }
}
