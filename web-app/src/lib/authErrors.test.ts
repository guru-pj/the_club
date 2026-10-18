import { describe, expect, it, vi } from 'vitest'

vi.stubGlobal('window', { location: { origin: 'http://localhost:5173' } })
vi.stubGlobal('BroadcastChannel', undefined)
const { ApiError } = await import('../api/client')
const { isEmailNotVerified, isTransientError, lockoutMinutes } = await import('./authErrors')

describe('auth error helpers', () => {
  it('detects unverified email by problem code', () => {
    expect(isEmailNotVerified(new ApiError(403, 'x', {}, 'EMAIL_NOT_VERIFIED'))).toBe(true)
    expect(isEmailNotVerified(new ApiError(403, 'x'))).toBe(false)
    expect(isEmailNotVerified(new Error('x'))).toBe(false)
  })

  it('rounds lockout time up to whole minutes', () => {
    expect(lockoutMinutes(new ApiError(429, 'x', {}, undefined, 61))).toBe(2)
    expect(lockoutMinutes(new ApiError(429, 'x', {}, undefined, 900))).toBe(15)
    expect(lockoutMinutes(new ApiError(429, 'x'))).toBe(1)
    expect(lockoutMinutes(new ApiError(401, 'x'))).toBeNull()
  })

  it('treats network and 5xx failures as transient', () => {
    expect(isTransientError(new ApiError(0, 'offline'))).toBe(true)
    expect(isTransientError(new ApiError(502, 'bad gateway'))).toBe(true)
    expect(isTransientError(new ApiError(400, 'bad signature'))).toBe(false)
    expect(isTransientError(new TypeError('boom'))).toBe(true)
  })
})
