import { useEffect, useRef, useState } from 'react'
import { paymentsApi } from '../../api/endpoints'
import type { CheckoutInfo, ClubEvent, Payment } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Button, ErrorMessage, Modal, Spinner } from '../../components/ui'
import { isTransientError } from '../../lib/authErrors'
import { formatDateTime, formatMoney } from '../../lib/format'
import { pollPayment } from './pollPayment'
import { loadRazorpay, randomId } from './shared'

interface Props {
  open: boolean
  event: ClubEvent
  checkout: CheckoutInfo
  onClose: () => void
  onPaid: (payment: Payment) => void
}

export function PaymentModal({ open, event, checkout, onClose, onPaid }: Props) {
  const { user } = useAuth()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [confirming, setConfirming] = useState(false)
  const unmounted = useRef(false)
  const amount = formatMoney(checkout.amount, checkout.currency)

  useEffect(() => () => { unmounted.current = true }, [])

  /**
   * The provider has taken the money at this point. If our confirm call fails (network, server restart), the
   * Razorpay webhook / reconciliation job still settles the payment server-side, so wait for that instead.
   */
  async function confirm(providerPaymentId: string, signature: string) {
    try {
      onPaid(await paymentsApi.confirm(checkout.paymentId, providerPaymentId, signature))
    } catch (e) {
      if (!isTransientError(e)) throw e
      setConfirming(true)
      const settled = await pollPayment(() => paymentsApi.get(checkout.paymentId), { cancelled: () => unmounted.current })
      if (unmounted.current) return
      setConfirming(false)
      if (settled?.status === 'SUCCEEDED') onPaid(settled)
      else if (settled) throw new Error('The payment could not be completed. If you were charged, it will be refunded.')
      else throw new Error("We're still confirming your payment. Check My tickets in a few minutes — you won't be charged twice.")
    }
  }

  async function payMock() {
    setBusy(true)
    setError(undefined)
    try {
      const providerPaymentId = `pay_mock_${randomId()}`
      const { signature } = await paymentsApi.mockSignature(checkout.paymentId, providerPaymentId)
      await confirm(providerPaymentId, signature)
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  async function payRazorpay() {
    setBusy(true)
    setError(undefined)
    try {
      const Razorpay = await loadRazorpay()
      new Razorpay({
        key: checkout.providerKeyId ?? '',
        order_id: checkout.providerOrderId,
        amount: Math.round(checkout.amount * 100),
        currency: checkout.currency,
        name: 'The Club',
        description: event.title,
        prefill: { name: user?.name, email: user?.email },
        handler: (r) => {
          setBusy(true)
          confirm(r.razorpay_payment_id, r.razorpay_signature)
            .catch(setError)
            .finally(() => setBusy(false))
        },
        modal: { ondismiss: () => setBusy(false) },
      }).open()
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }

  const isMock = checkout.provider === 'MOCK'

  return (
    <Modal open={open} title="Complete payment" onClose={() => !busy && !confirming && onClose()}
           footer={<>
             <Button variant="ghost" onClick={onClose} disabled={busy || confirming}>Not now</Button>
             <Button onClick={isMock ? payMock : payRazorpay} loading={busy || confirming}>Pay {amount}</Button>
           </>}>
      <div className="stack">
        <div>
          <div className="bold">{event.title}</div>
          <div className="muted small">{formatDateTime(event.startTime)}{event.location ? ` · ${event.location}` : ''}</div>
        </div>
        <hr className="divider" style={{ margin: 0 }} />
        <div className="row-between">
          <span>Registration fee</span>
          <span className="bold">{amount}</span>
        </div>
        <div className="muted tiny mono">Order {checkout.providerOrderId}</div>
        {isMock ? (
          <div className="alert alert-warning">Test mode — no real money is charged. Click Pay to simulate a successful payment.</div>
        ) : (
          <div className="muted small">You'll complete the payment securely with Razorpay.</div>
        )}
        {confirming && (
          <div className="alert alert-info row" role="status">
            <Spinner small /> Payment received — confirming your registration…
          </div>
        )}
        <ErrorMessage error={error} />
      </div>
    </Modal>
  )
}
