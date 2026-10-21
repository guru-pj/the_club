import { Link, useParams } from 'react-router-dom'
import { clubsApi, eventsApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { CalendarIcon, ClockIcon, PinIcon, StarIcon, UsersIcon } from '../components/icons'
import { mediaUrl } from '../api/client'
import { Badge, Card, ErrorMessage, Loading, PageHeader, StatusBadge } from '../components/ui'
import { formatDateTime, formatMoney } from '../lib/format'
import { registrationBadge } from '../lib/registration'
import { useAsync } from '../lib/useAsync'
import { OwnerPanel } from './events/OwnerPanel'
import { RegistrationPanel } from './events/RegistrationPanel'
import { canManage } from './events/shared'
import { isPast } from './events/time'
import './events.css'

export default function EventPage() {
  const { eventId = '' } = useParams()
  const { hasRole } = useAuth()
  const state = useAsync(async () => {
    const event = await eventsApi.get(eventId)
    const [club, registrations] = await Promise.all([
      clubsApi.get(event.clubId),
      event.myRegistration ? eventsApi.myRegistrations() : Promise.resolve([]),
    ])
    const registrationId = registrations.find((r) => r.eventId === event.id)?.registrationId
    return { event, club, registrationId }
  }, [eventId])

  if (state.loading && !state.data) return <Loading />
  if (state.error) return <div className="page"><ErrorMessage error={state.error} onRetry={state.reload} /></div>
  if (!state.data) return null
  const { event, club, registrationId } = state.data
  const manage = canManage(club, hasRole('ADMIN'))
  const windowLine = isPast(event.registrationOpensAt)
    ? `Registration ${isPast(event.registrationClosesAt) ? 'closed' : 'closes'} ${formatDateTime(event.registrationClosesAt)}`
    : `Registration opens ${formatDateTime(event.registrationOpensAt)}`

  return (
    <div className="page">
      <PageHeader back={{ to: `/clubs/${club.id}`, label: club.name }} title={event.title}
                  subtitle={<span>by <Link to={`/clubs/${club.id}`}>{club.name}</Link></span>}
                  actions={<div className="status-pills">
                    {event.status !== 'SCHEDULED' && <StatusBadge status={event.status} />}
                    <Badge tone={registrationBadge(event).tone}>{registrationBadge(event).label}</Badge>
                    {event.subscribersOnly && <Badge tone="accent">Subscribers only</Badge>}
                  </div>} />
      {event.imageUrl && <img className="event-cover" src={mediaUrl(event.imageUrl)} alt="" />}
      <div className="layout-sidebar">
        <div className="stack-lg">
          <Card>
            {event.status === 'CANCELLED' && (
              <div className="alert alert-error" style={{ marginBottom: 14 }}>This event has been cancelled by the club.</div>
            )}
            <div className="event-meta" style={{ marginTop: 0 }}>
              <div className="event-meta-item"><CalendarIcon size={18} /><span>{formatDateTime(event.startTime)}</span></div>
              {event.location && <div className="event-meta-item"><PinIcon size={18} /><span>{event.location}</span></div>}
              <div className="event-meta-item"><ClockIcon size={18} /><span>{windowLine}</span></div>
              <div className="event-meta-item">
                <UsersIcon size={18} />
                <span>
                  {event.registeredCount} registered
                  {event.capacity !== null ? ` · ${event.spotsLeft ?? 0} of ${event.capacity} spots left` : ' · unlimited spots'}
                </span>
              </div>
              <div className="event-meta-item">
                <StarIcon size={18} style={{ color: 'var(--accent)' }} />
                <span>Earn <strong>{event.points} points</strong> when you check in · {formatMoney(event.price, event.currency)}</span>
              </div>
            </div>
            {event.description && (
              <>
                <hr className="divider" />
                <p className="pre-wrap">{event.description}</p>
              </>
            )}
          </Card>
          {event.rules && (
            <Card title="Rules and instructions">
              <p className="pre-wrap">{event.rules}</p>
            </Card>
          )}
          {manage && <OwnerPanel event={event} onChanged={state.reload} />}
        </div>
        <RegistrationPanel event={event} club={club} registrationId={registrationId} onChanged={state.reload} />
      </div>
    </div>
  )
}
