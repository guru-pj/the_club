import { useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { eventsApi } from '../api/endpoints'
import { CalendarIcon, PinIcon } from '../components/icons'
import { Button, EmptyState, ErrorMessage, LinkButton, Loading, StatusBadge } from '../components/ui'
import { formatDateTime } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import './events.css'

export default function TicketPage() {
  const { registrationId = '' } = useParams()
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

  return (
    <div className="page">
      <div className="ticket">
        <div className="ticket-top">
          <div className="row-between">
            <span className="tiny bold" style={{ letterSpacing: '0.08em' }}>THE CLUB · EVENT TICKET</span>
            <StatusBadge status={t.status} />
          </div>
          <h1 style={{ marginTop: 10 }}>{t.eventTitle}</h1>
          <div className="ticket-sub stack" style={{ gap: 4, marginTop: 8 }}>
            <span className="row" style={{ gap: 6 }}><CalendarIcon size={16} />{formatDateTime(t.startTime)}</span>
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
        <Button variant="ghost" onClick={() => window.print()}>Print</Button>
      </div>
    </div>
  )
}
