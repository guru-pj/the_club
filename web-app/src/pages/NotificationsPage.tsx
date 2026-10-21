import { useState } from 'react'
import { notificationsApi } from '../api/endpoints'
import type { AppNotification } from '../api/types'
import { BellIcon, CalendarIcon, ChatIcon, CloseIcon, HeartIcon, RepeatIcon, ShieldIcon, UsersIcon } from '../components/icons'
import { useToast } from '../components/Toast'
import { Button, Card, EmptyState, ErrorMessage, Loading, PageHeader, Pagination } from '../components/ui'
import { timeAgo } from '../lib/format'
import { notificationsChanged } from '../lib/notificationsBus'
import { useAsync } from '../lib/useAsync'
import './account.css'

const PAGE_SIZE = 20

function TypeIcon({ type }: { type: string }) {
  switch (type) {
    case 'CLUB_POST':
    case 'CLUB_MEMBER_POST': return <UsersIcon size={18} />
    case 'POST_COMMENT': return <HeartIcon size={18} />
    case 'POST_RESHARED': return <RepeatIcon size={18} />
    case 'CHAT_MESSAGE': return <ChatIcon size={18} />
    case 'EVENT_REGISTRATION_CONFIRMED': return <CalendarIcon size={18} />
    case 'OWNER_VERIFICATION': return <ShieldIcon size={18} />
    case 'EVENT_CANCELLED': return <CloseIcon size={18} />
    default: return <BellIcon size={18} />
  }
}

export default function NotificationsPage() {
  const toast = useToast()
  const [page, setPage] = useState(0)
  const { data, error, loading, reload, setData } = useAsync(() => notificationsApi.mine({ page, size: PAGE_SIZE }), [page])
  const [markingAll, setMarkingAll] = useState(false)

  const markRead = async (n: AppNotification) => {
    if (n.isRead) return
    setData((prev) => ({ ...prev!, items: prev!.items.map((i) => (i.id === n.id ? { ...i, isRead: true } : i)) }))
    try {
      await notificationsApi.markRead(n.id)
      notificationsChanged()
    } catch (err) {
      toast.error(err)
      reload()
    }
  }

  const markAll = async () => {
    setMarkingAll(true)
    try {
      await notificationsApi.markAllRead()
      setData((prev) => ({ ...prev!, items: prev!.items.map((i) => ({ ...i, isRead: true })) }))
      notificationsChanged()
    } catch (err) {
      toast.error(err)
    } finally {
      setMarkingAll(false)
    }
  }

  const hasUnread = data?.items.some((n) => !n.isRead)
  return (
    <div className="page page-narrow">
      <PageHeader title="Notifications"
                  actions={hasUnread && <Button variant="secondary" size="sm" loading={markingAll} onClick={markAll}>Mark all as read</Button>} />
      {loading && !data ? <Loading /> : error ? <ErrorMessage error={error} onRetry={reload} /> : data && data.items.length === 0 ? (
        <Card><EmptyState icon={<BellIcon size={32} />} title="You're all caught up">Club posts, event confirmations and updates will show up here.</EmptyState></Card>
      ) : data && (
        <Card flush>
          <div className="list">
            {data.items.map((n) => (
              <button key={n.id} className={`list-item notification-row${n.isRead ? '' : ' unread'}`}
                      onClick={() => markRead(n)} aria-label={`${n.isRead ? '' : 'Unread: '}${n.message}`}>
                <span className="notification-icon"><TypeIcon type={n.type} /></span>
                <span className="list-item-main">
                  <span className={n.isRead ? '' : 'bold'} style={{ display: 'block' }}>{n.message}</span>
                  <span className="muted tiny">{timeAgo(n.createdAt)}</span>
                </span>
                {!n.isRead && <span className="unread-dot" aria-hidden="true" />}
              </button>
            ))}
          </div>
          <Pagination page={page} size={PAGE_SIZE} totalItems={data.totalItems} onChange={setPage} />
        </Card>
      )}
    </div>
  )
}
