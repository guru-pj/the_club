import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { CLUB_TYPES, type ApplyVerificationRequest, type ClubType, type VerificationRequest } from '../../api/types'
import { Button, ErrorMessage, Field } from '../../components/ui'
import { fieldError } from '../../lib/fieldError'
import { humanize } from '../../lib/format'
import { currentPosition } from '../../lib/location'
import { SOCIAL_NETWORKS } from '../clubs/clubFormValues'
import { UrlListInput } from '../clubs/UrlListInput'
import { applicationProblem, applicationValues, toApplication, type ApplicationValues } from './applicationValues'

/** The details of the club being claimed: for a new application, or to correct one. */
export function ApplicationForm({ request, submitLabel, onSubmit, onCancel }: {
  request?: VerificationRequest
  submitLabel: string
  onSubmit: (body: ApplyVerificationRequest) => Promise<void>
  onCancel?: () => void
}) {
  const [v, setV] = useState(() => applicationValues(request))
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState(false)
  const [locating, setLocating] = useState(false)
  const set = <K extends keyof ApplicationValues>(key: K, value: ApplicationValues[K]) => setV((prev) => ({ ...prev, [key]: value }))

  const locate = async () => {
    setLocating(true)
    try {
      const { latitude, longitude } = await currentPosition()
      setV((prev) => ({ ...prev, latitude: latitude.toFixed(5), longitude: longitude.toFixed(5) }))
    } catch (e) {
      setError(e)
    } finally {
      setLocating(false)
    }
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const problem = applicationProblem(v)
    if (problem) return setError(new ApiError(400, problem))
    setSaving(true)
    setError(undefined)
    try {
      await onSubmit(toApplication(v))
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  const general = error instanceof ApiError && Object.keys(error.fieldErrors).length ? undefined : error
  return (
    <form className="form" onSubmit={submit}>
      <ErrorMessage error={general} />
      <fieldset className="fieldset">
        <legend className="bold small">The club</legend>
        <Field label="Club name" htmlFor="ap-name" error={fieldError(error, 'clubName')}
               hint="Its official name, as on its documents or Instagram. The club is listed under the name an admin verifies.">
          <input id="ap-name" className="input" required maxLength={120} value={v.clubName} onChange={(e) => set('clubName', e.target.value)} />
        </Field>
        <div className="form-row">
          <Field label="Category" htmlFor="ap-type" error={fieldError(error, 'clubType')}>
            <select id="ap-type" className="input" required value={v.clubType} onChange={(e) => set('clubType', e.target.value as ClubType | '')}>
              <option value="">Select…</option>
              {CLUB_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
            </select>
          </Field>
          <Field label="City" htmlFor="ap-city" error={fieldError(error, 'city')}>
            <input id="ap-city" className="input" required maxLength={100} value={v.city} onChange={(e) => set('city', e.target.value)} />
          </Field>
        </div>
        <Field label="About the club" htmlFor="ap-desc" error={fieldError(error, 'description')}
               hint="How long it has been running, how many members, regular sessions…">
          <textarea id="ap-desc" className="input" maxLength={5000} value={v.description} onChange={(e) => set('description', e.target.value)} />
        </Field>
      </fieldset>
      <fieldset className="fieldset">
        <legend className="bold small">Where it meets</legend>
        <Field label="Address or meeting point" htmlFor="ap-address" error={fieldError(error, 'address')}>
          <input id="ap-address" className="input" maxLength={255} value={v.address} onChange={(e) => set('address', e.target.value)} />
        </Field>
        <div className="form-row">
          <Field label="Latitude" htmlFor="ap-lat">
            <input id="ap-lat" className="input" inputMode="decimal" value={v.latitude} onChange={(e) => set('latitude', e.target.value)} />
          </Field>
          <Field label="Longitude" htmlFor="ap-lng">
            <input id="ap-lng" className="input" inputMode="decimal" value={v.longitude} onChange={(e) => set('longitude', e.target.value)} />
          </Field>
        </div>
        <div><Button size="sm" variant="secondary" loading={locating} onClick={() => void locate()}>Use my current location</Button></div>
      </fieldset>
      <fieldset className="fieldset">
        <legend className="bold small">Contact and online presence</legend>
        <div className="form-row">
          <Field label="Contact email" htmlFor="ap-email" error={fieldError(error, 'contactEmail')}>
            <input id="ap-email" className="input" type="email" maxLength={255} value={v.contactEmail} onChange={(e) => set('contactEmail', e.target.value)} />
          </Field>
          <Field label="Contact phone" htmlFor="ap-phone" error={fieldError(error, 'contactPhone')}>
            <input id="ap-phone" className="input" type="tel" maxLength={20} value={v.contactPhone} onChange={(e) => set('contactPhone', e.target.value)} />
          </Field>
        </div>
        <Field label="Official website" htmlFor="ap-website" error={fieldError(error, 'website')}>
          <input id="ap-website" className="input" type="url" placeholder="https://…" maxLength={500} value={v.website}
                 onChange={(e) => set('website', e.target.value)} />
        </Field>
        <div className="form-row">
          {SOCIAL_NETWORKS.map(({ key, label }) => (
            <Field key={key} label={label} htmlFor={`ap-${key}`}>
              <input id={`ap-${key}`} className="input" type="url" placeholder="https://…" maxLength={500}
                     value={v.socialLinks[key] ?? ''} onChange={(e) => set('socialLinks', { ...v.socialLinks, [key]: e.target.value })} />
            </Field>
          ))}
        </div>
      </fieldset>
      <UrlListInput label="Proof links" value={v.proofFiles} onChange={(urls) => set('proofFiles', urls)} max={20}
                    hint="Optional links that show you run the club (photos of past events, press, a Strava club). You can also upload documents."
                    error={fieldError(error, 'proofFiles')} />
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        {onCancel && <Button variant="secondary" onClick={onCancel}>Cancel</Button>}
        <Button type="submit" loading={saving}>{submitLabel}</Button>
      </div>
    </form>
  )
}
