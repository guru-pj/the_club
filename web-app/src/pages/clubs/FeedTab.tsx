import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, mediaUrl } from '../../api/client'
import { eventsApi, postsApi, stravaApi } from '../../api/endpoints'
import type { Club, Post, PostType } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { CloseIcon, ImageIcon } from '../../components/icons'
import { useToast } from '../../components/Toast'
import { Button, Card, EmptyState, ErrorMessage, Field, Loading } from '../../components/ui'
import { fieldError } from '../../lib/fieldError'
import { formatDate, formatDistance } from '../../lib/format'
import { ACCEPTED_IMAGE_TYPES, imageProblem } from '../../lib/images'
import { useAsync } from '../../lib/useAsync'
import { ConfirmModal } from './ConfirmModal'
import { PostCard } from './PostCard'
import { UrlListInput } from './UrlListInput'

const PAGE_SIZE = 10

function PhotoUploads({ clubId, value, onChange, max = 10 }:
  { clubId: string; value: string[]; onChange: (urls: string[]) => void; max?: number }) {
  const toast = useToast()
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const add = async (files: FileList | null) => {
    const picked = Array.from(files ?? []).slice(0, max - value.length)
    if (input.current) input.current.value = ''
    if (!picked.length) return
    setBusy(true)
    const urls = [...value]
    try {
      for (const file of picked) {
        const problem = imageProblem(file)
        if (problem) throw new Error(`${file.name}: ${problem}`)
        urls.push((await postsApi.uploadPhoto(clubId, file)).url)
        onChange([...urls])
      }
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="field">
      <span className="field-label">Photos</span>
      {value.length > 0 && (
        <div className="upload-thumbs">
          {value.map((url) => (
            <div key={url} className="upload-thumb">
              <img src={mediaUrl(url)} alt="" />
              <button type="button" className="icon-button icon-button-sm" aria-label="Remove photo"
                      onClick={() => onChange(value.filter((u) => u !== url))}><CloseIcon size={14} /></button>
            </div>
          ))}
        </div>
      )}
      <input ref={input} type="file" multiple accept={ACCEPTED_IMAGE_TYPES.join(',')} className="visually-hidden"
             aria-label="Add photos" onChange={(e) => void add(e.target.files)} />
      <div className="row">
        <Button size="sm" variant="secondary" loading={busy} disabled={value.length >= max}
                onClick={() => input.current?.click()}><ImageIcon size={16} />Add photos</Button>
        <span className="small muted">Up to {max}; JPEG, PNG or WebP, 5 MB each</span>
      </div>
    </div>
  )
}

/** The owner writes announcements and event recaps; a subscriber writes a post. */
function Composer({ club, canManage, onPosted }: { club: Club; canManage: boolean; onPosted: (post: Post) => void }) {
  const { user } = useAuth()
  const toast = useToast()
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [type, setType] = useState<PostType>('ANNOUNCEMENT')
  const [eventId, setEventId] = useState('')
  const [photoUrls, setPhotoUrls] = useState<string[]>([])
  const [linkUrls, setLinkUrls] = useState<string[]>([])
  const [activityId, setActivityId] = useState('')
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState(false)
  // the owner links their own runs; so does a subscriber (an admin posting for a club can't)
  const ownActivities = club.myRole === 'OWNER' || club.myRole === 'MEMBER'

  const events = useAsync(
    () => (canManage && type === 'EVENT_RECAP' ? eventsApi.byClub(club.id, { size: 50 }).then((p) => p.items) : Promise.resolve([])),
    [club.id, type, canManage],
  )
  const activities = useAsync(
    () => (ownActivities && user ? stravaApi.activities(user.id, { size: 20 }).then((p) => p.items) : Promise.resolve([])),
    [ownActivities, user?.id],
  )

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (type === 'EVENT_RECAP' && !eventId) return setError(new ApiError(400, 'Pick the event this recap is about'))
    setSaving(true)
    setError(undefined)
    try {
      const post = await postsApi.create(club.id, {
        type: canManage ? type : undefined, title: title.trim(), content: content.trim(), photoUrls: [...photoUrls, ...linkUrls],
        eventId: canManage && type === 'EVENT_RECAP' ? eventId : undefined,
        activityId: activityId || undefined,
      })
      onPosted(post)
      setTitle(''); setContent(''); setType('ANNOUNCEMENT'); setEventId(''); setPhotoUrls([]); setLinkUrls([]); setActivityId('')
      toast.success('Posted to the club feed')
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  const general = error instanceof ApiError && Object.keys(error.fieldErrors).length ? undefined : error
  return (
    <Card title={canManage ? 'Share with your club' : 'Write a post'}>
      <form className="form" onSubmit={submit}>
        <ErrorMessage error={general} />
        {canManage && (
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
        )}
        <Field label="Title" htmlFor="post-title" error={fieldError(error, 'title')}>
          <input id="post-title" className="input" maxLength={150} required value={title} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <Field label="Message" htmlFor="post-content" error={fieldError(error, 'content')}>
          <textarea id="post-content" className="input" maxLength={5000} required value={content}
                    onChange={(e) => setContent(e.target.value)} />
        </Field>
        <PhotoUploads clubId={club.id} value={photoUrls} onChange={setPhotoUrls} max={10 - linkUrls.length} />
        {canManage && (
          <UrlListInput label="Photo links" value={linkUrls} onChange={setLinkUrls} max={10 - photoUrls.length}
                        hint="Or paste links to photos hosted elsewhere" error={fieldError(error, 'photoUrls')} />
        )}
        {ownActivities && (
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

export function FeedTab({ club, canManage }: { club: Club; canManage: boolean }) {
  const toast = useToast()
  const canPost = canManage || club.myRole === 'MEMBER'
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
      {canPost && <Composer club={club} canManage={canManage} onPosted={(post) => { setPosts((p) => [post, ...p]); setTotal((t) => t + 1) }} />}
      {!canPost && club.subscriptionsEnabled && (
        <p className="small muted">Subscribers can post in the feed. Anyone signed in can like and share posts; followers and subscribers can comment.</p>
      )}
      <ErrorMessage error={error} onRetry={() => load(page)} />
      {loading && posts.length === 0 ? <Loading /> : posts.length === 0 && !error ? (
        <Card><EmptyState title="No posts yet">{canPost ? 'Share the first update with the club.' : 'Nobody has posted anything yet.'}</EmptyState></Card>
      ) : (
        <div className="stack">
          {posts.map((post) => (
            <PostCard key={post.id} post={post} club={club} canManage={canManage} onDelete={() => setToDelete(post)}
                      onChange={(updated) => setPosts((list) => list.map((p) => (p.id === updated.id ? updated : p)))} />
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
        “{toDelete?.title}” will be removed from the feed for everyone, with its likes and comments.
      </ConfirmModal>
    </div>
  )
}
