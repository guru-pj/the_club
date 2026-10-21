import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { clubsApi, eventsApi, verificationApi } from '../api/endpoints'
import type { Club, ClubEventStats, EventStatsRow } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import {
  CalendarIcon, ChatIcon, CheckIcon, EditIcon, PlusIcon, ScanIcon, ShieldIcon, TrophyIcon, UsersIcon,
} from '../components/icons'
import { useToast } from '../components/Toast'
import {
  Badge, Button, Card, EmptyState, ErrorMessage, LinkButton, Loading, Modal, PageHeader, StatusBadge,
} from '../components/ui'
import { formatDateTime, formatMoney } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { ClubForm } from './clubs/ClubForm'
import { ClubImages } from './clubs/ClubImages'
import { ClubLogo } from './clubs/ClubLogo'
import { checkInWindow } from './owner/checkInWindow'
import { profileChecklist } from './owner/profileChecklist'
import './clubs.css'
import './owner/owner.css'

function ProfileChecklist({ club, onEdit }: { club: Club; onEdit: () => void }) {
  const items = profileChecklist(club)
  const done = items.filter((i) => i.done).length
  if (done === items.length) return null
  return (
    <Card title="Complete your club profile" actions={<Badge tone="warning">{done}/{items.length}</Badge>}>
      <div className="stack">
        <p className="small muted">A complete profile helps people find the club and decide to join.</p>
        <ul className="checklist">
          {items.map((i) => (
            <li key={i.label} className={i.done ? 'done' : undefined}>
              <span className="bullet">{i.done ? <CheckIcon size={12} /> : null}</span>{i.label}
            </li>
          ))}
        </ul>
        <div><Button onClick={onEdit}><EditIcon size={16} />Complete the profile</Button></div>
      </div>
    </Card>
  )
}

function Numbers({ club, stats }: { club: Club; stats: ClubEventStats | undefined }) {
  const tiles = [
    { label: 'Subscribers', value: club.memberCount },
    { label: 'Followers', value: club.followerCount },
    { label: 'Rating', value: club.ratingCount ? `${club.ratingAverage?.toFixed(1)} (${club.ratingCount})` : '—' },
    { label: 'Upcoming events', value: stats?.upcoming ?? '…' },
    { label: 'Drafts', value: stats?.drafts ?? '…' },
    { label: 'Registrations', value: stats?.registrations ?? '…' },
    { label: 'Check-ins', value: stats?.checkIns ?? '…' },
    { label: 'Attendance', value: stats ? (stats.attendanceRate != null ? `${Math.round(stats.attendanceRate * 100)}%` : '—') : '…' },
    ...Object.entries(stats?.revenue ?? {}).map(([currency, amount]) => ({ label: `Collected (${currency})`, value: formatMoney(amount, currency) })),
  ]
  return (
    <div className="stats-row owner-stats">
      {tiles.map((t) => (
        <div key={t.label} className="stat"><span className="stat-label">{t.label}</span><span className="stat-value">{t.value}</span></div>
      ))}
    </div>
  )
}

function EventRows({ club, rows, onChanged }: { club: Club; rows: EventStatsRow[]; onChanged: () => void }) {
  const toast = useToast()
  const [busy, setBusy] = useState<string>()
  const [now] = useState(Date.now)
  const act = async (row: EventStatsRow, action: 'publish' | 'complete') => {
    setBusy(row.eventId + action)
    try {
      if (action === 'publish') await eventsApi.publish(row.eventId)
      else await eventsApi.complete(row.eventId)
      toast.success(action === 'publish' ? `${row.title} is published` : `${row.title} is completed`)
      onChanged()
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(undefined)
    }
  }

  if (!rows.length) {
    return <EmptyState icon={<CalendarIcon size={28} />} title="No events yet"
                       action={<LinkButton to={`/clubs/${club.id}/events/new`}><PlusIcon size={16} />Create the first event</LinkButton>} />
  }
  return (
    <div className="table-wrap">
      <table className="table owner-events">
        <thead>
          <tr><th>Event</th><th>Status</th><th>Registered</th><th>Checked in</th><th><span className="visually-hidden">Actions</span></th></tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const started = Date.parse(r.startTime) <= now
            const checkIn = r.status === 'SCHEDULED' && checkInWindow(r.startTime, now)
            return (
              <tr key={r.eventId}>
                <td>
                  <Link to={`/events/${r.eventId}`} className="bold">{r.title}</Link>
                  <div className="small muted">{formatDateTime(r.startTime)}</div>
                </td>
                <td><StatusBadge status={r.status} /></td>
                <td>{r.registered}{r.capacity != null && <span className="muted"> / {r.capacity}</span>}</td>
                <td>{r.status === 'DRAFT' ? '—' : r.checkedIn}{r.attendanceRate != null && <span className="muted small"> ({Math.round(r.attendanceRate * 100)}%)</span>}</td>
                <td>
                  <div className="row" style={{ justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                    {r.status === 'DRAFT' && (
                      <Button size="sm" loading={busy === r.eventId + 'publish'} onClick={() => void act(r, 'publish')}>Publish</Button>
                    )}
                    {checkIn && <LinkButton size="sm" to={`/events/${r.eventId}/check-in`}><ScanIcon size={14} />Check in</LinkButton>}
                    {r.status === 'SCHEDULED' && started && (
                      <Button size="sm" variant="secondary" loading={busy === r.eventId + 'complete'} onClick={() => void act(r, 'complete')}>Complete</Button>
                    )}
                    {(r.status === 'COMPLETED' || (r.status === 'SCHEDULED' && started)) && (
                      <LinkButton size="sm" variant="ghost" to={`/clubs/${club.id}?recap=${r.eventId}`}>Recap</LinkButton>
                    )}
                    <LinkButton size="sm" variant="ghost" to={`/events/${r.eventId}`}>Registrations</LinkButton>
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function ClubDashboard({ club, onChanged }: { club: Club; onChanged: (club?: Club) => void }) {
  const toast = useToast()
  const { hasRole } = useAuth()
  const [editing, setEditing] = useState(false)
  const stats = useAsync(() => eventsApi.clubStats(club.id), [club.id])
  const [now] = useState(Date.now)
  const today = stats.data?.events.filter((e) => e.status === 'SCHEDULED' && checkInWindow(e.startTime, now)) ?? []

  return (
    <div className="stack-lg">
      <Card>
        <div className="row" style={{ flexWrap: 'nowrap' }}>
          <ClubLogo name={club.name} url={club.logoUrl} size={56} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="row" style={{ gap: 8 }}>
              <h2 className="truncate">{club.name}</h2>
              {club.verified && <span className="verified-badge"><ShieldIcon size={14} />Verified</span>}
            </div>
            <div className="small muted">{[club.address, club.city].filter(Boolean).join(', ')}</div>
          </div>
          <LinkButton variant="secondary" size="sm" to={`/clubs/${club.id}`}>Public page</LinkButton>
        </div>
      </Card>

      <ProfileChecklist club={club} onEdit={() => setEditing(true)} />

      {today.length > 0 && (
        <Card title="Today">
          <div className="stack">
            {today.map((e) => (
              <div key={e.eventId} className="row-between">
                <div><div className="bold">{e.title}</div><div className="small muted">{formatDateTime(e.startTime)} · {e.checkedIn} of {e.registered} checked in</div></div>
                <LinkButton to={`/events/${e.eventId}/check-in`}><ScanIcon size={16} />Scan tickets</LinkButton>
              </div>
            ))}
          </div>
        </Card>
      )}

      <div className="owner-actions">
        <LinkButton to={`/clubs/${club.id}/events/new`}><PlusIcon size={16} />Create event</LinkButton>
        <LinkButton variant="secondary" to={`/clubs/${club.id}`}><EditIcon size={16} />Post an update</LinkButton>
        <LinkButton variant="secondary" to={`/clubs/${club.id}?tab=members`}><UsersIcon size={16} />Members</LinkButton>
        <LinkButton variant="secondary" to={`/clubs/${club.id}?tab=chat`}><ChatIcon size={16} />Club chat</LinkButton>
        <LinkButton variant="secondary" to={`/clubs/${club.id}?tab=participation`}><TrophyIcon size={16} />Participation</LinkButton>
        <Button variant="secondary" onClick={() => setEditing(true)}><EditIcon size={16} />Edit profile</Button>
      </div>

      <Card title="Numbers">
        {stats.error ? <ErrorMessage error={stats.error} onRetry={stats.reload} /> : <Numbers club={club} stats={stats.data} />}
      </Card>

      <Card title="Events" flush actions={<LinkButton size="sm" to={`/clubs/${club.id}/events/new`}><PlusIcon size={14} />New</LinkButton>}>
        {stats.loading && !stats.data ? <Loading /> : stats.data && <EventRows club={club} rows={stats.data.events} onChanged={stats.reload} />}
      </Card>

      <Modal open={editing} title="Club profile" onClose={() => setEditing(false)}>
        <ClubImages club={club} onChanged={onChanged} />
        <ClubForm initial={club} submitLabel="Save profile" nameLocked={!hasRole('ADMIN')} onCancel={() => setEditing(false)}
                  onSubmit={async (request) => {
                    onChanged(await clubsApi.update(club.id, request))
                    toast.success('Profile saved')
                    setEditing(false)
                  }} />
      </Modal>
    </div>
  )
}

/** The club owner's home: every verified club they run, with what needs doing and how it's going. */
export default function OwnerDashboardPage() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const clubs = useAsync(() => (user ? clubsApi.ownedBy(user.id) : Promise.resolve([])), [user?.id])
  const applications = useAsync(() => verificationApi.mine(), [])
  const waiting = applications.data?.filter((a) => a.status === 'PENDING' || a.status === 'UNDER_REVIEW' || a.status === 'NEEDS_INFO') ?? []

  if (clubs.error) return <div className="page"><ErrorMessage error={clubs.error} onRetry={clubs.reload} /></div>
  if (!clubs.data) return <Loading />
  const owned = clubs.data
  const selected = owned.find((c) => c.id === params.get('club')) ?? owned[0]

  return (
    <div className="page">
      <PageHeader title="Owner dashboard" subtitle="Run your verified clubs: profile, events, registrations, check-ins and numbers."
                  actions={<LinkButton variant="secondary" to="/become-owner"><PlusIcon size={16} />Apply for another club</LinkButton>} />
      {waiting.length > 0 && (
        <div className="alert alert-info" style={{ marginBottom: 16 }}>
          <div style={{ flex: 1 }}>
            {waiting.length === 1 ? `Your application for ${waiting[0].clubName} is ` : `${waiting.length} club applications are `}
            {waiting.some((a) => a.status === 'NEEDS_INFO') ? 'waiting for changes from you.' : 'waiting for review.'}
          </div>
          <LinkButton size="sm" variant="secondary" to="/become-owner">View</LinkButton>
        </div>
      )}
      {!selected ? (
        <Card>
          <EmptyState icon={<ShieldIcon size={32} />} title="No verified clubs yet"
                      action={<LinkButton to="/become-owner">{waiting.length ? 'See your applications' : 'Apply for your club'}</LinkButton>}>
            Each club you run is verified by the admin team first. Once approved, it appears here with its owner tools.
          </EmptyState>
        </Card>
      ) : (
        <>
          {owned.length > 1 && (
            <div className="club-strip" role="tablist" aria-label="Your clubs" style={{ marginBottom: 16 }}>
              {owned.map((c) => (
                <button key={c.id} role="tab" aria-selected={c.id === selected.id}
                        className={c.id === selected.id ? 'club-chip active' : 'club-chip'}
                        onClick={() => setParams({ club: c.id }, { replace: true })}>
                  <ClubLogo name={c.name} url={c.logoUrl} size={28} />{c.name}
                </button>
              ))}
            </div>
          )}
          <ClubDashboard key={selected.id} club={selected}
                         onChanged={(updated) => (updated
                           ? clubs.setData((list) => (list ?? []).map((c) => (c.id === updated.id ? updated : c)))
                           : clubs.reload())} />
        </>
      )}
    </div>
  )
}
