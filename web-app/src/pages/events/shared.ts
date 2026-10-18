import { clubsApi } from '../../api/endpoints'
import type { CheckoutInfo, Club, UUID } from '../../api/types'

/** Owner of the club or a platform admin may manage its events. */
export function canManage(club: Club | undefined, isAdmin: boolean): boolean {
  return isAdmin || club?.myRole === 'OWNER'
}

export async function loadClub(clubId: UUID): Promise<Club> {
  return clubsApi.get(clubId)
}

// The backend has no endpoint to re-read a pending checkout, so remember it for this browser session.
const checkoutKey = (eventId: UUID) => `theclub.checkout.${eventId}`

export const pendingCheckout = {
  get(eventId: UUID): CheckoutInfo | null {
    try {
      const raw = sessionStorage.getItem(checkoutKey(eventId))
      return raw ? (JSON.parse(raw) as CheckoutInfo) : null
    } catch {
      return null
    }
  },
  set(eventId: UUID, info: CheckoutInfo) {
    try {
      sessionStorage.setItem(checkoutKey(eventId), JSON.stringify(info))
    } catch {
      // storage unavailable; the user can still cancel and start over
    }
  },
  clear(eventId: UUID) {
    try {
      sessionStorage.removeItem(checkoutKey(eventId))
    } catch {
      // ignore
    }
  },
}

interface RazorpayResponse {
  razorpay_payment_id: string
  razorpay_order_id: string
  razorpay_signature: string
}

interface RazorpayOptions {
  key: string
  order_id: string
  amount: number
  currency: string
  name: string
  description: string
  prefill?: { name?: string; email?: string }
  handler: (response: RazorpayResponse) => void
  modal?: { ondismiss?: () => void }
}

type RazorpayCtor = new (options: RazorpayOptions) => { open: () => void }

let razorpayScript: Promise<RazorpayCtor> | null = null

/** Loads Razorpay Checkout once per page load. */
export function loadRazorpay(): Promise<RazorpayCtor> {
  razorpayScript ??= new Promise<RazorpayCtor>((resolve, reject) => {
    const existing = (window as unknown as { Razorpay?: RazorpayCtor }).Razorpay
    if (existing) {
      resolve(existing)
      return
    }
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.async = true
    script.onload = () => {
      const ctor = (window as unknown as { Razorpay?: RazorpayCtor }).Razorpay
      if (ctor) resolve(ctor)
      else reject(new Error('Razorpay Checkout failed to initialise'))
    }
    script.onerror = () => {
      razorpayScript = null
      reject(new Error('Could not load Razorpay Checkout. Check your connection.'))
    }
    document.head.appendChild(script)
  })
  return razorpayScript
}

export function randomId(): string {
  const bytes = new Uint8Array(8)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

/** "2026-10" for a Date. */
export function monthKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

export function monthLabel(key: string): string {
  const [y, m] = key.split('-').map(Number)
  return new Date(y, m - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}

export function shiftMonth(key: string, delta: number): string {
  const [y, m] = key.split('-').map(Number)
  return monthKey(new Date(y, m - 1 + delta, 1))
}
