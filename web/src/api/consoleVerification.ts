import request from '@/utils/request'

export interface ConsoleVerificationContext {
  requestId: string
  consoleName: string
  hasPassword: boolean
}

export const getConsoleVerificationContext = async (requestId: string) => {
  const response = await request.get<ConsoleVerificationContext>('/auth/console-verification/context', {
    params: { request_id: requestId },
  })
  return response.data as ConsoleVerificationContext
}

export const completeConsoleVerification = async (requestId: string) => {
  const response = await request.post<{ redirectUrl: string }>('/auth/console-verification/complete', { requestId })
  return response.data as { redirectUrl: string }
}
