/**
 * JWT 解析工具
 */

// JWT Token 的 Payload 类型
export interface JWTPayload {
  sub?: string // subject（用户ID或用户名）
  exp?: number // expiration time（过期时间）
  iat?: number // issued at（签发时间）
  email?: string
  username?: string
  id?: number | string
  uuid?: string
  [key: string]: any // 允许其他自定义字段
}

/**
 * 解析 JWT Token
 * @param token JWT Token 字符串
 * @returns 解析后的 payload 对象，失败返回 null
 */
export const parseJWT = (token: string): JWTPayload | null => {
  try {
    // JWT 格式：header.payload.signature
    const parts = token.split('.')
    if (parts.length !== 3) {
      console.error('Invalid JWT format')
      return null
    }

    // 获取 payload 部分（第二部分）
    const payload = parts[1]
    if (!payload) {
      return null
    }

    // Base64URL 解码
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    )

    // 解析 JSON
    return JSON.parse(jsonPayload)
  } catch (error) {
    console.error('Failed to parse JWT:', error)
    return null
  }
}

/**
 * 检查 JWT Token 是否过期
 * @param token JWT Token 字符串
 * @returns true 表示已过期，false 表示未过期
 */
export const isTokenExpired = (token: string): boolean => {
  const payload = parseJWT(token)
  if (!payload || !payload.exp) {
    return true
  }

  // exp 是以秒为单位的时间戳，需要转换为毫秒
  const expirationTime = payload.exp * 1000
  const currentTime = Date.now()

  return currentTime >= expirationTime
}

/**
 * 从 sessionStorage 获取并解析当前用户的 Access Token
 * @returns 解析后的 payload 对象，失败返回 null
 */
export const getCurrentUserFromToken = (): JWTPayload | null => {
  const token = sessionStorage.getItem('accessToken')
  if (!token) {
    return null
  }

  return parseJWT(token)
}

/**
 * 格式化时间戳为可读字符串
 * @param timestamp 时间戳（秒）
 * @returns 格式化的时间字符串
 */
export const formatTimestamp = (timestamp: number): string => {
  const date = new Date(timestamp * 1000)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}
