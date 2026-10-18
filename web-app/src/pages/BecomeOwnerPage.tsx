import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { verificationApi } from '../api/endpoints'
import { CLUB_TYPES, type ClubType, type VerificationRequest } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CheckIcon, ShieldIcon } from '../components/icons'
import { useToast } from '../components/Toast'
import { Button, Card, EmptyState, ErrorMessage, Field, LinkButton, Loading, PageHeader, StatusBadge } from '../components/ui'
import { fieldError } from '../lib/fieldError'
import { formatDate, humanize } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { UrlListInput } from './clubs/UrlListInput'
import './clubs.css'

const SOCIAL_FIELDS = [
  { key: 'instagram', label: 'Instagram' },
  { key: 'facebook', label: 'Facebook' },
  { key: 'strava', label: 'Strava club' },
  { key: 'website', label: 'Website' },
] as const

function Progress({ status }: { status: VerificationRequest['status'] }) {
  const reviewed = status !== 'PENDING'
  const decided = status === 'APPROVED' || status === 'REJECTED'
  const steps = [
    { label: 'Submitted', done: true },
    { label: status === 'NEEDS_INFO' ? 'More info needed' : 'Under review', done: reviewed },
    { label: decided ? humanize(status) : 'Decision', done: decided },
  ]
  return (
    <div className="progress-steps" aria-label="Application progress">
      {steps.map((s, i) => (
        <span key={s.label} className="row" style={{ gap: 8 }}>
          {i > 0 && <span className="progress-sep" />}
          <span className={s.done ? 'progress-step done' : 'progress-step'}>
            <span className="bullet">{s.done ? <CheckIcon size={12} /> : i + 1}</span>{s.label}
          </span>
        </span>
      ))}
    </div>
  )
}

function ApplicationForm({ onSubmitted }: { onSubmitted: () => void }) {
  const toast = useToast()
  const [clubName, setClubName] = useState('')
  const [clubType, setClubType] = useState<ClubType | ''>('')
  const [city, setCity] = useState('')
  const [description, setDescription] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [social, setSocial] = useState<Record<string, string>>({})
  const [proofFiles, setProofFiles] = useState<string[]>([])
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(undefined)
    try {
      const socialLinks = Object.fromEntries(Object.entries(social).map(([k, v]) => [k, v.trim()]).filter(([, v]) => v))
      await verificationApi.apply({
        clubName: clubName.trim(),
        clubType: clubType || undefined,
        city: city.trim() || undefined,
        description: description.trim() || undefined,
        contactPhone: contactPhone.trim() || undefined,
        socialLinks,
        proofFiles,
      })
      toast.success('Application submitted — we\'ll review it within 48 hours')
      onSubmitted()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  const general = error instanceof ApiError && Object.keys(error.fieldErrors).length ? undefined : error
  return (
    <Card title="Apply to become a club owner">
      <form className="form" onSubmit={submit}>
        <p className="muted small">
          Tell us about your club and share links that show you run it (social pages, Strava club, photos of past events).
          An admin reviews every application, usually within 48 hours.
        </p>
        <ErrorMessage error={general} />
        <Field label="Club name" htmlFor="ov-name" error={fieldError(error, 'clubName')}>
          <input id="ov-name" className="input" required maxLength={200} value={clubName} onChange={(e) => setClubName(e.target.value)} />
        </Field>
        <div className="form-row">
          <Field label="Club type" htmlFor="ov-type">
            <select id="ov-type" className="input" value={clubType} onChange={(e) => setClubType(e.target.value as ClubType | '')}>
              <option value="">Select…</option>
              {CLUB_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
            </select>
          </Field>
          <Field label="City" htmlFor="ov-city" error={fieldError(error, 'city')}>
            <input id="ov-city" className="input" maxLength={100} value={city} onChange={(e) => setCity(e.target.value)} />
          </Field>
        </div>
        <Field label="About the club" htmlFor="ov-desc" error={fieldError(error, 'description')}
               hint="How long it has been running, how many members, regular sessions…">
          <textarea id="ov-desc" className="input" maxLength={5000} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <Field label="Contact phone" htmlFor="ov-phone" error={fieldError(error, 'contactPhone')}>
          <input id="ov-phone" className="input" type="tel" maxLength={20} value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} />
        </Field>
        <div className="form-row">
          {SOCIAL_FIELDS.map(({ key, label }) => (
            <Field key={key} label={label} htmlFor={`ov-${key}`}>
              <input id={`ov-${key}`} className="input" type="url" placeholder="https://…" maxLength={500}
                     value={social[key] ?? ''} onChange={(e) => setSocial((s) => ({ ...s, [key]: e.target.value }))} />
            </Field>
          ))}
        </div>
        <UrlListInput label="Proof documents" value={proofFiles} onChange={setProofFiles} max={20}
                      hint="Links to photos or documents — at least one is recommended" error={fieldError(error, 'proofFiles')} />
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button type="submit" loading={saving} disabled={!clubName.trim()}>Submit application</Button>
        </div>
      </form>
    </Card>
  )
}

function UploadMoreProof({ request, onDone }: { request: VerificationRequest; onDone: () => void }) {
  const toast = useToast()
  const [files, setFiles] = useState<string[]>([])
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    try {
      await verificationApi.uploadProof(request.id, files, note.trim() || undefined)
      toast.success('Thanks — your application is back under review')
      onDone()
    } catch (err) {
      toast.error(err)
    } finally {
      setSaving(false)
    }
  }
  return (
    <form className="form" onSubmit={submit}>
      <hr className="divider" />
      <h3>Send the requested information</h3>
      <UrlListInput label="Additional proof" value={files} onChange={setFiles} max={20} />
      <Field label="Note to the reviewer" htmlFor="ov-note">
        <textarea id="ov-note" className="input" maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)} />
      </Field>
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <Button type="submit" loading={saving} disabled={files.length === 0}>Send</Button>
      </div>
    </form>
  )
}

function StatusCard({ request, onChanged }: { request: VerificationRequest; onChanged: () => void }) {
  const { refreshSession } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [activating, setActivating] = useState(false)

  const activate = async () => {
    setActivating(true)
    try {
      await refreshSession()
      toast.success('Owner access activated')
      navigate(request.createdClubId ? `/clubs/${request.createdClubId}` : '/clubs/new')
    } catch (e) {
      toast.error(e)
    } finally {
      setActivating(false)
    }
  }

  return (
    <Card title="Your application" actions={<StatusBadge status={request.status} />}>
      <div className="stack">
        <Progress status={request.status} />
        <dl className="kv">
          <dt>Club</dt><dd className="bold">{request.clubName}</dd>
          {request.clubType && <><dt>Type</dt><dd>{humanize(request.clubType)}</dd></>}
          {request.city && <><dt>City</dt><dd>{request.city}</dd></>}
          <dt>Submitted</dt><dd>{formatDate(request.createdAt)}</dd>
          {request.reviewedAt && <><dt>Reviewed</dt><dd>{formatDate(request.reviewedAt)}</dd></>}
        </dl>
        {request.adminNotes && (
          <div className={request.status === 'REJECTED' ? 'alert alert-error' : 'alert alert-info'}>
            <div><div className="bold">Note from the reviewer</div><div className="pre-wrap">{request.adminNotes}</div></div>
          </div>
        )}
        {(request.status === 'PENDING' || request.status === 'UNDER_REVIEW') && (
          <p className="muted small">We'll notify you as soon as an admin has reviewed your application.</p>
        )}
        {request.status === 'APPROVED' && (
          <>
            <div className="alert alert-success">You're approved! Activate owner access to start creating clubs and events.</div>
            <div><Button onClick={activate} loading={activating}><ShieldIcon size={16} />Activate owner access</Button></div>
          </>
        )}
        {request.status === 'NEEDS_INFO' && <UploadMoreProof request={request} onDone={onChanged} />}
      </div>
    </Card>
  )
}

export default function BecomeOwnerPage() {
  const { hasRole } = useAuth()
  const isOwner = hasRole('CLUB_OWNER')
  const requests = useAsync(
    () => (isOwner ? Promise.resolve([]) : verificationApi.mine().then((all) =>
      [...all].sort((a, b) => b.createdAt.localeCompare(a.createdAt)))),
    [isOwner],
  )

  if (isOwner) {
    return (
      <div className="page page-narrow">
        <PageHeader title="Club ownership" />
        <Card>
          <EmptyState icon={<ShieldIcon size={36} />} title="You're a verified club owner"
                      action={<div className="row"><LinkButton to="/clubs/new">Create a club</LinkButton>
                        <LinkButton to="/clubs" variant="secondary">Your clubs</LinkButton></div>}>
            Create clubs, post to their feeds and run events.
          </EmptyState>
        </Card>
      </div>
    )
  }

  const [latest, ...older] = requests.data ?? []
  const canApply = !latest || latest.status === 'REJECTED'

  return (
    <div className="page page-narrow">
      <PageHeader title="Become a club owner" subtitle="Verified owners can create clubs, post updates and host events." />
      {requests.error ? <ErrorMessage error={requests.error} onRetry={requests.reload} />
        : requests.loading && !requests.data ? <Loading />
        : (
          <div className="stack-lg">
            {latest && <StatusCard request={latest} onChanged={requests.reload} />}
            {canApply && <ApplicationForm onSubmitted={requests.reload} />}
            {older.length > 0 && (
              <Card title="Previous applications" flush>
                <div className="list">
                  {older.map((r) => (
                    <div key={r.id} className="list-item">
                      <div className="list-item-main">
                        <div className="bold truncate">{r.clubName}</div>
                        <div className="muted small">{formatDate(r.createdAt)}{r.adminNotes ? ` · ${r.adminNotes}` : ''}</div>
                      </div>
                      <StatusBadge status={r.status} />
                    </div>
                  ))}
                </div>
              </Card>
            )}
          </div>
        )}
    </div>
  )
}
