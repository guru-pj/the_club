import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { accountApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { Button, ErrorMessage, Field } from '../components/ui'
import { PASSWORD_HINT } from '../lib/authErrors'
import { fieldError } from '../lib/fieldError'
import { AuthCard } from './account/AuthCard'
import './account.css'

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { user, forgetSession } = useAuth()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [mismatch, setMismatch] = useState(false)
  const [error, setError] = useState<unknown>()
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (password !== confirm) {
      setMismatch(true)
      return
    }
    setMismatch(false)
    setSubmitting(true)
    setError(undefined)
    try {
      await accountApi.resetPassword(token, password)
      // the reset revoked every session, including this browser's
      if (user) forgetSession()
      setDone(true)
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const invalidLink = !token
    || (error instanceof ApiError && error.status === 400 && Object.keys(error.fieldErrors).length === 0)
  if (done) {
    return (
      <AuthCard>
        <div className="stack">
          <h2>Password updated</h2>
          <p>Your password has been changed and you've been signed out on all devices.</p>
          <Link to="/login" className="btn btn-primary btn-block">Log in</Link>
        </div>
      </AuthCard>
    )
  }
  if (invalidLink) {
    return (
      <AuthCard>
        <div className="stack">
          <h2>This link is invalid or has expired</h2>
          <p className="muted">Reset links work once and expire after 30 minutes.</p>
          <Link to="/forgot-password" className="btn btn-primary btn-block">Request a new link</Link>
        </div>
      </AuthCard>
    )
  }
  const newPasswordError = fieldError(error, 'newPassword')
  return (
    <AuthCard subtitle="Choose a new password." footer={<Link to="/login">Back to log in</Link>}>
      <form className="form" onSubmit={submit}>
        <Field label="New password" htmlFor="reset-password" error={newPasswordError} hint={PASSWORD_HINT}>
          <input id="reset-password" className="input" type="password" autoComplete="new-password" required
                 minLength={8} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label="Confirm new password" htmlFor="reset-confirm" error={mismatch ? 'Passwords do not match' : undefined}>
          <input id="reset-confirm" className="input" type="password" autoComplete="new-password" required
                 value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        {!newPasswordError && <ErrorMessage error={error} />}
        <Button type="submit" block size="lg" loading={submitting}>Set new password</Button>
      </form>
    </AuthCard>
  )
}
