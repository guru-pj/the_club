import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// The client touches window/localStorage at import time, so stub them before importing it.
function jsonResponse(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('api client', () => {
  beforeEach(() => {
    vi.resetModules()
    const store = new Map<string, string>()
    vi.stubGlobal('window', { location: { origin: 'http://localhost:5173' } })
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, v),
      removeItem: (k: string) => void store.delete(k),
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('refreshes once on 401 and retries with the new token', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(401))
      .mockResolvedValueOnce(jsonResponse(401))
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'new-a', refreshToken: 'new-r', tokenType: 'Bearer', expiresIn: 3600 }))
      .mockImplementation(async () => jsonResponse(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    const { request, tokenStore } = await import('./client')
    tokenStore.set({ accessToken: 'old-a', refreshToken: 'old-r' })

    const [a, b] = await Promise.all([request('GET', '/api/v1/a'), request('GET', '/api/v1/b')])

    expect(a).toEqual({ ok: true })
    expect(b).toEqual({ ok: true })
    const refreshCalls = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/api/v1/auth/refresh'))
    expect(refreshCalls).toHaveLength(1)
    expect(tokenStore.get()).toEqual({ accessToken: 'new-a', refreshToken: 'new-r' })
    const lastHeaders = fetchMock.mock.calls.at(-1)?.[1]?.headers as Record<string, string>
    expect(lastHeaders.Authorization).toBe('Bearer new-a')
  })

  it('signs out when the refresh token is rejected', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse(401))
      .mockResolvedValueOnce(jsonResponse(401, { detail: 'Invalid refresh token' })))
    const { request, tokenStore, ApiError } = await import('./client')
    tokenStore.set({ accessToken: 'a', refreshToken: 'r' })

    await expect(request('GET', '/api/v1/users/me')).rejects.toBeInstanceOf(ApiError)
    expect(tokenStore.get()).toBeNull()
  })

  it('maps problem details to ApiError with field errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => jsonResponse(400, {
      title: 'Bad Request', detail: 'Validation failed', errors: { email: 'must be a well-formed email address' },
    })))
    const { request } = await import('./client')

    await expect(request('POST', '/api/v1/users/register', { body: {}, anonymous: true })).rejects.toMatchObject({
      status: 400,
      message: 'Validation failed',
      fieldErrors: { email: 'must be a well-formed email address' },
    })
  })

  it('reports network failures clearly', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    const { request } = await import('./client')

    await expect(request('GET', '/api/v1/clubs')).rejects.toMatchObject({ status: 0 })
  })

  it('decodes token roles', async () => {
    const { decodeToken } = await import('./client')
    const payload = btoa(JSON.stringify({ sub: 'u1', roles: ['USER', 'CLUB_OWNER'] })).replace(/=+$/, '')
    expect(decodeToken(`x.${payload}.y`)?.roles).toEqual(['USER', 'CLUB_OWNER'])
    expect(decodeToken('garbage')).toBeNull()
  })
})
