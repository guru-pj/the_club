import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button, ErrorMessage, Field } from '../components/ui'
import { Brand } from './account/Brand'
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

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <div className="card">
          <Brand />
          <p className="muted" style={{ marginBottom: 20 }}>Run, ride and race together with your club.</p>
          <form className="form" onSubmit={submit}>
            <Field label="Email" htmlFor="login-email">
              <input id="login-email" className="input" type="email" autoComplete="email" required
                     value={email} onChange={(e) => setEmail(e.target.value)} />
            </Field>
            <Field label="Password" htmlFor="login-password">
              <input id="login-password" className="input" type="password" autoComplete="current-password" required
                     value={password} onChange={(e) => setPassword(e.target.value)} />
            </Field>
            <ErrorMessage error={error} />
            <Button type="submit" block size="lg" loading={submitting}>Log in</Button>
          </form>
        </div>
        <p className="auth-footer muted">New here? <Link to="/register">Create an account</Link></p>
      </div>
    </div>
  )
}
