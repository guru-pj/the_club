import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { accountApi } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Button, Card, ErrorMessage, Field, Modal } from '../../components/ui'
import { PASSWORD_HINT } from '../../lib/authErrors'
import { fieldError } from '../../lib/fieldError'

/** Change password and "sign out everywhere"; both end every session, so the user logs in again. */
export function SecurityCard() {
  const { forgetSession, logoutEverywhere } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [mismatch, setMismatch] = useState(false)
  const [error, setError] = useState<unknown>()
  const [saving, setSaving] = useState(false)
  const [confirmSignOut, setConfirmSignOut] = useState(false)
  const [signingOut, setSigningOut] = useState(false)

  const changePassword = async (e: FormEvent) => {
    e.preventDefault()
    if (next !== confirm) {
      setMismatch(true)
      return
    }
    setMismatch(false)
    setSaving(true)
    setError(undefined)
    try {
      await accountApi.changePassword(current, next)
      toast.success('Password changed — please log in again')
      forgetSession()
      navigate('/login', { replace: true })
    } catch (err) {
      setError(err)
      setSaving(false)
    }
  }

  const signOutEverywhere = async () => {
    setSigningOut(true)
    try {
      await logoutEverywhere()
      toast.success('Signed out of all devices')
      navigate('/login', { replace: true })
    } catch (err) {
      toast.error(err)
      setSigningOut(false)
    }
  }

  const currentError = fieldError(error, 'currentPassword')
  const nextError = fieldError(error, 'newPassword')
  return (
    <Card title="Security">
      <form className="form" onSubmit={changePassword}>
        <h3>Change password</h3>
        <Field label="Current password" htmlFor="sec-current" error={currentError}>
          <input id="sec-current" className="input" type="password" autoComplete="current-password" required
                 value={current} onChange={(e) => setCurrent(e.target.value)} />
        </Field>
        <Field label="New password" htmlFor="sec-new" error={nextError} hint={PASSWORD_HINT}>
          <input id="sec-new" className="input" type="password" autoComplete="new-password" required
                 minLength={8} maxLength={72} value={next} onChange={(e) => setNext(e.target.value)} />
        </Field>
        <Field label="Confirm new password" htmlFor="sec-confirm" error={mismatch ? 'Passwords do not match' : undefined}>
          <input id="sec-confirm" className="input" type="password" autoComplete="new-password" required
                 value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        {!currentError && !nextError && <ErrorMessage error={error} />}
        <p className="muted small">Changing your password signs you out on every device.</p>
        <div><Button type="submit" loading={saving}>Change password</Button></div>
      </form>
      <hr className="divider" />
      <div className="row-between">
        <div>
          <h3>Sessions</h3>
          <p className="muted small">Lost a phone or used a shared computer? End every session at once.</p>
        </div>
        <Button variant="secondary" onClick={() => setConfirmSignOut(true)}>Sign out of all devices</Button>
      </div>
      <Modal open={confirmSignOut} title="Sign out of all devices?" onClose={() => setConfirmSignOut(false)}
             footer={<>
               <Button variant="ghost" onClick={() => setConfirmSignOut(false)}>Cancel</Button>
               <Button variant="danger" loading={signingOut} onClick={signOutEverywhere}>Sign out everywhere</Button>
             </>}>
        You'll be signed out here and on every other device, and will need to log in again.
      </Modal>
    </Card>
  )
}
