import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { eventsApi, postsApi, stravaApi } from '../../api/endpoints'
import type { Club, Post, PostType } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ActivityIcon, TrashIcon } from '../../components/icons'
import { useToast } from '../../components/Toast'
import { Badge, Button, Card, EmptyState, ErrorMessage, Field, Loading } from '../../components/ui'
import { fieldError } from '../../lib/fieldError'
import { formatDate, formatDistance, formatDuration, formatPace, humanize, timeAgo } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { ConfirmModal } from './ConfirmModal'
import { UrlListInput } from './UrlListInput'

const PAGE_SIZE = 10

function Composer({ club, onPosted }: { club: Club; onPosted: (post: Post) => void }) {
  const { user } = useAuth()
  const toast = useToast()
  const isOwner = club.myRole === 'OWNER'
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [type, setType] = useState<PostType>('ANNOUNCEMENT')
  const [eventId, setEventId] = useState('')
  const [photoUrls, setPhotoUrls] = useState<string[]>([])
  const [activityId, setActivityId] = useState('')
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState(false)

  const events = useAsync(
    () => (type === 'EVENT_RECAP' ? eventsApi.byClub(club.id, { size: 50 }).then((p) => p.items) : Promise.resolve([])),
    [club.id, type],
  )
  const activities = useAsync(
    () => (isOwner && user ? stravaApi.activities(user.id, { size: 20 }).then((p) => p.items) : Promise.resolve([])),
    [isOwner, user?.id],
  )

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (type === 'EVENT_RECAP' && !eventId) return setError(new ApiError(400, 'Pick the event this recap is about'))
    setSaving(true)
    setError(undefined)
    try {
      const post = await postsApi.create(club.id, {
        type, title: title.trim(), content: content.trim(), photoUrls,
        eventId: type === 'EVENT_RECAP' ? eventId : undefined,
        activityId: activityId || undefined,
      })
      onPosted(post)
      setTitle(''); setContent(''); setType('ANNOUNCEMENT'); setEventId(''); setPhotoUrls([]); setActivityId('')
      toast.success('Posted to the club feed')
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  const general = error instanceof ApiError && Object.keys(error.fieldErrors).length ? undefined : error
  return (
    <Card title="Share with your club">
      <form className="form" onSubmit={submit}>
        <ErrorMessage error={general} />
        <div className="form-row">
          <Field label="Post type" htmlFor="post-type">
            <select id="post-type" className="input" value={type} onChange={(e) => setType(e.target.value as PostType)}>
              <option value="ANNOUNCEMENT">Announcement</option>
              <option value="EVENT_RECAP">Event recap</option>
            </select>
          </Field>
          {type === 'EVENT_RECAP' && (
            <Field label="Event" htmlFor="post-event" error={fieldError(error, 'eventId')}>
              <select id="post-event" className="input" value={eventId} onChange={(e) => setEventId(e.target.value)} required>
                <option value="">{events.loading ? 'Loading events…' : 'Select an event'}</option>
                {events.data?.map((ev) => (
                  <option key={ev.id} value={ev.id}>{ev.title} · {formatDate(ev.startTime)}</option>
                ))}
              </select>
            </Field>
          )}
        </div>
        <Field label="Title" htmlFor="post-title" error={fieldError(error, 'title')}>
          <input id="post-title" className="input" maxLength={150} required value={title} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <Field label="Message" htmlFor="post-content" error={fieldError(error, 'content')}>
          <textarea id="post-content" className="input" maxLength={5000} required value={content}
                    onChange={(e) => setContent(e.target.value)} />
        </Field>
        <UrlListInput label="Photos" value={photoUrls} onChange={setPhotoUrls} max={10}
                      hint="Paste links to photos (up to 10)" error={fieldError(error, 'photoUrls')} />
        {isOwner && (
          <Field label="Link a Strava activity" htmlFor="post-activity" error={fieldError(error, 'activityId')}
                 hint={!activities.loading && !activities.data?.length
                   ? <>Connect Strava on the <Link to="/activities">Activities page</Link> to attach your runs.</>
                   : 'Optional — only your own Strava activities can be linked'}>
            <select id="post-activity" className="input" value={activityId} onChange={(e) => setActivityId(e.target.value)}
                    disabled={!activities.data?.length}>
              <option value="">None</option>
              {activities.data?.map((a) => (
                <option key={a.id} value={a.id}>{a.title} · {formatDistance(a.distance)} · {formatDate(a.startTime)}</option>
              ))}
            </select>
          </Field>
        )}
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button type="submit" loading={saving} disabled={!title.trim() || !content.trim()}>Post</Button>
        </div>
      </form>
    </Card>
  )
}

function PostCard({ post, canManage, onDelete }: { post: Post; canManage: boolean; onDelete: () => void }) {
  const a = post.activity
  const pace = a?.distanceKm && a.durationSeconds ? formatPace(a.distanceKm, a.durationSeconds) : null
  return (
    <Card>
      <div className="row-between" style={{ marginBottom: 8 }}>
        <div className="row">
          <Badge tone={post.type === 'EVENT_RECAP' ? 'accent' : 'primary'}>
            {post.type === 'EVENT_RECAP' ? 'Event recap' : 'Announcement'}
          </Badge>
          <span className="muted small">{timeAgo(post.createdAt)}</span>
        </div>
        <div className="row">
          {post.type === 'EVENT_RECAP' && post.eventId && <Link className="small" to={`/events/${post.eventId}`}>View event</Link>}
          {canManage && (
            <button className="icon-button" aria-label="Delete post" onClick={onDelete}><TrashIcon size={18} /></button>
          )}
        </div>
      </div>
      <div className="stack">
        <h3>{post.title}</h3>
        <p className="pre-wrap">{post.content}</p>
        {post.photoUrls.length > 0 && (
          <div className="photo-grid">
            {post.photoUrls.map((url) => (
              <a key={url} href={url} target="_blank" rel="noopener noreferrer"><img src={url} alt="" loading="lazy" /></a>
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
    </Card>
  )
}

export function FeedTab({ club, canManage }: { club: Club; canManage: boolean }) {
  const toast = useToast()
  const [posts, setPosts] = useState<Post[]>([])
  const [page, setPage] = useState(0)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>()
  const [toDelete, setToDelete] = useState<Post | null>(null)

  useEffect(() => {
    let cancelled = false
    postsApi.feed(club.id, { page: 0, size: PAGE_SIZE })
      .then((res) => {
        if (cancelled) return
        setPosts(res.items)
        setTotal(res.totalItems)
        setPage(0)
      })
      .catch((e) => { if (!cancelled) setError(e) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [club.id])

  const load = async (nextPage: number) => {
    setLoading(true)
    setError(undefined)
    try {
      const res = await postsApi.feed(club.id, { page: nextPage, size: PAGE_SIZE })
      setPosts((prev) => (nextPage === 0 ? res.items : [...prev, ...res.items]))
      setTotal(res.totalItems)
      setPage(nextPage)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="stack-lg">
      {canManage && <Composer club={club} onPosted={(post) => { setPosts((p) => [post, ...p]); setTotal((t) => t + 1) }} />}
      <ErrorMessage error={error} onRetry={() => load(page)} />
      {loading && posts.length === 0 ? <Loading /> : posts.length === 0 && !error ? (
        <Card><EmptyState title="No posts yet">{canManage ? 'Share the first update with your members.' : 'The club owner hasn\'t posted anything yet.'}</EmptyState></Card>
      ) : (
        <div className="stack">
          {posts.map((post) => (
            <PostCard key={post.id} post={post} canManage={canManage} onDelete={() => setToDelete(post)} />
          ))}
          {posts.length < total && (
            <Button variant="secondary" onClick={() => load(page + 1)} loading={loading}>Load more</Button>
          )}
        </div>
      )}
      <ConfirmModal open={!!toDelete} title="Delete post?" confirmLabel="Delete" onClose={() => setToDelete(null)}
                    onConfirm={async () => {
                      if (!toDelete) return
                      await postsApi.remove(club.id, toDelete.id)
                      setPosts((p) => p.filter((x) => x.id !== toDelete.id))
                      setTotal((t) => t - 1)
                      toast.success('Post deleted')
                    }}>
        “{toDelete?.title}” will be removed from the feed for everyone.
      </ConfirmModal>
    </div>
  )
}
