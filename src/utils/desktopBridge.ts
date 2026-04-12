import { createSessionTransfer, exchangeSessionTransfer } from '@/api/auth'
import { clearStoredUser, storeAuthSession } from '@/utils/authSession'

const DESKTOP_BRIDGE_BASE_URLS = ['http://127.0.0.1:43921', 'http://localhost:43921']
const BRIDGE_DISCOVERY_TIMEOUT_MS = 500
const BRIDGE_ACTION_TIMEOUT_MS = 10000

export interface DesktopBridgeUser {
  uuid: string
  username: string
  email: string
  avatarUrl?: string | null
}

export interface DesktopBridgeStatus {
  authenticated: boolean
  environmentName?: string
  apiBaseUrl?: string
  user?: DesktopBridgeUser
}

export interface DesktopBridgeExportResponse {
  transferCode: string
  expiresInSeconds: number
  user?: DesktopBridgeUser
}

let cachedBridgeBaseUrl: string | null | undefined

const withTimeout = async (
  input: RequestInfo | URL,
  init?: RequestInit,
  timeoutMs = BRIDGE_DISCOVERY_TIMEOUT_MS,
): Promise<Response> => {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort('bridge-timeout'), timeoutMs)

  try {
    return await fetch(input, {
      ...init,
      signal: controller.signal,
    })
  } finally {
    clearTimeout(timer)
  }
}

const normalizeBridgeError = (error: unknown, timeoutMs: number): Error => {
  if (error instanceof Error) {
    if (error.name === 'AbortError' || error.message.includes('signal is aborted')) {
      return new Error(`桌面端响应超时（>${Math.ceil(timeoutMs / 1000)} 秒），请确认桌面端仍在运行`)
    }
    return error
  }

  return new Error('桌面桥接请求失败')
}

const resolveBridgeBaseUrl = async (forceRefresh = false): Promise<string | null> => {
  if (!forceRefresh && cachedBridgeBaseUrl !== undefined) {
    return cachedBridgeBaseUrl
  }

  for (const baseUrl of DESKTOP_BRIDGE_BASE_URLS) {
    try {
      const response = await withTimeout(
        `${baseUrl}/ksuser-auth/bridge/status`,
        {
          method: 'GET',
          headers: {
            Accept: 'application/json',
          },
        },
        BRIDGE_DISCOVERY_TIMEOUT_MS,
      )

      if (!response.ok) {
        continue
      }

      cachedBridgeBaseUrl = baseUrl
      return baseUrl
    } catch {
      continue
    }
  }

  cachedBridgeBaseUrl = null
  return null
}

const requestBridge = async <T>(
  path: string,
  init?: RequestInit,
  forceRefresh = false,
  timeoutMs = BRIDGE_ACTION_TIMEOUT_MS,
): Promise<T> => {
  const baseUrl = await resolveBridgeBaseUrl(forceRefresh)
  if (!baseUrl) {
    throw new Error('桌面端未运行或未开启桥接服务')
  }

  let response: Response
  try {
    response = await withTimeout(
      `${baseUrl}${path}`,
      {
        ...init,
        headers: {
          Accept: 'application/json',
          ...(init?.headers || {}),
        },
      },
      timeoutMs,
    )
  } catch (error) {
    cachedBridgeBaseUrl = undefined
    throw normalizeBridgeError(error, timeoutMs)
  }

  const payload = (await response.json().catch(() => ({}))) as T & { message?: string }

  if (!response.ok) {
    throw new Error(payload.message || '桌面桥接请求失败')
  }

  return payload
}

export const getDesktopBridgeStatus = async (
  forceRefresh = false,
): Promise<DesktopBridgeStatus | null> => {
  try {
    return await requestBridge<DesktopBridgeStatus>(
      '/ksuser-auth/bridge/status',
      undefined,
      forceRefresh,
      BRIDGE_DISCOVERY_TIMEOUT_MS,
    )
  } catch {
    return null
  }
}

export const exchangeDesktopSessionToWeb = async () => {
  const exportResponse = await requestBridge<DesktopBridgeExportResponse>(
    '/ksuser-auth/bridge/export',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({}),
    },
    false,
    BRIDGE_ACTION_TIMEOUT_MS,
  )

  return exchangeSessionTransfer(exportResponse.transferCode, 'web')
}

export const syncCurrentWebSessionToDesktop = async (): Promise<boolean> => {
  try {
    const available = await resolveBridgeBaseUrl()
    if (!available) {
      return false
    }

    const transfer = await createSessionTransfer('desktop', 'session_sync')
    await requestBridge(
      '/ksuser-auth/bridge/import',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ transferCode: transfer.transferCode }),
      },
      false,
      BRIDGE_ACTION_TIMEOUT_MS,
    )
    return true
  } catch {
    return false
  }
}

export const storeWebSession = (accessToken: string, user?: unknown): void => {
  if (user != null) {
    storeAuthSession(accessToken, user)
    return
  }
  storeAuthSession(accessToken)
  clearStoredUser()
}

export const finalizeWebLogin = async ({
  accessToken,
  user,
  syncDesktop = true,
}: {
  accessToken: string
  user?: unknown
  syncDesktop?: boolean
}): Promise<boolean> => {
  storeWebSession(accessToken, user)
  if (!syncDesktop) {
    return false
  }
  return syncCurrentWebSessionToDesktop()
}
