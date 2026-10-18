import { useState } from 'react'
import { eventsApi } from '../../api/endpoints'
import type { ClubEvent, RegistrationStatus } from '../../api/types'
import { EditIcon, ScanIcon } from '../../components/icons'
import { useToast } from '../../components/Toast'
import { Badge, Button, Card, EmptyState, ErrorMessage, LinkButton, Loading, Modal, StatusBadge } from '../../components/ui'
import { formatDateTime, humanize } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'

const ORDER: RegistrationStatus[] = ['CHECKED_IN', 'CONFIRMED', 'PENDING_PAYMENT', 'CANCELLED']

export function OwnerPanel({ event, onChanged }: { event: ClubEvent; onChanged: () => void }) {
  const toast = useToast()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const regs = useAsync(() => eventsApi.registrations(event.id), [event.id])
  const cancelled = event.status === 'CANCELLED'

  async function cancelEvent() {
    setCancelling(true)
    try {
      await eventsApi.cancel(event.id)
      toast.success('Event cancelled. Registered members have been notified.')
      setConfirmCancel(false)
      onChanged()
    } catch (e) {
      toast.error(e)
    } finally {
      setCancelling(false)
    }
  }

  const counts = ORDER.map((s) => ({ status: s, n: regs.data?.filter((r) => r.status === s).length ?? 0 }))
    .filter((c) => c.n > 0)

  return (
    <Card title="Manage event">
      <div className="stack">
        <div className="row">
          {!cancelled && <LinkButton to={`/events/${event.id}/check-in`}><ScanIcon size={18} />Check-in scanner</LinkButton>}
          {!cancelled && <LinkButton variant="secondary" to={`/events/${event.id}/edit`}><EditIcon size={16} />Edit event</LinkButton>}
          <LinkButton variant="secondary" to={`/clubs/${event.clubId}?tab=feed`}>Post a recap</LinkButton>
          {!cancelled && <Button variant="danger" onClick={() => setConfirmCancel(true)}>Cancel event</Button>}
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
                  <tr><th>Member</th><th>Status</th><th>Registered</th><th>Checked in</th></tr>
                </thead>
                <tbody>
                  {regs.data.map((r) => (
                    <tr key={r.registrationId}>
                      <td>{r.name ?? 'Member'}</td>
                      <td><StatusBadge status={r.status} /></td>
                      <td className="muted">{formatDateTime(r.registeredAt)}</td>
                      <td className="muted">{r.checkedInAt ? formatDateTime(r.checkedInAt) : '—'}</td>
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
        {event.price > 0 && <p className="muted small" style={{ marginTop: 8 }}>Refunds for paid registrations must be handled outside the app.</p>}
      </Modal>
    </Card>
  )
}
