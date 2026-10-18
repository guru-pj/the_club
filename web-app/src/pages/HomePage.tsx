import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { activitiesApi, clubsApi, eventsApi, participationApi, verificationApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { ActivityIcon, CalendarIcon, ChevronRightIcon, PlusIcon, ShieldIcon, StarIcon, UsersIcon } from '../components/icons'
import { useToast } from '../components/Toast'
import {
  Avatar, Badge, Button, Card, EmptyState, ErrorMessage, LinkButton, Loading, Stat, StatusBadge,
} from '../components/ui'
import { formatDateTime, formatDistance, formatDuration, humanize, nowLocal } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { monthStart } from './account/monthStart'
import './account.css'

function MonthStats() {
  const activities = useAsync(() => activitiesApi.mine({ from: monthStart(), size: 100 }), [])
  const participation = useAsync(() => participationApi.me(), [])
  const totals = (activities.data?.items ?? []).reduce(
    (acc, a) => ({ distance: acc.distance + a.distance, duration: acc.duration + a.duration }),
    { distance: 0, duration: 0 },
  )
  const dash = (loading: boolean, failed: unknown, value: string | number) => (loading ? '…' : failed ? '—' : value)
  return (
    <Card>
      <div className="grid grid-3">
        <Stat label="Distance this month" value={dash(activities.loading, activities.error, formatDistance(totals.distance))} />
        <Stat label="Time this month" value={dash(activities.loading, activities.error, formatDuration(totals.duration))} />
        <Stat label="Sessions this month" value={dash(activities.loading, activities.error, activities.data?.totalItems ?? 0)} />
        <Stat label="Club points" value={dash(participation.loading, participation.error, participation.data?.totalPoints ?? 0)} />
        <Stat label="Stars" value={<span className="row" style={{ gap: 4 }}>
          {dash(participation.loading, participation.error, participation.data?.stars ?? 0)}
          <StarIcon size={20} style={{ color: 'var(--accent)' }} />
        </span>} />
        <Stat label="Events attended" value={dash(participation.loading, participation.error, participation.data?.eventsAttended ?? 0)} />
      </div>
    </Card>
  )
}

function UpcomingEvents() {
  const { data, error, loading, reload } = useAsync(() => eventsApi.myRegistrations(), [])
  const now = nowLocal()
  const upcoming = (data ?? [])
    .filter((r) => (r.status === 'CONFIRMED' || r.status === 'PENDING_PAYMENT') && r.startTime >= now && r.eventStatus !== 'CANCELLED')
    .sort((a, b) => a.startTime.localeCompare(b.startTime))
  return (
    <Card title="Upcoming events" flush actions={<Link to="/tickets" className="small">All tickets</Link>}>
      {loading ? <Loading /> : error ? <div style={{ padding: 20 }}><ErrorMessage error={error} onRetry={reload} /></div>
        : upcoming.length === 0 ? (
          <EmptyState icon={<CalendarIcon size={28} />} title="No upcoming events">Events from your clubs will appear here once you register.</EmptyState>
        ) : (
          <div className="list" style={{ marginTop: 12 }}>
            {upcoming.map((r) => (
              <Link key={r.registrationId} to={`/events/${r.eventId}`} className="list-item">
                <span className="notification-icon"><CalendarIcon size={18} /></span>
                <span className="list-item-main">
                  <span className="bold truncate" style={{ display: 'block' }}>{r.eventTitle}</span>
                  <span className="muted small">{formatDateTime(r.startTime)}{r.location ? ` · ${r.location}` : ''}</span>
                </span>
                {r.status === 'PENDING_PAYMENT' ? <Badge tone="warning">Payment pending</Badge> : <StatusBadge status={r.status} />}
              </Link>
            ))}
          </div>
        )}
    </Card>
  )
}

function MyClubs({ clubs }: { clubs: ReturnType<typeof useAsync<Awaited<ReturnType<typeof clubsApi.mine>>>> }) {
  const { data, error, loading, reload } = clubs
  return (
    <Card title="My clubs" flush actions={<Link to="/clubs" className="small">Browse</Link>}>
      {loading ? <Loading /> : error ? <div style={{ padding: 20 }}><ErrorMessage error={error} onRetry={reload} /></div>
        : !data || data.length === 0 ? (
          <EmptyState icon={<UsersIcon size={28} />} title="You haven't joined a club yet"
                      action={<LinkButton to="/clubs">Find a club</LinkButton>} />
        ) : (
          <div className="list" style={{ marginTop: 12 }}>
            {data.map((c) => (
              <Link key={c.id} to={`/clubs/${c.id}`} className="list-item">
                <Avatar name={c.name} url={c.logoUrl} />
                <span className="list-item-main">
                  <span className="bold truncate" style={{ display: 'block' }}>{c.name}</span>
                  <span className="muted small">{[c.city, c.clubType && humanize(c.clubType), `${c.memberCount} members`].filter(Boolean).join(' · ')}</span>
                </span>
                {c.myRole === 'OWNER' && <StatusBadge status="OWNER" />}
                <ChevronRightIcon size={18} className="muted" />
              </Link>
            ))}
          </div>
        )}
    </Card>
  )
}

function OwnerBanner() {
  const { refreshSession } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [refreshing, setRefreshing] = useState(false)
  const { data, loading, error } = useAsync(() => verificationApi.mine(), [])
  if (loading || error) return null
  const latest = [...(data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]

  if (latest?.status === 'APPROVED') {
    return (
      <div className="alert alert-success">
        <ShieldIcon size={20} />
        <div style={{ flex: 1 }}>Your club owner application was approved.</div>
        <Button size="sm" loading={refreshing} onClick={async () => {
          setRefreshing(true)
          try {
            await refreshSession()
            toast.success('You are now a club owner')
            navigate('/clubs/new')
          } catch (err) {
            toast.error(err)
            setRefreshing(false)
          }
        }}>Start my club</Button>
      </div>
    )
  }
  if (latest && ['PENDING', 'UNDER_REVIEW', 'NEEDS_INFO'].includes(latest.status)) {
    return (
      <div className={latest.status === 'NEEDS_INFO' ? 'alert alert-warning' : 'alert alert-info'}>
        <ShieldIcon size={20} />
        <div style={{ flex: 1 }}>
          {latest.status === 'NEEDS_INFO'
            ? 'The admin team needs more information about your club owner application.'
            : 'Your club owner application is being reviewed.'}
        </div>
        <LinkButton to="/become-owner" size="sm" variant="secondary">View application</LinkButton>
      </div>
    )
  }
  return (
    <Card>
      <div className="row-between">
        <div className="row"><ShieldIcon size={20} className="muted" /><span>Run a club? Apply to become a club owner.</span></div>
        <LinkButton to="/become-owner" size="sm" variant="secondary">Apply</LinkButton>
      </div>
    </Card>
  )
}

export default function HomePage() {
  const { user, hasRole } = useAuth()
  const clubs = useAsync(() => clubsApi.mine(), [])
  const firstName = user?.name?.split(/\s+/)[0] ?? ''
  const ownsClub = clubs.data?.some((c) => c.myRole === 'OWNER')

  return (
    <div className="page">
      <div className="hero-band" style={{ marginBottom: 20 }}>
        <div className="row-between">
          <div>
            <h1>Hi {firstName} 👋</h1>
            <p className="muted" style={{ marginTop: 4 }}>Ready for your next session?</p>
          </div>
          <div className="row">
            <LinkButton to="/activities?log=1" variant="secondary"><PlusIcon size={18} />Log activity</LinkButton>
            <LinkButton to="/clubs" variant="secondary"><UsersIcon size={18} />Find clubs</LinkButton>
          </div>
        </div>
      </div>
      <div className="stack-lg">
        {!hasRole('CLUB_OWNER') && <OwnerBanner />}
        {hasRole('CLUB_OWNER') && clubs.data && !ownsClub && (
          <div className="alert alert-info">
            <ActivityIcon size={20} />
            <div style={{ flex: 1 }}>You're a verified club owner. Set up your club to start posting and hosting events.</div>
            <LinkButton to="/clubs/new" size="sm">Create your club</LinkButton>
          </div>
        )}
        <MonthStats />
        <div className="grid grid-2" style={{ alignItems: 'start' }}>
          <UpcomingEvents />
          <MyClubs clubs={clubs} />
        </div>
      </div>
    </div>
  )
}
