import type { LocalDateTime } from '../../api/types'

/** Check-in opens 6 hours before the start and closes 12 hours after (event-service's defaults). */
export const CHECK_IN_OPENS_BEFORE_MS = 6 * 3600_000
export const CHECK_IN_CLOSES_AFTER_MS = 12 * 3600_000

export function checkInWindow(startTime: LocalDateTime, now = Date.now()): boolean {
  const start = Date.parse(startTime)
  return now >= start - CHECK_IN_OPENS_BEFORE_MS && now <= start + CHECK_IN_CLOSES_AFTER_MS
}
