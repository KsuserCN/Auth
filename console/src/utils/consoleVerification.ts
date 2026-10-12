import { ref } from 'vue'
import { ConsoleApiError, consoleApi } from '@/api/console'

export const verificationDialogVisible = ref(false)
export const verificationRequestId = ref('')

let activeVerification: Promise<boolean> | null = null
let resolveVerification: ((verified: boolean) => void) | null = null

export class VerificationCancelledError extends Error {
  constructor() {
    super('身份验证已取消')
    this.name = 'VerificationCancelledError'
  }
}

export function requestConsoleVerification(): Promise<boolean> {
  if (activeVerification) return activeVerification

  const current = (async () => {
    try {
      const flow = await consoleApi.verification()
      if (!flow.requestId) throw new Error('无法开始身份验证，请重试。')
      verificationRequestId.value = flow.requestId
      verificationDialogVisible.value = true
      return await new Promise<boolean>((resolve) => {
        resolveVerification = resolve
      })
    } finally {
      verificationDialogVisible.value = false
      verificationRequestId.value = ''
      resolveVerification = null
    }
  })()

  activeVerification = current
  void current.then(
    () => {
      if (activeVerification === current) activeVerification = null
    },
    () => {
      if (activeVerification === current) activeVerification = null
    },
  )
  return current
}

export function completeConsoleVerification(verified: boolean): void {
  const resolve = resolveVerification
  resolveVerification = null
  verificationDialogVisible.value = false
  resolve?.(verified)
}

export async function withConsoleVerification<T>(operation: () => Promise<T>): Promise<T> {
  try {
    return await operation()
  } catch (failure) {
    if (
      !(failure instanceof ConsoleApiError) ||
      failure.errorCode !== 'SENSITIVE_VERIFICATION_REQUIRED'
    ) {
      throw failure
    }

    if (!(await requestConsoleVerification())) throw new VerificationCancelledError()
    return operation()
  }
}
