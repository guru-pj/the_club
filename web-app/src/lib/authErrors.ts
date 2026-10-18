import { ApiError } from '../api/client'

export const EMAIL_NOT_VERIFIED = 'EMAIL_NOT_VERIFIED'

export const PASSWORD_HINT = 'At least 8 characters. Avoid common passwords and your email address.'

export function isEmailNotVerified(error: unknown): boolean {
  return error instanceof ApiError && error.code === EMAIL_NOT_VERIFIED
}

/** Minutes until a locked-out login may be retried (from Retry-After), or null when not a lockout. */
export function lockoutMinutes(error: unknown): number | null {
  if (!(error instanceof ApiError) || error.status !== 429) return null
  return Math.max(1, Math.ceil((error.retryAfterSeconds ?? 60) / 60))
}

/** Network failures and server errors, where the request may still have taken effect. */
export function isTransientError(error: unknown): boolean {
  return !(error instanceof ApiError) || error.status === 0 || error.status >= 500
}
