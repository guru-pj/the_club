import type { Payment, PaymentStatus } from '../../api/types'

/** Statuses after which polling stops. */
const FINAL: PaymentStatus[] = ['SUCCEEDED', 'FAILED', 'EXPIRED', 'REFUND_PENDING', 'REFUNDED']

export interface PollOptions {
  intervalMs?: number
  timeoutMs?: number
  sleep?: (ms: number) => Promise<void>
  /** stop early (e.g. the modal was closed) */
  cancelled?: () => boolean
}

/**
 * Polls a payment until the server settles it. Used when the browser's confirm call failed after the provider
 * already took the money: the Razorpay webhook / reconciliation job confirms it server-side.
 * Resolves with the final payment, or null if it was still pending when the time ran out.
 */
export async function pollPayment(get: () => Promise<Payment>, options: PollOptions = {}): Promise<Payment | null> {
  const { intervalMs = 3000, timeoutMs = 60_000, cancelled = () => false } = options
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)))
  const attempts = Math.max(1, Math.floor(timeoutMs / intervalMs))
  for (let i = 0; i < attempts && !cancelled(); i++) {
    await sleep(intervalMs)
    if (cancelled()) break
    try {
      const payment = await get()
      if (FINAL.includes(payment.status)) return payment
    } catch {
      // keep polling through transient errors
    }
  }
  return null
}
