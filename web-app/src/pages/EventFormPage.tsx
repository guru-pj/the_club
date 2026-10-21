import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { clubsApi, eventsApi } from '../api/endpoints'
import type { Club, ClubEvent, EventRequest } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { Button, Card, EmptyState, ErrorMessage, Field, LinkButton, Loading, PageHeader } from '../components/ui'
import { fieldError } from '../lib/fieldError'
import { fromInputDateTime, nowLocal, toInputDateTime } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { canManage } from './events/shared'
import { toMs } from './events/time'
import './events.css'
import { mediaUrl } from '../api/client'
import { ImagePicker } from '../components/ImagePicker'

interface FormState {
  title: string
  description: string
  location: string
  startTime: string
  registrationOpensAt: string
  registrationClosesAt: string
  capacity: string
  price: string
  currency: string
  points: string
  rules: string
  subscribersOnly: boolean
}

type Errors = Partial<Record<keyof FormState, string>>

function initialState(event?: ClubEvent): FormState {
  if (!event) {
    return {
      title: '', description: '', location: '', startTime: '',
      registrationOpensAt: toInputDateTime(nowLocal()), registrationClosesAt: '',
      capacity: '', price: '0', currency: 'INR', points: '10', rules: '', subscribersOnly: false,
    }
  }
  return {
    title: event.title,
    description: event.description ?? '',
    location: event.location ?? '',
    startTime: toInputDateTime(event.startTime),
    registrationOpensAt: toInputDateTime(event.registrationOpensAt),
    // the API reports the effective close time (start time when unset); leave blank in that case
    registrationClosesAt: event.registrationClosesAt === event.startTime ? '' : toInputDateTime(event.registrationClosesAt),
    capacity: event.capacity === null ? '' : String(event.capacity),
    price: String(event.price ?? 0),
    currency: event.currency || 'INR',
    points: String(event.points),
    rules: event.rules ?? '',
    subscribersOnly: event.subscribersOnly,
  }
}

/** Mirrors the event-service rules so most mistakes are caught before submitting. */
function validate(f: FormState, editing: boolean): Errors {
  const errors: Errors = {}
  if (!f.title.trim()) errors.title = 'Give the event a title'
  else if (f.title.length > 150) errors.title = 'Keep the title under 150 characters'
  if (!f.startTime) errors.startTime = 'Choose when the event starts'
  else if (!editing && toMs(f.startTime) <= Date.now()) errors.startTime = 'The start time must be in the future'
  if (f.startTime && f.registrationOpensAt && toMs(f.registrationOpensAt) >= toMs(f.startTime)) {
    errors.registrationOpensAt = 'Registration must open before the event starts'
  }
  if (f.registrationClosesAt) {
    if (f.registrationOpensAt && toMs(f.registrationClosesAt) <= toMs(f.registrationOpensAt)) {
      errors.registrationClosesAt = 'Registration must close after it opens'
    } else if (f.startTime && toMs(f.registrationClosesAt) > toMs(f.startTime)) {
      errors.registrationClosesAt = 'Registration must close by the start time'
    }
  }
  if (f.capacity && (!Number.isInteger(Number(f.capacity)) || Number(f.capacity) < 1)) {
    errors.capacity = 'Capacity must be a whole number of at least 1 (or leave empty for unlimited)'
  }
  const price = Number(f.price || 0)
  if (Number.isNaN(price) || price < 0) errors.price = 'Price cannot be negative'
  else if (Math.round(price * 100) !== price * 100) errors.price = 'Use at most two decimals'
  if (!/^[A-Z]{3}$/.test(f.currency)) errors.currency = 'Use a 3-letter currency code, e.g. INR'
  const points = Number(f.points)
  if (!Number.isInteger(points) || points < 0 || points > 1000) errors.points = 'Points must be between 0 and 1000'
  return errors
}

function toRequest(f: FormState): EventRequest {
  return {
    title: f.title.trim(),
    description: f.description.trim() || undefined,
    location: f.location.trim() || undefined,
    startTime: fromInputDateTime(f.startTime),
    registrationOpensAt: fromInputDateTime(f.registrationOpensAt),
    registrationClosesAt: fromInputDateTime(f.registrationClosesAt) ?? null,
    capacity: f.capacity ? Number(f.capacity) : null,
    price: Number(f.price || 0),
    currency: f.currency,
    points: Number(f.points),
    // '' clears the rules on an existing event
    rules: f.rules.trim(),
    subscribersOnly: f.subscribersOnly,
  }
}

function EventForm({ club, event }: { club: Club; event?: ClubEvent }) {
  const navigate = useNavigate()
  const toast = useToast()
  const editing = !!event
  const [form, setForm] = useState<FormState>(() => initialState(event))
  const [errors, setErrors] = useState<Errors>({})
  const [serverError, setServerError] = useState<unknown>()
  const [saving, setSaving] = useState(false)
  const [imageUrl, setImageUrl] = useState(event?.imageUrl ?? null)

  const set = (key: keyof FormState) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: key === 'currency' ? e.target.value.toUpperCase() : e.target.value }))
  const err = (key: keyof FormState) => errors[key] ?? fieldError(serverError, key)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const found = validate(form, editing)
    setErrors(found)
    if (Object.keys(found).length) return
    setSaving(true)
    setServerError(undefined)
    try {
      const body = toRequest(form)
      // a new event starts as a draft: the owner reviews it on its page and publishes it there
      const saved = event ? await eventsApi.update(event.id, body) : await eventsApi.create({ ...body, clubId: club.id, publish: false })
      toast.success(event ? 'Event updated' : 'Draft saved. Review it and publish when you\'re ready.')
      navigate(`/events/${saved.id}`)
    } catch (ex) {
      setServerError(ex)
    } finally {
      setSaving(false)
    }
  }

  const cancelTo = event ? `/events/${event.id}` : `/clubs/${club.id}`

  return (
    <form className="form" onSubmit={submit} noValidate>
      <Card title="Details">
        <div className="form">
          <Field label="Title" htmlFor="title" error={err('title')}>
            <input id="title" className={err('title') ? 'input has-error' : 'input'} value={form.title}
                   onChange={set('title')} maxLength={150} placeholder="Sunday Long Run" />
          </Field>
          <Field label="Description" htmlFor="description" error={err('description')}>
            <textarea id="description" className="input" value={form.description} onChange={set('description')}
                      placeholder="Route, pace groups, what to bring…" />
          </Field>
          <Field label="Rules and instructions (optional)" htmlFor="rules" error={err('rules')}
                 hint="What to bring, pace groups, cut-off times, safety rules">
            <textarea id="rules" className="input" value={form.rules} maxLength={10_000}
                      onChange={(e) => setForm({ ...form, rules: e.target.value })} />
          </Field>
          <Field label="Location" htmlFor="location" error={err('location')}>
            <input id="location" className="input" value={form.location} onChange={set('location')} maxLength={255}
                   placeholder="Marina Beach lighthouse" />
          </Field>
          <Field label="Starts at" htmlFor="startTime" error={err('startTime')}>
            <input id="startTime" type="datetime-local" className={err('startTime') ? 'input has-error' : 'input'}
                   value={form.startTime} onChange={set('startTime')} />
          </Field>
        </div>
      </Card>

      <Card title="Event image">
        {event
          ? <ImagePicker label="Cover image" shape="wide" currentUrl={mediaUrl(imageUrl)}
                         onUpload={async (file) => { setImageUrl((await eventsApi.uploadImage(event.id, file)).imageUrl); toast.success('Image updated') }}
                         onRemove={async () => { setImageUrl((await eventsApi.removeImage(event.id)).imageUrl); toast.success('Image removed') }} />
          : <p className="small muted">You can add a cover image once the event is created.</p>}
      </Card>

      <Card title="Registration">
        <div className="form">
          <label className="row" style={{ gap: 8 }}>
            <input type="checkbox" checked={form.subscribersOnly}
                   onChange={(e) => setForm({ ...form, subscribersOnly: e.target.checked })} />
            <span>Only the club's subscribers can register (otherwise anyone signed in, e.g. followers and people nearby)</span>
          </label>
          {editing && (
            <div className="alert alert-info">
              Changing only the registration window is fine even after registration has opened.
            </div>
          )}
          <div className="form-row">
            <Field label="Opens at" htmlFor="opens" error={err('registrationOpensAt')} hint="Members can register from this time">
              <input id="opens" type="datetime-local" className={err('registrationOpensAt') ? 'input has-error' : 'input'}
                     value={form.registrationOpensAt} onChange={set('registrationOpensAt')} />
            </Field>
            <Field label="Closes at (optional)" htmlFor="closes" error={err('registrationClosesAt')} hint="Defaults to the start time">
              <input id="closes" type="datetime-local" className={err('registrationClosesAt') ? 'input has-error' : 'input'}
                     value={form.registrationClosesAt} onChange={set('registrationClosesAt')} />
            </Field>
          </div>
          <div className="form-row">
            <Field label="Capacity (optional)" htmlFor="capacity" error={err('capacity')} hint="Leave empty for unlimited">
              <input id="capacity" type="number" min={1} step={1} inputMode="numeric"
                     className={err('capacity') ? 'input has-error' : 'input'} value={form.capacity} onChange={set('capacity')} />
            </Field>
            <Field label="Points for attending" htmlFor="points" error={err('points')} hint="Awarded at check-in">
              <input id="points" type="number" min={0} max={1000} step={1} inputMode="numeric"
                     className={err('points') ? 'input has-error' : 'input'} value={form.points} onChange={set('points')} />
            </Field>
          </div>
          <div className="form-row">
            <Field label="Price" htmlFor="price" error={err('price')} hint="0 makes the event free">
              <input id="price" type="number" min={0} step="0.01" inputMode="decimal"
                     className={err('price') ? 'input has-error' : 'input'} value={form.price} onChange={set('price')} />
            </Field>
            <Field label="Currency" htmlFor="currency" error={err('currency')}>
              <input id="currency" className={err('currency') ? 'input has-error' : 'input'} value={form.currency}
                     onChange={set('currency')} maxLength={3} />
            </Field>
          </div>
        </div>
      </Card>

      {serverError !== undefined && !Object.keys((serverError as { fieldErrors?: object }).fieldErrors ?? {}).length && (
        <ErrorMessage error={serverError} />
      )}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <LinkButton variant="ghost" to={cancelTo}>Cancel</LinkButton>
        <Button type="submit" loading={saving}>{editing ? 'Save changes' : 'Save and review'}</Button>
      </div>
    </form>
  )
}

export default function EventFormPage() {
  const { clubId, eventId } = useParams()
  const { hasRole } = useAuth()
  const state = useAsync(async () => {
    if (eventId) {
      const event = await eventsApi.get(eventId)
      return { event, club: await clubsApi.get(event.clubId) }
    }
    return { event: undefined, club: await clubsApi.get(clubId ?? '') }
  }, [clubId, eventId])

  if (state.loading && !state.data) return <Loading />
  if (state.error) return <div className="page page-narrow"><ErrorMessage error={state.error} onRetry={state.reload} /></div>
  if (!state.data) return null
  const { club, event } = state.data

  if (!canManage(club, hasRole('ADMIN'))) {
    return (
      <div className="page">
        <EmptyState title="Only the club owner can manage events"
                    action={<LinkButton variant="secondary" to={`/clubs/${club.id}`}>Back to {club.name}</LinkButton>} />
      </div>
    )
  }

  return (
    <div className="page page-narrow">
      <PageHeader
        back={event ? { to: `/events/${event.id}`, label: event.title } : { to: `/clubs/${club.id}`, label: club.name }}
        title={event ? 'Edit event' : 'New event'}
        subtitle={club.name} />
      <EventForm club={club} event={event} />
    </div>
  )
}
