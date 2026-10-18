import type { TokenResponse } from './types'

/** RFC 7807 error returned by every service, with optional per-field validation messages. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

const STORAGE_KEY = 'theclub.tokens'

export interface StoredTokens {
  accessToken: string
  refreshToken: string
}

function readTokens(): StoredTokens | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StoredTokens) : null
  } catch {
    return null
  }
}

let tokens: StoredTokens | null = typeof localStorage === 'undefined' ? null : readTokens()
const listeners = new Set<(t: StoredTokens | null) => void>()

export const tokenStore = {
  get: () => tokens,
  set(next: StoredTokens | null) {
    tokens = next
    try {
      if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      else localStorage.removeItem(STORAGE_KEY)
    } catch {
      // storage unavailable (private mode); keep tokens in memory only
    }
    listeners.forEach((l) => l(next))
  },
  subscribe(listener: (t: StoredTokens | null) => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

let refreshing: Promise<boolean> | null = null

/** Exchanges the refresh token once, even if many requests hit 401 at the same time. */
export function refreshTokens(): Promise<boolean> {
  const current = tokens
  if (!current) return Promise.resolve(false)
  refreshing ??= fetch(`${BASE}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: current.refreshToken }),
  })
    .then(async (res) => {
      if (!res.ok) {
        tokenStore.set(null)
        return false
      }
      const body = (await res.json()) as TokenResponse
      tokenStore.set({ accessToken: body.accessToken, refreshToken: body.refreshToken })
      return true
    })
    .catch(() => false)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

async function toError(res: Response): Promise<ApiError> {
  let message = res.statusText || `Request failed (${res.status})`
  let fieldErrors: Record<string, string> = {}
  try {
    const body = await res.json()
    if (body?.detail) message = body.detail
    else if (body?.title) message = body.title
    if (body?.errors && typeof body.errors === 'object') fieldErrors = body.errors
  } catch {
    // non-JSON error body
  }
  if (res.status === 503 && message === res.statusText) message = 'Service temporarily unavailable, try again shortly'
  return new ApiError(res.status, message, fieldErrors)
}

export interface RequestOptions {
  body?: unknown
  query?: Record<string, string | number | boolean | null | undefined>
  /** skip the Authorization header (login, register, …) */
  anonymous?: boolean
}

export async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${BASE}${path}`, window.location.origin)
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
  }

  const send = () => {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (options.body !== undefined) headers['Content-Type'] = 'application/json'
    if (!options.anonymous && tokens) headers.Authorization = `Bearer ${tokens.accessToken}`
    return fetch(url, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  }

  let res: Response
  try {
    res = await send()
    if (res.status === 401 && !options.anonymous && tokens && (await refreshTokens())) {
      res = await send()
    }
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection.')
  }
  if (!res.ok) throw await toError(res)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
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
