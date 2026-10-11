import { describe, expect, it } from 'vitest'
import { apiResultMessages } from '@/generated/apiResultCodes'
import { ApiError, resolveApiResult, resolveAuditReason, unwrapApiResult } from './apiResult'

describe('API result copy', () => {
  it('resolves detailed result codes without using server prose', () => {
    const payload = {
      errorCode: 'SENSITIVE_VERIFICATION_REQUIRED',
      msg: 'server supplied prose',
      message: 'server supplied prose',
      error_description: 'server supplied prose',
    }
    expect(resolveApiResult(payload, 202)).toBe(apiResultMessages.SENSITIVE_VERIFICATION_REQUIRED)
  })

  it.each([
    [200, 'REQUEST_SUCCESS'],
    [201, 'REQUEST_SUCCESS'],
    [202, 'REQUEST_SUCCESS'],
    [401, 'UNAUTHENTICATED'],
    [403, 'FORBIDDEN'],
    [404, 'NOT_FOUND'],
    [405, 'METHOD_NOT_ALLOWED'],
    [408, 'REQUEST_TIMEOUT'],
    [409, 'REQUEST_CONFLICT'],
    [413, 'REQUEST_UPLOAD_TOO_LARGE'],
    [415, 'UNSUPPORTED_MEDIA_TYPE'],
    [429, 'RATE_LIMITED'],
    [400, 'BAD_REQUEST'],
    [503, 'SERVER_ERROR'],
    [undefined, 'REQUEST_FAILED'],
  ])('falls back safely for unknown codes at HTTP %s', (status, code) => {
    expect(resolveApiResult({ errorCode: 'FUTURE_UNKNOWN_CODE' }, status)).toBe(
      apiResultMessages[code as string],
    )
    expect(resolveApiResult({ errorCode: 'toString' }, status)).toBe(
      apiResultMessages[code as string],
    )
  })

  it('formats locally defined templates using scalar parameters', () => {
    const params = { min: 9, max: 42 }
    expect(resolveApiResult({ errorCode: 'AUTH_020', params })).toBe('密码长度必须在9-42个字符之间')
  })

  it.each([
    undefined,
    { min: 9 },
    { min: { value: 9 }, max: 42 },
    { min: [9], max: 42 },
    { min: null, max: 42 },
  ])('uses HTTP fallback instead of exposing incomplete template parameters %s', (params) => {
    expect(resolveApiResult({ errorCode: 'AUTH_020', params, code: 400 })).toBe(
      apiResultMessages.BAD_REQUEST,
    )
  })

  it('preserves detailed codes and status for decisions without parsing translated messages', () => {
    const error = new ApiError({ errorCode: 'SENSITIVE_VERIFICATION_REQUIRED', params: {} }, 202)
    expect(error).toMatchObject({
      name: 'ApiError',
      status: 202,
      errorCode: 'SENSITIVE_VERIFICATION_REQUIRED',
      message: apiResultMessages.SENSITIVE_VERIFICATION_REQUIRED,
    })
  })

  it('retains envelope result metadata when callback payloads are unwrapped', () => {
    expect(
      unwrapApiResult({ code: 202, errorCode: 'OAUTH_BIND_REQUIRED', data: { needBind: true } }),
    ).toMatchObject({ code: 202, errorCode: 'OAUTH_BIND_REQUIRED', needBind: true })
  })

  it('translates exact known result codes in audit records and preserves historical prose', () => {
    expect(resolveAuditReason('AUTH_008')).toBe('邮箱格式不正确')
    expect(resolveAuditReason('历史原因：邮箱格式不正确')).toBe('历史原因：邮箱格式不正确')
    expect(resolveAuditReason('AUTH_008: 邮箱格式不正确')).toBe('AUTH_008: 邮箱格式不正确')
    expect(resolveAuditReason(' AUTH_008 ')).toBe(' AUTH_008 ')
    expect(resolveAuditReason('FUTURE_UNKNOWN_CODE')).toBe('FUTURE_UNKNOWN_CODE')
    expect(resolveAuditReason('toString')).toBe('toString')
    expect(resolveAuditReason(null)).toBeNull()
  })

  it('uses generic local copy for templated audit codes without stored parameters', () => {
    expect(resolveAuditReason('AUTH_020')).toBe(apiResultMessages.REQUEST_FAILED)
  })
})
