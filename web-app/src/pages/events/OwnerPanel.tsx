import { useState } from 'react'
import { eventsApi } from '../../api/endpoints'
import type { ClubEvent, EventRegistrationRow, RegistrationStatus } from '../../api/types'
import { EditIcon, ScanIcon } from '../../components/icons'
import { useToast } from '../../components/Toast'
import { Badge, Button, Card, EmptyState, ErrorMessage, LinkButton, Loading, Modal, StatusBadge } from '../../components/ui'
import { formatDateTime, formatMoney, humanize } from '../../lib/format'
import { isPast } from './time'
import { useAsync } from '../../lib/useAsync'
import { RefundModal } from './RefundModal'

const ORDER: RegistrationStatus[] = ['CHECKED_IN', 'CONFIRMED', 'PENDING_PAYMENT', 'CANCELLED']

export function OwnerPanel({ event, onChanged }: { event: ClubEvent; onChanged: () => void }) {
  const toast = useToast()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [refunding, setRefunding] = useState<EventRegistrationRow | null>(null)
  const regs = useAsync(() => eventsApi.registrations(event.id), [event.id])
  const stats = useAsync(() => eventsApi.stats(event.id), [event.id, event.status, event.registeredCount])
  const [busy, setBusy] = useState<'publish' | 'complete'>()
  const cancelled = event.status === 'CANCELLED'
  const draft = event.status === 'DRAFT'
  const completed = event.status === 'COMPLETED'
  const started = isPast(event.startTime)

  async function change(action: 'publish' | 'complete') {
    setBusy(action)
    try {
      if (action === 'publish') {
        await eventsApi.publish(event.id)
        toast.success('Published. Followers and subscribers have been told about it.')
      } else {
        await eventsApi.complete(event.id)
        toast.success('Marked as completed. It now shows under past events.')
      }
      onChanged()
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(undefined)
    }
  }

  async function cancelEvent() {
    setCancelling(true)
    try {
      await eventsApi.cancel(event.id)
      toast.success(event.price > 0
        ? 'Event cancelled. Members have been notified and paid registrations are being refunded.'
        : 'Event cancelled. Registered members have been notified.')
      setConfirmCancel(false)
      onChanged()
    } catch (e) {
      toast.error(e)
    } finally {
      setCancelling(false)
    }
  }

  const paid = event.price > 0
  const canRefund = (r: EventRegistrationRow) => r.paymentId !== null && r.paymentStatus === 'SUCCEEDED'
    && r.status !== 'CHECKED_IN'

  const counts = ORDER.map((s) => ({ status: s, n: regs.data?.filter((r) => r.status === s).length ?? 0 }))
    .filter((c) => c.n > 0)

  return (
    <Card title="Manage event">
      <div className="stack">
        {draft && (
          <div className="alert alert-info" style={{ alignItems: 'center' }}>
            <div style={{ flex: 1 }}>
              <div className="bold">Draft: only you can see this event</div>
              <div className="small">Check the date, registration window, capacity and fee above, then publish it. Publishing tells
                the club's followers and subscribers, and registration opens at the time you set.</div>
            </div>
            <Button loading={busy === 'publish'} onClick={() => void change('publish')}>Publish event</Button>
          </div>
        )}
        {!draft && stats.data && (
          <div className="stats-row">
            <div className="stat"><span className="stat-label">Registered</span>
              <span className="stat-value">{stats.data.registered}{stats.data.capacity != null && <span className="muted small"> / {stats.data.capacity}</span>}</span></div>
            <div className="stat"><span className="stat-label">Checked in</span><span className="stat-value">{stats.data.checkedIn}</span></div>
            {stats.data.pendingPayment > 0 && <div className="stat"><span className="stat-label">Awaiting payment</span><span className="stat-value">{stats.data.pendingPayment}</span></div>}
            {stats.data.attendanceRate != null && <div className="stat"><span className="stat-label">Attendance</span><span className="stat-value">{Math.round(stats.data.attendanceRate * 100)}%</span></div>}
            {Object.entries(stats.data.revenue).map(([currency, amount]) => (
              <div key={currency} className="stat"><span className="stat-label">Collected</span><span className="stat-value">{formatMoney(amount, currency)}</span></div>
            ))}
          </div>
        )}
        <div className="row">
          {!cancelled && !draft && !completed && <LinkButton to={`/events/${event.id}/check-in`}><ScanIcon size={18} />Check-in scanner</LinkButton>}
          {!cancelled && !completed && <LinkButton variant="secondary" to={`/events/${event.id}/edit`}><EditIcon size={16} />Edit event</LinkButton>}
          {(started || completed) && !cancelled && !draft && (
            <LinkButton variant="secondary" to={`/clubs/${event.clubId}?recap=${event.id}`}>Write a recap</LinkButton>
          )}
          {started && event.status === 'SCHEDULED' && (
            <Button variant="secondary" loading={busy === 'complete'} onClick={() => void change('complete')}>Mark as completed</Button>
          )}
          {!cancelled && !completed && <Button variant="danger" onClick={() => setConfirmCancel(true)}>{draft ? 'Discard draft' : 'Cancel event'}</Button>}
        </div>

        <div className="row-between" style={{ marginTop: 8 }}>
          <h3>Registrations</h3>
          <div className="counts">
            {counts.map((c) => <Badge key={c.status} tone="neutral">{humanize(c.status)}: {c.n}</Badge>)}
          </div>
        </div>
        {regs.loading && !regs.data ? <Loading /> : regs.error ? <ErrorMessage error={regs.error} onRetry={regs.reload} /> :
          !regs.data?.length ? <EmptyState title="No registrations yet" /> : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Member</th><th>Status</th>{paid && <th>Payment</th>}<th>Registered</th><th>Checked in</th>
                    {paid && <th><span className="visually-hidden">Actions</span></th>}
                  </tr>
                </thead>
                <tbody>
                  {regs.data.map((r) => (
                    <tr key={r.registrationId}>
                      <td>{r.name ?? 'Member'}</td>
                      <td><StatusBadge status={r.status} /></td>
                      {paid && <td>{r.paymentStatus ? <StatusBadge status={r.paymentStatus} /> : <span className="muted">—</span>}</td>}
                      <td className="muted">{formatDateTime(r.registeredAt)}</td>
                      <td className="muted">{r.checkedInAt ? formatDateTime(r.checkedInAt) : '—'}</td>
                      {paid && (
                        <td>
                          {canRefund(r) && (
                            <Button size="sm" variant="secondary" onClick={() => setRefunding(r)}
                                    aria-label={`Refund ${r.name ?? 'member'}`}>Refund</Button>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </div>

      <Modal open={confirmCancel} title="Cancel this event?" onClose={() => setConfirmCancel(false)}
             footer={<>
               <Button variant="ghost" onClick={() => setConfirmCancel(false)} disabled={cancelling}>Keep event</Button>
               <Button variant="danger" loading={cancelling} onClick={cancelEvent}>Cancel event</Button>
             </>}>
        <p>Everyone registered for <strong>{event.title}</strong> will be notified. This can't be undone.</p>
        {paid && <p className="muted small" style={{ marginTop: 8 }}>Every paid registration that hasn't checked in is refunded automatically.</p>}
      </Modal>

      <RefundModal registration={refunding} onClose={() => setRefunding(null)}
                   onRefunded={() => { setRefunding(null); regs.reload(); onChanged() }} />
    </Card>
  )
}
