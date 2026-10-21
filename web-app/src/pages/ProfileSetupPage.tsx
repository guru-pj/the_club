import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, mediaUrl } from '../api/client'
import { usersApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { ImagePicker } from '../components/ImagePicker'
import { LocationControl } from '../components/LocationControl'
import { useToast } from '../components/Toast'
import { Button, Card, ErrorMessage, Field, Loading, PageHeader } from '../components/ui'
import { fieldError } from '../lib/fieldError'
import { afterProfileSetup } from '../lib/startPage'
import './account.css'

/**
 * Shown once after the first login (user journey step 2): a picture, a few details and a location so the app can
 * recommend nearby clubs and events. Everything is optional; "Skip for now" also counts as done.
 */
export default function ProfileSetupPage() {
  const { user, setUser } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [name, setName] = useState(user?.name ?? '')
  const [city, setCity] = useState(user?.city ?? '')
  const [bio, setBio] = useState(user?.bio ?? '')
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState<'finish' | 'skip'>()

  if (!user) return <Loading />

  const done = async (finish: boolean) => {
    setSaving(finish ? 'finish' : 'skip')
    setError(undefined)
    try {
      const updated = await usersApi.update(user.id, finish
        ? { name: name.trim(), city: city.trim(), bio: bio.trim(), profileComplete: true }
        : { profileComplete: true })
      setUser(updated)
      if (finish) toast.success('Profile ready')
      navigate(afterProfileSetup(updated), { replace: true })
    } catch (e) {
      setError(e)
    } finally {
      setSaving(undefined)
    }
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    void done(true)
  }

  const hasFieldErrors = error instanceof ApiError && Object.keys(error.fieldErrors).length > 0
  return (
    <div className="page page-narrow">
      <PageHeader title={`Welcome, ${user.name.split(' ')[0]}!`}
                  subtitle="Set up your profile so clubs know who you are and we can show clubs and events near you." />
      <form className="stack-lg" onSubmit={submit}>
        <Card title="1. Profile picture">
          <ImagePicker label="Your photo" shape="circle" currentUrl={mediaUrl(user.profilePicUrl)}
                       onUpload={async (file) => setUser(await usersApi.uploadPhoto(file))}
                       onRemove={async () => setUser(await usersApi.removePhoto())} />
        </Card>
        <Card title="2. About you">
          <div className="form">
            <Field label="Name" htmlFor="s-name" error={fieldError(error, 'name')}>
              <input id="s-name" className="input" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field label="City" htmlFor="s-city" error={fieldError(error, 'city')} hint="Used when your location isn't available">
              <input id="s-city" className="input" maxLength={100} autoComplete="address-level2" value={city}
                     onChange={(e) => setCity(e.target.value)} />
            </Field>
            <Field label="About you" htmlFor="s-bio" error={fieldError(error, 'bio')} hint="Your sports, your pace, what you're training for">
              <textarea id="s-bio" className="input" maxLength={255} value={bio} onChange={(e) => setBio(e.target.value)} />
            </Field>
          </div>
        </Card>
        <Card title="3. Your location">
          <LocationControl />
        </Card>
        {!hasFieldErrors && <ErrorMessage error={error} />}
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <Button variant="ghost" loading={saving === 'skip'} disabled={!!saving} onClick={() => void done(false)}>Skip for now</Button>
          <Button type="submit" loading={saving === 'finish'} disabled={!!saving || !name.trim()}>Finish and find clubs</Button>
        </div>
      </form>
    </div>
  )
}
