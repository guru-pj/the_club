import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { chatApi, clubsApi } from '../../api/endpoints'
import type { Membership } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Avatar, Button, Card, EmptyState, ErrorMessage, Loading, Pagination, StatusBadge } from '../../components/ui'
import { formatDate } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { ConfirmModal } from './ConfirmModal'

const PAGE_SIZE = 20

/** `canMessage`: the caller is in the club too, so they can message its members directly. */
export function MembersTab({ clubId, canManage, canMessage, onChanged }:
  { clubId: string; canManage: boolean; canMessage: boolean; onChanged: () => void }) {
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [opening, setOpening] = useState<string>()

  const message = async (m: Membership) => {
    setOpening(m.userId)
    try {
      const dm = await chatApi.direct(m.userId)
      if (dm.id) navigate(`/messages/${dm.id}`)
    } catch (e) {
      toast.error(e)
    } finally {
      setOpening(undefined)
    }
  }
  const [page, setPage] = useState(0)
  const [toRemove, setToRemove] = useState<Membership | null>(null)
  const members = useAsync(() => clubsApi.members(clubId, { page, size: PAGE_SIZE }), [clubId, page])

  if (members.error) return <ErrorMessage error={members.error} onRetry={members.reload} />
  if (members.loading && !members.data) return <Loading />
  if (!members.data?.items.length) return <Card><EmptyState title="No members yet" /></Card>

  return (
    <Card flush>
      <div className="list">
        {members.data.items.map((m) => (
          <div key={m.userId} className="list-item">
            <Avatar name={m.name} />
            <div className="list-item-main">
              <div className="bold truncate">{m.name ?? 'Member'}</div>
              <div className="muted small">Joined {formatDate(m.joinedAt)}</div>
            </div>
            <StatusBadge status={m.role} />
            {canMessage && m.userId !== user?.id && (
              <Button size="sm" variant="secondary" loading={opening === m.userId} onClick={() => void message(m)}>Message</Button>
            )}
            {canManage && m.role === 'MEMBER' && (
              <Button size="sm" variant="ghost" onClick={() => setToRemove(m)}>Remove</Button>
            )}
          </div>
        ))}
      </div>
      <Pagination page={members.data.page} size={PAGE_SIZE} totalItems={members.data.totalItems} onChange={setPage} />
      <ConfirmModal open={!!toRemove} title="Remove member?" confirmLabel="Remove" onClose={() => setToRemove(null)}
                    onConfirm={async () => {
                      if (!toRemove) return
                      await clubsApi.removeMember(clubId, toRemove.userId)
                      toast.success(`${toRemove.name ?? 'Member'} removed`)
                      members.reload()
                      onChanged()
                    }}>
        {toRemove?.name ?? 'This member'} will lose access to member-only actions like event registration. They can join again later.
      </ConfirmModal>
    </Card>
  )
}
