import { describe, expect, it } from 'vitest'
import { registrationBadge } from './registration'

const base = { registrationState: 'OPEN' as const, myRegistration: null, spotsLeft: null, subscribersOnly: false }

describe('registrationBadge', () => {
  it('shows your own registration first', () => {
    expect(registrationBadge({ ...base, myRegistration: 'CONFIRMED' }).label).toBe('Registered')
    expect(registrationBadge({ ...base, registrationState: 'FULL', myRegistration: 'PENDING_PAYMENT' }).label).toBe('Payment pending')
  })

  it('explains why registration is unavailable', () => {
    expect(registrationBadge({ ...base, registrationState: 'NOT_YET_OPEN' }).label).toBe('Opens soon')
    expect(registrationBadge({ ...base, registrationState: 'FULL' })).toEqual({ label: 'Full', tone: 'danger' })
    expect(registrationBadge({ ...base, registrationState: 'CANCELLED' }).label).toBe('Cancelled')
    // a cancelled registration doesn't hide the event's own state
    expect(registrationBadge({ ...base, myRegistration: 'CANCELLED' }).label).toBe('Registration open')
  })

  it('warns when few spots are left', () => {
    expect(registrationBadge({ ...base, spotsLeft: 1 }).label).toBe('Open · 1 spot left')
    expect(registrationBadge({ ...base, spotsLeft: 40 }).label).toBe('Registration open')
  })
})
