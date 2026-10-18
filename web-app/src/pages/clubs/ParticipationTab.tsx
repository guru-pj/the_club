import { participationApi } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { StarIcon } from '../../components/icons'
import { Avatar, Card, EmptyState, ErrorMessage, Loading, Rank } from '../../components/ui'
import { useAsync } from '../../lib/useAsync'

export function ParticipationTab({ clubId }: { clubId: string }) {
  const { user } = useAuth()
  const rows = useAsync(() => participationApi.club(clubId), [clubId])

  return (
    <div className="stack">
      <p className="muted small">Members earn points each time they're checked in at a club event, and a star for every five events attended.</p>
      {rows.error ? <ErrorMessage error={rows.error} onRetry={rows.reload} />
        : rows.loading && !rows.data ? <Loading />
        : !rows.data?.length ? (
          <Card><EmptyState icon={<StarIcon size={32} />} title="No check-ins yet">Points appear after the first event check-in.</EmptyState></Card>
        ) : (
          <Card flush>
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>#</th><th>Member</th><th className="num">Events</th><th className="num">Points</th><th className="num">Stars</th></tr></thead>
                <tbody>
                  {rows.data.map((r, i) => (
                    <tr key={r.userId} className={r.userId === user?.id ? 'me-row' : undefined}>
                      <td><Rank rank={i + 1} /></td>
                      <td><span className="row" style={{ flexWrap: 'nowrap' }}><Avatar name={r.name} size={28} />{r.name ?? 'Member'}</span></td>
                      <td className="num">{r.eventsAttended}</td>
                      <td className="num bold">{r.totalPoints}</td>
                      <td className="num">
                        <span className="row" style={{ justifyContent: 'flex-end', gap: 4, color: 'var(--accent)' }}>
                          <StarIcon size={16} />{r.stars}
                        </span>
                      </td>
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
