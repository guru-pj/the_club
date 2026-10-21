import { useState, type FormEvent } from 'react'
import { reviewsApi } from '../../api/endpoints'
import type { Club, Review } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { StarIcon } from '../../components/icons'
import { StarInput, Stars } from '../../components/Stars'
import { useToast } from '../../components/Toast'
import { Button, Card, EmptyState, ErrorMessage, Field, Loading, Pagination } from '../../components/ui'
import { timeAgo } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'

const PAGE_SIZE = 10

function ReviewForm({ clubId, mine, onSaved }: { clubId: string; mine: Review | null; onSaved: () => void }) {
  const toast = useToast()
  const [rating, setRating] = useState(mine?.rating ?? 0)
  const [comment, setComment] = useState(mine?.comment ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>()

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!rating) return
    setSaving(true)
    setError(undefined)
    try {
      await reviewsApi.save(clubId, rating, comment.trim())
      toast.success(mine ? 'Review updated' : 'Thanks for your review')
      onSaved()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  const remove = async () => {
    setSaving(true)
    try {
      await reviewsApi.removeMine(clubId)
      toast.success('Review removed')
      onSaved()
    } catch (err) {
      toast.error(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card title={mine ? 'Your review' : 'Write a review'}>
      <form className="form" onSubmit={submit}>
        <StarInput name={`rating-${clubId}`} value={rating} onChange={setRating} />
        <Field label="Comment" htmlFor="review-comment" hint="Optional: what's it like to train with this club?">
          <textarea id="review-comment" className="input" maxLength={2000} value={comment} onChange={(e) => setComment(e.target.value)} />
        </Field>
        <ErrorMessage error={error} />
        <div className="row">
          <Button type="submit" loading={saving} disabled={!rating}>{mine ? 'Update review' : 'Post review'}</Button>
          {mine && <Button variant="ghost" disabled={saving} onClick={() => void remove()}>Delete</Button>}
        </div>
      </form>
    </Card>
  )
}

function OwnerReply({ clubId, review, onSaved }: { clubId: string; review: Review; onSaved: () => void }) {
  const toast = useToast()
  const [open, setOpen] = useState(false)
  const [reply, setReply] = useState(review.ownerReply ?? '')
  const [saving, setSaving] = useState(false)
  if (!open) {
    return <Button size="sm" variant="ghost" onClick={() => setOpen(true)}>{review.ownerReply ? 'Edit reply' : 'Reply'}</Button>
  }
  const save = async () => {
    setSaving(true)
    try {
      if (reply.trim()) await reviewsApi.reply(clubId, review.id, reply.trim())
      else await reviewsApi.removeReply(clubId, review.id)
      setOpen(false)
      onSaved()
    } catch (e) {
      toast.error(e)
    } finally {
      setSaving(false)
    }
  }
  return (
    <div className="stack" style={{ gap: 6 }}>
      <label className="visually-hidden" htmlFor={`reply-${review.id}`}>Reply</label>
      <textarea id={`reply-${review.id}`} className="input" maxLength={1000} value={reply} onChange={(e) => setReply(e.target.value)} />
      <div className="row">
        <Button size="sm" loading={saving} onClick={() => void save()}>Save reply</Button>
        <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
      </div>
    </div>
  )
}

/** Reviews and ratings (user journey step 4). Subscribers and event attendees review; the owner replies. */
export function ReviewsTab({ club, canManage }: { club: Club; canManage: boolean }) {
  const { hasRole } = useAuth()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const reviews = useAsync(() => reviewsApi.list(club.id, { page, size: PAGE_SIZE }), [club.id, page])

  if (reviews.error) return <ErrorMessage error={reviews.error} onRetry={reviews.reload} />
  if (!reviews.data) return <Loading />
  const { summary, canReview, mine } = reviews.data
  const items = reviews.data.reviews.items

  return (
    <div className="stack-lg">
      <Card>
        <div className="review-summary">
          <div className="stack" style={{ gap: 4, alignItems: 'center' }}>
            <span className="review-average">{summary.average?.toFixed(1) ?? '–'}</span>
            <Stars value={summary.average} />
            <span className="small muted">{summary.count} {summary.count === 1 ? 'review' : 'reviews'}</span>
          </div>
          <div className="review-bars" aria-label="Ratings breakdown">
            {[5, 4, 3, 2, 1].map((stars) => {
              const n = summary.byRating[String(stars)] ?? 0
              const pct = summary.count ? Math.round((n / summary.count) * 100) : 0
              return (
                <div key={stars} className="review-bar-row small">
                  <span>{stars}</span>
                  <div className="review-bar"><div style={{ width: `${pct}%` }} /></div>
                  <span className="muted">{n}</span>
                </div>
              )
            })}
          </div>
        </div>
      </Card>
      {canReview
        ? <ReviewForm key={mine?.id ?? 'new'} clubId={club.id} mine={mine} onSaved={reviews.reload} />
        : club.myRole !== 'OWNER' && (
          <p className="small muted">Subscribers and people who've attended one of {club.name}'s events can review it.</p>
        )}
      {!items.length ? (
        <Card><EmptyState icon={<StarIcon size={28} />} title="No reviews yet" /></Card>
      ) : (
        <Card flush>
          <div className="list">
            {items.map((review) => (
              <div key={review.id} className="list-item" style={{ alignItems: 'flex-start' }}>
                <div className="list-item-main stack" style={{ gap: 6 }}>
                  <div className="row" style={{ gap: 8 }}>
                    <Stars value={review.rating} size={14} />
                    <span className="bold small">{review.userName ?? 'Member'}</span>
                    <span className="small muted">{timeAgo(review.updatedAt)}</span>
                  </div>
                  {review.comment && <p className="pre-wrap">{review.comment}</p>}
                  {review.ownerReply && (
                    <div className="review-reply small">
                      <span className="bold">Reply from {club.name}</span>
                      <p className="pre-wrap">{review.ownerReply}</p>
                    </div>
                  )}
                  <div className="row">
                    {canManage && <OwnerReply clubId={club.id} review={review} onSaved={reviews.reload} />}
                    {hasRole('ADMIN') && (
                      <Button size="sm" variant="ghost" onClick={async () => {
                        try {
                          await reviewsApi.remove(club.id, review.id)
                          toast.success('Review removed')
                          reviews.reload()
                        } catch (e) {
                          toast.error(e)
                        }
                      }}>Remove (admin)</Button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
          <Pagination page={reviews.data.reviews.page} size={PAGE_SIZE} totalItems={reviews.data.reviews.totalItems} onChange={setPage} />
        </Card>
      )}
    </div>
  )
}
