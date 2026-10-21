import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { clubsApi, eventsApi } from '../api/endpoints'
import type { Club } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CalendarIcon, PinIcon, PlusIcon, UsersIcon } from '../components/icons'
import { LocationControl } from '../components/LocationControl'
import { Stars } from '../components/Stars'
import { Badge, Card, EmptyState, ErrorMessage, LinkButton, Loading, PageHeader, Pagination } from '../components/ui'
import { humanize } from '../lib/format'
import { formatAway, savedLocation } from '../lib/location'
import { useAsync } from '../lib/useAsync'
import { ClubLogo } from './clubs/ClubLogo'
import { MembershipBadge } from './clubs/MembershipBadge'
import { EventCardView } from './events/EventCardView'
import './clubs.css'

const PAGE_SIZE = 12

function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(t)
  }, [value, delay])
  return debounced
}

function ClubCard({ club }: { club: Club }) {
  return (
    <Link to={`/clubs/${club.id}`} className="club-card-link">
      <Card className="club-card">
        <div className="row" style={{ flexWrap: 'nowrap' }}>
          <ClubLogo name={club.name} url={club.logoUrl} />
          <div style={{ minWidth: 0 }}>
            <h3 className="club-card-name">{club.name}</h3>
            <div className="row small muted" style={{ gap: 6 }}>
              {club.clubType && <Badge tone="primary">{humanize(club.clubType)}</Badge>}
              <MembershipBadge role={club.myRole} />
            </div>
          </div>
        </div>
        {club.description && <p className="small muted" style={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{club.description}</p>}
        {club.ratingCount > 0 && (
          <div className="row small" style={{ gap: 6 }}>
            <Stars value={club.ratingAverage} size={14} />
            <span className="muted">{club.ratingAverage?.toFixed(1)} ({club.ratingCount})</span>
          </div>
        )}
        <div className="row small muted" style={{ marginTop: 'auto' }}>
          {club.city && <span className="row" style={{ gap: 4 }}><PinIcon size={14} />{club.city}{club.distanceKm != null && ` · ${formatAway(club.distanceKm)}`}</span>}
          <span className="row" style={{ gap: 4 }}><UsersIcon size={14} />{club.memberCount} {club.memberCount === 1 ? 'subscriber' : 'subscribers'}</span>
          {club.followerCount > 0 && <span>{club.followerCount} {club.followerCount === 1 ? 'follower' : 'followers'}</span>}
          {club.following && <Badge tone="info">Following</Badge>}
        </div>
      </Card>
    </Link>
  )
}

function YourClubs() {
  const { data } = useAsync(() => clubsApi.mine(), [])
  if (!data?.length) return null
  return (
    <div style={{ marginBottom: 20 }}>
      <h2 className="small muted bold" style={{ marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Your clubs</h2>
      <div className="club-strip">
        {data.map((club) => (
          <Link key={club.id} to={`/clubs/${club.id}`} className="club-chip">
            <ClubLogo name={club.name} url={club.logoUrl} size={28} />
            <span className="bold small">{club.name}</span>
            {club.myRole === 'OWNER' && <Badge tone="accent">Owner</Badge>}
          </Link>
        ))}
      </div>
    </div>
  )
}

function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="row-between" style={{ marginBottom: 8 }}>
      <h2 className="small muted bold" style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{children}</h2>
      {action}
    </div>
  )
}

/** Nearby clubs, popular clubs and upcoming events (user journey step 3); hidden while someone searches. */
function Discover() {
  const { user } = useAuth()
  const location = savedLocation(user)
  const city = user?.city?.trim()
  const nearby = useAsync(
    () => location ? clubsApi.nearby(location.latitude, location.longitude, 25, 8)
      : city ? clubsApi.search({ city, size: 8 }).then((p) => p.items) : Promise.resolve([]),
    [location?.latitude, location?.longitude, city],
  )
  const popular = useAsync(() => clubsApi.popular(8), [])
  const events = useAsync(
    () => eventsApi.upcoming({ lat: location?.latitude, lng: location?.longitude, limit: 4 }),
    [location?.latitude, location?.longitude],
  )

  return (
    <div className="stack-lg" style={{ marginBottom: 24 }}>
      <section>
        <SectionTitle>{location ? 'Near you' : city ? `In ${city}` : 'Near you'}</SectionTitle>
        {!location && !city ? (
          <Card><LocationControl /></Card>
        ) : nearby.error ? <ErrorMessage error={nearby.error} onRetry={nearby.reload} />
          : nearby.loading && !nearby.data ? <Loading />
          : !nearby.data?.length ? (
            <Card>
              <p className="muted small">{location ? 'No clubs within 25 km yet.' : `No clubs in ${city} yet.`}</p>
              {!location && <div style={{ marginTop: 8 }}><LocationControl /></div>}
            </Card>
          ) : (
            <div className="grid grid-auto">{nearby.data.map((club) => <ClubCard key={club.id} club={club} />)}</div>
          )}
      </section>
      <section>
        <SectionTitle action={<LinkButton to="/events" size="sm" variant="ghost">All events</LinkButton>}>Upcoming events</SectionTitle>
        {events.error ? <ErrorMessage error={events.error} onRetry={events.reload} />
          : events.loading && !events.data ? <Loading />
          : !events.data?.length ? (
            <Card>
              <EmptyState icon={<CalendarIcon size={28} />} title="No upcoming events yet">
                Follow or subscribe to clubs{location ? '' : ', or share your location,'} to see their events here.
              </EmptyState>
            </Card>
          ) : (
            <div className="grid grid-auto">{events.data.map((card) => <EventCardView key={card.event.id} card={card} />)}</div>
          )}
      </section>
      {!!popular.data?.length && (
        <section>
          <SectionTitle>Popular</SectionTitle>
          <div className="grid grid-auto">{popular.data.map((club) => <ClubCard key={club.id} club={club} />)}</div>
        </section>
      )}
    </div>
  )
}

export default function ClubsPage() {
  const { hasRole } = useAuth()
  const [q, setQ] = useState('')
  const [city, setCity] = useState('')
  const dq = useDebounced(q.trim())
  const dcity = useDebounced(city.trim())
  // the page resets whenever the search changes
  const filterKey = `${dq}|${dcity}`
  const [paging, setPaging] = useState({ key: filterKey, page: 0 })
  const page = paging.key === filterKey ? paging.page : 0
  const setPage = (p: number) => setPaging({ key: filterKey, page: p })

  const clubs = useAsync(() => clubsApi.search({ q: dq, city: dcity, page, size: PAGE_SIZE }), [dq, dcity, page])

  return (
    <div className="page">
      <PageHeader title="Discover clubs" subtitle="Find a club to run, ride and train with."
                  actions={hasRole('CLUB_OWNER')
                    ? <LinkButton to="/clubs/new"><PlusIcon size={18} />Create club</LinkButton>
                    : <LinkButton to="/become-owner" variant="secondary">Become a club owner</LinkButton>} />
      <YourClubs />
      {!dq && !dcity && <Discover />}
      {!dq && !dcity && <h2 className="small muted bold" style={{ marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.04em' }}>All clubs</h2>}
      <div className="search-bar">
        <label className="visually-hidden" htmlFor="club-search">Search clubs</label>
        <input id="club-search" className="input" type="search" placeholder="Search clubs by name…" value={q}
               onChange={(e) => setQ(e.target.value)} />
        <label className="visually-hidden" htmlFor="club-city-filter">City</label>
        <input id="club-city-filter" className="input" type="search" placeholder="City" value={city}
               onChange={(e) => setCity(e.target.value)} />
      </div>
      {clubs.error ? <ErrorMessage error={clubs.error} onRetry={clubs.reload} />
        : clubs.loading && !clubs.data ? <Loading />
        : !clubs.data?.items.length ? (
          <Card>
            <EmptyState icon={<UsersIcon size={32} />} title={dq || dcity ? 'No clubs match your search' : 'No clubs yet'}
                        action={hasRole('CLUB_OWNER') ? <LinkButton to="/clubs/new">Create the first club</LinkButton> : undefined}>
              {dq || dcity ? 'Try a different name or city.' : 'Clubs appear here once verified owners create them.'}
            </EmptyState>
          </Card>
        ) : (
          <>
            <div className="grid grid-auto">
              {clubs.data.items.map((club) => <ClubCard key={club.id} club={club} />)}
            </div>
            <Pagination page={clubs.data.page} size={PAGE_SIZE} totalItems={clubs.data.totalItems} onChange={setPage} />
          </>
        )}
    </div>
  )
}
