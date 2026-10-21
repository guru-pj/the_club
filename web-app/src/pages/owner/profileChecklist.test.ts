import { describe, expect, it } from 'vitest'
import type { Club } from '../../api/types'
import { checkInWindow } from './checkInWindow'
import { profileChecklist } from './profileChecklist'

describe('profileChecklist', () => {
  it('lists what the public profile still lacks', () => {
    const club = { description: 'Sunday runs', logoUrl: '/media/clubs/x.png', coverUrl: null, address: 'Marina', latitude: 13,
      contactEmail: null, contactPhone: '+91 1', rules: ' ', website: null, socialLinks: { instagram: 'https://i/x' } } as unknown as Club
    const missing = profileChecklist(club).filter((i) => !i.done).map((i) => i.label)
    expect(missing).toEqual(['Cover photo', 'Club rules'])
  })
})

describe('checkInWindow', () => {
  it('is open from 6 hours before the start until 12 hours after', () => {
    const start = '2026-10-25T06:00:00'
    const t = Date.parse(start)
    expect(checkInWindow(start, t - 7 * 3600_000)).toBe(false)
    expect(checkInWindow(start, t - 5 * 3600_000)).toBe(true)
    expect(checkInWindow(start, t + 11 * 3600_000)).toBe(true)
    expect(checkInWindow(start, t + 13 * 3600_000)).toBe(false)
  })
})
