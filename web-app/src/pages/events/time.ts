import type { LocalDateTime } from '../../api/types'

/** Milliseconds for a zone-less LocalDateTime (Jackson omits ":00" seconds, so lengths vary). */
export function toMs(value: LocalDateTime): number {
  return new Date(value.length === 16 ? `${value}:00` : value).getTime()
}

export function isPast(value: LocalDateTime, now = Date.now()): boolean {
  return toMs(value) <= now
}
