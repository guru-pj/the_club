import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { Role } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { startPage } from '../lib/startPage'
import { EmptyState, Loading } from './ui'

/** Sends signed-out visitors to /login, remembering where they were going. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (user === undefined) return <Loading />
  if (user === null) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  // A new account sets up its profile first (picture, details, location); finishing or skipping counts as done
  if (user.profileComplete === false && location.pathname !== '/welcome') return <Navigate to="/welcome" replace />
  return <>{children}</>
}

export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { hasRole } = useAuth()
  if (!hasRole(role)) {
    return <div className="page"><EmptyState title="You don't have access to this page" /></div>
  }
  return <>{children}</>
}

/** Login/register pages: bounce signed-in users to the app. */
export function GuestOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (user === undefined) return <Loading />
  if (user) return <Navigate to={startPage(user)} replace />
  return <>{children}</>
}
