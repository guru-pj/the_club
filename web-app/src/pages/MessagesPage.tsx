import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { chatApi } from '../api/endpoints'
import type { BlockedUser, Conversation } from '../api/types'
import { useChat, useChatEvents } from '../components/chat/ChatContext'
import { ChatView } from '../components/chat/ChatView'
import { ChatIcon } from '../components/icons'
import { useToast } from '../components/Toast'
import { Avatar, Button, Card, EmptyState, ErrorMessage, Loading, Modal, PageHeader } from '../components/ui'
import { timeAgo } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import './messages.css'

function preview(c: Conversation): string {
  const m = c.lastMessage
  if (!m) return c.kind === 'GROUP' ? 'Club chat' : 'No messages yet'
  const who = c.kind === 'GROUP' && m.senderName ? `${m.senderName.split(' ')[0]}: ` : ''
  return who + (m.body ?? (m.photoUrl ? 'Photo' : ''))
}

function BlockedModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const toast = useToast()
  const blocked = useAsync<BlockedUser[]>(() => (open ? chatApi.blocked() : Promise.resolve([])), [open])
  return (
    <Modal open={open} title="Blocked people" onClose={onClose}>
      {blocked.loading ? <Loading /> : !blocked.data?.length ? <p className="muted">You haven't blocked anyone.</p> : (
        <div className="list">
          {blocked.data.map((b) => (
            <div key={b.userId} className="list-item">
              <Avatar name={b.name} url={b.photoUrl} />
              <div className="list-item-main bold">{b.name ?? 'Member'}</div>
              <Button size="sm" variant="secondary" onClick={async () => {
                try {
                  await chatApi.unblock(b.userId)
                  blocked.setData((list) => (list ?? []).filter((x) => x.userId !== b.userId))
                } catch (e) {
                  toast.error(e)
                }
              }}>Unblock</Button>
            </div>
          ))}
        </div>
      )}
      <p className="small muted" style={{ marginTop: 12 }}>Blocked people can't message you, and you don't see their messages in club chats.</p>
    </Modal>
  )
}

export default function MessagesPage() {
  const { conversationId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { unread } = useChat()
  const inbox = useAsync(() => chatApi.inbox(), [conversationId, unread])
  const [blockedOpen, setBlockedOpen] = useState(false)
  const [opening, setOpening] = useState<string>()

  useChatEvents(() => inbox.reload(), inbox.reload)

  const open = async (c: Conversation) => {
    if (c.id) return navigate(`/messages/${c.id}`)
    if (!c.clubId) return
    setOpening(c.clubId)
    try {
      const chat = await chatApi.club(c.clubId)
      if (chat.id) navigate(`/messages/${chat.id}`)
    } catch (e) {
      toast.error(e)
    } finally {
      setOpening(undefined)
    }
  }

  const list = (
    <Card flush className="inbox">
      {inbox.error ? <ErrorMessage error={inbox.error} onRetry={inbox.reload} />
        : !inbox.data ? <Loading />
        : inbox.data.length === 0 ? (
          <EmptyState icon={<ChatIcon size={28} />} title="No conversations yet">
            Subscribe to a club to join its chat. You can message people who are in one of your clubs from the club's Members tab.
          </EmptyState>
        ) : (
          <div className="list">
            {inbox.data.map((c) => (
              <button key={c.id ?? c.clubId} className={`list-item inbox-item${c.id && c.id === conversationId ? ' selected' : ''}`}
                      onClick={() => void open(c)} disabled={opening === c.clubId}>
                <Avatar name={c.title} url={c.imageUrl} size={40} />
                <div className="list-item-main">
                  <div className="row-between">
                    <span className={`truncate${c.unread ? ' bold' : ''}`}>{c.title ?? 'Member'}</span>
                    {c.lastMessageAt && <span className="small muted nowrap">{timeAgo(c.lastMessageAt)}</span>}
                  </div>
                  <div className="row-between">
                    <span className="small muted truncate">{preview(c)}</span>
                    {c.unread > 0 && <span className="dot-count inline">{c.unread > 99 ? '99+' : c.unread}</span>}
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}
    </Card>
  )

  return (
    <div className="page">
      <PageHeader title="Messages" subtitle="Your clubs' chats and direct messages"
                  actions={<Button size="sm" variant="ghost" onClick={() => setBlockedOpen(true)}>Blocked people</Button>} />
      <div className={`messages-layout${conversationId ? ' has-conversation' : ''}`}>
        <div className="messages-list">{list}</div>
        <div className="messages-conversation">
          {conversationId ? (
            <Card flush className="chat-card">
              <button className="back-link messages-back" onClick={() => navigate('/messages')}>‹ All conversations</button>
              <ChatView key={conversationId} conversationId={conversationId} />
            </Card>
          ) : (
            <Card className="messages-placeholder"><EmptyState icon={<ChatIcon size={28} />} title="Pick a conversation" /></Card>
          )}
        </div>
      </div>
      <BlockedModal open={blockedOpen} onClose={() => setBlockedOpen(false)} />
    </div>
  )
}
