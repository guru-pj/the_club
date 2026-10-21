import { useState } from 'react'
import { Link } from 'react-router-dom'
import { mediaUrl } from '../api/client'
import { chatApi } from '../api/endpoints'
import type { ChatReport, ChatReportStatus } from '../api/types'
import { useToast } from '../components/Toast'
import { Badge, Button, Card, EmptyState, ErrorMessage, Loading, PageHeader, Pagination, Tabs } from '../components/ui'
import { formatDateTime } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import './messages.css'

const PAGE_SIZE = 20
const TABS: { id: ChatReportStatus; label: string }[] = [
  { id: 'OPEN', label: 'Open' },
  { id: 'REMOVED', label: 'Removed' },
  { id: 'DISMISSED', label: 'Kept' },
]

function ReportCard({ report, onResolved }: { report: ChatReport; onResolved: () => void }) {
  const toast = useToast()
  const [busy, setBusy] = useState<'remove' | 'keep'>()
  const m = report.message
  const resolve = async (removeMessage: boolean) => {
    setBusy(removeMessage ? 'remove' : 'keep')
    try {
      await chatApi.resolveReport(report.id, removeMessage)
      toast.success(removeMessage ? 'Message removed' : 'Report closed; the message stays')
      onResolved()
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(undefined)
    }
  }
  return (
    <Card>
      <div className="stack">
        <div className="row-between">
          <div className="row">
            <Badge tone={report.kind === 'GROUP' ? 'primary' : 'info'}>{report.kind === 'GROUP' ? 'Club chat' : 'Direct message'}</Badge>
            {report.clubId && <Link to={`/clubs/${report.clubId}`}>{report.clubName}</Link>}
          </div>
          <span className="small muted">{formatDateTime(report.createdAt)}</span>
        </div>
        <blockquote className="chat-quote">
          <div className="small bold">{m?.senderName ?? 'Member'}{m?.deleted && <span className="muted"> · deleted</span>}</div>
          {m?.photoUrl && <img className="chat-photo" src={mediaUrl(m.photoUrl)} alt="" />}
          {m?.body && <p className="pre-wrap">{m.body}</p>}
        </blockquote>
        <p className="small">
          Reported by <strong>{report.reporterName ?? 'a member'}</strong>{report.reason ? `: “${report.reason}”` : ''}
        </p>
        {report.status === 'OPEN' && (
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" loading={busy === 'keep'} disabled={!!busy} onClick={() => void resolve(false)}>Keep message</Button>
            <Button variant="danger" loading={busy === 'remove'} disabled={!!busy} onClick={() => void resolve(true)}>Remove message</Button>
          </div>
        )}
      </div>
    </Card>
  )
}

export default function AdminChatReportsPage() {
  const [status, setStatus] = useState<ChatReportStatus>('OPEN')
  const [page, setPage] = useState(0)
  const reports = useAsync(() => chatApi.reports({ status, page, size: PAGE_SIZE }), [status, page])
  return (
    <div className="page page-narrow">
      <PageHeader title="Reported messages" subtitle="Chat messages people reported. Removing one hides it for everyone." />
      <Tabs tabs={TABS} active={status} onChange={(s) => { setStatus(s); setPage(0) }} />
      {reports.error ? <ErrorMessage error={reports.error} onRetry={reports.reload} />
        : !reports.data ? <Loading />
        : reports.data.items.length === 0 ? <Card><EmptyState title={status === 'OPEN' ? 'Nothing to review' : 'None yet'} /></Card>
        : (
          <div className="stack">
            {reports.data.items.map((r) => <ReportCard key={r.id} report={r} onResolved={reports.reload} />)}
            <Pagination page={page} size={PAGE_SIZE} totalItems={reports.data.totalItems} onChange={setPage} />
          </div>
        )}
    </div>
  )
}
