import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button, ErrorMessage, Field } from '../components/ui'
import { isEmailNotVerified, lockoutMinutes } from '../lib/authErrors'
import { AuthCard } from './account/AuthCard'
import { ResendVerification } from './account/ResendVerification'
import './account.css'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>()
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(undefined)
    try {
      await login(email.trim(), password)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : '/', { replace: true })
    } catch (err) {
      setError(err)
      setSubmitting(false)
    }
  }

  const locked = lockoutMinutes(error)
  return (
    <AuthCard subtitle="Run, ride and race together with your club."
              footer={<>New here? <Link to="/register">Create an account</Link></>}>
      <form className="form" onSubmit={submit}>
        <Field label="Email" htmlFor="login-email">
          <input id="login-email" className="input" type="email" autoComplete="email" required
                 value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password" htmlFor="login-password"
               hint={<Link to="/forgot-password" state={{ email }}>Forgot password?</Link>}>
          <input id="login-password" className="input" type="password" autoComplete="current-password" required
                 value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        {isEmailNotVerified(error) ? (
          <div className="alert alert-warning stack" role="alert">
            <div>Please verify your email address before logging in. We sent a link to <strong>{email.trim()}</strong>.</div>
            <div><ResendVerification email={email.trim()} /></div>
          </div>
        ) : locked !== null ? (
          <div className="alert alert-error" role="alert">
            Too many failed attempts. Try again in {locked} minute{locked === 1 ? '' : 's'}, or{' '}
            <Link to="/forgot-password" state={{ email }}>reset your password</Link>.
          </div>
        ) : (
          <ErrorMessage error={error} />
        )}
        <Button type="submit" block size="lg" loading={submitting}>Log in</Button>
      </form>
    </AuthCard>
  )
}
