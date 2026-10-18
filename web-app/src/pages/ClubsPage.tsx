import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { clubsApi } from '../api/endpoints'
import type { Club } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { PinIcon, PlusIcon, UsersIcon } from '../components/icons'
import { Badge, Card, EmptyState, ErrorMessage, LinkButton, Loading, PageHeader, Pagination, StatusBadge } from '../components/ui'
import { humanize } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { ClubLogo } from './clubs/ClubLogo'
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
              <StatusBadge status={club.myRole} />
            </div>
          </div>
        </div>
        {club.description && <p className="small muted" style={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{club.description}</p>}
        <div className="row small muted" style={{ marginTop: 'auto' }}>
          {club.city && <span className="row" style={{ gap: 4 }}><PinIcon size={14} />{club.city}</span>}
          <span className="row" style={{ gap: 4 }}><UsersIcon size={14} />{club.memberCount} {club.memberCount === 1 ? 'member' : 'members'}</span>
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
      <PageHeader title="Clubs" subtitle="Find a club to run, ride and train with."
                  actions={hasRole('CLUB_OWNER')
                    ? <LinkButton to="/clubs/new"><PlusIcon size={18} />Create club</LinkButton>
                    : <LinkButton to="/become-owner" variant="secondary">Become a club owner</LinkButton>} />
      <YourClubs />
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
