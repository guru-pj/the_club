import { useState } from 'react'
import { Button, Field } from '../../components/ui'
import { CloseIcon } from '../../components/icons'
import { isHttpUrl } from './urls'

/** Add/remove list of http(s) URLs (photos, proof documents). */
export function UrlListInput({ label, value, onChange, max = 10, hint, placeholder = 'https://…', error }: {
  label: string
  value: string[]
  onChange: (urls: string[]) => void
  max?: number
  hint?: string
  placeholder?: string
  error?: string
}) {
  const [draft, setDraft] = useState('')
  const [draftError, setDraftError] = useState<string>()
  const id = `url-${label.replace(/\W+/g, '-').toLowerCase()}`

  const add = () => {
    const url = draft.trim()
    if (!url) return
    if (!isHttpUrl(url)) return setDraftError('Enter a full http(s) URL')
    if (value.includes(url)) return setDraftError('Already added')
    if (value.length >= max) return setDraftError(`At most ${max}`)
    onChange([...value, url])
    setDraft('')
    setDraftError(undefined)
  }

  return (
    <Field label={label} htmlFor={id} error={draftError ?? error} hint={hint ?? `Up to ${max}`}>
      <div className="row" style={{ flexWrap: 'nowrap' }}>
        <input id={id} className="input" type="url" placeholder={placeholder} value={draft}
               disabled={value.length >= max}
               onChange={(e) => { setDraft(e.target.value); setDraftError(undefined) }}
               onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); add() } }} />
        <Button variant="secondary" onClick={add} disabled={!draft.trim() || value.length >= max}>Add</Button>
      </div>
      {value.length > 0 && (
        <ul className="url-list">
          {value.map((url) => (
            <li key={url}>
              <span className="truncate small">{url}</span>
              <button type="button" className="icon-button" style={{ width: 28, height: 28 }}
                      aria-label={`Remove ${url}`} onClick={() => onChange(value.filter((u) => u !== url))}>
                <CloseIcon size={14} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </Field>
  )
}
