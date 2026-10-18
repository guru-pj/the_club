import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { stravaApi } from '../api/endpoints'
import { useToast } from '../components/Toast'
import { EmptyState, ErrorMessage, LinkButton, Spinner } from '../components/ui'

export default function StravaCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const toast = useToast()
  const started = useRef(false)
  const code = params.get('code')
  const state = params.get('state')
  const denied = params.get('error')
  const [error, setError] = useState<unknown>(
    denied ? new Error('Strava access was not granted.') : !code || !state ? new Error('The Strava response was incomplete.') : undefined,
  )

  useEffect(() => {
    if (started.current || denied || !code || !state) return
    started.current = true
    stravaApi.callback(code, state)
      .then(() => {
        toast.success('Strava connected')
        navigate('/activities', { replace: true })
      })
      .catch(setError)
  }, [code, state, denied, navigate, toast])

  return (
    <div className="page page-narrow">
      {error ? (
        <div className="stack">
          <ErrorMessage error={error} />
          <LinkButton to="/activities" variant="secondary">Back to activities</LinkButton>
        </div>
      ) : (
        <EmptyState icon={<Spinner />} title="Connecting your Strava account…" />
      )}
    </div>
  )
}
