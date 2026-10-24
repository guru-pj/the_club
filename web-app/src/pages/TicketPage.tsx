import { useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { eventsApi } from '../api/endpoints'
import { CalendarIcon, CheckIcon, ClockIcon, PinIcon } from '../components/icons'
import { Button, EmptyState, ErrorMessage, LinkButton, Loading, StatusBadge } from '../components/ui'
import { formatDate } from '../lib/format'
import { toMs } from './events/time'
import { useAsync } from '../lib/useAsync'
import './events.css'

export default function TicketPage() {
  const { registrationId = '' } = useParams()
  // ?new=1 right after registering (or paying): show the confirmation above the ticket
  const [params] = useSearchParams()
  const justRegistered = params.get('new') === '1'
  const ticket = useAsync(() => eventsApi.ticket(registrationId), [registrationId])

  if (ticket.loading && !ticket.data) return <Loading />
  if (ticket.error) {
    const e = ticket.error
    if (e instanceof ApiError && (e.status === 404 || e.status === 409 || e.status === 403)) {
      return (
        <div className="page">
          <EmptyState title={e.status === 404 ? 'Ticket not found' : 'Ticket not available'}
                      action={<LinkButton variant="secondary" to="/tickets">My tickets</LinkButton>}>
            {e.status === 409 ? 'Tickets are issued once your registration is confirmed — complete the payment first.' : e.message}
          </EmptyState>
        </div>
      )
    }
    return <div className="page"><ErrorMessage error={e} onRetry={ticket.reload} /></div>
  }
  if (!ticket.data) return null
  const t = ticket.data
  const time = new Date(toMs(t.startTime)).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })

  return (
    <div className="page">
      {justRegistered && (
        <div className="confirmation no-print" role="status">
          <span className="confirmation-icon"><CheckIcon size={28} /></span>
          <h1>You're registered!</h1>
          <p className="muted">
            We've added <strong>{t.eventTitle}</strong> to your tickets and emailed you a confirmation.
            Show the QR code below at check-in.
          </p>
        </div>
      )}
      <div className="ticket">
        <div className="ticket-top">
          <div className="row-between">
            <span className="tiny bold" style={{ letterSpacing: '0.08em' }}>THE CLUB · EVENT TICKET</span>
            <StatusBadge status={t.status} />
          </div>
          <h1 style={{ marginTop: 10 }}>{t.eventTitle}</h1>
          <div className="ticket-sub stack" style={{ gap: 4, marginTop: 8 }}>
            <span className="row" style={{ gap: 6 }}><CalendarIcon size={16} />{formatDate(t.startTime)}</span>
            <span className="row" style={{ gap: 6 }}><ClockIcon size={16} />{time}</span>
            {t.location && <span className="row" style={{ gap: 6 }}><PinIcon size={16} />{t.location}</span>}
          </div>
        </div>
        <div className="ticket-perforation"><span /></div>
        <div className="ticket-body">
          <div className="ticket-qr">
            <img src={`data:image/png;base64,${t.qrCodeBase64}`} alt={`QR code for ticket ${t.ticketCode}`} />
          </div>
          <div className="ticket-code">{t.ticketCode}</div>
          <p className="muted small">
            {t.status === 'CHECKED_IN' ? "You're checked in. Enjoy the event!" : 'Show this QR code at the event for check-in.'}
          </p>
        </div>
      </div>
      <div className="row no-print" style={{ justifyContent: 'center', marginTop: 20 }}>
        <LinkButton variant="secondary" to={`/events/${t.eventId}`}>View event</LinkButton>
        {justRegistered && <LinkButton variant="secondary" to="/tickets">All my tickets</LinkButton>}
        <Button variant="ghost" onClick={() => window.print()}>Print</Button>
      </div>
    </div>
  )
}
