import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuthSession, getStoredAccessToken, setStoredAccessToken } from '@/utils/authSession'

type RetryableRequestConfig = InternalAxiosRequestConfig & {
  _refreshRetried?: boolean
  _skipAuthRefresh?: boolean
  _suppressErrorToast?: boolean
}

// API 响应格式
export interface ApiResponse<T = any> {
  code: number
  msg: string
  data: T
}

const defaultApiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000'

export const getRequestBaseUrl = (): string => {
  return String(request.defaults.baseURL || defaultApiBaseUrl)
}

export const setRequestBaseUrl = (value?: string): void => {
  request.defaults.baseURL = value?.trim() || defaultApiBaseUrl
}

// 创建 axios 实例
const request: AxiosInstance = axios.create({
  baseURL: defaultApiBaseUrl,
  timeout: 30000,
  withCredentials: true, // 允许携带 Cookie
  xsrfCookieName: 'XSRF-TOKEN', // 从 Cookie 中读取的 CSRF token 名称
  xsrfHeaderName: 'X-XSRF-TOKEN', // 发送到服务器的请求头名称
  headers: {
    'Content-Type': 'application/json',
  },
})

const REFRESH_LOCK_KEY = 'ksuser:auth:refresh-lock'
const REFRESH_EVENT_KEY = 'ksuser:auth:refresh-event'
const REFRESH_LOCK_TTL_MS = 10000
const REFRESH_WAIT_TIMEOUT_MS = 12000
const REFRESH_WAIT_POLL_MS = 250
const refreshOwnerId = `${Date.now()}-${Math.random().toString(36).slice(2)}`

// 是否正在刷新 Token
let isRefreshing = false
// 等待刷新的请求队列
let refreshSubscribers: Array<{
  resolve: (token: string) => void
  reject: (error: Error) => void
}> = []
let refreshPromise: Promise<string> | null = null

// 添加到刷新队列
const subscribeTokenRefresh = (
  resolve: (token: string) => void,
  reject: (error: Error) => void,
) => {
  refreshSubscribers.push({ resolve, reject })
}

// 通知所有等待的请求
const onRefreshed = (token: string) => {
  refreshSubscribers.forEach((subscriber) => subscriber.resolve(token))
  refreshSubscribers = []
}

const onRefreshFailed = (error: Error) => {
  refreshSubscribers.forEach((subscriber) => subscriber.reject(error))
  refreshSubscribers = []
}

interface RefreshLock {
  owner: string
  expiresAt: number
}

interface RefreshEvent {
  owner: string
  status: 'success' | 'error'
  accessToken?: string
  message?: string
  at: number
}

const readRefreshLock = (): RefreshLock | null => {
  try {
    const raw = localStorage.getItem(REFRESH_LOCK_KEY)
    if (!raw) {
      return null
    }
    return JSON.parse(raw) as RefreshLock
  } catch {
    return null
  }
}

const tryAcquireRefreshLock = (): boolean => {
  const current = readRefreshLock()
  const now = Date.now()
  if (current && current.expiresAt > now && current.owner !== refreshOwnerId) {
    return false
  }

  const nextLock: RefreshLock = {
    owner: refreshOwnerId,
    expiresAt: now + REFRESH_LOCK_TTL_MS,
  }
  localStorage.setItem(REFRESH_LOCK_KEY, JSON.stringify(nextLock))
  const confirmed = readRefreshLock()
  return confirmed?.owner === refreshOwnerId
}

const releaseRefreshLock = (): void => {
  const current = readRefreshLock()
  if (current?.owner === refreshOwnerId) {
    localStorage.removeItem(REFRESH_LOCK_KEY)
  }
}

const publishRefreshEvent = (event: RefreshEvent): void => {
  localStorage.setItem(REFRESH_EVENT_KEY, JSON.stringify(event))
}

const readRefreshEvent = (): RefreshEvent | null => {
  try {
    const raw = localStorage.getItem(REFRESH_EVENT_KEY)
    if (!raw) {
      return null
    }
    return JSON.parse(raw) as RefreshEvent
  } catch {
    return null
  }
}

const waitForRefreshResult = (previousToken: string | null): Promise<string> => {
  const startedAt = Date.now()

  return new Promise((resolve, reject) => {
    let settled = false
    let pollTimer: number | null = null
    let timeoutTimer: number | null = null

    const cleanup = () => {
      window.removeEventListener('storage', handleStorage)
      if (pollTimer != null) {
        window.clearInterval(pollTimer)
      }
      if (timeoutTimer != null) {
        window.clearTimeout(timeoutTimer)
      }
    }

    const resolveWithToken = (token: string) => {
      if (settled) {
        return
      }
      settled = true
      cleanup()
      setStoredAccessToken(token)
      resolve(token)
    }

    const rejectWithMessage = (message: string) => {
      if (settled) {
        return
      }
      settled = true
      cleanup()
      reject(new Error(message))
    }

    const inspectSharedRefreshState = () => {
      const token = getStoredAccessToken()
      if (token && token !== previousToken) {
        resolveWithToken(token)
        return
      }

      const event = readRefreshEvent()
      if (event && event.at >= startedAt) {
        if (event.status === 'success' && event.accessToken) {
          resolveWithToken(event.accessToken)
          return
        }
        if (event.status === 'error') {
          rejectWithMessage(event.message || '刷新 Token 失败')
        }
      }
    }

    const handleStorage = (event: StorageEvent) => {
      if (event.key === REFRESH_EVENT_KEY || event.key === 'accessToken' || event.key === null) {
        inspectSharedRefreshState()
      }
    }

    window.addEventListener('storage', handleStorage)
    pollTimer = window.setInterval(inspectSharedRefreshState, REFRESH_WAIT_POLL_MS)
    timeoutTimer = window.setTimeout(() => {
      rejectWithMessage('等待其他标签页刷新登录态超时，请重试')
    }, REFRESH_WAIT_TIMEOUT_MS)

    inspectSharedRefreshState()
  })
}

const refreshTokenWithCoordination = async (): Promise<string> => {
  if (refreshPromise) {
    return refreshPromise
  }

  refreshPromise = (async () => {
    const previousToken = getStoredAccessToken()

    if (!tryAcquireRefreshLock()) {
      return waitForRefreshResult(previousToken)
    }

    try {
      const newToken = await refreshToken()
      publishRefreshEvent({
        owner: refreshOwnerId,
        status: 'success',
        accessToken: newToken,
        at: Date.now(),
      })
      return newToken
    } catch (error) {
      const message = error instanceof Error ? error.message : '刷新 Token 失败'
      publishRefreshEvent({
        owner: refreshOwnerId,
        status: 'error',
        message,
        at: Date.now(),
      })
      throw error
    } finally {
      releaseRefreshLock()
      refreshPromise = null
    }
  })()

  return refreshPromise
}

const shouldSuppressErrorToast = (config?: RetryableRequestConfig): boolean => {
  return Boolean(config?._suppressErrorToast)
}

// 刷新 Token
const refreshToken = async (): Promise<string> => {
  try {
    const response = await request.post<ApiResponse<{ accessToken: string }>>('/auth/refresh', {})

    // 拦截器直接返回 ApiResponse，所以直接访问 response.data
    const apiResponse = response as any as ApiResponse<{ accessToken: string }>
    if (apiResponse.code === 200 && apiResponse.data?.accessToken) {
      const newToken = apiResponse.data.accessToken
      setStoredAccessToken(newToken)
      return newToken
    }

    throw new Error('刷新 Token 失败')
  } catch (error) {
    clearAuthSession()
    throw error
  }
}

// 从 Cookie 中获取指定的值
const getCookieValue = (name: string): string => {
  const value = `; ${document.cookie}`
  const parts = value.split(`; ${name}=`)
  if (parts.length === 2) return parts.pop()?.split(';').shift() || ''
  return ''
}

// 请求拦截器
request.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    if (config.data instanceof FormData && config.headers) {
      const headers = config.headers as Record<string, unknown> & {
        setContentType?: (value?: string) => void
      }
      headers.setContentType?.(undefined)
      delete headers['Content-Type']
      delete headers['content-type']
    }

    const token = getStoredAccessToken()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 检查 Cookie 中是否有 XSRF-TOKEN，如果没有则请求一次获取
    const xsrfToken = getCookieValue('XSRF-TOKEN')
    if (!xsrfToken) {
      try {
        await axios.get(`${getRequestBaseUrl()}/auth/health`, {
          withCredentials: true,
        })
      } catch (error) {
        console.warn('Failed to fetch XSRF token from /auth/health:', error)
      }
    }

    // 从 Cookie 中读取 XSRF-TOKEN 并添加到请求头
    const currentXsrfToken = getCookieValue('XSRF-TOKEN')
    if (currentXsrfToken && config.headers) {
      config.headers['X-XSRF-TOKEN'] = currentXsrfToken
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const data = response.data
    const { code, msg } = data

    // 成功响应 (200)、MFA 需求 (201)、未绑定账号或需要验证 (202) - 直接返回 ApiResponse 对象
    if (code === 200 || code === 201 || code === 202) {
      return response.data as any
    }

    // 其他错误码
    if (!shouldSuppressErrorToast(response.config as RetryableRequestConfig)) {
      ElMessage.error(msg || '请求失败')
    }
    return Promise.reject(new Error(msg || '请求失败'))
  },
  async (error) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined

    // 网络错误或服务器错误
    if (!error.response) {
      if (!shouldSuppressErrorToast(originalRequest)) {
        ElMessage.error('网络连接失败，请检查网络')
      }
      return Promise.reject(error)
    }

    const { status, data, config } = error.response

    // 401 未授权 - AccessToken 过期或无效
    if (status === 401) {
      const originalRequest = config as RetryableRequestConfig

      // 如果是登录相关接口（登录、刷新等）返回 401，直接返回错误，不尝试刷新
      const authEndpoints = [
        '/auth/login',
        '/auth/refresh',
        '/auth/email-login',
        '/auth/passkey-login',
      ]
      const isAuthEndpoint = authEndpoints.some((endpoint) =>
        originalRequest.url?.includes(endpoint),
      )

      if (isAuthEndpoint) {
        // 登录接口的 401 错误直接显示并返回，不做其他处理
        const errorMsg = data?.msg || '认证失败'
        if (!shouldSuppressErrorToast(originalRequest)) {
          ElMessage.error(errorMsg)
        }
        return Promise.reject(new Error(errorMsg))
      }

      if (originalRequest._skipAuthRefresh) {
        const errorMsg = data?.msg || '登录状态已失效，请重新登录'
        clearAuthSession()
        if (!shouldSuppressErrorToast(originalRequest)) {
          ElMessage.error(errorMsg)
        }
        return Promise.reject(new Error(errorMsg))
      }

      // 同一个请求最多只做一次 refresh + retry，避免认证异常时无限重放
      if (originalRequest._refreshRetried) {
        const errorMsg = data?.msg || '登录状态已失效，请重新登录'
        clearAuthSession()
        if (!shouldSuppressErrorToast(originalRequest)) {
          ElMessage.error(errorMsg)
        }
        return Promise.reject(new Error(errorMsg))
      }
      originalRequest._refreshRetried = true

      // 如果正在刷新 Token，将请求加入队列
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          subscribeTokenRefresh(
            (token: string) => {
              if (originalRequest.headers) {
                originalRequest.headers.Authorization = `Bearer ${token}`
              }
              resolve(request(originalRequest))
            },
            (refreshError: Error) => {
              reject(refreshError)
            },
          )
        })
      }

      // 开始刷新 Token
      isRefreshing = true

      try {
        const newToken = await refreshTokenWithCoordination()
        isRefreshing = false
        onRefreshed(newToken)

        // 用新 Token 重试原请求
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
        }
        return request(originalRequest)
      } catch (refreshError) {
        isRefreshing = false
        const normalizedError =
          refreshError instanceof Error ? refreshError : new Error('认证已失效，请重新登录')
        onRefreshFailed(normalizedError)
        return Promise.reject(normalizedError)
      }
    }

    // 其他错误状态码
    const errorMsg = data?.msg || '请求失败'
    if (!shouldSuppressErrorToast(config as RetryableRequestConfig)) {
      ElMessage.error(errorMsg)
    }
    return Promise.reject(new Error(errorMsg))
  },
)

export default request
