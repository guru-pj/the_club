import type { LocalDateTime } from '../../api/types'

/** First day of the current month at 00:00, as a backend LocalDateTime. */
export function monthStart(): LocalDateTime {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01T00:00:00`
}
