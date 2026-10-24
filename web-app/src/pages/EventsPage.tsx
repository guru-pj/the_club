import { useState } from 'react'
import { eventsApi } from '../api/endpoints'
import type { FeedSource } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CalendarIcon } from '../components/icons'
import { LocationControl } from '../components/LocationControl'
import { Card, EmptyState, ErrorMessage, LinkButton, Loading, PageHeader, Tabs } from '../components/ui'
import { savedLocation } from '../lib/location'
import { useAsync } from '../lib/useAsync'
import { EventCardView } from './events/EventCardView'
import './events.css'

type Filter = 'ALL' | FeedSource
const FILTERS: { id: Filter; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'FOLLOWING', label: 'Following' },
  { id: 'SUBSCRIBED', label: 'Subscribed' },
  { id: 'NEARBY', label: 'Nearby' },
]

/** Upcoming events from clubs you follow or subscribe to, and from clubs near you (user journey step 6). */
export default function EventsPage() {
  const { user } = useAuth()
  const location = savedLocation(user)
  const [filter, setFilter] = useState<Filter>('ALL')
  const feed = useAsync(
    () => eventsApi.upcoming({ lat: location?.latitude, lng: location?.longitude, city: user?.city?.trim() || undefined, limit: 50 }),
    [location?.latitude, location?.longitude, user?.city],
  )
  const cards = (feed.data ?? []).filter((c) => filter === 'ALL' || c.sources.includes(filter))

  return (
    <div className="page">
      <PageHeader title="Upcoming events" subtitle="From clubs you follow or subscribe to, and clubs near you." />
      {!location && (
        <Card>
          <p className="bold" style={{ marginBottom: 6 }}>See events near you</p>
          <LocationControl />
        </Card>
      )}
      <div style={{ margin: '16px 0 12px' }}>
        <Tabs tabs={FILTERS} active={filter} onChange={setFilter} />
      </div>
      {feed.error ? <ErrorMessage error={feed.error} onRetry={feed.reload} />
        : feed.loading && !feed.data ? <Loading />
        : !cards.length ? (
          <Card>
            <EmptyState icon={<CalendarIcon size={32} />}
                        title={filter === 'ALL' ? 'No upcoming events yet' : `No upcoming events from ${FILTERS.find((f) => f.id === filter)?.label.toLowerCase()} clubs`}
                        action={<LinkButton to="/clubs">Discover clubs</LinkButton>}>
              Follow or subscribe to clubs to see their events here.
            </EmptyState>
          </Card>
        ) : (
          <div className="grid grid-auto">
            {cards.map((card) => <EventCardView key={card.event.id} card={card} />)}
          </div>
        )}
    </div>
  )
}
