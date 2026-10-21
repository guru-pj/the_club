import type { User } from '../api/types'

/**
 * Where a signed-in user starts: profile setup for a new account, otherwise where they were going (or home).
 * Every redirect after signing in uses this, so two redirects never send the app back and forth.
 */
export function startPage(user: User | null | undefined, from?: string): string {
  if (user?.profileComplete === false) return '/welcome'
  return from && from !== '/login' ? from : '/'
}
