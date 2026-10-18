import { useState } from 'react'
import { paymentsApi } from '../../api/endpoints'
import type { CheckoutInfo, ClubEvent, Payment } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Button, ErrorMessage, Modal } from '../../components/ui'
import { formatDateTime, formatMoney } from '../../lib/format'
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
  const amount = formatMoney(checkout.amount, checkout.currency)

  async function confirm(providerPaymentId: string, signature: string) {
    const payment = await paymentsApi.confirm(checkout.paymentId, providerPaymentId, signature)
    onPaid(payment)
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
    <Modal open={open} title="Complete payment" onClose={() => !busy && onClose()}
           footer={<>
             <Button variant="ghost" onClick={onClose} disabled={busy}>Not now</Button>
             <Button onClick={isMock ? payMock : payRazorpay} loading={busy}>Pay {amount}</Button>
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
        <ErrorMessage error={error} />
      </div>
    </Modal>
  )
}
