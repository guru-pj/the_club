import { useEffect, useState } from 'react'
import { clubsApi, postsApi } from '../../api/endpoints'
import type { Club } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Button, Field, Loading, Modal } from '../../components/ui'

/** Club owners share another club's post, or an event, into the feed of a club they own. */
export function ReshareModal({ open, onClose, source, excludeClubId }: {
  open: boolean
  onClose: () => void
  source: { postId?: string; eventId?: string; title: string }
  /** the club the post already belongs to */
  excludeClubId?: string
}) {
  const { user } = useAuth()
  const toast = useToast()
  const [clubs, setClubs] = useState<Club[]>()
  const [clubId, setClubId] = useState('')
  const [comment, setComment] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open || !user) return
    clubsApi.ownedBy(user.id)
      .then((owned) => {
        const options = owned.filter((c) => c.id !== excludeClubId)
        setClubs(options)
        setClubId(options[0]?.id ?? '')
      })
      .catch(toast.error)
  }, [open, user, excludeClubId, toast])

  const submit = async () => {
    setSaving(true)
    try {
      await postsApi.reshare(clubId, { postId: source.postId, eventId: source.eventId, comment: comment.trim() || undefined })
      toast.success(`Reshared to ${clubs?.find((c) => c.id === clubId)?.name ?? 'your club'}`)
      setComment('')
      onClose()
    } catch (e) {
      toast.error(e)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} title="Reshare to my club" onClose={onClose}
           footer={<>
             <Button variant="secondary" onClick={onClose}>Cancel</Button>
             <Button loading={saving} disabled={!clubId} onClick={() => void submit()}>Reshare</Button>
           </>}>
      {!clubs ? <Loading /> : clubs.length === 0 ? <p className="muted">You don't own another club to reshare this to.</p> : (
        <div className="form">
          <p>“{source.title}” will appear in your club's feed, and its subscribers and followers will hear about it.</p>
          <Field label="Club" htmlFor="reshare-club">
            <select id="reshare-club" className="input" value={clubId} onChange={(e) => setClubId(e.target.value)}>
              {clubs.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="Add a few words (optional)" htmlFor="reshare-comment">
            <textarea id="reshare-comment" className="input" maxLength={2000} value={comment} onChange={(e) => setComment(e.target.value)} />
          </Field>
        </div>
      )}
    </Modal>
  )
}
