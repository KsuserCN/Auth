/**
 * WebAuthn 工具函数
 * 用于处理 Passkey 相关的浏览器 API 调用
 */

/**
 * 检查浏览器是否支持 WebAuthn
 */
export function isWebAuthnSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.PublicKeyCredential !== undefined &&
    typeof window.PublicKeyCredential === 'function'
  )
}

/**
 * Base64 字符串转 ArrayBuffer
 */
export function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binaryString = atob(base64)
  const bytes = new Uint8Array(binaryString.length)
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i)
  }
  return bytes.buffer
}

/**
 * Base64URL 字符串转 ArrayBuffer
 * Base64URL 使用 - 和 _ 替代 + 和 /，且没有填充
 */
export function base64UrlToArrayBuffer(base64url: string): ArrayBuffer {
  // 将 Base64URL 转换为标准 Base64
  let base64 = base64url.replace(/-/g, '+').replace(/_/g, '/')

  // 添加填充
  const padding = base64.length % 4
  if (padding > 0) {
    base64 += '='.repeat(4 - padding)
  }

  return base64ToArrayBuffer(base64)
}

/**
 * ArrayBuffer 转 Base64 字符串
 */
export function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]!)
  }
  return btoa(binary)
}

/**
 * ArrayBuffer 转 Base64URL 字符串
 * Base64URL 使用 - 和 _ 替代 + 和 /，且移除填充
 */
export function arrayBufferToBase64Url(buffer: ArrayBuffer): string {
  const base64 = arrayBufferToBase64(buffer)
  // 将标准 Base64 转换为 Base64URL
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

/**
 * 创建 Passkey 注册凭证
 */
export async function createPasskeyCredential(options: {
  challenge: string
  rp: string
  user: string
  pubKeyCredParams: string
  timeout: string
  attestation: string
  authenticatorSelection: string
}): Promise<PublicKeyCredential | null> {
  try {
    // 解析 JSON 字符串
    const rpData = typeof options.rp === 'string' ? JSON.parse(options.rp) : options.rp
    const userData = typeof options.user === 'string' ? JSON.parse(options.user) : options.user
    const pubKeyParamsData =
      typeof options.pubKeyCredParams === 'string'
        ? JSON.parse(options.pubKeyCredParams)
        : options.pubKeyCredParams
    const authenticatorSelectionData =
      typeof options.authenticatorSelection === 'string'
        ? JSON.parse(options.authenticatorSelection)
        : options.authenticatorSelection

    // 构建创建凭证的选项
    const credentialCreationOptions: CredentialCreationOptions = {
      publicKey: {
        challenge: base64UrlToArrayBuffer(options.challenge),
        rp: rpData,
        user: {
          id: base64UrlToArrayBuffer(userData.id),
          name: userData.name,
          displayName: userData.displayName,
        },
        pubKeyCredParams: pubKeyParamsData,
        timeout: parseInt(options.timeout),
        attestation: options.attestation as AttestationConveyancePreference,
        authenticatorSelection: authenticatorSelectionData,
      },
    }

    // 创建凭证
    const credential = (await navigator.credentials.create(
      credentialCreationOptions,
    )) as PublicKeyCredential | null

    return credential
  } catch (error) {
    console.error('创建 Passkey 失败:', error)
    throw error
  }
}

/**
 * 获取 Passkey 认证凭证
 */
export async function getPasskeyCredential(options: {
  challenge: string
  timeout: string
  rpId: string
  userVerification: string
}): Promise<PublicKeyCredential | null> {
  try {
    // 构建获取凭证的选项
    const credentialRequestOptions: CredentialRequestOptions = {
      publicKey: {
        challenge: base64UrlToArrayBuffer(options.challenge),
        timeout: parseInt(options.timeout),
        rpId: options.rpId,
        userVerification: options.userVerification as UserVerificationRequirement,
      },
    }

    // 获取凭证
    const credential = (await navigator.credentials.get(
      credentialRequestOptions,
    )) as PublicKeyCredential | null

    return credential
  } catch (error) {
    console.error('获取 Passkey 失败:', error)
    throw error
  }
}

/**
 * 从 PublicKeyCredential 提取注册数据
 */
export function extractRegistrationData(credential: PublicKeyCredential): {
  credentialRawId: string
  clientDataJSON: string
  attestationObject: string
  transports: string
} {
  const response = credential.response as AuthenticatorAttestationResponse

  return {
    credentialRawId: arrayBufferToBase64Url(credential.rawId),
    clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
    attestationObject: arrayBufferToBase64Url(response.attestationObject),
    transports: response.getTransports ? response.getTransports().join(',') : '',
  }
}

/**
 * 从 PublicKeyCredential 提取认证数据
 */
export function extractAuthenticationData(credential: PublicKeyCredential): {
  credentialRawId: string
  clientDataJSON: string
  authenticatorData: string
  signature: string
} {
  const response = credential.response as AuthenticatorAssertionResponse

  return {
    credentialRawId: arrayBufferToBase64Url(credential.rawId),
    clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
    authenticatorData: arrayBufferToBase64Url(response.authenticatorData),
    signature: arrayBufferToBase64Url(response.signature),
  }
}
