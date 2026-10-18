import { useCallback, useRef, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { clubsApi, eventsApi } from '../api/endpoints'
import type { CheckInResponse } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CheckIcon, CloseIcon, ScanIcon } from '../components/icons'
import { Button, Card, EmptyState, ErrorMessage, Field, LinkButton, Loading, PageHeader, Stat } from '../components/ui'
import { formatDateTime } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { canManage } from './events/shared'
import { useQrScanner } from './events/useQrScanner'
import './events.css'

type Result =
  | { ok: true; checkIn: CheckInResponse }
  | { ok: false; code: string; message: string }

const SCANNER_ID = 'checkin-scanner'
const REPEAT_WINDOW_MS = 5000

function shortCode(code: string) {
  const raw = code.replace(/^THECLUB-TICKET:/, '')
  return raw.length > 10 ? `${raw.slice(0, 6)}…${raw.slice(-4)}` : raw
}

export default function CheckInPage() {
  const { eventId = '' } = useParams()
  const { hasRole } = useAuth()
  const page = useAsync(async () => {
    const event = await eventsApi.get(eventId)
    return { event, club: await clubsApi.get(event.clubId) }
  }, [eventId])
  const allowed = !!page.data && canManage(page.data.club, hasRole('ADMIN'))
  const regs = useAsync(() => (allowed ? eventsApi.registrations(eventId) : Promise.resolve([])), [eventId, allowed])
  const reloadRegs = regs.reload

  const [result, setResult] = useState<Result>()
  const [recent, setRecent] = useState<CheckInResponse[]>([])
  const [manualCode, setManualCode] = useState('')
  const [busy, setBusy] = useState(false)
  const inFlight = useRef(false)
  const lastScan = useRef<{ code: string; at: number }>({ code: '', at: 0 })

  const checkIn = useCallback(async (code: string) => {
    const trimmed = code.trim()
    if (!trimmed || inFlight.current) return
    inFlight.current = true
    setBusy(true)
    try {
      const checkIn = await eventsApi.checkIn(eventId, trimmed)
      setResult({ ok: true, checkIn })
      setRecent((all) => [checkIn, ...all].slice(0, 20))
      reloadRegs()
      navigator.vibrate?.(80)
    } catch (e) {
      setResult({ ok: false, code: trimmed, message: e instanceof Error ? e.message : String(e) })
      navigator.vibrate?.([60, 60, 60])
    } finally {
      inFlight.current = false
      setBusy(false)
    }
  }, [eventId, reloadRegs])

  const onScan = useCallback((text: string) => {
    const now = Date.now()
    if (inFlight.current) return
    if (text === lastScan.current.code && now - lastScan.current.at < REPEAT_WINDOW_MS) return
    lastScan.current = { code: text, at: now }
    void checkIn(text)
  }, [checkIn])

  const scanner = useQrScanner(SCANNER_ID, onScan)

  function submitManual(e: FormEvent) {
    e.preventDefault()
    void checkIn(manualCode).then(() => setManualCode(''))
  }

  if (page.loading && !page.data) return <Loading />
  if (page.error) return <div className="page"><ErrorMessage error={page.error} onRetry={page.reload} /></div>
  if (!page.data) return null
  const { event, club } = page.data
  if (!allowed) {
    return (
      <div className="page">
        <EmptyState title="Only the club owner can check members in"
                    action={<LinkButton variant="secondary" to={`/events/${event.id}`}>Back to event</LinkButton>} />
      </div>
    )
  }

  const checkedIn = regs.data?.filter((r) => r.status === 'CHECKED_IN').length ?? 0
  const expected = checkedIn + (regs.data?.filter((r) => r.status === 'CONFIRMED').length ?? 0)
  const scanning = scanner.state === 'scanning' || scanner.state === 'starting'

  return (
    <div className="page">
      <PageHeader back={{ to: `/events/${event.id}`, label: event.title }} title="Check-in"
                  subtitle={`${event.title} · ${formatDateTime(event.startTime)} · ${club.name}`} />
      <div className="layout-sidebar">
        <div className="stack-lg">
          <Card title="Scan tickets" actions={scanning
            ? <Button variant="secondary" onClick={() => void scanner.stop()}>Stop camera</Button>
            : <Button onClick={() => void scanner.start()} loading={scanner.state === 'starting'}><ScanIcon size={18} />Start camera</Button>}>
            <div className="stack">
              <div id={SCANNER_ID} className="scanner-box" />
              {!scanning && !scanner.error && (
                <p className="muted small">Point the camera at a member's QR ticket. Check-in opens 6 hours before the start and closes 12 hours after.</p>
              )}
              {scanner.error && <div className="alert alert-warning">{scanner.error}</div>}
              <form className="row" onSubmit={submitManual} style={{ alignItems: 'flex-end' }}>
                <div style={{ flex: 1, minWidth: 200 }}>
                  <Field label="Ticket code" htmlFor="ticket-code">
                    <input id="ticket-code" className="input mono" value={manualCode} autoComplete="off"
                           onChange={(e) => setManualCode(e.target.value)} placeholder="Type or paste the code from the ticket" />
                  </Field>
                </div>
                <Button type="submit" variant="secondary" loading={busy} disabled={!manualCode.trim()}>Check in</Button>
              </form>
            </div>
          </Card>

          {result && (
            result.ok ? (
              <div className="scan-result scan-result-success" role="status">
                <span className="icon-circle"><CheckIcon size={20} /></span>
                <div>
                  <div className="bold">{result.checkIn.name ?? 'Member'} checked in</div>
                  <div className="small">+{result.checkIn.pointsAwarded} points · {formatDateTime(result.checkIn.checkedInAt)}</div>
                </div>
              </div>
            ) : (
              <div className="scan-result scan-result-error" role="alert">
                <span className="icon-circle"><CloseIcon size={20} /></span>
                <div>
                  <div className="bold">Not checked in</div>
                  <div className="small">{result.message}</div>
                  <div className="tiny mono">Ticket {shortCode(result.code)}</div>
                </div>
              </div>
            )
          )}
        </div>

        <div className="stack-lg">
          <Card>
            <div className="grid grid-2">
              <Stat label="Checked in" value={checkedIn} />
              <Stat label="Expected" value={expected} />
            </div>
            {regs.error && <div style={{ marginTop: 12 }}><ErrorMessage error={regs.error} onRetry={regs.reload} /></div>}
          </Card>
          <Card title="This session" flush>
            {recent.length === 0 ? (
              <EmptyState title="No check-ins yet">Scanned members will appear here.</EmptyState>
            ) : (
              <div className="list">
                {recent.map((r) => (
                  <div key={r.registrationId} className="list-item">
                    <CheckIcon size={18} style={{ color: 'var(--success)' }} />
                    <div className="list-item-main">
                      <div className="bold truncate">{r.name ?? 'Member'}</div>
                      <div className="muted tiny">{formatDateTime(r.checkedInAt)}</div>
                    </div>
                    <span className="badge badge-accent">+{r.pointsAwarded}</span>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}
