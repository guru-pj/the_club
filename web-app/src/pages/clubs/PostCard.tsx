import { useState } from 'react'
import { Link } from 'react-router-dom'
import { mediaUrl } from '../../api/client'
import { postsApi } from '../../api/endpoints'
import type { Club, Post, SharedContent } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ActionMenu } from '../../components/ActionMenu'
import { ActivityIcon, CalendarIcon, ChatIcon, HeartIcon, RepeatIcon, ShareIcon } from '../../components/icons'
import { useToast } from '../../components/Toast'
import { Avatar, Badge, Card } from '../../components/ui'
import { formatDateTime, formatDistance, formatDuration, formatPace, humanize, timeAgo } from '../../lib/format'
import { postPath, publicUrl, shareLink } from '../../lib/share'
import { ClubLogo } from './ClubLogo'
import { Comments } from './Comments'
import { ReshareModal } from './ReshareModal'

const TYPE_BADGES: Partial<Record<Post['type'], { label: string; tone: 'primary' | 'accent' | 'info' }>> = {
  ANNOUNCEMENT: { label: 'Announcement', tone: 'primary' },
  EVENT_RECAP: { label: 'Event recap', tone: 'accent' },
  RESHARE: { label: 'Reshared', tone: 'info' },
}

function SharedCard({ shared }: { shared: SharedContent }) {
  if (shared.kind === 'POST' && !shared.available) {
    return <div className="shared-card muted small">The original post was removed.</div>
  }
  if (shared.kind === 'EVENT') {
    return (
      <Link className="shared-card" to={`/events/${shared.eventId}`}>
        <div className="row small muted"><CalendarIcon size={16} />Event{shared.clubName && <> by {shared.clubName}</>}</div>
        <div className="bold">{shared.title}</div>
        {shared.startTime && <div className="small">{formatDateTime(shared.startTime)}</div>}
      </Link>
    )
  }
  return (
    <Link className="shared-card" to={postPath(shared.clubId ?? '', shared.postId ?? '')}>
      <div className="row small muted">
        <ClubLogo name={shared.clubName ?? ''} url={shared.clubLogoUrl} size={20} />
        {shared.clubName}{shared.createdAt && <> · {timeAgo(shared.createdAt)}</>}
      </div>
      <div className="bold">{shared.title}</div>
      {shared.excerpt && <p className="small pre-wrap">{shared.excerpt}</p>}
      {shared.photoUrl && <img className="shared-photo" src={mediaUrl(shared.photoUrl)} alt="" loading="lazy" />}
    </Link>
  )
}

/** A feed post with its reactions. `canManage`: the club owner (or an admin), who may remove any post or comment. */
export function PostCard({ post, club, canManage, onDelete, onChange, commentsOpen = false }: {
  post: Post
  club: Club
  canManage: boolean
  onDelete: () => void
  onChange: (post: Post) => void
  commentsOpen?: boolean
}) {
  const { user, hasRole } = useAuth()
  const toast = useToast()
  const [showComments, setShowComments] = useState(commentsOpen)
  const [liking, setLiking] = useState(false)
  const [reshareOpen, setReshareOpen] = useState(false)
  const a = post.activity
  const pace = a?.distanceKm && a.durationSeconds ? formatPace(a.distanceKm, a.durationSeconds) : null
  const badge = TYPE_BADGES[post.type]
  const canComment = canManage || !!club.myRole || club.following

  const toggleLike = async () => {
    const liked = !post.likedByMe
    setLiking(true)
    onChange({ ...post, likedByMe: liked, likeCount: post.likeCount + (liked ? 1 : -1) })
    try {
      const res = liked ? await postsApi.like(post.clubId, post.id) : await postsApi.unlike(post.clubId, post.id)
      onChange({ ...post, likedByMe: res.liked, likeCount: res.likeCount })
    } catch (e) {
      onChange(post)
      toast.error(e)
    } finally {
      setLiking(false)
    }
  }

  const share = async () => {
    const result = await shareLink(post.title, publicUrl(postPath(post.clubId, post.id), window.location.origin),
      `${club.name} on The Club`)
    if (result === 'copied') toast.success('Link copied')
  }

  // a reshare of a post reshares the original
  const reshareSource = post.shared?.kind === 'EVENT'
    ? { eventId: post.shared.eventId ?? undefined, title: post.title }
    : { postId: post.shared?.postId ?? post.id, title: post.title }
  const reshareFrom = post.shared?.kind === 'POST' ? post.shared.clubId ?? undefined : post.clubId

  return (
    <Card>
      <div className="post-head">
        <Avatar name={post.authorName} url={post.authorPhotoUrl} size={36} />
        <div className="post-head-main">
          <div className="row" style={{ gap: 6 }}>
            <span className="bold">{post.authorName ?? (post.authorId === club.ownerId ? club.name : 'Member')}</span>
            {post.authorId === club.ownerId && <span className="small muted">Club owner</span>}
          </div>
          <div className="row small muted" style={{ gap: 8 }}>
            <span>{timeAgo(post.createdAt)}</span>
            {badge && <Badge tone={badge.tone}>{badge.label}</Badge>}
            {post.type === 'EVENT_RECAP' && post.eventId && <Link to={`/events/${post.eventId}`}>View event</Link>}
          </div>
        </div>
        <ActionMenu label="Post actions" actions={post.authorId === user?.id || canManage
          ? [{ label: 'Delete post', danger: true, onSelect: onDelete }] : []} />
      </div>
      <div className="stack">
        {post.type !== 'RESHARE' && <h3>{post.title}</h3>}
        {post.content && <p className="pre-wrap">{post.content}</p>}
        {post.shared && <SharedCard shared={post.shared} />}
        {post.photoUrls.length > 0 && (
          <div className="photo-grid">
            {post.photoUrls.map((url) => (
              <a key={url} href={mediaUrl(url)} target="_blank" rel="noopener noreferrer"><img src={mediaUrl(url)} alt="" loading="lazy" /></a>
            ))}
          </div>
        )}
        {a && (
          <div className="activity-chip">
            <ActivityIcon size={18} />
            <span className="bold">{a.title ?? 'Activity'}</span>
            <Badge tone="accent">Strava activity</Badge>
            {a.type && <span className="small muted">{humanize(a.type)}</span>}
            <span className="small">{formatDistance(a.distanceKm)}</span>
            <span className="small">{formatDuration(a.durationSeconds)}</span>
            {pace && <span className="small">{pace}</span>}
          </div>
        )}
      </div>
      <div className="post-actions">
        <button className={`post-action${post.likedByMe ? ' active' : ''}`} onClick={() => void toggleLike()} disabled={liking}
                aria-pressed={post.likedByMe} aria-label={post.likedByMe ? `Unlike (${post.likeCount})` : post.likeCount ? `Like (${post.likeCount})` : 'Like'}>
          <HeartIcon size={18} filled={post.likedByMe} />{post.likeCount > 0 && post.likeCount}
          <span className="post-action-label">{post.likedByMe ? 'Liked' : 'Like'}</span>
        </button>
        <button className="post-action" onClick={() => setShowComments((s) => !s)} aria-expanded={showComments}
                aria-label={post.commentCount ? `Comments (${post.commentCount})` : 'Comment'}>
          <ChatIcon size={18} />{post.commentCount > 0 && post.commentCount}<span className="post-action-label">Comment</span>
        </button>
        <button className="post-action" onClick={() => void share()} aria-label="Share">
          <ShareIcon size={18} /><span className="post-action-label">Share</span>
        </button>
        {hasRole('CLUB_OWNER') && !(post.shared?.kind === 'POST' && !post.shared.available) && (
          <button className="post-action" onClick={() => setReshareOpen(true)} aria-label="Reshare">
            <RepeatIcon size={18} /><span className="post-action-label">Reshare</span>
          </button>
        )}
      </div>
      {showComments && (
        <Comments clubId={post.clubId} postId={post.id} canComment={canComment} canModerate={canManage}
                  onCountChange={(delta) => onChange({ ...post, commentCount: post.commentCount + delta })} />
      )}
      {reshareOpen && (
        <ReshareModal open source={reshareSource} excludeClubId={reshareFrom} onClose={() => setReshareOpen(false)} />
      )}
    </Card>
  )
}
