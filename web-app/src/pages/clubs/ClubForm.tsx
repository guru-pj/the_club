import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { CLUB_TYPES, type Club, type ClubRequest, type ClubType } from '../../api/types'
import { Button, ErrorMessage, Field } from '../../components/ui'
import { fieldError } from '../../lib/fieldError'
import { humanize } from '../../lib/format'
import { clubFormValues, toClubRequest, type ClubFormValues } from './clubFormValues'
import { isHttpUrl } from './urls'

/** Club fields as controlled inputs; reused by create, edit and admin approval. */
export function ClubFields({ values, onChange, error, nameRequired = true }: {
  values: ClubFormValues
  onChange: (v: ClubFormValues) => void
  error?: unknown
  nameRequired?: boolean
}) {
  const set = <K extends keyof ClubFormValues>(key: K, value: ClubFormValues[K]) => onChange({ ...values, [key]: value })
  const nameError = error instanceof ApiError && error.status === 409
    ? 'A club with this name already exists'
    : fieldError(error, 'name')
  const logoInvalid = values.logoUrl.trim() !== '' && !isHttpUrl(values.logoUrl)
  return (
    <>
      <Field label="Club name" htmlFor="club-name" error={nameError}>
        <input id="club-name" className={nameError ? 'input has-error' : 'input'} required={nameRequired} maxLength={120}
               value={values.name} onChange={(e) => set('name', e.target.value)} />
      </Field>
      <Field label="Description" htmlFor="club-description" error={fieldError(error, 'description')}>
        <textarea id="club-description" className="input" maxLength={2000} value={values.description}
                  onChange={(e) => set('description', e.target.value)} />
      </Field>
      <div className="form-row">
        <Field label="City" htmlFor="club-city" error={fieldError(error, 'city')}>
          <input id="club-city" className="input" maxLength={100} value={values.city}
                 onChange={(e) => set('city', e.target.value)} />
        </Field>
        <Field label="Club type" htmlFor="club-type">
          <select id="club-type" className="input" value={values.clubType}
                  onChange={(e) => set('clubType', e.target.value as ClubType | '')}>
            <option value="">Select…</option>
            {CLUB_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Logo URL" htmlFor="club-logo" hint="Optional link to an image"
             error={logoInvalid ? 'Enter a full http(s) URL' : fieldError(error, 'logoUrl')}>
        <input id="club-logo" className="input" type="url" maxLength={500} placeholder="https://…" value={values.logoUrl}
               onChange={(e) => set('logoUrl', e.target.value)} />
      </Field>
    </>
  )
}

export function ClubForm({ initial, submitLabel, onSubmit, onCancel }: {
  initial?: Club | null
  submitLabel: string
  onSubmit: (request: ClubRequest) => Promise<void>
  onCancel?: () => void
}) {
  const [values, setValues] = useState(() => clubFormValues(initial))
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (values.logoUrl.trim() && !isHttpUrl(values.logoUrl)) return
    setSaving(true)
    setError(undefined)
    try {
      await onSubmit(toClubRequest(values))
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  const generalError = error instanceof ApiError && (error.status === 409 || Object.keys(error.fieldErrors).length > 0)
    ? undefined : error
  return (
    <form className="form" onSubmit={submit} noValidate={false}>
      <ErrorMessage error={generalError} />
      <ClubFields values={values} onChange={setValues} error={error} />
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        {onCancel && <Button variant="secondary" onClick={onCancel}>Cancel</Button>}
        <Button type="submit" loading={saving} disabled={!values.name.trim()}>{submitLabel}</Button>
      </div>
    </form>
  )
}
