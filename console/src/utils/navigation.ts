import { consoleApi } from '@/api/console'

const RETURN_KEY = 'ksuser:console:verification-return'
export const safeReturnPath = (value: string | null | undefined): string =>
  value &&
  value.startsWith('/') &&
  !value.startsWith('//') &&
  !value.includes('\\') &&
  !value.startsWith('/auth/') &&
  !value.startsWith('/verification') &&
  !value.startsWith('/login')
    ? value
    : '/users'

export const beginVerification = async (returnPath: string): Promise<void> => {
  const response = await consoleApi.verification()
  sessionStorage.setItem(RETURN_KEY, safeReturnPath(returnPath))
  window.location.assign(response.authorizationUrl)
}
export const consumeVerificationReturn = (): string => {
  const value = sessionStorage.getItem(RETURN_KEY)
  sessionStorage.removeItem(RETURN_KEY)
  return safeReturnPath(value)
}

// Application drafts retain only editable form fields; secrets are never persisted here.
const DRAFT_KEY = 'ksuser:console:application-draft'
export interface ApplicationDraft {
  clientId: string
  clientName: string
  logoUrl: string
  clientType: 'public' | 'confidential'
  redirectText: string
  scopes: string[]
  isActive: boolean
  allowMissingNonce: boolean
  editing: boolean
}
export const preserveApplicationDraft = (draft: ApplicationDraft) =>
  sessionStorage.setItem(
    DRAFT_KEY,
    JSON.stringify({
      clientId: draft.clientId,
      clientName: draft.clientName,
      logoUrl: draft.logoUrl,
      clientType: draft.clientType,
      redirectText: draft.redirectText,
      scopes: draft.scopes,
      isActive: draft.isActive,
      allowMissingNonce: draft.allowMissingNonce,
      editing: draft.editing,
    }),
  )
export const clearApplicationDraft = () => sessionStorage.removeItem(DRAFT_KEY)
export const consumeApplicationDraft = (): ApplicationDraft | null => {
  const raw = sessionStorage.getItem(DRAFT_KEY)
  clearApplicationDraft()
  if (!raw) return null
  try {
    const value = JSON.parse(raw)
    if (
      !value ||
      typeof value.clientId !== 'string' ||
      typeof value.clientName !== 'string' ||
      typeof value.logoUrl !== 'string' ||
      typeof value.redirectText !== 'string' ||
      !['public', 'confidential'].includes(value.clientType) ||
      !Array.isArray(value.scopes) ||
      value.scopes.some((scope: unknown) => typeof scope !== 'string') ||
      typeof value.isActive !== 'boolean' ||
      typeof value.allowMissingNonce !== 'boolean' ||
      typeof value.editing !== 'boolean'
    )
      return null
    return value as ApplicationDraft
  } catch {
    return null
  }
}

const ROLE_DRAFT_KEY = 'ksuser:console:role-draft'
const USER_DELETE_DRAFT_KEY = 'ksuser:console:user-delete-draft'
export const clearConsoleNavigation = () => {
  clearApplicationDraft()
  sessionStorage.removeItem(ROLE_DRAFT_KEY)
  sessionStorage.removeItem(USER_DELETE_DRAFT_KEY)
  sessionStorage.removeItem(RETURN_KEY)
}
export const preserveRoleDraft = (uuid: string, systemRole: 'USER' | 'ADMIN') =>
  sessionStorage.setItem(ROLE_DRAFT_KEY, JSON.stringify({ uuid, systemRole }))
export const consumeRoleDraft = (): {
  uuid: string
  systemRole: 'USER' | 'ADMIN'
} | null => {
  const raw = sessionStorage.getItem(ROLE_DRAFT_KEY)
  sessionStorage.removeItem(ROLE_DRAFT_KEY)
  if (!raw) return null
  try {
    const value = JSON.parse(raw)
    return typeof value.uuid === 'string' && ['USER', 'ADMIN'].includes(value.systemRole)
      ? value
      : null
  } catch {
    return null
  }
}

export const preserveUserDeleteDraft = (uuid: string) =>
  sessionStorage.setItem(USER_DELETE_DRAFT_KEY, JSON.stringify({ uuid }))
export const consumeUserDeleteDraft = (): { uuid: string } | null => {
  const raw = sessionStorage.getItem(USER_DELETE_DRAFT_KEY)
  sessionStorage.removeItem(USER_DELETE_DRAFT_KEY)
  if (!raw) return null
  try {
    const value = JSON.parse(raw)
    return typeof value.uuid === 'string' ? value : null
  } catch {
    return null
  }
}
