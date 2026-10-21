import { useEffect, useState, type FormEvent } from 'react'
import { postsApi } from '../../api/endpoints'
import type { PostComment } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { TrashIcon } from '../../components/icons'
import { useToast } from '../../components/Toast'
import { Avatar, Button, ErrorMessage, Spinner } from '../../components/ui'
import { timeAgo } from '../../lib/format'

const PAGE_SIZE = 50

/**
 * A post's comments, oldest first. `canComment`: the club's subscribers and followers (and its owner);
 * `canModerate`: the club owner, who can remove anyone's comment.
 */
export function Comments({ clubId, postId, canComment, canModerate, onCountChange }: {
  clubId: string
  postId: string
  canComment: boolean
  canModerate: boolean
  onCountChange: (delta: number) => void
}) {
  const { user } = useAuth()
  const toast = useToast()
  const [comments, setComments] = useState<PostComment[]>()
  const [total, setTotal] = useState(0)
  const [error, setError] = useState<unknown>()
  const [draft, setDraft] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false
    postsApi.comments(clubId, postId, { size: PAGE_SIZE })
      .then((page) => { if (!cancelled) { setComments(page.items); setTotal(page.totalItems) } })
      .catch((e) => { if (!cancelled) setError(e) })
    return () => { cancelled = true }
  }, [clubId, postId])

  const loadMore = async () => {
    try {
      const page = await postsApi.comments(clubId, postId, { page: Math.floor((comments?.length ?? 0) / PAGE_SIZE), size: PAGE_SIZE })
      setComments((list) => [...(list ?? []), ...page.items.filter((c) => !list?.some((x) => x.id === c.id))])
    } catch (e) {
      toast.error(e)
    }
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!draft.trim()) return
    setSaving(true)
    try {
      const comment = await postsApi.addComment(clubId, postId, draft.trim())
      setComments((list) => [...(list ?? []), comment])
      setTotal((t) => t + 1)
      onCountChange(1)
      setDraft('')
    } catch (err) {
      toast.error(err)
    } finally {
      setSaving(false)
    }
  }

  const remove = async (c: PostComment) => {
    try {
      await postsApi.removeComment(clubId, postId, c.id)
      setComments((list) => list?.filter((x) => x.id !== c.id))
      setTotal((t) => t - 1)
      onCountChange(-1)
    } catch (err) {
      toast.error(err)
    }
  }

  return (
    <div className="comments">
      <ErrorMessage error={error} />
      {!comments && !error && <Spinner small />}
      {comments?.map((c) => (
        <div key={c.id} className="comment">
          <Avatar name={c.authorName} url={c.authorPhotoUrl} size={28} />
          <div className="comment-body">
            <div className="small"><span className="bold">{c.authorName ?? 'Member'}</span> <span className="muted">{timeAgo(c.createdAt)}</span></div>
            <div className="pre-wrap">{c.content}</div>
          </div>
          {(c.authorId === user?.id || canModerate) && (
            <button className="icon-button icon-button-sm" aria-label="Delete comment" onClick={() => void remove(c)}>
              <TrashIcon size={16} />
            </button>
          )}
        </div>
      ))}
      {comments && comments.length < total && (
        <Button size="sm" variant="ghost" onClick={() => void loadMore()}>More comments</Button>
      )}
      {canComment ? (
        <form className="comment-form" onSubmit={submit}>
          <input className="input" maxLength={1000} placeholder="Write a comment…" aria-label="Write a comment" value={draft}
                 onChange={(e) => setDraft(e.target.value)} />
          <Button type="submit" size="sm" loading={saving} disabled={!draft.trim()}>Comment</Button>
        </form>
      ) : (
        <p className="small muted">Follow or subscribe to the club to comment.</p>
      )}
    </div>
  )
}
