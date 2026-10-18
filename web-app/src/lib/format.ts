import type { LocalDateTime } from '../api/types'

// Backend LocalDateTime strings carry no zone; they are shown as-is (platform zone == club's local time).

function parse(value: LocalDateTime): Date {
  return new Date(value.length === 16 ? `${value}:00` : value)
}

export function formatDateTime(value: LocalDateTime | null | undefined): string {
  if (!value) return '—'
  return parse(value).toLocaleString(undefined, {
    weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  })
}

export function formatDate(value: LocalDateTime | null | undefined): string {
  if (!value) return '—'
  return parse(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

export function timeAgo(value: LocalDateTime, now: Date = new Date()): string {
  const seconds = Math.round((now.getTime() - parse(value).getTime()) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.round(hours / 24)
  if (days < 7) return `${days}d ago`
  return formatDate(value)
}

/** 3725 → "1h 2m", 95 → "1m 35s" */
export function formatDuration(totalSeconds: number | null | undefined): string {
  if (!totalSeconds) return '0m'
  const h = Math.floor(totalSeconds / 3600)
  const m = Math.floor((totalSeconds % 3600) / 60)
  const s = totalSeconds % 60
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return s && m < 10 ? `${m}m ${s}s` : `${m}m`
  return `${s}s`
}

export function formatDistance(km: number | null | undefined): string {
  if (km === null || km === undefined) return '—'
  return `${km.toFixed(km >= 100 ? 0 : km >= 10 ? 1 : 2)} km`
}

/** Pace in min/km for runs, e.g. "5:42 /km". */
export function formatPace(km: number, seconds: number): string | null {
  if (!km || !seconds) return null
  const perKm = seconds / km
  const m = Math.floor(perKm / 60)
  const s = Math.round(perKm % 60)
  return `${m}:${String(s).padStart(2, '0')} /km`
}

export function formatMoney(amount: number, currency: string): string {
  if (!amount) return 'Free'
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount)
  } catch {
    return `${amount} ${currency}`
  }
}

/** <input type="datetime-local"> value ("2026-10-18T06:00") → backend LocalDateTime ("2026-10-18T06:00:00"). */
export function fromInputDateTime(value: string): LocalDateTime | undefined {
  if (!value) return undefined
  return value.length === 16 ? `${value}:00` : value
}

/** Backend LocalDateTime → value for <input type="datetime-local">. */
export function toInputDateTime(value: LocalDateTime | null | undefined): string {
  return value ? value.slice(0, 16) : ''
}

/** Current local time as a LocalDateTime string, optionally shifted by minutes. */
export function nowLocal(offsetMinutes = 0): LocalDateTime {
  const d = new Date(Date.now() + offsetMinutes * 60_000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

export function humanize(value: string | null | undefined): string {
  if (!value) return ''
  return value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ')
}

export function initials(name: string | null | undefined): string {
  if (!name) return '?'
  return name.trim().split(/\s+/).slice(0, 2).map((p) => p[0]?.toUpperCase() ?? '').join('')
}
