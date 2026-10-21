import type { User } from '../api/types'

/** Where someone who signed up as a club owner belongs: their application until a club is verified, then the dashboard. */
function ownerHome(user: User): string | null {
  if (!user.clubOwnerSignup) return null
  return user.roles?.includes('CLUB_OWNER') ? '/owner' : '/become-owner'
}

/**
 * Where a signed-in user starts: profile setup for a new account, otherwise where they were going (or their home:
 * the club application or owner dashboard for people who signed up as club owners).
 * Every redirect after signing in uses this, so two redirects never send the app back and forth.
 */
export function startPage(user: User | null | undefined, from?: string): string {
  if (user?.profileComplete === false) return '/welcome'
  if (from && from !== '/login') return from
  return (user && ownerHome(user)) ?? '/'
}

/** Where profile setup continues to. */
export function afterProfileSetup(user: User | null | undefined): string {
  return (user && ownerHome(user)) ?? '/clubs'
}
