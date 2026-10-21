import { useState } from 'react'
import { mediaUrl } from '../../api/client'
import { clubsApi } from '../../api/endpoints'
import type { ClubPhoto } from '../../api/types'
import { ImagePicker } from '../../components/ImagePicker'
import { useToast } from '../../components/Toast'
import { Button, Card, EmptyState, ErrorMessage, Field, Loading, Modal } from '../../components/ui'
import { useAsync } from '../../lib/useAsync'

/** The club's photo gallery; owners add and remove photos. */
export function PhotosTab({ clubId, canManage }: { clubId: string; canManage: boolean }) {
  const toast = useToast()
  const photos = useAsync(() => clubsApi.photos(clubId), [clubId])
  const [caption, setCaption] = useState('')
  const [open, setOpen] = useState<ClubPhoto>()

  if (photos.error) return <ErrorMessage error={photos.error} onRetry={photos.reload} />
  if (!photos.data) return <Loading />

  return (
    <div className="stack-lg">
      {canManage && (
        <Card title="Add a photo">
          <div className="stack">
            <Field label="Caption" htmlFor="photo-caption" hint="Optional">
              <input id="photo-caption" className="input" maxLength={200} value={caption} onChange={(e) => setCaption(e.target.value)} />
            </Field>
            <ImagePicker label="Photo" shape="wide" onUpload={async (file) => {
              await clubsApi.addPhoto(clubId, file, caption.trim() || undefined)
              setCaption('')
              toast.success('Photo added')
              photos.reload()
            }} />
          </div>
        </Card>
      )}
      {!photos.data.length ? (
        <Card><EmptyState title="No photos yet">{canManage ? 'Add photos of runs, rides and race days.' : undefined}</EmptyState></Card>
      ) : (
        <div className="photo-grid">
          {photos.data.map((photo) => (
            <button key={photo.id} type="button" className="photo-tile" onClick={() => setOpen(photo)}
                    aria-label={photo.caption ? `Open photo: ${photo.caption}` : 'Open photo'}>
              <img src={mediaUrl(photo.url)} alt={photo.caption ?? ''} loading="lazy" />
            </button>
          ))}
        </div>
      )}
      <Modal open={!!open} title={open?.caption ?? 'Photo'} onClose={() => setOpen(undefined)}
             footer={canManage && open ? (
               <Button variant="danger" onClick={async () => {
                 try {
                   await clubsApi.removePhoto(clubId, open.id)
                   setOpen(undefined)
                   toast.success('Photo removed')
                   photos.reload()
                 } catch (e) {
                   toast.error(e)
                 }
               }}>Delete photo</Button>
             ) : undefined}>
        {open && <img className="photo-full" src={mediaUrl(open.url)} alt={open.caption ?? ''} />}
      </Modal>
    </div>
  )
}
