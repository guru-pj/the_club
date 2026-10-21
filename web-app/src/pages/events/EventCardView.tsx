import { Link } from 'react-router-dom'
import { mediaUrl } from '../../api/client'
import type { EventCard, FeedSource } from '../../api/types'
import { CalendarIcon, PinIcon } from '../../components/icons'
import { Badge } from '../../components/ui'
import { formatMoney } from '../../lib/format'
import { formatAway } from '../../lib/location'
import { registrationBadge } from '../../lib/registration'
import { ClubLogo } from '../clubs/ClubLogo'

const SOURCE_LABEL: Record<FeedSource, string> = { FOLLOWING: 'Following', SUBSCRIBED: 'Subscribed', NEARBY: 'Nearby' }

function when(value: string) {
  const d = new Date(value)
  return d.toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}

/** An event in a list (user journey step 6): what, who, when, where, cost and whether you can still register. */
export function EventCardView({ card }: { card: EventCard }) {
  const { event } = card
  const badge = registrationBadge(event)
  const away = formatAway(card.distanceKm)
  return (
    <Link to={`/events/${event.id}`} className="event-card-link">
      <article className="event-card">
        <div className="event-card-media">
          {event.imageUrl
            ? <img src={mediaUrl(event.imageUrl)} alt="" loading="lazy" />
            : <div className="event-card-placeholder"><CalendarIcon size={32} /></div>}
          <span className="event-card-price">{event.price > 0 ? formatMoney(event.price, event.currency) : 'Free'}</span>
        </div>
        <div className="event-card-body">
          <div className="row small muted" style={{ gap: 6, flexWrap: 'nowrap' }}>
            <ClubLogo name={card.clubName ?? 'Club'} url={card.clubLogoUrl} size={20} />
            <span className="truncate">{card.clubName ?? 'Club'}</span>
          </div>
          <h3 className="event-card-title">{event.title}</h3>
          <div className="stack small muted" style={{ gap: 2 }}>
            <span className="row" style={{ gap: 4 }}><CalendarIcon size={14} />{when(event.startTime)}</span>
            {event.location && (
              <span className="event-card-where"><PinIcon size={14} /><span>{event.location}{away && ` · ${away} away`}</span></span>
            )}
          </div>
          {event.description && <p className="small event-card-description">{event.description}</p>}
          <div className="row" style={{ gap: 6, marginTop: 'auto' }}>
            <Badge tone={badge.tone}>{badge.label}</Badge>
            {event.subscribersOnly && <Badge tone="accent">Subscribers only</Badge>}
            {card.sources.map((s) => <span key={s} className="chip small">{SOURCE_LABEL[s]}</span>)}
          </div>
        </div>
      </article>
    </Link>
  )
}
