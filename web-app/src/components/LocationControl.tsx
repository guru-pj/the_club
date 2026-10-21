import { useState } from 'react'
import { usersApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { currentPosition, savedLocation } from '../lib/location'
import { PinIcon } from './icons'
import { useToast } from './Toast'
import { Button } from './ui'

/**
 * Saves the device's approximate location on the profile (rounded to ~100 m by the server), used to show nearby
 * clubs and events. Asks for permission only when the button is pressed.
 */
export function LocationControl() {
  const { user, setUser } = useAuth()
  const toast = useToast()
  const [busy, setBusy] = useState<'locate' | 'clear'>()
  const [problem, setProblem] = useState<string>()
  if (!user) return null
  const saved = savedLocation(user)

  const locate = async () => {
    setBusy('locate')
    setProblem(undefined)
    try {
      const { latitude, longitude } = await currentPosition()
      setUser(await usersApi.update(user.id, { latitude, longitude }))
      toast.success('Location saved')
    } catch (e) {
      setProblem(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(undefined)
    }
  }

  const clear = async () => {
    setBusy('clear')
    try {
      setUser(await usersApi.update(user.id, { clearLocation: true }))
      toast.success('Location removed')
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <div className="stack" style={{ gap: 8 }}>
      <p className="small muted" style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
        <PinIcon size={14} style={{ flexShrink: 0, marginTop: 2 }} />
        <span>{saved ? 'Your approximate location is saved, so you see clubs and events near you.'
          : "No location saved: clubs near you can't be shown, only clubs in your city."}</span>
      </p>
      <div className="row">
        <Button size="sm" variant={saved ? 'secondary' : 'primary'} loading={busy === 'locate'} disabled={!!busy} onClick={() => void locate()}>
          {saved ? 'Update to my current location' : 'Use my current location'}
        </Button>
        {saved && <Button size="sm" variant="ghost" loading={busy === 'clear'} disabled={!!busy} onClick={() => void clear()}>Remove location</Button>}
      </div>
      {problem && <p className="small" role="alert">{problem}</p>}
    </div>
  )
}
