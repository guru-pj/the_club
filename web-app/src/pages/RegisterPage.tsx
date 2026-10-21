import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Button, ErrorMessage, Field } from '../components/ui'
import { isEmailNotVerified, PASSWORD_HINT } from '../lib/authErrors'
import { fieldError } from '../lib/fieldError'
import { startPage } from '../lib/startPage'
import { AuthCard } from './account/AuthCard'
import { ResendVerification } from './account/ResendVerification'
import './account.css'

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [clubOwner, setClubOwner] = useState(params.get('as') === 'owner')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<unknown>()
  const [mismatch, setMismatch] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [awaitingVerification, setAwaitingVerification] = useState(false)

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
      const me = await register(name.trim(), email.trim(), password, clubOwner)
      navigate(startPage(me), { replace: true })
    } catch (err) {
      // The account exists now; logging in just has to wait for the emailed link.
      if (isEmailNotVerified(err)) setAwaitingVerification(true)
      else setError(err)
      setSubmitting(false)
    }
  }

  if (awaitingVerification) {
    return (
      <AuthCard footer={<>Already verified? <Link to="/login">Log in</Link></>}>
        <div className="stack">
          <h2>Check your inbox</h2>
          <p>We sent a verification link to <strong>{email.trim()}</strong>. Open it to activate your account, then log in.</p>
          {clubOwner && <p className="small">After you log in, tell us about your club. Our admin team verifies each club before you can manage it.</p>}
          <p className="muted small">Didn't get it? Check your spam folder or send it again.</p>
          <div><ResendVerification email={email.trim()} /></div>
        </div>
      </AuthCard>
    )
  }

  const hasFieldErrors = error instanceof ApiError && Object.keys(error.fieldErrors).length > 0
  return (
    <AuthCard subtitle={clubOwner ? 'Create your account, then apply to manage your club.' : 'Create your account to join clubs and events.'}
              footer={<>Already a member? <Link to="/login">Log in</Link></>}>
      <form className="form" onSubmit={submit}>
        <div className="segmented" role="radiogroup" aria-label="Account type">
          <button type="button" role="radio" aria-checked={!clubOwner} className={!clubOwner ? 'active' : undefined}
                  onClick={() => setClubOwner(false)}>I want to join clubs</button>
          <button type="button" role="radio" aria-checked={clubOwner} className={clubOwner ? 'active' : undefined}
                  onClick={() => setClubOwner(true)}>I run a club</button>
        </div>
        {clubOwner && (
          <p className="small muted">
            You'll sign up and verify your email as usual. Then you apply for your club with its details and proof that you run
            it; an admin approves each club before its owner tools open. You can join other clubs too.
          </p>
        )}
        <Field label="Name" htmlFor="reg-name" error={fieldError(error, 'name')}>
          <input id="reg-name" className="input" autoComplete="name" required maxLength={100}
                 value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Email" htmlFor="reg-email" error={fieldError(error, 'email')}>
          <input id="reg-email" className="input" type="email" autoComplete="email" required
                 value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password" htmlFor="reg-password" error={fieldError(error, 'password')} hint={PASSWORD_HINT}>
          <input id="reg-password" className="input" type="password" autoComplete="new-password" required
                 minLength={8} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label="Confirm password" htmlFor="reg-confirm" error={mismatch ? 'Passwords do not match' : undefined}>
          <input id="reg-confirm" className="input" type="password" autoComplete="new-password" required
                 value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        {!hasFieldErrors && <ErrorMessage error={error} />}
        <Button type="submit" block size="lg" loading={submitting}>Create account</Button>
      </form>
    </AuthCard>
  )
}
