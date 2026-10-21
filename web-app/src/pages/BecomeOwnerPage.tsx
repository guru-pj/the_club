import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { verificationApi } from '../api/endpoints'
import type { VerificationDocument, VerificationRequest } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CheckIcon, PlusIcon, ShieldIcon, TrashIcon } from '../components/icons'
import { useToast } from '../components/Toast'
import { Button, Card, EmptyState, ErrorMessage, Field, LinkButton, Loading, PageHeader, StatusBadge } from '../components/ui'
import { formatBytes, saveFile } from '../lib/download'
import { formatDate, humanize } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { ApplicationForm } from './owner/ApplicationForm'
import './clubs.css'

const MAX_OPEN = 3
const ACCEPTED_DOCUMENTS = 'application/pdf,image/jpeg,image/png,image/webp'
const isOpen = (r: VerificationRequest) => r.status === 'PENDING' || r.status === 'UNDER_REVIEW' || r.status === 'NEEDS_INFO'

function Progress({ status }: { status: VerificationRequest['status'] }) {
  const decided = status === 'APPROVED' || status === 'REJECTED'
  const steps = [
    { label: 'Submitted', done: true },
    { label: status === 'NEEDS_INFO' ? 'Changes required' : 'Pending review', done: status !== 'PENDING' && status !== 'UNDER_REVIEW' },
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

/** Private proof documents: only the applicant and the admin team can open them. */
function Documents({ request, onChanged }: { request: VerificationRequest; onChanged: () => void }) {
  const toast = useToast()
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const editable = isOpen(request)

  const add = async (file: File | undefined) => {
    if (input.current) input.current.value = ''
    if (!file) return
    if (file.size > 5 * 1024 * 1024) return toast.error('Files can be at most 5 MB')
    setBusy(true)
    try {
      await verificationApi.addDocument(request.id, file)
      toast.success(`${file.name} added`)
      onChanged()
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(false)
    }
  }
  const open = async (d: VerificationDocument) => {
    try {
      await saveFile(await verificationApi.downloadDocument(request.id, d.id), d.fileName)
    } catch (e) {
      toast.error(e)
    }
  }
  const remove = async (d: VerificationDocument) => {
    try {
      await verificationApi.removeDocument(request.id, d.id)
      onChanged()
    } catch (e) {
      toast.error(e)
    }
  }

  return (
    <div className="stack" style={{ gap: 8 }}>
      <div className="field-label">Documents</div>
      {request.documents.length === 0 && <p className="small muted">No documents yet. Proof of ownership or authorisation (a registration certificate, a letter from the club, a screenshot of the club's admin page) speeds up the review.</p>}
      {request.documents.length > 0 && (
        <ul className="url-list">
          {request.documents.map((d) => (
            <li key={d.id}>
              <span className="truncate">{d.fileName} <span className="muted small">· {formatBytes(d.sizeBytes)}</span></span>
              <Button size="sm" variant="ghost" onClick={() => void open(d)}>Download</Button>
              {editable && (
                <button className="icon-button icon-button-sm" aria-label={`Remove ${d.fileName}`} onClick={() => void remove(d)}>
                  <TrashIcon size={16} />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {editable && (
        <div className="row">
          <input ref={input} type="file" accept={ACCEPTED_DOCUMENTS} className="visually-hidden" aria-label="Upload a document"
                 onChange={(e) => void add(e.target.files?.[0])} />
          <Button size="sm" variant="secondary" loading={busy} onClick={() => input.current?.click()}>Upload a document</Button>
          <span className="small muted">PDF, JPEG, PNG or WebP, up to 5 MB. Only you and the admin team can see them.</span>
        </div>
      )}
    </div>
  )
}

function ApplicationCard({ request, onChanged }: { request: VerificationRequest; onChanged: () => void }) {
  const { hasRole, refreshSession } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  const openClub = async () => {
    setBusy(true)
    try {
      // the approval granted CLUB_OWNER; a fresh token carries it
      if (!hasRole('CLUB_OWNER')) await refreshSession()
      navigate(`/owner?club=${request.createdClubId}`)
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(false)
    }
  }

  const resubmit = async () => {
    setBusy(true)
    try {
      await verificationApi.resubmit(request.id, note.trim())
      toast.success('Sent back for review')
      setNote('')
      onChanged()
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title={request.clubName} actions={<StatusBadge status={request.status} />}>
      <div className="stack">
        <Progress status={request.status} />
        {request.adminNotes && (
          <div className={request.status === 'REJECTED' ? 'alert alert-error' : 'alert alert-info'}>
            <div>
              <div className="bold">{request.status === 'NEEDS_INFO' ? 'What needs to change' : 'Note from the admin team'}</div>
              <div className="pre-wrap">{request.adminNotes}</div>
            </div>
          </div>
        )}
        {editing ? (
          <ApplicationForm request={request} submitLabel="Save changes" onCancel={() => setEditing(false)}
                           onSubmit={async (body) => {
                             await verificationApi.update(request.id, body)
                             toast.success('Application updated')
                             setEditing(false)
                             onChanged()
                           }} />
        ) : (
          <dl className="kv">
            {request.clubType && <><dt>Category</dt><dd>{humanize(request.clubType)}</dd></>}
            <dt>Location</dt><dd>{[request.address, request.city].filter(Boolean).join(', ') || '—'}</dd>
            <dt>Contact</dt><dd>{[request.contactEmail, request.contactPhone].filter(Boolean).join(' · ') || '—'}</dd>
            {request.website && <><dt>Website</dt><dd>{request.website}</dd></>}
            <dt>Submitted</dt><dd>{formatDate(request.createdAt)}</dd>
            {request.reviewedAt && <><dt>Reviewed</dt><dd>{formatDate(request.reviewedAt)}</dd></>}
          </dl>
        )}
        {!editing && <Documents request={request} onChanged={onChanged} />}

        {(request.status === 'PENDING' || request.status === 'UNDER_REVIEW') && !editing && (
          <div className="row-between">
            <p className="muted small">An admin checks every application, including the club's social pages and documents. We'll notify you of the decision.</p>
            {request.status === 'PENDING' && <Button size="sm" variant="ghost" onClick={() => setEditing(true)}>Edit details</Button>}
          </div>
        )}
        {request.status === 'NEEDS_INFO' && !editing && (
          <div className="stack">
            <div className="row"><Button variant="secondary" onClick={() => setEditing(true)}>Update details</Button></div>
            <Field label="Message to the admin team (optional)" htmlFor={`resubmit-${request.id}`}>
              <textarea id={`resubmit-${request.id}`} className="input" maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)} />
            </Field>
            <div className="row" style={{ justifyContent: 'flex-end' }}>
              <Button loading={busy} onClick={() => void resubmit()}>Resubmit for review</Button>
            </div>
          </div>
        )}
        {request.status === 'APPROVED' && request.createdClubId && (
          <div className="alert alert-success" style={{ alignItems: 'center' }}>
            <div style={{ flex: 1 }}>Verified. The club is linked to your account; complete its profile to welcome members.</div>
            <Button size="sm" loading={busy} onClick={() => void openClub()}><ShieldIcon size={16} />Open the club dashboard</Button>
          </div>
        )}
      </div>
    </Card>
  )
}

/**
 * Club ownership: one application per club. Each is reviewed by the admin team, can be corrected and resubmitted
 * when changes are required, and once approved creates the club and links it to this account.
 */
export default function BecomeOwnerPage() {
  const { hasRole } = useAuth()
  const toast = useToast()
  const requests = useAsync(() => verificationApi.mine().then((all) => [...all].sort((a, b) => b.createdAt.localeCompare(a.createdAt))), [])
  const [applying, setApplying] = useState(false)

  const all = requests.data ?? []
  const openCount = all.filter(isOpen).length
  const current = all.filter((r) => r.status !== 'REJECTED')
  const rejected = all.filter((r) => r.status === 'REJECTED')
  const canApply = openCount < MAX_OPEN

  return (
    <div className="page page-narrow">
      <PageHeader title={hasRole('CLUB_OWNER') ? 'Your club applications' : 'Become a club owner'}
                  subtitle="Every club is verified on its own by The Club's admin team before you can manage it."
                  actions={!applying && canApply && all.length > 0 && (
                    <Button onClick={() => setApplying(true)}><PlusIcon size={16} />Apply for {hasRole('CLUB_OWNER') ? 'another' : 'a'} club</Button>
                  )} />
      {requests.error ? <ErrorMessage error={requests.error} onRetry={requests.reload} />
        : requests.loading && !requests.data ? <Loading />
        : (
          <div className="stack-lg">
            {(applying || all.length === 0) && (
              <Card title="Apply to manage a club">
                <p className="muted small" style={{ marginBottom: 12 }}>
                  Tell us about the club and how to reach it. After submitting you can upload documents that show you own or
                  represent it. Applications are never approved automatically: an admin reviews each one.
                </p>
                <ApplicationForm submitLabel="Submit application" onCancel={all.length ? () => setApplying(false) : undefined}
                                 onSubmit={async (body) => {
                                   await verificationApi.apply(body)
                                   toast.success('Application submitted. Upload documents below to speed up the review.')
                                   setApplying(false)
                                   requests.reload()
                                 }} />
              </Card>
            )}
            {current.map((r) => <ApplicationCard key={r.id} request={r} onChanged={requests.reload} />)}
            {!canApply && <p className="small muted">You have {MAX_OPEN} applications waiting for review. You can apply for another club once one is decided.</p>}
            {rejected.length > 0 && (
              <Card title="Rejected applications" flush>
                <div className="list">
                  {rejected.map((r) => (
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
            {all.length > 0 && hasRole('CLUB_OWNER') && (
              <Card><EmptyState icon={<ShieldIcon size={28} />} title="Manage your verified clubs"
                                action={<LinkButton to="/owner">Owner dashboard</LinkButton>} /></Card>
            )}
          </div>
        )}
    </div>
  )
}
