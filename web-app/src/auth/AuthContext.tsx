import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { decodeToken, refreshTokens, tokenStore, type StoredTokens } from '../api/client'
import { authApi, usersApi } from '../api/endpoints'
import type { Role, User } from '../api/types'

interface AuthState {
  /** null = signed out, undefined = still loading */
  user: User | null | undefined
  /** roles from the current access token — these are what the backend enforces */
  roles: Role[]
  hasRole: (role: Role) => boolean
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** Gets a fresh token (e.g. after becoming a club owner) and reloads the profile. */
  refreshSession: () => Promise<void>
  setUser: (user: User) => void
}

const AuthContext = createContext<AuthState | null>(null)

function rolesOf(tokens: StoredTokens | null): Role[] {
  return ((tokens && decodeToken(tokens.accessToken)?.roles) ?? []) as Role[]
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [tokens, setTokens] = useState(tokenStore.get())
  const [user, setUser] = useState<User | null | undefined>(tokens ? undefined : null)

  useEffect(() => tokenStore.subscribe((t) => {
    setTokens(t)
    if (!t) setUser(null)
  }), [])

  const loadUser = useCallback(async () => {
    try {
      setUser(await usersApi.me())
    } catch {
      tokenStore.set(null)
      setUser(null)
    }
  }, [])

  // Restore the session on first load when tokens are already stored
  useEffect(() => {
    if (!tokenStore.get()) return
    let cancelled = false
    usersApi.me()
      .then((me) => { if (!cancelled) setUser(me) })
      .catch(() => { if (!cancelled) tokenStore.set(null) })
    return () => { cancelled = true }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password)
    tokenStore.set({ accessToken: res.accessToken, refreshToken: res.refreshToken })
    await loadUser()
  }, [loadUser])

  const register = useCallback(async (name: string, email: string, password: string) => {
    await usersApi.register(name, email, password)
    await login(email, password)
  }, [login])

  const logout = useCallback(async () => {
    const current = tokenStore.get()
    tokenStore.set(null)
    if (current) await authApi.logout(current.refreshToken).catch(() => undefined)
  }, [])

  const refreshSession = useCallback(async () => {
    if (await refreshTokens()) await loadUser()
  }, [loadUser])

  const value = useMemo<AuthState>(() => {
    const roles = rolesOf(tokens)
    return {
      user, roles, hasRole: (r) => roles.includes(r), login, register, logout, refreshSession,
      setUser: (u) => setUser(u),
    }
  }, [user, tokens, login, register, logout, refreshSession])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
