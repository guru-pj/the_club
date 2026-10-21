import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { decodeToken, refreshTokens, tokenStore } from '../api/client'
import { authApi, usersApi } from '../api/endpoints'
import type { Role, User } from '../api/types'

interface AuthState {
  /** null = signed out, undefined = still loading */
  user: User | null | undefined
  /** roles from the current access token — these are what the backend enforces */
  roles: Role[]
  hasRole: (role: Role) => boolean
  /** Resolves with the signed-in user (null if the profile couldn't be loaded). */
  login: (email: string, password: string) => Promise<User | null>
  /** Creates the account, then tries to log in (which fails with EMAIL_NOT_VERIFIED when verification is required). */
  /** clubOwner: signed up "as a club owner"; owner access still needs an approved club application */
  register: (name: string, email: string, password: string, clubOwner?: boolean) => Promise<User | null>
  logout: () => Promise<void>
  /** Revokes every session of this user (all devices), then signs out here. */
  logoutEverywhere: () => Promise<void>
  /** Drops the local session without calling the server (e.g. after a password change revoked it). */
  forgetSession: () => void
  /** the session ended because someone signed out here (not because it expired) */
  signedOut: boolean
  /** Gets a fresh token (e.g. after becoming a club owner) and reloads the profile. */
  refreshSession: () => Promise<void>
  setUser: (user: User) => void
}

const AuthContext = createContext<AuthState | null>(null)

function rolesOf(token: string | null): Role[] {
  return ((token && decodeToken(token)?.roles) ?? []) as Role[]
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(tokenStore.get())
  const [user, setUser] = useState<User | null | undefined>(undefined)
  const [signedOut, setSignedOut] = useState(false)

  const userRef = useRef(user)
  useEffect(() => { userRef.current = user }, [user])

  const loadUser = useCallback(async (): Promise<User | null> => {
    try {
      const me = await usersApi.me()
      setUser(me)
      return me
    } catch {
      tokenStore.set(null)
      setUser(null)
      return null
    }
  }, [])

  useEffect(() => tokenStore.subscribe((t) => {
    setToken(t)
    if (!t) setUser(null)
    // signed in from another tab while this one was signed out: pick up the shared token
    else if (userRef.current === null) void loadUser()
  }), [loadUser])

  // Restore the session on page load from the HttpOnly refresh cookie
  useEffect(() => {
    let cancelled = false
    void (async () => {
      const restored = tokenStore.get() !== null || await refreshTokens()
      if (cancelled) return
      if (!restored) {
        setUser(null)
        return
      }
      try {
        const me = await usersApi.me()
        if (!cancelled) setUser(me)
      } catch {
        if (!cancelled) setUser(null)
      }
    })()
    return () => { cancelled = true }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password)
    tokenStore.set(res.accessToken, res.expiresIn, res.refreshToken)
    setSignedOut(false)
    return loadUser()
  }, [loadUser])

  const register = useCallback(async (name: string, email: string, password: string, clubOwner = false) => {
    await usersApi.register(name, email, password, clubOwner)
    return login(email, password)
  }, [login])

  const logout = useCallback(async () => {
    await authApi.logout().catch(() => undefined)
    setSignedOut(true)
    tokenStore.clearEverywhere()
  }, [])

  const logoutEverywhere = useCallback(async () => {
    await authApi.logoutAll()
    setSignedOut(true)
    tokenStore.clearEverywhere()
  }, [])

  const forgetSession = useCallback(() => {
    setSignedOut(true)
    tokenStore.clearEverywhere()
  }, [])

  const refreshSession = useCallback(async () => {
    if (await refreshTokens()) await loadUser()
  }, [loadUser])

  const value = useMemo<AuthState>(() => {
    const roles = rolesOf(token)
    return {
      user, roles, hasRole: (r) => roles.includes(r), login, register, logout, logoutEverywhere, forgetSession,
      refreshSession, setUser: (u) => setUser(u), signedOut,
    }
  }, [user, token, login, register, logout, logoutEverywhere, forgetSession, refreshSession, signedOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
