import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { clubsApi, eventsApi } from '../../api/endpoints'
import type { CheckoutInfo, Club, ClubEvent, Payment, UUID } from '../../api/types'
import { CheckIcon, StarIcon, TicketIcon } from '../../components/icons'
import { useToast } from '../../components/Toast'
import { Button, Card, LinkButton, Modal } from '../../components/ui'
import { formatDateTime, formatMoney } from '../../lib/format'
import { PaymentModal } from './PaymentModal'
import { pendingCheckout } from './shared'
import { isPast } from './time'

interface Props {
  event: ClubEvent
  club: Club
  registrationId: UUID | undefined
  onChanged: () => void
}

export function RegistrationPanel({ event, club, registrationId, onChanged }: Props) {
  const toast = useToast()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [checkout, setCheckout] = useState<CheckoutInfo | null>(() => pendingCheckout.get(event.id))
  const [payOpen, setPayOpen] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const price = formatMoney(event.price, event.currency)
  const status = event.myRegistration

  async function run(action: () => Promise<void>) {
    setBusy(true)
    try {
      await action()
    } catch (e) {
      toast.error(e)
      if (e instanceof ApiError && (e.status === 409 || e.status === 403)) onChanged()
    } finally {
      setBusy(false)
    }
  }

  async function startRegistration() {
    const res = await eventsApi.register(event.id)
    if (res.payment) {
      pendingCheckout.set(event.id, res.payment)
      setCheckout(res.payment)
      setPayOpen(true)
    } else {
      toast.success("You're registered! Your ticket is ready.")
    }
    onChanged()
  }

  const register = () => run(startRegistration)

  const restartPayment = () => run(async () => {
    await eventsApi.cancelMyRegistration(event.id)
    pendingCheckout.clear(event.id)
    setCheckout(null)
    await startRegistration()
  })

  const join = () => run(async () => {
    await clubsApi.join(club.id)
    toast.success(`Welcome to ${club.name}!`)
    onChanged()
  })

  const cancelRegistration = () => run(async () => {
    await eventsApi.cancelMyRegistration(event.id)
    pendingCheckout.clear(event.id)
    setCheckout(null)
    setConfirmCancel(false)
    toast.success('Your registration was cancelled')
    onChanged()
  })

  function paid(payment: Payment) {
    pendingCheckout.clear(event.id)
    setCheckout(null)
    setPayOpen(false)
    toast.success("Payment successful — you're registered")
    onChanged()
    navigate(`/tickets/${payment.registrationId}`)
  }

  const started = isPast(event.startTime)
  const notYetOpen = !isPast(event.registrationOpensAt)
  const closed = isPast(event.registrationClosesAt)

  let body: ReactNode
  if (status === 'CHECKED_IN') {
    body = (
      <div className="stack">
        <div className="alert alert-success"><CheckIcon size={18} /><div><strong>Checked in.</strong> Thanks for showing up!</div></div>
        <div className="row"><StarIcon size={18} style={{ color: 'var(--accent)' }} /><span>You earned <strong>{event.points} points</strong></span></div>
        {registrationId && <LinkButton variant="secondary" to={`/tickets/${registrationId}`}>View ticket</LinkButton>}
      </div>
    )
  } else if (status === 'CONFIRMED') {
    body = (
      <div className="stack">
        <div className="alert alert-success"><CheckIcon size={18} /><div><strong>You're going!</strong> Show your QR ticket at check-in.</div></div>
        {registrationId && <LinkButton to={`/tickets/${registrationId}`} block><TicketIcon size={18} />View ticket</LinkButton>}
        {event.price === 0 && !started && (
          <Button variant="ghost" onClick={() => setConfirmCancel(true)}>Cancel registration</Button>
        )}
        {event.price > 0 && <p className="muted small">Need to cancel a paid registration? Contact the club owner.</p>}
      </div>
    )
  } else if (event.status === 'CANCELLED') {
    body = <p className="muted">This event was cancelled.</p>
  } else if (started) {
    body = <p className="muted">This event has already started.</p>
  } else if (status === 'PENDING_PAYMENT') {
    body = (
      <div className="stack">
        <div className="alert alert-warning">Your spot is reserved — complete the payment to confirm it.</div>
        {checkout ? (
          <>
            <Button onClick={() => setPayOpen(true)} block size="lg">Pay {formatMoney(checkout.amount, checkout.currency)}</Button>
            <Button variant="ghost" onClick={restartPayment} loading={busy}>Start over</Button>
          </>
        ) : (
          <>
            <p className="muted small">We couldn't find your payment session. Start over to get a new checkout.</p>
            <Button onClick={restartPayment} loading={busy} block>Cancel and start over</Button>
          </>
        )}
        <Button variant="ghost" onClick={() => setConfirmCancel(true)} disabled={busy}>Cancel registration</Button>
      </div>
    )
  } else if (!club.myRole) {
    body = (
      <div className="stack">
        <p>Join <strong>{club.name}</strong> to register for its events.</p>
        <Button onClick={join} loading={busy} block>Join the club</Button>
      </div>
    )
  } else if (notYetOpen) {
    body = (
      <div className="stack">
        <Button disabled block>Registration opens {formatDateTime(event.registrationOpensAt)}</Button>
        <p className="muted small">Come back when registration opens — spots can go fast.</p>
      </div>
    )
  } else if (closed || !event.registrationOpen && event.spotsLeft !== 0) {
    body = <p className="muted">Registration closed.</p>
  } else if (event.spotsLeft === 0) {
    body = <p className="muted">This event is full.</p>
  } else {
    body = (
      <div className="stack">
        <Button size="lg" block onClick={register} loading={busy}>
          {event.price > 0 ? `Register · ${price}` : 'Register for free'}
        </Button>
        {event.spotsLeft !== null && <p className="muted small">{event.spotsLeft} spots left</p>}
      </div>
    )
  }

  return (
    <Card className="register-panel" title="Your registration">
      <div className="stack">
        <div className="price-tag">{price}</div>
        {body}
      </div>
      {checkout && (
        <PaymentModal open={payOpen} event={event} checkout={checkout} onClose={() => setPayOpen(false)} onPaid={paid} />
      )}
      <Modal open={confirmCancel} title="Cancel registration?" onClose={() => setConfirmCancel(false)}
             footer={<>
               <Button variant="ghost" onClick={() => setConfirmCancel(false)} disabled={busy}>Keep my spot</Button>
               <Button variant="danger" onClick={cancelRegistration} loading={busy}>Cancel registration</Button>
             </>}>
        <p>You'll give up your spot for <strong>{event.title}</strong>.</p>
      </Modal>
    </Card>
  )
}
