import { describe, expect, it } from 'vitest'
import { formatDistance, formatDuration, formatMoney, formatPace, fromInputDateTime, humanize, initials, timeAgo, toInputDateTime } from './format'

describe('format helpers', () => {
  it('formats durations', () => {
    expect(formatDuration(0)).toBe('0m')
    expect(formatDuration(45)).toBe('45s')
    expect(formatDuration(95)).toBe('1m 35s')
    expect(formatDuration(1800)).toBe('30m')
    expect(formatDuration(3725)).toBe('1h 2m')
  })

  it('formats distances with sensible precision', () => {
    expect(formatDistance(5.234)).toBe('5.23 km')
    expect(formatDistance(10.55)).toBe('10.6 km')
    expect(formatDistance(120.4)).toBe('120 km')
    expect(formatDistance(null)).toBe('—')
  })

  it('computes pace per km', () => {
    expect(formatPace(10, 3000)).toBe('5:00 /km')
    expect(formatPace(5.2, 1800)).toBe('5:46 /km')
    expect(formatPace(0, 100)).toBeNull()
  })

  it('treats zero price as free', () => {
    expect(formatMoney(0, 'INR')).toBe('Free')
    expect(formatMoney(499, 'INR')).toContain('499')
  })

  it('converts between datetime-local and LocalDateTime', () => {
    expect(fromInputDateTime('2026-10-18T06:30')).toBe('2026-10-18T06:30:00')
    expect(fromInputDateTime('')).toBeUndefined()
    expect(toInputDateTime('2026-10-18T06:30:00')).toBe('2026-10-18T06:30')
    expect(toInputDateTime(null)).toBe('')
  })

  it('describes relative time', () => {
    const now = new Date('2026-10-18T12:00:00')
    expect(timeAgo('2026-10-18T11:59:30', now)).toBe('just now')
    expect(timeAgo('2026-10-18T11:15:00', now)).toBe('45m ago')
    expect(timeAgo('2026-10-18T09:00:00', now)).toBe('3h ago')
    expect(timeAgo('2026-10-16T12:00:00', now)).toBe('2d ago')
  })

  it('humanizes enums and builds initials', () => {
    expect(humanize('PENDING_PAYMENT')).toBe('Pending payment')
    expect(initials('Guru PJ')).toBe('GP')
    expect(initials(null)).toBe('?')
  })
})
