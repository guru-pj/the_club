import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { accountApi } from '../api/endpoints'
import { Button, ErrorMessage, Field } from '../components/ui'
import { AuthCard } from './account/AuthCard'
import './account.css'

export default function ForgotPasswordPage() {
  const location = useLocation()
  const [email, setEmail] = useState((location.state as { email?: string } | null)?.email ?? '')
  const [error, setError] = useState<unknown>()
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(undefined)
    try {
      await accountApi.requestPasswordReset(email.trim())
      setSent(true)
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const footer = <>Remembered it? <Link to="/login">Log in</Link></>
  if (sent) {
    return (
      <AuthCard footer={footer}>
        <div className="stack">
          <h2>Check your email</h2>
          <p>If an account exists for <strong>{email.trim()}</strong>, we've emailed a link to reset your password.
            The link expires in 30 minutes.</p>
          <p className="muted small">No email? Check your spam folder, or <button type="button" className="link-button"
            onClick={() => setSent(false)}>try another address</button>.</p>
        </div>
      </AuthCard>
    )
  }
  return (
    <AuthCard subtitle="Enter your email and we'll send you a link to choose a new password." footer={footer}>
      <form className="form" onSubmit={submit}>
        <Field label="Email" htmlFor="forgot-email">
          <input id="forgot-email" className="input" type="email" autoComplete="email" required
                 value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <ErrorMessage error={error} />
        <Button type="submit" block size="lg" loading={submitting}>Send reset link</Button>
      </form>
    </AuthCard>
  )
}
