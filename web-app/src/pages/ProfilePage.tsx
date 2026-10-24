import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, mediaUrl } from '../api/client'
import { eventsApi, usersApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { ImagePicker } from '../components/ImagePicker'
import { LocationControl } from '../components/LocationControl'
import { useToast } from '../components/Toast'
import { Avatar, Badge, Button, Card, ErrorMessage, Field, Loading, PageHeader } from '../components/ui'
import { fieldError } from '../lib/fieldError'
import { ResendVerification } from './account/ResendVerification'
import { SecurityCard } from './account/SecurityCard'
import { formatDate, formatDateTime, humanize, nowLocal } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import './account.css'

/** The user's upcoming registered events (journey step 9: registrations are kept on the profile). */
function MyRegistrations() {
  const { data, error, loading, reload } = useAsync(() => eventsApi.myRegistrations(), [])
  const now = nowLocal()
  const upcoming = (data ?? [])
    .filter((r) => (r.status === 'CONFIRMED' || r.status === 'PENDING_PAYMENT') && r.eventStatus !== 'CANCELLED' && r.startTime >= now)
    .sort((a, b) => a.startTime.localeCompare(b.startTime))
    .slice(0, 5)
  return (
    <Card title="My upcoming events" actions={<Link to="/tickets" className="small">All tickets</Link>}>
      {loading && !data ? <Loading /> : error ? <ErrorMessage error={error} onRetry={reload} />
        : upcoming.length === 0 ? <p className="muted small">You haven't registered for any upcoming events.</p>
        : (
          <div className="list">
            {upcoming.map((r) => (
              <Link key={r.registrationId} className="list-item"
                    to={r.status === 'CONFIRMED' ? `/tickets/${r.registrationId}` : `/events/${r.eventId}`}>
                <span className="list-item-main">
                  <span className="bold truncate" style={{ display: 'block' }}>{r.eventTitle}</span>
                  <span className="muted small">{formatDateTime(r.startTime)}{r.location ? ` · ${r.location}` : ''}</span>
                </span>
                {r.status === 'PENDING_PAYMENT' ? <Badge tone="warning">Payment pending</Badge> : <Badge tone="success">Registered</Badge>}
              </Link>
            ))}
          </div>
        )}
    </Card>
  )
}

export default function ProfilePage() {
  const { user, setUser, logout } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [name, setName] = useState(user?.name ?? '')
  const [city, setCity] = useState(user?.city ?? '')
  const [bio, setBio] = useState(user?.bio ?? '')
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState(false)

  if (!user) return <Loading />

  const save = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(undefined)
    try {
      const updated = await usersApi.update(user.id, {
        name: name.trim(),
        city: city.trim(),
        bio: bio.trim(),
      })
      setUser(updated)
      toast.success('Profile saved')
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  const hasFieldErrors = error instanceof ApiError && Object.keys(error.fieldErrors).length > 0
  return (
    <div className="page page-narrow">
      <PageHeader title="Profile" />
      <Card>
        <div className="row" style={{ gap: 16 }}>
          <Avatar name={user.name} url={user.profilePicUrl} size={64} />
          <div className="stack" style={{ gap: 4, minWidth: 0 }}>
            <h2 className="truncate">{user.name}</h2>
            <span className="muted truncate">{user.email}</span>
            {user.emailVerified === false && (
              <div className="row" style={{ gap: 8 }}>
                <Badge tone="warning">Email not verified</Badge>
                {user.email && <ResendVerification email={user.email} label="Resend verification" />}
              </div>
            )}
            <div className="row" style={{ gap: 6 }}>
              {user.roles.map((r) => <Badge key={r} tone={r === 'ADMIN' ? 'danger' : r === 'CLUB_OWNER' ? 'accent' : 'primary'}>{humanize(r)}</Badge>)}
            </div>
            <span className="muted small">Member since {formatDate(user.createdAt)}</span>
          </div>
        </div>
      </Card>
      <Card title="Edit profile">
        <form className="form" onSubmit={save}>
          <Field label="Name" htmlFor="p-name" error={fieldError(error, 'name')}>
            <input id="p-name" className="input" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="City" htmlFor="p-city" error={fieldError(error, 'city')}>
            <input id="p-city" className="input" maxLength={100} value={city} onChange={(e) => setCity(e.target.value)} />
          </Field>
          <Field label="Bio" htmlFor="p-bio" error={fieldError(error, 'bio')}>
            <textarea id="p-bio" className="input" maxLength={255} value={bio} onChange={(e) => setBio(e.target.value)} />
          </Field>
          {!hasFieldErrors && <ErrorMessage error={error} />}
          <div><Button type="submit" loading={saving}>Save changes</Button></div>
        </form>
      </Card>
      <Card title="Profile picture">
        <ImagePicker label="Your photo" shape="circle" currentUrl={mediaUrl(user.profilePicUrl)}
                     onUpload={async (file) => { setUser(await usersApi.uploadPhoto(file)); toast.success('Photo updated') }}
                     onRemove={async () => { setUser(await usersApi.removePhoto()); toast.success('Photo removed') }} />
      </Card>
      <Card title="Location">
        <LocationControl />
      </Card>
      <MyRegistrations />
      <SecurityCard />
      <Card title="Connected apps">
        <p className="muted">Connect Strava to import your runs and rides automatically. <Link to="/activities">Manage on the Activities page</Link>.</p>
      </Card>
      <div style={{ marginTop: 16 }}>
        <Button variant="danger" onClick={async () => { await logout(); navigate('/login') }}>Log out</Button>
      </div>
    </div>
  )
}
