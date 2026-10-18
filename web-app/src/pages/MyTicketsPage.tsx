import { useState } from 'react'
import { Link } from 'react-router-dom'
import { eventsApi } from '../api/endpoints'
import type { UserEventRegistration } from '../api/types'
import { PinIcon, TicketIcon } from '../components/icons'
import { Card, EmptyState, ErrorMessage, LinkButton, Loading, PageHeader, StatusBadge, Tabs } from '../components/ui'
import { formatDateTime } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { isPast, toMs } from './events/time'
import './events.css'

type Tab = 'upcoming' | 'past'

function DateChip({ value }: { value: string }) {
  const d = new Date(toMs(value))
  return (
    <div className="ticket-date" aria-hidden="true">
      <div className="d">{d.getDate()}</div>
      <div className="m">{d.toLocaleDateString(undefined, { month: 'short' })}</div>
    </div>
  )
}

function TicketRow({ reg }: { reg: UserEventRegistration }) {
  const hasTicket = reg.status === 'CONFIRMED' || reg.status === 'CHECKED_IN'
  const muted = reg.status === 'CANCELLED' || reg.eventStatus === 'CANCELLED'
  return (
    <div className={muted ? 'list-item ticket-card is-muted' : 'list-item ticket-card'}>
      <DateChip value={reg.startTime} />
      <div className="list-item-main">
        <Link to={`/events/${reg.eventId}`} className="bold">{reg.eventTitle}</Link>
        <div className="muted small">{formatDateTime(reg.startTime)}</div>
        {reg.location && <div className="muted small row" style={{ gap: 4 }}><PinIcon size={14} />{reg.location}</div>}
        <div className="row" style={{ marginTop: 6, gap: 6 }}>
          <StatusBadge status={reg.status} />
          {reg.eventStatus === 'CANCELLED' && <StatusBadge status="CANCELLED" />}
        </div>
      </div>
      {hasTicket && reg.eventStatus !== 'CANCELLED' && (
        <LinkButton size="sm" to={`/tickets/${reg.registrationId}`}><TicketIcon size={16} />Ticket</LinkButton>
      )}
      {reg.status === 'PENDING_PAYMENT' && reg.eventStatus === 'SCHEDULED' && (
        <LinkButton size="sm" variant="accent" to={`/events/${reg.eventId}`}>Complete payment</LinkButton>
      )}
    </div>
  )
}

export default function MyTicketsPage() {
  const [tab, setTab] = useState<Tab>('upcoming')
  const regs = useAsync(() => eventsApi.myRegistrations(), [])

  const all = regs.data ?? []
  const upcoming = all
    .filter((r) => !isPast(r.startTime) && r.status !== 'CANCELLED' && r.eventStatus !== 'CANCELLED')
    .sort((a, b) => toMs(a.startTime) - toMs(b.startTime))
  const past = all
    .filter((r) => !upcoming.includes(r))
    .sort((a, b) => toMs(b.startTime) - toMs(a.startTime))
  const shown = tab === 'upcoming' ? upcoming : past

  return (
    <div className="page page-narrow">
      <PageHeader title="My tickets" subtitle="Events you've registered for" />
      <Tabs<Tab> active={tab} onChange={setTab} tabs={[
        { id: 'upcoming', label: `Upcoming${upcoming.length ? ` (${upcoming.length})` : ''}` },
        { id: 'past', label: 'Past' },
      ]} />
      {regs.loading && !regs.data ? <Loading /> : regs.error ? <ErrorMessage error={regs.error} onRetry={regs.reload} /> : (
        <Card flush>
          {shown.length === 0 ? (
            <EmptyState icon={<TicketIcon size={32} />}
                        title={tab === 'upcoming' ? 'No upcoming events' : 'No past events yet'}
                        action={<LinkButton to="/clubs">Find a club event</LinkButton>}>
              {tab === 'upcoming' ? 'Register for an event and your QR ticket will show up here.' : null}
            </EmptyState>
          ) : (
            <div className="list">{shown.map((r) => <TicketRow key={r.registrationId} reg={r} />)}</div>
          )}
        </Card>
      )}
    </div>
  )
}
