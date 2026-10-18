import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { usersApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { Avatar, Badge, Button, Card, ErrorMessage, Field, Loading, PageHeader } from '../components/ui'
import { fieldError } from '../lib/fieldError'
import { formatDate, humanize } from '../lib/format'
import './account.css'

export default function ProfilePage() {
  const { user, setUser, logout } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [name, setName] = useState(user?.name ?? '')
  const [city, setCity] = useState(user?.city ?? '')
  const [bio, setBio] = useState(user?.bio ?? '')
  const [profilePicUrl, setProfilePicUrl] = useState(user?.profilePicUrl ?? '')
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
        // the API rejects an empty string as a non-URL; omit it to keep the current picture
        profilePicUrl: profilePicUrl.trim() || undefined,
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
          <Field label="Profile picture URL" htmlFor="p-pic" error={fieldError(error, 'profilePicUrl')} hint="A link to an image (https://…)">
            <input id="p-pic" className="input" type="url" maxLength={500} value={profilePicUrl}
                   onChange={(e) => setProfilePicUrl(e.target.value)} />
          </Field>
          {!hasFieldErrors && <ErrorMessage error={error} />}
          <div><Button type="submit" loading={saving}>Save changes</Button></div>
        </form>
      </Card>
      <Card title="Connected apps">
        <p className="muted">Connect Strava to import your runs and rides automatically. <Link to="/activities">Manage on the Activities page</Link>.</p>
      </Card>
      <div style={{ marginTop: 16 }}>
        <Button variant="danger" onClick={async () => { await logout(); navigate('/login') }}>Log out</Button>
      </div>
    </div>
  )
}
