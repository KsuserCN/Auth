import axios from 'axios'
import type {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'

// API 响应格式
export interface ApiResponse<T = any> {
  code: number
  msg: string
  data: T
}

// 创建 axios 实例
const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000',
  timeout: 30000,
  withCredentials: true, // 允许携带 Cookie
  headers: {
    'Content-Type': 'application/json',
  },
})

// 是否正在刷新 Token
let isRefreshing = false
// 等待刷新的请求队列
let refreshSubscribers: Array<(token: string) => void> = []

// 添加到刷新队列
const subscribeTokenRefresh = (callback: (token: string) => void) => {
  refreshSubscribers.push(callback)
}

// 通知所有等待的请求
const onRefreshed = (token: string) => {
  refreshSubscribers.forEach((callback) => callback(token))
  refreshSubscribers = []
}

// 刷新 Token
const refreshToken = async (): Promise<string> => {
  try {
    const response = await axios.post<ApiResponse<{ accessToken: string }>>(
      `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000'}/auth/refresh`,
      {},
      {
        withCredentials: true, // 携带 refreshToken Cookie
      },
    )

    if (response.data.code === 200 && response.data.data?.accessToken) {
      const newToken = response.data.data.accessToken
      // 更新 sessionStorage 中的 token
      sessionStorage.setItem('accessToken', newToken)
      return newToken
    }

    throw new Error('刷新 Token 失败')
  } catch (error) {
    // 刷新失败，清除本地存储并跳转到登录页
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('user')
    window.location.href = '/login'
    throw error
  }
}

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从 sessionStorage 获取 accessToken
    const token = sessionStorage.getItem('accessToken')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
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
    const { code, msg, data } = response.data

    // 成功响应
    if (code === 200) {
      return response.data
    }

    // 其他错误码
    ElMessage.error(msg || '请求失败')
    return Promise.reject(new Error(msg || '请求失败'))
  },
  async (error) => {
    // 网络错误或服务器错误
    if (!error.response) {
      ElMessage.error('网络连接失败，请检查网络')
      return Promise.reject(error)
    }

    const { status, data, config } = error.response

    // 401 未授权 - AccessToken 过期或无效
    if (status === 401) {
      const originalRequest = config

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
        ElMessage.error(errorMsg)
        return Promise.reject(new Error(errorMsg))
      }

      // 如果正在刷新 Token，将请求加入队列
      if (isRefreshing) {
        return new Promise((resolve) => {
          subscribeTokenRefresh((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(request(originalRequest))
          })
        })
      }

      // 开始刷新 Token
      isRefreshing = true

      try {
        const newToken = await refreshToken()
        isRefreshing = false
        onRefreshed(newToken)

        // 用新 Token 重试原请求
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return request(originalRequest)
      } catch (refreshError) {
        isRefreshing = false
        return Promise.reject(refreshError)
      }
    }

    // 其他错误状态码
    const errorMsg = data?.msg || '请求失败'
    ElMessage.error(errorMsg)
    return Promise.reject(new Error(errorMsg))
  },
)

export default request
