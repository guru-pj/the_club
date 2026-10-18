import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { accountApi, usersApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { Field, Loading } from '../components/ui'
import { AuthCard } from './account/AuthCard'
import { ResendVerification } from './account/ResendVerification'
import './account.css'

type State = 'verifying' | 'verified' | 'failed'

export default function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { user, setUser } = useAuth()
  const [state, setState] = useState<State>(token ? 'verifying' : 'failed')
  const [email, setEmail] = useState('')
  const started = useRef(false)

  useEffect(() => {
    // tokens are single-use: never submit twice (StrictMode runs effects twice in development)
    if (!token || started.current) return
    started.current = true
    accountApi.verifyEmail(token)
      .then(() => setState('verified'))
      .catch(() => setState('failed'))
  }, [token])

  // keep the signed-in profile's "not verified" badge in sync
  useEffect(() => {
    if (state === 'verified' && user) usersApi.me().then(setUser).catch(() => undefined)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state])

  if (state === 'verifying') {
    return <AuthCard subtitle="Verifying your email…"><Loading /></AuthCard>
  }
  if (state === 'verified') {
    return (
      <AuthCard>
        <div className="stack">
          <h2>Email verified</h2>
          <p>Thanks! Your email address is confirmed.</p>
          <Link to={user ? '/' : '/login'} className="btn btn-primary btn-block">{user ? 'Continue' : 'Log in'}</Link>
        </div>
      </AuthCard>
    )
  }
  return (
    <AuthCard footer={<Link to="/login">Back to log in</Link>}>
      <div className="stack">
        <h2>This link is invalid or has expired</h2>
        <p className="muted">Verification links expire after 48 hours and can be used once. Enter your email to get a new one.</p>
        <Field label="Email" htmlFor="verify-email">
          <input id="verify-email" className="input" type="email" autoComplete="email"
                 value={email || user?.email || ''} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <div><ResendVerification email={(email || user?.email || '').trim()} /></div>
      </div>
    </AuthCard>
  )
}
