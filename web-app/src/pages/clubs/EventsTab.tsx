import { useState } from 'react'
import { Link } from 'react-router-dom'
import { eventsApi } from '../../api/endpoints'
import type { ClubEvent } from '../../api/types'
import { CalendarIcon, PinIcon, PlusIcon } from '../../components/icons'
import { Card, EmptyState, ErrorMessage, LinkButton, Loading, Pagination, StatusBadge, Tabs } from '../../components/ui'
import { formatMoney } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'

const PAGE_SIZE = 10

function dateParts(value: string) {
  const d = new Date(value)
  return {
    day: d.getDate(),
    month: d.toLocaleDateString(undefined, { month: 'short' }),
    time: d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }),
    weekday: d.toLocaleDateString(undefined, { weekday: 'short' }),
  }
}

function EventRow({ event }: { event: ClubEvent }) {
  const { day, month, time, weekday } = dateParts(event.startTime)
  return (
    <Link to={`/events/${event.id}`} className="list-item">
      <div className="date-block"><div className="day">{day}</div><div className="month">{month}</div></div>
      <div className="list-item-main">
        <div className="bold truncate">{event.title}</div>
        <div className="row small muted" style={{ gap: 12 }}>
          <span>{weekday} · {time}</span>
          {event.location && <span className="row" style={{ gap: 4 }}><PinIcon size={14} />{event.location}</span>}
          <span>{formatMoney(event.price, event.currency)}</span>
          {event.capacity !== null && <span>{event.spotsLeft ?? 0} of {event.capacity} spots left</span>}
        </div>
      </div>
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        {event.status !== 'SCHEDULED' && <StatusBadge status={event.status} />}
        <StatusBadge status={event.myRegistration} />
      </div>
    </Link>
  )
}

export function EventsTab({ clubId, canManage }: { clubId: string; canManage: boolean }) {
  const [when, setWhen] = useState<'upcoming' | 'past'>('upcoming')
  const [page, setPage] = useState(0)
  const events = useAsync(
    () => eventsApi.byClub(clubId, { upcoming: when === 'upcoming', page, size: PAGE_SIZE }),
    [clubId, when, page],
  )

  return (
    <div>
      <div className="row-between" style={{ marginBottom: 12 }}>
        <Tabs tabs={[{ id: 'upcoming', label: 'Upcoming' }, { id: 'past', label: 'Past' }]} active={when}
              onChange={(w) => { setWhen(w); setPage(0) }} />
        {canManage && <LinkButton to={`/clubs/${clubId}/events/new`} size="sm"><PlusIcon size={16} />Create event</LinkButton>}
      </div>
      {events.error ? <ErrorMessage error={events.error} onRetry={events.reload} />
        : events.loading && !events.data ? <Loading />
        : !events.data?.items.length ? (
          <Card>
            <EmptyState icon={<CalendarIcon size={32} />} title={when === 'upcoming' ? 'No upcoming events' : 'No past events'}
                        action={canManage && when === 'upcoming'
                          ? <LinkButton to={`/clubs/${clubId}/events/new`}>Create event</LinkButton> : undefined}>
              {when === 'upcoming' ? 'New events will show up here.' : undefined}
            </EmptyState>
          </Card>
        ) : (
          <Card flush>
            <div className="list">{events.data.items.map((e) => <EventRow key={e.id} event={e} />)}</div>
            <Pagination page={events.data.page} size={PAGE_SIZE} totalItems={events.data.totalItems} onChange={setPage} />
          </Card>
        )}
    </div>
  )
}
