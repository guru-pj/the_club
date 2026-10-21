import { useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { ApiError, mediaUrl } from '../api/client'
import { clubsApi } from '../api/endpoints'
import type { Club } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { EditIcon, PinIcon, PlusIcon, UsersIcon } from '../components/icons'
import { ImagePicker } from '../components/ImagePicker'
import { Stars } from '../components/Stars'
import { useToast } from '../components/Toast'
import { Badge, Button, Card, EmptyState, ErrorMessage, LinkButton, Loading, Modal, Tabs } from '../components/ui'
import { humanize } from '../lib/format'
import { mapLink } from '../lib/location'
import { useAsync } from '../lib/useAsync'
import { ClubForm } from './clubs/ClubForm'
import { ClubLogo } from './clubs/ClubLogo'
import { MembershipBadge } from './clubs/MembershipBadge'
import { ConfirmModal } from './clubs/ConfirmModal'
import { EventsTab } from './clubs/EventsTab'
import { FeedTab } from './clubs/FeedTab'
import { LeaderboardTab } from './clubs/LeaderboardTab'
import { MembersTab } from './clubs/MembersTab'
import { ParticipationTab } from './clubs/ParticipationTab'
import { PhotosTab } from './clubs/PhotosTab'
import { ReviewsTab } from './clubs/ReviewsTab'
import './clubs.css'

type TabId = 'feed' | 'events' | 'reviews' | 'photos' | 'leaderboard' | 'members' | 'participation'
const TABS: { id: TabId; label: string }[] = [
  { id: 'feed', label: 'Feed' },
  { id: 'events', label: 'Events' },
  { id: 'reviews', label: 'Reviews' },
  { id: 'photos', label: 'Photos' },
  { id: 'leaderboard', label: 'Leaderboard' },
  { id: 'members', label: 'Members' },
  { id: 'participation', label: 'Participation' },
]

function ClubHeader({ club, canManage, onChanged }: { club: Club; canManage: boolean; onChanged: (club?: Club) => void }) {
  const toast = useToast()
  const [busy, setBusy] = useState<'subscribe' | 'follow'>()
  const [leaveOpen, setLeaveOpen] = useState(false)
  const [subscribeOpen, setSubscribeOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)

  const subscribe = async () => {
    setBusy('subscribe')
    try {
      await clubsApi.join(club.id)
      toast.success(`You're subscribed to ${club.name}!`)
      setSubscribeOpen(false)
      onChanged()
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(undefined)
    }
  }

  const toggleFollow = async () => {
    setBusy('follow')
    try {
      if (club.following) {
        await clubsApi.unfollow(club.id)
        toast.success(`You unfollowed ${club.name}`)
      } else {
        await clubsApi.follow(club.id)
        toast.success(`Following ${club.name}: you'll get its updates and event announcements`)
      }
      onChanged()
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <Card>
      <div className="club-hero">
        <ClubLogo name={club.name} url={club.logoUrl} size={72} />
        <div className="club-hero-main stack" style={{ gap: 6 }}>
          <div className="row">
            <h1>{club.name}</h1>
            <MembershipBadge role={club.myRole} />
          </div>
          <div className="row small muted" style={{ gap: 14 }}>
            {club.clubType && <Badge tone="primary">{humanize(club.clubType)}</Badge>}
            {club.city && <span className="row" style={{ gap: 4 }}><PinIcon size={14} />{club.city}</span>}
            <span className="row" style={{ gap: 4 }}><UsersIcon size={14} />{club.memberCount} {club.memberCount === 1 ? 'subscriber' : 'subscribers'}</span>
            <span>{club.followerCount} {club.followerCount === 1 ? 'follower' : 'followers'}</span>
            {club.ratingCount > 0 && (
              <span className="row" style={{ gap: 4 }}><Stars value={club.ratingAverage} size={14} />{club.ratingAverage?.toFixed(1)} ({club.ratingCount})</span>
            )}
          </div>
          {(club.address || club.latitude != null) && (
            <p className="small row" style={{ gap: 6 }}>
              <PinIcon size={14} />
              {club.address ?? 'Meeting point'}
              {club.latitude != null && club.longitude != null && (
                <a href={mapLink(club.latitude, club.longitude)} target="_blank" rel="noreferrer">View on map</a>
              )}
            </p>
          )}
          {club.description && <p className="pre-wrap">{club.description}</p>}
        </div>
        <div className="row">
          {club.myRole !== 'OWNER' && (
            <Button variant={club.following ? 'secondary' : 'primary'} loading={busy === 'follow'} disabled={!!busy}
                    onClick={() => void toggleFollow()} aria-pressed={club.following}>
              {club.following ? 'Following' : 'Follow'}
            </Button>
          )}
          {!club.myRole && club.subscriptionsEnabled && (
            <Button variant="accent" disabled={!!busy} onClick={() => setSubscribeOpen(true)}>Subscribe</Button>
          )}
          {club.myRole === 'MEMBER' && <Button variant="secondary" onClick={() => setLeaveOpen(true)}>Unsubscribe</Button>}
          {canManage && (
            <>
              <Button variant="secondary" onClick={() => setEditOpen(true)}><EditIcon size={16} />Edit club</Button>
              <LinkButton to={`/clubs/${club.id}/events/new`}><PlusIcon size={16} />Create event</LinkButton>
            </>
          )}
        </div>
      </div>
      <Modal open={subscribeOpen} title={`Subscribe to ${club.name}`} onClose={() => setSubscribeOpen(false)}
             footer={<>
               <Button variant="secondary" onClick={() => setSubscribeOpen(false)}>Not now</Button>
               <Button loading={busy === 'subscribe'} onClick={() => void subscribe()}>Subscribe (free)</Button>
             </>}>
        <div className="stack">
          <p>Subscribers are members of the club: they appear on its leaderboard, can register for subscriber-only events and can review the club.</p>
          {club.subscriptionBenefits && (
            <div className="callout">
              <p className="bold small">What {club.name} offers subscribers</p>
              <p className="pre-wrap">{club.subscriptionBenefits}</p>
            </div>
          )}
          <p className="small muted">Subscribing is free. To only get updates and event announcements, follow the club instead.</p>
        </div>
      </Modal>
      <ConfirmModal open={leaveOpen} title={`Unsubscribe from ${club.name}?`} confirmLabel="Unsubscribe" onClose={() => setLeaveOpen(false)}
                    onConfirm={async () => {
                      await clubsApi.leave(club.id)
                      toast.success(`You unsubscribed from ${club.name}`)
                      onChanged()
                    }}>
        You won't be able to register for subscriber-only events. {club.following ? 'You still follow the club.' : 'Follow the club to keep getting its updates.'}
      </ConfirmModal>
      <Modal open={editOpen} title="Edit club" onClose={() => setEditOpen(false)}>
        <div style={{ marginBottom: 16 }}>
          <ImagePicker label="Club logo" currentUrl={mediaUrl(club.logoUrl)} onUpload={async (file) => {
            onChanged(await clubsApi.uploadLogo(club.id, file))
            toast.success('Logo updated')
          }} />
        </div>
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
        {tab === 'reviews' && <ReviewsTab club={data} canManage={canManage} />}
        {tab === 'photos' && <PhotosTab clubId={data.id} canManage={canManage} />}
        {tab === 'leaderboard' && <LeaderboardTab clubId={data.id} />}
        {tab === 'members' && <MembersTab clubId={data.id} canManage={canManage} onChanged={() => club.reload()} />}
        {tab === 'participation' && <ParticipationTab clubId={data.id} />}
      </div>
    </div>
  )
}
