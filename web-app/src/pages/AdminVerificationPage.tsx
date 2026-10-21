import { useState, type FormEvent, type ReactNode } from 'react'
import { usersApi, verificationApi } from '../api/endpoints'
import type { VerificationRequest, VerificationStatus } from '../api/types'
import { ChevronLeftIcon, ShieldIcon } from '../components/icons'
import { useToast } from '../components/Toast'
import {
  Avatar, Button, Card, EmptyState, ErrorMessage, Field, Loading, Modal, PageHeader, Pagination, StatusBadge, Tabs,
} from '../components/ui'
import { formatBytes, saveFile } from '../lib/download'
import { formatDateTime, humanize, timeAgo } from '../lib/format'
import { mapLink } from '../lib/location'
import { useAsync } from '../lib/useAsync'
import { ClubFields } from './clubs/ClubForm'
import { clubFormValues, toClubRequest } from './clubs/clubFormValues'
import { isHttpUrl } from './clubs/urls'
import './clubs.css'

type Filter = VerificationStatus | 'ALL'
const FILTERS: { id: Filter; label: string }[] = [
  { id: 'PENDING', label: 'Pending' },
  { id: 'UNDER_REVIEW', label: 'Under review' },
  { id: 'NEEDS_INFO', label: 'Changes required' },
  { id: 'APPROVED', label: 'Approved' },
  { id: 'REJECTED', label: 'Rejected' },
  { id: 'ALL', label: 'All' },
]
const PAGE_SIZE = 20
const IMAGE_RE = /\.(jpe?g|png|webp|gif)(\?.*)?$/i

type Action = 'approve' | 'reject' | 'info' | null

function ApproveModal({ request, onClose, onDone }: { request: VerificationRequest; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const [values, setValues] = useState(() => clubFormValues({
    name: request.clubName, city: request.city, description: request.description, clubType: request.clubType,
    address: request.address, latitude: request.latitude, longitude: request.longitude, contactEmail: request.contactEmail,
    contactPhone: request.contactPhone, website: request.website, socialLinks: request.socialLinks,
  }))
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(undefined)
    try {
      await verificationApi.approve(request.id, toClubRequest(values))
      toast.success(`${values.name.trim()} approved and linked to the applicant`)
      onDone()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open title="Approve application" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <p className="muted small">
          This creates the club, links it to the applicant and makes them its owner. Use the club's exact official name as
          on its documents or Instagram; owners can't change it later. Approving covers this club only.
        </p>
        <ErrorMessage error={error} />
        <ClubFields values={values} onChange={setValues} error={error} />
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={saving} disabled={!values.name.trim()}>Approve and create the club</Button>
        </div>
      </form>
    </Modal>
  )
}

function TextActionModal({ title, label, submitLabel, danger, onSubmit, onClose }: {
  title: string; label: string; submitLabel: string; danger?: boolean
  onSubmit: (text: string) => Promise<void>; onClose: () => void
}) {
  const [text, setText] = useState('')
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState(false)
  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(undefined)
    try {
      await onSubmit(text.trim())
    } catch (err) {
      setError(err)
      setSaving(false)
    }
  }
  return (
    <Modal open title={title} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <ErrorMessage error={error} />
        <Field label={label} htmlFor="admin-action-text" hint="The applicant will see this message">
          <textarea id="admin-action-text" className="input" required maxLength={2000} autoFocus value={text}
                    onChange={(e) => setText(e.target.value)} />
        </Field>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" variant={danger ? 'danger' : 'primary'} loading={saving} disabled={!text.trim()}>{submitLabel}</Button>
        </div>
      </form>
    </Modal>
  )
}

function SafeLink({ url, children }: { url: string; children?: ReactNode }) {
  if (!isHttpUrl(url)) return <span className="mono small">{url}</span>
  return <a href={url} target="_blank" rel="noopener noreferrer" style={{ overflowWrap: 'anywhere' }}>{children ?? url}</a>
}

function RequestDetail({ request, onChanged, onBack }: { request: VerificationRequest; onChanged: () => void; onBack: () => void }) {
  const toast = useToast()
  const [action, setAction] = useState<Action>(null)
  const applicant = useAsync(() => usersApi.get(request.userId), [request.userId])
  const history = useAsync(() => verificationApi.history(request.id), [request.id, request.updatedAt])
  const reviewable = request.status === 'PENDING' || request.status === 'UNDER_REVIEW'
  const open = reviewable || request.status === 'NEEDS_INFO'
  const socialLinks = Object.entries(request.socialLinks ?? {})

  const done = () => { setAction(null); onChanged() }

  return (
    <Card>
      <div className="stack">
        <button className="btn btn-ghost btn-sm admin-back" style={{ alignSelf: 'flex-start' }} onClick={onBack}>
          <ChevronLeftIcon size={16} />All requests
        </button>
        <div className="row-between">
          <div>
            <h2>{request.clubName}</h2>
            <div className="muted small">Submitted {formatDateTime(request.createdAt)}</div>
          </div>
          <StatusBadge status={request.status} />
        </div>

        <div className="row" style={{ flexWrap: 'nowrap' }}>
          <Avatar name={applicant.data?.name} url={applicant.data?.profilePicUrl} />
          <div style={{ minWidth: 0 }}>
            {applicant.loading ? <span className="muted small">Loading applicant…</span>
              : applicant.error ? <span className="muted small">Applicant unavailable</span>
              : <>
                  <div className="bold truncate">{applicant.data?.name}</div>
                  <div className="muted small truncate">{applicant.data?.email}</div>
                </>}
          </div>
        </div>

        <dl className="kv">
          <dt>Type</dt><dd>{request.clubType ? humanize(request.clubType) : '—'}</dd>
          <dt>City</dt><dd>{request.city ?? '—'}</dd>
          <dt>Address</dt>
          <dd>
            {request.address ?? '—'}
            {request.latitude != null && request.longitude != null && (
              <> · <a href={mapLink(request.latitude, request.longitude)} target="_blank" rel="noreferrer">Map</a></>
            )}
          </dd>
          <dt>Email</dt><dd>{request.contactEmail ? <a href={`mailto:${request.contactEmail}`}>{request.contactEmail}</a> : '—'}</dd>
          <dt>Phone</dt><dd>{request.contactPhone ? <a href={`tel:${request.contactPhone}`}>{request.contactPhone}</a> : '—'}</dd>
          <dt>Website</dt><dd>{request.website ? <SafeLink url={request.website} /> : '—'}</dd>
          <dt>Social links</dt>
          <dd>
            {socialLinks.length ? (
              <div className="stack" style={{ gap: 4 }}>
                {socialLinks.map(([k, url]) => <div key={k}><span className="muted small">{humanize(k)}: </span><SafeLink url={url} /></div>)}
              </div>
            ) : '—'}
          </dd>
        </dl>

        {request.description && (
          <div><div className="field-label">About the club</div><p className="pre-wrap">{request.description}</p></div>
        )}

        <div>
          <div className="field-label" style={{ marginBottom: 6 }}>Proof files ({request.proofFiles.length})</div>
          {request.proofFiles.length === 0 ? <span className="muted small">None provided</span> : (
            <div className="photo-grid">
              {request.proofFiles.map((url) => IMAGE_RE.test(url) && isHttpUrl(url) ? (
                <a key={url} href={url} target="_blank" rel="noopener noreferrer"><img className="proof-thumb" src={url} alt="Proof document" loading="lazy" /></a>
              ) : (
                <div key={url} className="card" style={{ padding: 10 }}><SafeLink url={url}>Open document</SafeLink></div>
              ))}
            </div>
          )}
        </div>

        <div>
          <div className="field-label" style={{ marginBottom: 6 }}>Uploaded documents ({request.documents.length})</div>
          {request.documents.length === 0 ? <span className="muted small">None uploaded</span> : (
            <ul className="url-list">
              {request.documents.map((d) => (
                <li key={d.id}>
                  <span className="truncate">{d.fileName} <span className="muted small">· {formatBytes(d.sizeBytes)} · {timeAgo(d.uploadedAt)}</span></span>
                  <Button size="sm" variant="ghost" onClick={async () => {
                    try {
                      await saveFile(await verificationApi.downloadDocument(request.id, d.id), d.fileName)
                    } catch (e) {
                      toast.error(e)
                    }
                  }}>Download</Button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {request.adminNotes && <div className="alert alert-info"><div><div className="bold">Admin notes</div><div className="pre-wrap">{request.adminNotes}</div></div></div>}

        {open && (
          <div className="row">
            {reviewable && <Button onClick={() => setAction('approve')}>Approve</Button>}
            {reviewable && <Button variant="secondary" onClick={() => setAction('info')}>Ask for changes</Button>}
            <Button variant="danger" onClick={() => setAction('reject')}>Reject</Button>
          </div>
        )}

        <hr className="divider" />
        <div>
          <div className="field-label" style={{ marginBottom: 6 }}>History</div>
          {history.loading && !history.data ? <Loading /> : history.error ? <ErrorMessage error={history.error} /> : (
            <ul className="url-list">
              {history.data?.map((h) => (
                <li key={h.id}>
                  <span><span className="bold small">{humanize(h.action)}</span>{h.note && <span className="small"> — {h.note}</span>}</span>
                  <span className="muted tiny">{timeAgo(h.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {action === 'approve' && <ApproveModal request={request} onClose={() => setAction(null)} onDone={done} />}
      {action === 'reject' && (
        <TextActionModal title="Reject application" label="Reason" submitLabel="Reject" danger onClose={() => setAction(null)}
                         onSubmit={async (reason) => { await verificationApi.reject(request.id, reason); toast.success('Application rejected'); done() }} />
      )}
      {action === 'info' && (
        <TextActionModal title="Ask for changes" label="What should the applicant correct or add?" submitLabel="Send to the applicant"
                         onClose={() => setAction(null)}
                         onSubmit={async (message) => { await verificationApi.requestInfo(request.id, message); toast.success('Sent back to the applicant'); done() }} />
      )}
    </Card>
  )
}

export default function AdminVerificationPage() {
  const [filter, setFilter] = useState<Filter>('PENDING')
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const list = useAsync(
    () => verificationApi.list({ status: filter === 'ALL' ? undefined : filter, page, size: PAGE_SIZE }),
    [filter, page],
  )
  const selected = list.data?.items.find((r) => r.id === selectedId) ?? null

  return (
    <div className="page">
      <PageHeader title="Owner applications" subtitle="Check that each applicant really runs (or represents) the club they claim. Each club is verified on its own." />
      <Tabs tabs={FILTERS} active={filter} onChange={(f) => { setFilter(f); setPage(0); setSelectedId(null) }} />
      {list.error ? <ErrorMessage error={list.error} onRetry={list.reload} />
        : list.loading && !list.data ? <Loading />
        : !list.data?.items.length ? (
          <Card><EmptyState icon={<ShieldIcon size={32} />} title={`No ${filter === 'ALL' ? '' : humanize(filter).toLowerCase() + ' '}applications`}>
            {filter === 'PENDING' ? 'You\'re all caught up.' : undefined}
          </EmptyState></Card>
        ) : (
          <div className="admin-layout">
            <div className={selected ? 'admin-hide-mobile' : undefined}>
              <Card flush>
                <div className="list">
                  {list.data.items.map((r) => (
                    <button key={r.id} type="button" onClick={() => setSelectedId(r.id)}
                            aria-pressed={r.id === selectedId}
                            className={r.id === selectedId ? 'list-item request-item selected' : 'list-item request-item'}>
                      <div className="list-item-main">
                        <div className="bold truncate">{r.clubName}</div>
                        <div className="muted small truncate">{r.city ?? 'No city'} · {timeAgo(r.createdAt)}</div>
                      </div>
                      <StatusBadge status={r.status} />
                    </button>
                  ))}
                </div>
                <Pagination page={list.data.page} size={PAGE_SIZE} totalItems={list.data.totalItems} onChange={(p) => { setPage(p); setSelectedId(null) }} />
              </Card>
            </div>
            <div>
              {selected ? (
                <RequestDetail key={selected.id} request={selected} onChanged={list.reload} onBack={() => setSelectedId(null)} />
              ) : (
                <Card className="admin-hide-mobile"><EmptyState title="Select an application">Pick a request to see its details and proof.</EmptyState></Card>
              )}
            </div>
          </div>
        )}
    </div>
  )
}
