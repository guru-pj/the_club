import { useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { clubsApi } from '../api/endpoints'
import type { Club } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { EditIcon, PinIcon, PlusIcon, UsersIcon } from '../components/icons'
import { useToast } from '../components/Toast'
import { Badge, Button, Card, EmptyState, ErrorMessage, LinkButton, Loading, Modal, StatusBadge, Tabs } from '../components/ui'
import { humanize } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { ClubForm } from './clubs/ClubForm'
import { ClubLogo } from './clubs/ClubLogo'
import { ConfirmModal } from './clubs/ConfirmModal'
import { EventsTab } from './clubs/EventsTab'
import { FeedTab } from './clubs/FeedTab'
import { LeaderboardTab } from './clubs/LeaderboardTab'
import { MembersTab } from './clubs/MembersTab'
import { ParticipationTab } from './clubs/ParticipationTab'
import './clubs.css'

type TabId = 'feed' | 'events' | 'leaderboard' | 'members' | 'participation'
const TABS: { id: TabId; label: string }[] = [
  { id: 'feed', label: 'Feed' },
  { id: 'events', label: 'Events' },
  { id: 'leaderboard', label: 'Leaderboard' },
  { id: 'members', label: 'Members' },
  { id: 'participation', label: 'Participation' },
]

function ClubHeader({ club, canManage, onChanged }: { club: Club; canManage: boolean; onChanged: (club?: Club) => void }) {
  const toast = useToast()
  const [joining, setJoining] = useState(false)
  const [leaveOpen, setLeaveOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)

  const join = async () => {
    setJoining(true)
    try {
      await clubsApi.join(club.id)
      toast.success(`Welcome to ${club.name}!`)
      onChanged()
    } catch (e) {
      toast.error(e)
    } finally {
      setJoining(false)
    }
  }

  return (
    <Card>
      <div className="club-hero">
        <ClubLogo name={club.name} url={club.logoUrl} size={72} />
        <div className="club-hero-main stack" style={{ gap: 6 }}>
          <div className="row">
            <h1>{club.name}</h1>
            <StatusBadge status={club.myRole} />
          </div>
          <div className="row small muted" style={{ gap: 14 }}>
            {club.clubType && <Badge tone="primary">{humanize(club.clubType)}</Badge>}
            {club.city && <span className="row" style={{ gap: 4 }}><PinIcon size={14} />{club.city}</span>}
            <span className="row" style={{ gap: 4 }}><UsersIcon size={14} />{club.memberCount} {club.memberCount === 1 ? 'member' : 'members'}</span>
          </div>
          {club.description && <p className="pre-wrap">{club.description}</p>}
        </div>
        <div className="row">
          {!club.myRole && <Button onClick={join} loading={joining}>Join club</Button>}
          {club.myRole === 'MEMBER' && <Button variant="secondary" onClick={() => setLeaveOpen(true)}>Leave</Button>}
          {canManage && (
            <>
              <Button variant="secondary" onClick={() => setEditOpen(true)}><EditIcon size={16} />Edit club</Button>
              <LinkButton to={`/clubs/${club.id}/events/new`}><PlusIcon size={16} />Create event</LinkButton>
            </>
          )}
        </div>
      </div>
      <ConfirmModal open={leaveOpen} title={`Leave ${club.name}?`} confirmLabel="Leave club" onClose={() => setLeaveOpen(false)}
                    onConfirm={async () => {
                      await clubsApi.leave(club.id)
                      toast.success(`You left ${club.name}`)
                      onChanged()
                    }}>
        You won't be able to register for this club's events until you join again.
      </ConfirmModal>
      <Modal open={editOpen} title="Edit club" onClose={() => setEditOpen(false)}>
        <ClubForm initial={club} submitLabel="Save changes" onCancel={() => setEditOpen(false)} onSubmit={async (request) => {
          const updated = await clubsApi.update(club.id, request)
          toast.success('Club updated')
          setEditOpen(false)
          onChanged(updated)
        }} />
      </Modal>
    </Card>
  )
}

export default function ClubPage() {
  const { clubId = '' } = useParams()
  const { hasRole } = useAuth()
  const [params, setParams] = useSearchParams()
  const club = useAsync(() => clubsApi.get(clubId), [clubId])

  const requested = params.get('tab') as TabId | null
  const tab: TabId = TABS.some((t) => t.id === requested) ? (requested as TabId) : 'feed'

  if (club.error instanceof ApiError && club.error.status === 404) {
    return <div className="page"><EmptyState title="Club not found" action={<LinkButton to="/clubs">Browse clubs</LinkButton>} /></div>
  }
  if (club.error) return <div className="page"><ErrorMessage error={club.error} onRetry={club.reload} /></div>
  if (!club.data) return <Loading />

  const data = club.data
  const canManage = data.myRole === 'OWNER' || hasRole('ADMIN')
  const onChanged = (updated?: Club) => (updated ? club.setData(updated) : club.reload())

  return (
    <div className="page stack-lg">
      <ClubHeader club={data} canManage={canManage} onChanged={onChanged} />
      <div key={data.id}>
        <Tabs tabs={TABS} active={tab} onChange={(id) => setParams(id === 'feed' ? {} : { tab: id }, { replace: true })} />
        {tab === 'feed' && <FeedTab club={data} canManage={canManage} />}
        {tab === 'events' && <EventsTab clubId={data.id} canManage={canManage} />}
        {tab === 'leaderboard' && <LeaderboardTab clubId={data.id} />}
        {tab === 'members' && <MembersTab clubId={data.id} canManage={canManage} onChanged={() => club.reload()} />}
        {tab === 'participation' && <ParticipationTab clubId={data.id} />}
      </div>
    </div>
  )
}
