import { useState } from 'react'
import { Link } from 'react-router-dom'
import { leaderboardsApi, participationApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { ChevronLeftIcon, ChevronRightIcon, StarIcon, TrophyIcon } from '../components/icons'
import { Button, Card, EmptyState, ErrorMessage, Loading, PageHeader, Rank, Stat } from '../components/ui'
import { useAsync } from '../lib/useAsync'
import { monthKey, monthLabel, shiftMonth } from './events/shared'
import './events.css'

const EVENTS_PER_STAR = 5

function Stars({ count }: { count: number }) {
  const shown = Math.min(count, 5)
  return (
    <span className="stars" aria-label={`${count} stars`}>
      {Array.from({ length: 5 }, (_, i) => (
        <StarIcon key={i} size={18} className={i < shown ? undefined : 'off'} fill={i < shown ? 'currentColor' : 'none'} />
      ))}
    </span>
  )
}

function MyStats() {
  const me = useAsync(() => participationApi.me(), [])
  if (me.loading && !me.data) return <Card><Loading /></Card>
  if (me.error) return <ErrorMessage error={me.error} onRetry={me.reload} />
  if (!me.data) return null
  const { totalPoints, stars, eventsAttended } = me.data
  const progress = eventsAttended % EVENTS_PER_STAR
  return (
    <Card title="Your stats">
      <div className="grid grid-3">
        <Stat label="Total points" value={totalPoints} />
        <Stat label="Stars" value={<span className="row" style={{ gap: 8 }}><Stars count={stars} />{stars}</span>} />
        <Stat label="Events attended" value={eventsAttended} />
      </div>
      <div className="stack" style={{ gap: 6, marginTop: 16 }}>
        <div className="row-between small">
          <span className="muted">Next star</span>
          <span className="bold">{progress} / {EVENTS_PER_STAR} events</span>
        </div>
        <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={EVENTS_PER_STAR} aria-valuenow={progress}>
          <div style={{ width: `${(progress / EVENTS_PER_STAR) * 100}%` }} />
        </div>
      </div>
    </Card>
  )
}

export default function LeaderboardPage() {
  const { user } = useAuth()
  const [current] = useState(() => monthKey(new Date()))
  const [month, setMonth] = useState(current)
  const board = useAsync(() => leaderboardsApi.monthly(month, 10), [month])

  return (
    <div className="page">
      <PageHeader title="Leaderboard"
                  subtitle="Earn points by checking in at club events. Every 5 events attended earns a star."
                  actions={
                    <div className="month-picker">
                      <Button variant="secondary" size="sm" aria-label="Previous month" onClick={() => setMonth((m) => shiftMonth(m, -1))}>
                        <ChevronLeftIcon size={16} />
                      </Button>
                      <span className="label">{monthLabel(month)}</span>
                      <Button variant="secondary" size="sm" aria-label="Next month" disabled={month >= current}
                              onClick={() => setMonth((m) => shiftMonth(m, 1))}>
                        <ChevronRightIcon size={16} />
                      </Button>
                    </div>
                  } />
      <div className="stack-lg">
        <MyStats />
        {board.loading && !board.data ? <Loading /> : board.error ? <ErrorMessage error={board.error} onRetry={board.reload} /> : board.data && (
          <div className="grid grid-2">
            <Card title="Top members" flush>
              {board.data.topIndividuals.length === 0 ? (
                <EmptyState icon={<TrophyIcon size={28} />} title="No points earned this month yet" />
              ) : (
                <div className="table-wrap">
                  <table className="table">
                    <thead><tr><th>#</th><th>Member</th><th className="num">Points</th><th className="num">Events</th></tr></thead>
                    <tbody>
                      {board.data.topIndividuals.map((e) => (
                        <tr key={e.userId} className={e.userId === user?.id ? 'me-row' : undefined}>
                          <td><Rank rank={e.rank} /></td>
                          <td className="bold">{e.name ?? 'Member'}{e.userId === user?.id && <span className="muted small"> (you)</span>}</td>
                          <td className="num bold">{e.points}</td>
                          <td className="num">{e.eventsAttended}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
            <Card title="Top clubs" flush>
              {board.data.topClubs.length === 0 ? (
                <EmptyState icon={<TrophyIcon size={28} />} title="No club activity this month yet" />
              ) : (
                <div className="table-wrap">
                  <table className="table">
                    <thead><tr><th>#</th><th>Club</th><th className="num">Points</th><th className="num">Check-ins</th></tr></thead>
                    <tbody>
                      {board.data.topClubs.map((c) => (
                        <tr key={c.clubId}>
                          <td><Rank rank={c.rank} /></td>
                          <td className="bold"><Link to={`/clubs/${c.clubId}`}>{c.name ?? 'Club'}</Link></td>
                          <td className="num bold">{c.points}</td>
                          <td className="num">{c.participations}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          </div>
        )}
      </div>
    </div>
  )
}
