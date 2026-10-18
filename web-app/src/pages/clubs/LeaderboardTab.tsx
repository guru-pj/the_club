import { useState } from 'react'
import { clubsApi } from '../../api/endpoints'
import type { LeaderboardMetric, LeaderboardPeriod } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { TrophyIcon } from '../../components/icons'
import { Avatar, Card, EmptyState, ErrorMessage, Field, Loading, Rank, Tabs } from '../../components/ui'
import { formatDate, formatDistance, formatDuration } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'

const PERIODS: { id: LeaderboardPeriod; label: string }[] = [
  { id: 'WEEK', label: 'This week' },
  { id: 'MONTH', label: 'This month' },
  { id: 'YEAR', label: 'This year' },
  { id: 'ALL', label: 'All time' },
]

export function LeaderboardTab({ clubId }: { clubId: string }) {
  const { user } = useAuth()
  const [metric, setMetric] = useState<LeaderboardMetric>('DISTANCE')
  const [period, setPeriod] = useState<LeaderboardPeriod>('MONTH')
  const board = useAsync(() => clubsApi.leaderboard(clubId, metric, period), [clubId, metric, period])
  const entries = board.data?.entries ?? []
  const hasActivity = entries.some((e) => e.sessions > 0)

  return (
    <div className="stack">
      <div className="row-between" style={{ alignItems: 'flex-end' }}>
        <Tabs tabs={PERIODS} active={period} onChange={setPeriod} />
        <div style={{ minWidth: 180 }}>
          <Field label="Rank by" htmlFor="lb-metric">
            <select id="lb-metric" className="input" value={metric} onChange={(e) => setMetric(e.target.value as LeaderboardMetric)}>
              <option value="DISTANCE">Distance</option>
              <option value="TIME">Time</option>
              <option value="SESSIONS">Sessions</option>
            </select>
          </Field>
        </div>
      </div>
      {board.data && (
        <p className="muted small">
          {board.data.from ? `${formatDate(board.data.from)} – ${formatDate(board.data.to)}` : 'All recorded activities'}
        </p>
      )}
      {board.error ? <ErrorMessage error={board.error} onRetry={board.reload} />
        : board.loading && !board.data ? <Loading />
        : !hasActivity ? (
          <Card><EmptyState icon={<TrophyIcon size={32} />} title="No activity yet">
            Log a run or ride to get on the board.
          </EmptyState></Card>
        ) : (
          <Card flush>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th>#</th><th>Member</th><th className="num">Distance</th><th className="num">Time</th><th className="num">Sessions</th></tr>
                </thead>
                <tbody>
                  {entries.map((e) => (
                    <tr key={e.userId} className={e.userId === user?.id ? 'me-row' : undefined}>
                      <td><Rank rank={e.rank} /></td>
                      <td>
                        <span className="row" style={{ flexWrap: 'nowrap' }}>
                          <Avatar name={e.name} size={28} />
                          <span>{e.name ?? 'Member'}{e.userId === user?.id && <span className="muted small"> (you)</span>}</span>
                        </span>
                      </td>
                      <td className="num">{formatDistance(e.totalDistanceKm)}</td>
                      <td className="num">{formatDuration(e.totalDurationSeconds)}</td>
                      <td className="num">{e.sessions}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}
    </div>
  )
}
