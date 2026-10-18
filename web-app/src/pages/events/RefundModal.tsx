import { useState } from 'react'
import { paymentsApi } from '../../api/endpoints'
import type { EventRegistrationRow } from '../../api/types'
import { useToast } from '../../components/Toast'
import { Button, ErrorMessage, Field, Modal } from '../../components/ui'

/** Owner/admin refund of one paid registration; the member is notified and the registration is cancelled. */
export function RefundModal({ registration, onClose, onRefunded }:
  { registration: EventRegistrationRow | null; onClose: () => void; onRefunded: () => void }) {
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()

  const close = () => {
    if (busy) return
    setReason('')
    setError(undefined)
    onClose()
  }

  async function refund() {
    if (!registration?.paymentId) return
    setBusy(true)
    setError(undefined)
    try {
      const payment = await paymentsApi.refund(registration.paymentId, reason.trim())
      toast.success(payment.status === 'REFUNDED'
        ? `Refunded ${registration.name ?? 'the member'}`
        : 'Refund requested — it will complete shortly')
      setReason('')
      onRefunded()
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={registration !== null} title="Refund registration" onClose={close}
           footer={<>
             <Button variant="ghost" onClick={close} disabled={busy}>Cancel</Button>
             <Button variant="danger" loading={busy} onClick={refund}>Refund payment</Button>
           </>}>
      <div className="stack">
        <p>
          Refund <strong>{registration?.name ?? 'this member'}</strong>'s payment? Their registration will be cancelled
          and they'll be notified by email.
        </p>
        <Field label="Reason (optional)" htmlFor="refund-reason" hint="Shared with the member">
          <input id="refund-reason" className="input" maxLength={255} value={reason}
                 onChange={(e) => setReason(e.target.value)} />
        </Field>
        <ErrorMessage error={error} />
      </div>
    </Modal>
  )
}
