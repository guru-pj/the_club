import { isNativeApp } from '../lib/platform'
import type { TokenResponse } from './types'

/** RFC 7807 error returned by every service, with optional per-field validation messages. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>
  /** machine-readable problem `code`, e.g. "EMAIL_NOT_VERIFIED" */
  readonly code?: string
  /** from the Retry-After header (429 lockouts) */
  readonly retryAfterSeconds?: number

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {},
              code?: string, retryAfterSeconds?: number) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }
}

// ---------------------------------------------------------------------------------------------------------------
// Session tokens. The access token lives in memory only; the refresh token is an HttpOnly cookie
// (theclub_refresh, Path=/api/v1/auth) that scripts can't read. A page reload restores the session by
// calling /refresh, which uses that cookie.
// The native apps (isNativeApp) call the API cross-origin, where that cookie is never sent. They keep the refresh
// token in the app's own sandboxed storage and send it in the request body instead.

/** Refresh this long before the access token expires. */
const REFRESH_AHEAD_MS = 60_000
const MIN_REFRESH_DELAY_MS = 5_000
const LEGACY_STORAGE_KEY = 'theclub.tokens'
const CHANNEL_NAME = 'theclub-auth'
const NATIVE_REFRESH_KEY = 'theclub.native.refresh'

type AuthMessage = { type: 'logout' } | { type: 'token'; accessToken: string; expiresIn: number }

let accessToken: string | null = null
let refreshTimer: ReturnType<typeof setTimeout> | undefined
const listeners = new Set<(token: string | null) => void>()

// Older builds kept tokens in localStorage, which any injected script could read; remove them once.
try {
  globalThis.localStorage?.removeItem(LEGACY_STORAGE_KEY)
} catch {
  // storage unavailable
}

const channel: BroadcastChannel | null =
  typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME)

function broadcast(message: AuthMessage) {
  try {
    channel?.postMessage(message)
  } catch {
    // channel closed
  }
}

/** The stored refresh token in the native apps; always null in the browser. */
export function nativeRefreshToken(): string | null {
  if (!isNativeApp) return null
  try {
    return globalThis.localStorage?.getItem(NATIVE_REFRESH_KEY) ?? null
  } catch {
    return null
  }
}

function storeNativeRefreshToken(token: string | null) {
  if (!isNativeApp) return
  try {
    if (token) globalThis.localStorage?.setItem(NATIVE_REFRESH_KEY, token)
    else globalThis.localStorage?.removeItem(NATIVE_REFRESH_KEY)
  } catch {
    // storage unavailable
  }
}

function scheduleRefresh(expiresInSeconds: number | undefined) {
  clearTimeout(refreshTimer)
  refreshTimer = undefined
  if (!accessToken || !expiresInSeconds) return
  const delay = Math.max(MIN_REFRESH_DELAY_MS, expiresInSeconds * 1000 - REFRESH_AHEAD_MS)
  refreshTimer = setTimeout(() => void refreshTokens(), delay)
}

function apply(token: string | null, expiresInSeconds?: number) {
  accessToken = token
  if (!token) storeNativeRefreshToken(null)
  scheduleRefresh(expiresInSeconds)
  listeners.forEach((l) => l(token))
}

export const tokenStore = {
  get: () => accessToken,
  /**
   * Stores a new access token (and tells other tabs), or clears it with `null`.
   * `refreshToken` is kept only by the native apps; browsers rely on the cookie.
   */
  set(token: string | null, expiresInSeconds?: number, refreshToken?: string) {
    if (refreshToken) storeNativeRefreshToken(refreshToken)
    apply(token, expiresInSeconds)
    if (token && expiresInSeconds) broadcast({ type: 'token', accessToken: token, expiresIn: expiresInSeconds })
  },
  /** Signs out locally and in every other open tab. */
  clearEverywhere() {
    apply(null)
    broadcast({ type: 'logout' })
  },
  subscribe(listener: (token: string | null) => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

channel?.addEventListener('message', (event: MessageEvent<AuthMessage>) => {
  const message = event.data
  if (message?.type === 'logout') apply(null)
  // another tab refreshed: reuse its token instead of rotating the cookie again
  else if (message?.type === 'token') apply(message.accessToken, message.expiresIn)
})

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

/** The full address of an API path: the gateway's address in the native apps, same-origin in the browser. */
export function apiUrl(path: string): string {
  return `${BASE}${path}`
}

let refreshing: Promise<boolean> | null = null

/**
 * Exchanges the refresh cookie for a new access token, once, even if many requests hit 401 at the same time.
 * Also used on page load to restore the session.
 */
export function refreshTokens(): Promise<boolean> {
  refreshing ??= (async () => {
    const before = accessToken
    const stored = nativeRefreshToken()
    try {
      if (isNativeApp && !stored) return false
      const res = await fetch(`${BASE}/api/v1/auth/refresh`, {
        method: 'POST',
        credentials: 'same-origin',
        ...(stored && { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: stored }) }),
      })
      if (res.ok) {
        const body = (await res.json()) as TokenResponse
        tokenStore.set(body.accessToken, body.expiresIn, body.refreshToken)
        return true
      }
      // Another tab may have rotated the cookie first and shared its token with us meanwhile.
      if (accessToken && accessToken !== before) return true
      apply(null)
      return false
    } catch {
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

function retryAfter(res: Response): number | undefined {
  const value = res.headers.get('Retry-After')
  if (!value) return undefined
  const seconds = Number(value)
  if (Number.isFinite(seconds)) return Math.max(0, Math.round(seconds))
  const date = Date.parse(value)
  return Number.isNaN(date) ? undefined : Math.max(0, Math.round((date - Date.now()) / 1000))
}

async function toError(res: Response): Promise<ApiError> {
  let message = res.statusText || `Request failed (${res.status})`
  let fieldErrors: Record<string, string> = {}
  let code: string | undefined
  try {
    const body = await res.json()
    if (body?.detail) message = body.detail
    else if (body?.title) message = body.title
    if (body?.errors && typeof body.errors === 'object') fieldErrors = body.errors
    if (typeof body?.code === 'string') code = body.code
  } catch {
    // non-JSON error body
  }
  if (res.status === 503 && message === res.statusText) message = 'Service temporarily unavailable, try again shortly'
  return new ApiError(res.status, message, fieldErrors, code, retryAfter(res))
}

export interface RequestOptions {
  body?: unknown
  /** multipart upload instead of a JSON body (images); the browser sets the boundary header */
  form?: FormData
  query?: Record<string, string | number | boolean | null | undefined>
  /** skip the Authorization header and the 401 → refresh retry (login, register, …) */
  anonymous?: boolean
}

export async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${BASE}${path}`, window.location.origin)
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
  }

  const send = () => {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (options.body !== undefined && !options.form) headers['Content-Type'] = 'application/json'
    if (!options.anonymous && accessToken) headers.Authorization = `Bearer ${accessToken}`
    return fetch(url, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.form ?? (options.body === undefined ? undefined : JSON.stringify(options.body)),
    })
  }

  let res: Response
  try {
    res = await send()
    if (res.status === 401 && !options.anonymous && accessToken && (await refreshTokens())) {
      res = await send()
    }
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection.')
  }
  if (!res.ok) throw await toError(res)
  if (res.status === 202 || res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Uploads one image (multipart field "file", plus optional text fields). */
export function upload<T>(path: string, file: File | Blob, fields: Record<string, string> = {}): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  for (const [key, value] of Object.entries(fields)) form.append(key, value)
  return request<T>('POST', path, { form })
}

/**
 * Where to load an uploaded image from. Stored images have site-relative URLs ("/media/…"); the native apps load
 * the UI from the device, so those need the server's address in front. Other URLs are returned unchanged.
 */
export function mediaUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined
  return url.startsWith('/') && !url.startsWith('//') ? `${BASE}${url}` : url
}

/** Decodes the JWT payload (no verification — the server is the authority). */
export function decodeToken(token: string): { sub: string; email?: string; name?: string; roles?: string[]; exp?: number } | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(payload)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    return JSON.parse(json)
  } catch {
    return null
  }
}
