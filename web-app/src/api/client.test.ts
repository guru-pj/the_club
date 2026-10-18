import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// The client touches window/localStorage/BroadcastChannel at import time, so stub them before importing it.
function jsonResponse(status: number, body?: unknown, headers: Record<string, string> = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

const tokenBody = (accessToken: string) => ({ accessToken, refreshToken: 'ignored-by-browser', tokenType: 'Bearer', expiresIn: 900 })
const isRefresh = (url: unknown) => String(url).endsWith('/api/v1/auth/refresh')

/** In-process stand-in for BroadcastChannel so tests can play "the other tab". */
class FakeChannel {
  static instances: FakeChannel[] = []
  posted: unknown[] = []
  private listener?: (e: MessageEvent) => void
  readonly name: string
  constructor(name: string) {
    this.name = name
    FakeChannel.instances.push(this)
  }
  postMessage(data: unknown) { this.posted.push(data) }
  addEventListener(_type: string, listener: (e: MessageEvent) => void) { this.listener = listener }
  receive(data: unknown) { this.listener?.({ data } as MessageEvent) }
}

describe('api client', () => {
  const storage = { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() }

  beforeEach(() => {
    vi.resetModules()
    FakeChannel.instances = []
    storage.getItem.mockReset()
    storage.setItem.mockReset()
    storage.removeItem.mockReset()
    vi.stubGlobal('window', { location: { origin: 'http://localhost:5173' } })
    vi.stubGlobal('localStorage', storage)
    vi.stubGlobal('BroadcastChannel', FakeChannel)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('never stores tokens in localStorage and removes the legacy key', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => jsonResponse(200, tokenBody('a1'))))
    const { refreshTokens, tokenStore } = await import('./client')

    expect(await refreshTokens()).toBe(true)

    expect(tokenStore.get()).toBe('a1')
    expect(storage.setItem).not.toHaveBeenCalled()
    expect(storage.removeItem).toHaveBeenCalledWith('theclub.tokens')
  })

  it('restores a session from the refresh cookie without sending a body', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => jsonResponse(200, tokenBody('restored')))
    vi.stubGlobal('fetch', fetchMock)
    const { refreshTokens, tokenStore } = await import('./client')

    expect(await refreshTokens()).toBe(true)

    const [url, init] = fetchMock.mock.calls[0]
    expect(isRefresh(url)).toBe(true)
    expect(init).toMatchObject({ method: 'POST', credentials: 'same-origin' })
    expect(init.body).toBeUndefined()
    expect(tokenStore.get()).toBe('restored')
  })

  it('refreshes once on concurrent 401s and retries with the new token', async () => {
    const fetchMock = vi.fn().mockImplementation(async (url: unknown, init: RequestInit) => {
      if (isRefresh(url)) return jsonResponse(200, tokenBody('new-a'))
      const auth = (init.headers as Record<string, string>).Authorization
      return auth === 'Bearer new-a' ? jsonResponse(200, { ok: true }) : jsonResponse(401)
    })
    vi.stubGlobal('fetch', fetchMock)
    const { request, tokenStore } = await import('./client')
    tokenStore.set('old-a', 900)

    const [a, b] = await Promise.all([request('GET', '/api/v1/a'), request('GET', '/api/v1/b')])

    expect(a).toEqual({ ok: true })
    expect(b).toEqual({ ok: true })
    expect(fetchMock.mock.calls.filter(([url]) => isRefresh(url))).toHaveLength(1)
    expect(tokenStore.get()).toBe('new-a')
    for (const [, init] of fetchMock.mock.calls) expect(init.credentials).toBe('same-origin')
  })

  it('signs out when the refresh cookie is rejected', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: unknown) =>
      isRefresh(url) ? jsonResponse(401, { detail: 'Invalid refresh token' }) : jsonResponse(401)))
    const { request, tokenStore, ApiError } = await import('./client')
    tokenStore.set('a', 900)

    await expect(request('GET', '/api/v1/users/me')).rejects.toBeInstanceOf(ApiError)
    expect(tokenStore.get()).toBeNull()
  })

  it('does not refresh for anonymous calls such as login', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => jsonResponse(401, { detail: 'Invalid email or password' }))
    vi.stubGlobal('fetch', fetchMock)
    const { request } = await import('./client')

    await expect(request('POST', '/api/v1/auth/login', { body: {}, anonymous: true }))
      .rejects.toMatchObject({ status: 401, message: 'Invalid email or password' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('maps problem details to ApiError with field errors, code and Retry-After', async () => {
    const responses = [
      jsonResponse(400, { title: 'Bad Request', detail: 'Validation failed', errors: { password: 'This password is too common' } }),
      jsonResponse(403, { detail: 'Please verify your email address before logging in', code: 'EMAIL_NOT_VERIFIED' }),
      jsonResponse(429, { detail: 'Too many failed login attempts.' }, { 'Retry-After': '840' }),
    ]
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => responses.shift()))
    const { request } = await import('./client')

    await expect(request('POST', '/api/v1/users/register', { body: {}, anonymous: true })).rejects.toMatchObject({
      status: 400, message: 'Validation failed', fieldErrors: { password: 'This password is too common' },
    })
    await expect(request('POST', '/api/v1/auth/login', { body: {}, anonymous: true }))
      .rejects.toMatchObject({ status: 403, code: 'EMAIL_NOT_VERIFIED' })
    await expect(request('POST', '/api/v1/auth/login', { body: {}, anonymous: true }))
      .rejects.toMatchObject({ status: 429, retryAfterSeconds: 840 })
  })

  it('reports network failures clearly', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    const { request } = await import('./client')

    await expect(request('GET', '/api/v1/clubs')).rejects.toMatchObject({ status: 0 })
  })

  it('refreshes proactively a minute before the access token expires', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn().mockImplementation(async () => jsonResponse(200, tokenBody('next')))
    vi.stubGlobal('fetch', fetchMock)
    const { tokenStore } = await import('./client')

    tokenStore.set('first', 900)
    await vi.advanceTimersByTimeAsync(839_000)
    expect(fetchMock).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(2_000)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(tokenStore.get()).toBe('next')
  })

  it('syncs sign-in and sign-out across tabs', async () => {
    vi.stubGlobal('fetch', vi.fn())
    const { tokenStore } = await import('./client')
    const channel = FakeChannel.instances[0]
    expect(channel.name).toBe('theclub-auth')

    tokenStore.set('mine', 900)
    expect(channel.posted).toContainEqual({ type: 'token', accessToken: 'mine', expiresIn: 900 })

    channel.receive({ type: 'token', accessToken: 'from-other-tab', expiresIn: 900 })
    expect(tokenStore.get()).toBe('from-other-tab')

    channel.receive({ type: 'logout' })
    expect(tokenStore.get()).toBeNull()

    tokenStore.set('again', 900)
    tokenStore.clearEverywhere()
    expect(channel.posted).toContainEqual({ type: 'logout' })
  })

  it('decodes token roles', async () => {
    const { decodeToken } = await import('./client')
    const payload = btoa(JSON.stringify({ sub: 'u1', roles: ['USER', 'CLUB_OWNER'] })).replace(/=+$/, '')
    expect(decodeToken(`x.${payload}.y`)?.roles).toEqual(['USER', 'CLUB_OWNER'])
    expect(decodeToken('garbage')).toBeNull()
  })
})
