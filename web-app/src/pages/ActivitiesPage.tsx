import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { activitiesApi, stravaApi } from '../api/endpoints'
import type { Activity } from '../api/types'
import { ActivityIcon, EditIcon, PlusIcon, RefreshIcon, TrashIcon } from '../components/icons'
import { useToast } from '../components/Toast'
import { Badge, Button, Card, EmptyState, ErrorMessage, Loading, Modal, PageHeader, Pagination, Stat, StatusBadge } from '../components/ui'
import { isNativeApp, webAppUrl } from '../lib/platform'
import { formatDate, formatDateTime, formatDistance, formatDuration, formatPace, humanize } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { ActivityForm } from './account/ActivityForm'
import { monthStart } from './account/monthStart'
import './account.css'

const PAGE_SIZE = 10
const PACE_TYPES = new Set(['RUN', 'WALK', 'HIKE'])

function ActivityRow({ activity, onEdit, onDelete }: { activity: Activity; onEdit: () => void; onDelete: () => void }) {
  const pace = PACE_TYPES.has(activity.type) ? formatPace(activity.distance, activity.duration) : null
  return (
    <div className="list-item">
      <span className="type-chip">{activity.type.slice(0, 4)}</span>
      <div className="list-item-main">
        <div className="row" style={{ gap: 8 }}>
          <span className="bold truncate">{activity.title}</span>
          {activity.source === 'STRAVA' && <StatusBadge status="STRAVA" />}
        </div>
        <div className="activity-meta muted small">
          <span>{humanize(activity.type)} · {formatDateTime(activity.startTime)}</span>
          <span>{formatDistance(activity.distance)}</span>
          <span>{formatDuration(activity.duration)}</span>
          {pace && <span>{pace}</span>}
          {activity.calories != null && <span>{activity.calories} kcal</span>}
        </div>
      </div>
      {activity.source === 'MANUAL' && (
        <div className="row" style={{ gap: 2 }}>
          <button className="icon-button" aria-label={`Edit ${activity.title}`} onClick={onEdit}><EditIcon size={18} /></button>
          <button className="icon-button" aria-label={`Delete ${activity.title}`} onClick={onDelete}><TrashIcon size={18} /></button>
        </div>
      )}
    </div>
  )
}

function StravaCard({ onSynced }: { onSynced: () => void }) {
  const toast = useToast()
  const { data, error, loading, reload } = useAsync(() => stravaApi.connection(), [])
  const [busy, setBusy] = useState<'connect' | 'sync' | 'disconnect'>()
  const [notConfigured, setNotConfigured] = useState(false)

  const connect = async () => {
    setBusy('connect')
    try {
      const { authorizeUrl } = await stravaApi.connect()
      window.location.assign(authorizeUrl)
    } catch (err) {
      if (err instanceof ApiError && err.status === 503) setNotConfigured(true)
      else toast.error(err)
      setBusy(undefined)
    }
  }

  const sync = async () => {
    setBusy('sync')
    try {
      const r = await stravaApi.sync()
      toast.success(`Imported ${r.imported}, updated ${r.updated}`)
      onSynced()
    } catch (err) {
      if (err instanceof ApiError && err.status === 503) setNotConfigured(true)
      else toast.error(err)
    } finally {
      setBusy(undefined)
    }
  }

  const disconnect = async () => {
    if (!window.confirm('Disconnect Strava? Activities already imported are kept.')) return
    setBusy('disconnect')
    try {
      await stravaApi.disconnect()
      toast.success('Strava disconnected')
      reload()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <Card title="Strava">
      {loading ? <Loading /> : error ? <ErrorMessage error={error} onRetry={reload} /> : data?.connected ? (
        <div className="stack">
          <div className="row"><Badge tone="success">Connected</Badge><span className="muted small">Athlete #{data.athleteId}</span></div>
          {data.connectedAt && <span className="muted small">Since {formatDate(data.connectedAt)}</span>}
          <p className="muted small">New Strava activities are imported automatically.</p>
          <Button variant="secondary" loading={busy === 'sync'} disabled={!!busy} onClick={sync}><RefreshIcon size={16} />Sync last 30 days</Button>
          <Button variant="ghost" loading={busy === 'disconnect'} disabled={!!busy} onClick={disconnect}>Disconnect</Button>
        </div>
      ) : (
        <div className="stack">
          <p className="muted small">Connect Strava to bring your runs and rides into The Club automatically.</p>
          {notConfigured
            ? <p className="muted small">Strava isn't configured on this server.</p>
            : isNativeApp
              // Strava redirects back to the web app, which must finish the connection in a signed-in browser
              ? <>
                  <p className="muted small">Connect Strava once on the website{webAppUrl && <> at <strong>{webAppUrl}</strong></>}, then come back here.</p>
                  <Button variant="secondary" onClick={reload}><RefreshIcon size={16} />Check again</Button>
                </>
              : <Button variant="accent" loading={busy === 'connect'} onClick={connect}>Connect with Strava</Button>}
        </div>
      )}
      {data?.connected && notConfigured && <p className="muted small" style={{ marginTop: 8 }}>Strava isn't configured on this server.</p>}
    </Card>
  )
}

function MonthSummary({ version }: { version: number }) {
  const { data, error, loading } = useAsync(() => activitiesApi.mine({ from: monthStart(), size: 100 }), [version])
  const totals = (data?.items ?? []).reduce(
    (acc, a) => ({ distance: acc.distance + a.distance, duration: acc.duration + a.duration }),
    { distance: 0, duration: 0 },
  )
  return (
    <Card title="This month">
      {loading && !data ? <Loading /> : error ? <ErrorMessage error={error} /> : (
        <div className="grid grid-3" style={{ gap: 12 }}>
          <Stat label="Distance" value={formatDistance(totals.distance)} />
          <Stat label="Time" value={formatDuration(totals.duration)} />
          <Stat label="Sessions" value={data?.totalItems ?? 0} />
        </div>
      )}
    </Card>
  )
}

export default function ActivitiesPage() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const [showForm, setShowForm] = useState(params.get('log') === '1')
  const [page, setPage] = useState(0)
  const [version, setVersion] = useState(0)
  const [editing, setEditing] = useState<Activity>()
  const { data, error, loading, reload } = useAsync(() => activitiesApi.mine({ page, size: PAGE_SIZE }), [page, version])

  const refresh = () => setVersion((v) => v + 1)

  const closeForm = () => {
    setShowForm(false)
    if (params.has('log')) {
      params.delete('log')
      setParams(params, { replace: true })
    }
  }

  const remove = async (a: Activity) => {
    if (!window.confirm(`Delete "${a.title}"?`)) return
    try {
      await activitiesApi.remove(a.id)
      toast.success('Activity deleted')
      refresh()
    } catch (err) {
      toast.error(err)
    }
  }

  return (
    <div className="page">
      <PageHeader title="Activities" subtitle="Your training log"
                  actions={!showForm && <Button onClick={() => setShowForm(true)}><PlusIcon size={18} />Log activity</Button>} />
      <div className="layout-sidebar">
        <div className="stack-lg">
          {showForm && (
            <Card title="Log activity">
              <ActivityForm submitLabel="Save activity" onCancel={closeForm} onSubmit={async (body) => {
                await activitiesApi.create(body)
                toast.success('Activity logged')
                closeForm()
                setPage(0)
                refresh()
              }} />
            </Card>
          )}
          <Card title="My activities" flush>
            {loading && !data ? <Loading /> : error ? <div style={{ padding: 20 }}><ErrorMessage error={error} onRetry={reload} /></div>
              : data && data.items.length === 0 ? (
                <EmptyState icon={<ActivityIcon size={32} />} title="No activities yet"
                            action={!showForm && <Button onClick={() => setShowForm(true)}>Log your first activity</Button>}>
                  Log a session manually or connect Strava.
                </EmptyState>
              ) : data && (
                <>
                  <div className="list" style={{ marginTop: 12 }}>
                    {data.items.map((a) => (
                      <ActivityRow key={a.id} activity={a} onEdit={() => setEditing(a)} onDelete={() => remove(a)} />
                    ))}
                  </div>
                  <Pagination page={page} size={PAGE_SIZE} totalItems={data.totalItems} onChange={setPage} />
                </>
              )}
          </Card>
        </div>
        <div className="stack-lg">
          <MonthSummary version={version} />
          <StravaCard onSynced={refresh} />
        </div>
      </div>
      <Modal open={!!editing} title="Edit activity" onClose={() => setEditing(undefined)}>
        {editing && (
          <ActivityForm key={editing.id} initial={editing} submitLabel="Save changes" onCancel={() => setEditing(undefined)}
                        onSubmit={async (body) => {
                          await activitiesApi.update(editing.id, body)
                          toast.success('Activity updated')
                          setEditing(undefined)
                          refresh()
                        }} />
        )}
      </Modal>
    </div>
  )
}
