import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { ACTIVITY_TYPES, type Activity, type ActivityRequest, type ActivityType } from '../../api/types'
import { Button, ErrorMessage, Field } from '../../components/ui'
import { fieldError } from '../../lib/fieldError'
import { fromInputDateTime, humanize, nowLocal, toInputDateTime } from '../../lib/format'

interface Props {
  initial?: Activity
  submitLabel: string
  onSubmit: (body: ActivityRequest) => Promise<void>
  onCancel?: () => void
}

export function ActivityForm({ initial, submitLabel, onSubmit, onCancel }: Props) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [type, setType] = useState<ActivityType>(initial?.type ?? 'RUN')
  const [distance, setDistance] = useState(initial ? String(initial.distance) : '')
  const d = initial?.duration ?? 0
  const [hours, setHours] = useState(initial ? String(Math.floor(d / 3600)) : '')
  const [minutes, setMinutes] = useState(initial ? String(Math.floor((d % 3600) / 60)) : '')
  const [seconds, setSeconds] = useState(initial ? String(d % 60) : '')
  const [calories, setCalories] = useState(initial?.calories != null ? String(initial.calories) : '')
  const [startTime, setStartTime] = useState(toInputDateTime(initial?.startTime ?? nowLocal()))
  const [error, setError] = useState<unknown>()
  const [localError, setLocalError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setLocalError(undefined)
    const duration = (Number(hours) || 0) * 3600 + (Number(minutes) || 0) * 60 + (Number(seconds) || 0)
    if (duration <= 0) {
      setLocalError('Enter how long the activity took')
      return
    }
    const start = fromInputDateTime(startTime)
    if (!start) {
      setLocalError('Enter when the activity started')
      return
    }
    if (start > nowLocal(5)) {
      setLocalError('Start time cannot be in the future')
      return
    }
    setSubmitting(true)
    setError(undefined)
    try {
      await onSubmit({
        title: title.trim(),
        type,
        distance: Number(distance) || 0,
        duration,
        calories: calories === '' ? null : Number(calories),
        startTime: start,
      })
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const idp = initial ? `edit-${initial.id}` : 'new'
  return (
    <form className="form" onSubmit={submit} noValidate>
      <Field label="Title" htmlFor={`${idp}-title`} error={fieldError(error, 'title')}>
        <input id={`${idp}-title`} className="input" required maxLength={100} value={title}
               placeholder="Morning run" onChange={(e) => setTitle(e.target.value)} />
      </Field>
      <div className="form-row">
        <Field label="Type" htmlFor={`${idp}-type`} error={fieldError(error, 'type')}>
          <select id={`${idp}-type`} className="input" value={type} onChange={(e) => setType(e.target.value as ActivityType)}>
            {ACTIVITY_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </select>
        </Field>
        <Field label="Distance (km)" htmlFor={`${idp}-distance`} error={fieldError(error, 'distance')}>
          <input id={`${idp}-distance`} className="input" type="number" min={0} max={1000} step={0.01}
                 inputMode="decimal" value={distance} onChange={(e) => setDistance(e.target.value)} />
        </Field>
      </div>
      <Field label="Duration" error={fieldError(error, 'duration')}>
        <div className="duration-inputs">
          <label><input className="input" type="number" min={0} aria-label="Hours" value={hours}
                        onChange={(e) => setHours(e.target.value)} />h</label>
          <label><input className="input" type="number" min={0} max={59} aria-label="Minutes" value={minutes}
                        onChange={(e) => setMinutes(e.target.value)} />m</label>
          <label><input className="input" type="number" min={0} max={59} aria-label="Seconds" value={seconds}
                        onChange={(e) => setSeconds(e.target.value)} />s</label>
        </div>
      </Field>
      <div className="form-row">
        <Field label="Calories (optional)" htmlFor={`${idp}-calories`} error={fieldError(error, 'calories')}>
          <input id={`${idp}-calories`} className="input" type="number" min={0} max={20000} value={calories}
                 onChange={(e) => setCalories(e.target.value)} />
        </Field>
        <Field label="Started at" htmlFor={`${idp}-start`} error={fieldError(error, 'startTime')}>
          <input id={`${idp}-start`} className="input" type="datetime-local" required value={startTime}
                 max={toInputDateTime(nowLocal())} onChange={(e) => setStartTime(e.target.value)} />
        </Field>
      </div>
      {localError && <div className="alert alert-error" role="alert">{localError}</div>}
      {!(error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) && <ErrorMessage error={error} />}
      <div className="row">
        <Button type="submit" loading={submitting}>{submitLabel}</Button>
        {onCancel && <Button variant="ghost" onClick={onCancel}>Cancel</Button>}
      </div>
    </form>
  )
}
