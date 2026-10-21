import { useId, useRef, useState } from 'react'
import { ACCEPTED_IMAGE_TYPES, imageProblem } from '../lib/images'
import { useToast } from './Toast'
import { Button } from './ui'


/**
 * Pick an image and upload it straight away. The current image (if any) shows until the upload succeeds and the
 * server returns the stored image's address. There's deliberately no local preview: it would put a file the user
 * picked into the page as an image URL (and need blob: in the Content-Security-Policy).
 */
export function ImagePicker({ label, currentUrl, onUpload, onRemove, shape = 'square', size = 96 }: {
  label: string
  currentUrl?: string
  onUpload: (file: File) => Promise<void>
  onRemove?: () => Promise<void>
  shape?: 'square' | 'circle' | 'wide'
  size?: number
}) {
  const toast = useToast()
  const inputId = useId()
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)

  const choose = async (file: File | undefined) => {
    if (!file) return
    const problem = imageProblem(file)
    if (problem) {
      toast.error(problem)
      return
    }
    setBusy(true)
    try {
      await onUpload(file)
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  const remove = async () => {
    if (!onRemove) return
    setBusy(true)
    try {
      await onRemove()
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(false)
    }
  }

  const width = shape === 'wide' ? size * 2 : size
  return (
    <div className="image-picker">
      <div className={`image-picker-frame ${shape}`} style={{ width, height: size }}>
        {busy ? <span className="small muted">Uploading…</span>
          : currentUrl ? <img src={currentUrl} alt="" />
          : <span className="small muted">No image</span>}
      </div>
      <div className="stack" style={{ gap: 6 }}>
        <label htmlFor={inputId} className="bold small">{label}</label>
        <input id={inputId} ref={input} type="file" accept={ACCEPTED_IMAGE_TYPES.join(',')} className="visually-hidden"
               onChange={(e) => void choose(e.target.files?.[0])} disabled={busy} />
        <div className="row">
          <Button size="sm" variant="secondary" loading={busy} onClick={() => input.current?.click()}>
            {currentUrl ? 'Change' : 'Upload'}
          </Button>
          {currentUrl && onRemove && <Button size="sm" variant="ghost" disabled={busy} onClick={() => void remove()}>Remove</Button>}
        </div>
        <span className="small muted">JPEG, PNG or WebP, up to 5 MB</span>
      </div>
    </div>
  )
}
