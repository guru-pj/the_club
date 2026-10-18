import { describe, expect, it, vi } from 'vitest'
import type { Payment, PaymentStatus } from '../../api/types'
import { pollPayment } from './pollPayment'

const payment = (status: PaymentStatus) => ({ id: 'p1', status } as Payment)
const noSleep = () => Promise.resolve()

describe('pollPayment', () => {
  it('returns once the payment is settled', async () => {
    const get = vi.fn()
      .mockResolvedValueOnce(payment('CREATED'))
      .mockRejectedValueOnce(new Error('502'))
      .mockResolvedValueOnce(payment('SUCCEEDED'))
    const result = await pollPayment(get, { sleep: noSleep })
    expect(result?.status).toBe('SUCCEEDED')
    expect(get).toHaveBeenCalledTimes(3)
  })

  it('stops on failure states', async () => {
    const result = await pollPayment(() => Promise.resolve(payment('EXPIRED')), { sleep: noSleep })
    expect(result?.status).toBe('EXPIRED')
  })

  it('gives up after the timeout', async () => {
    const get = vi.fn().mockResolvedValue(payment('CREATED'))
    const result = await pollPayment(get, { sleep: noSleep, intervalMs: 3000, timeoutMs: 9000 })
    expect(result).toBeNull()
    expect(get).toHaveBeenCalledTimes(3)
  })

  it('can be cancelled', async () => {
    const get = vi.fn().mockResolvedValue(payment('CREATED'))
    let calls = 0
    const result = await pollPayment(get, { sleep: noSleep, cancelled: () => ++calls > 2 })
    expect(result).toBeNull()
    expect(get).toHaveBeenCalledTimes(1)
  })
})
