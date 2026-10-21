import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { mediaUrl } from '../../api/client'
import { chatApi } from '../../api/endpoints'
import type { ChatMessage, Conversation } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmModal } from '../../pages/clubs/ConfirmModal'
import { mergeMessages, sameGroup } from '../../lib/chatMessages'
import { formatDateTime } from '../../lib/format'
import { ACCEPTED_IMAGE_TYPES, imageProblem } from '../../lib/images'
import { ActionMenu, type MenuAction } from '../ActionMenu'
import { ImageIcon, SendIcon } from '../icons'
import { useToast } from '../Toast'
import { Avatar, Button, EmptyState, ErrorMessage, Field, Loading, Modal } from '../ui'
import { useChat, useChatEvents } from './ChatContext'
import '../../pages/messages.css'

const PAGE = 50

const CANT_SEND: Record<string, string> = {
  MUTED: 'The club owner muted you in this chat. You can still read it.',
  BLOCKED: "You can't message each other.",
  NOT_CONNECTED: "You're no longer in a club together, so you can't send messages.",
}

function time(value: string) {
  return new Date(value).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

function MutesModal({ clubId, open, onClose }: { clubId: string; open: boolean; onClose: () => void }) {
  const toast = useToast()
  const [mutes, setMutes] = useState<Awaited<ReturnType<typeof chatApi.mutes>>>()
  useEffect(() => {
    if (open) chatApi.mutes(clubId).then(setMutes).catch(toast.error)
  }, [open, clubId, toast])
  return (
    <Modal open={open} title="Muted in this chat" onClose={onClose}>
      {!mutes ? <Loading /> : mutes.length === 0 ? <p className="muted">Nobody is muted.</p> : (
        <div className="list">
          {mutes.map((m) => (
            <div key={m.userId} className="list-item">
              <div className="list-item-main">
                <div className="bold">{m.name ?? 'Member'}</div>
                <div className="small muted">{m.mutedUntil ? `Until ${formatDateTime(m.mutedUntil)}` : 'Until you unmute them'}</div>
              </div>
              <Button size="sm" variant="secondary" onClick={async () => {
                try {
                  await chatApi.unmute(clubId, m.userId)
                  setMutes((list) => list?.filter((x) => x.userId !== m.userId))
                  toast.success(`${m.name ?? 'Member'} can write again`)
                } catch (e) {
                  toast.error(e)
                }
              }}>Unmute</Button>
            </div>
          ))}
        </div>
      )}
    </Modal>
  )
}

/**
 * One conversation: a club's group chat or a direct conversation. Messages arrive live; the conversation is marked
 * read while it's on screen.
 */
export function ChatView({ conversationId, header = true }: { conversationId: string; header?: boolean }) {
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const { refreshUnread } = useChat()
  const [conversation, setConversation] = useState<Conversation>()
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<unknown>()
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [blockedByMe, setBlockedByMe] = useState(false)
  const [toDelete, setToDelete] = useState<ChatMessage | null>(null)
  const [toReport, setToReport] = useState<ChatMessage | null>(null)
  const [reportReason, setReportReason] = useState('')
  const [toMute, setToMute] = useState<ChatMessage | null>(null)
  const [mutesOpen, setMutesOpen] = useState(false)
  const list = useRef<HTMLDivElement>(null)
  const photoInput = useRef<HTMLInputElement>(null)
  const stickToBottom = useRef(true)
  const readTimer = useRef<ReturnType<typeof setTimeout>>(undefined)

  const markRead = useCallback(() => {
    clearTimeout(readTimer.current)
    readTimer.current = setTimeout(() => {
      if (document.visibilityState !== 'visible') return
      chatApi.markRead(conversationId).then(refreshUnread).catch(() => undefined)
    }, 300)
  }, [conversationId, refreshUnread])

  const loadLatest = useCallback(async () => {
    const [c, latest] = await Promise.all([chatApi.get(conversationId), chatApi.messages(conversationId, { limit: PAGE })])
    setConversation(c)
    setMessages((current) => mergeMessages(current, latest))
    return { c, latest }
  }, [conversationId])

  useEffect(() => {
    // callers key this component by conversation, so state starts fresh for each one
    let cancelled = false
    Promise.all([chatApi.get(conversationId), chatApi.messages(conversationId, { limit: PAGE })])
      .then(([c, latest]) => {
        if (cancelled) return
        setConversation(c)
        setMessages((current) => mergeMessages(current, latest))
        setHasMore(latest.length === PAGE)
        markRead()
        if (c.kind === 'DIRECT' && c.otherUserId) {
          chatApi.blocked().then((b) => !cancelled && setBlockedByMe(b.some((x) => x.userId === c.otherUserId))).catch(() => undefined)
        }
      })
      .catch((e) => { if (!cancelled) setError(e) })
    return () => { cancelled = true; clearTimeout(readTimer.current) }
  }, [conversationId, markRead])

  useChatEvents((message) => {
    if (message.conversationId !== conversationId) return
    setMessages((current) => mergeMessages(current, [message]))
    if (message.senderId !== user?.id) markRead()
  }, () => { void loadLatest().catch(() => undefined) })

  // keep the newest message in view, unless the person scrolled up to read older ones
  useLayoutEffect(() => {
    const el = list.current
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight
  }, [messages])

  const onScroll = () => {
    const el = list.current
    if (el) stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  }

  const loadOlder = async () => {
    if (!messages.length) return
    setLoadingOlder(true)
    const el = list.current
    const before = el ? el.scrollHeight - el.scrollTop : 0
    try {
      const older = await chatApi.messages(conversationId, { before: messages[0].id, limit: PAGE })
      stickToBottom.current = false
      setMessages((current) => mergeMessages(current, older))
      setHasMore(older.length === PAGE)
      requestAnimationFrame(() => { if (el) el.scrollTop = el.scrollHeight - before })
    } catch (e) {
      toast.error(e)
    } finally {
      setLoadingOlder(false)
    }
  }

  const send = async (body: string, photoUrl?: string) => {
    setSending(true)
    try {
      const sent = await chatApi.send(conversationId, { body: body || undefined, photoUrl })
      stickToBottom.current = true
      setMessages((current) => mergeMessages(current, [sent]))
      setDraft('')
    } catch (e) {
      toast.error(e)
      void loadLatest().catch(() => undefined)
    } finally {
      setSending(false)
    }
  }

  const submit = (e?: FormEvent) => {
    e?.preventDefault()
    if (draft.trim() && !sending) void send(draft.trim())
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit()
    }
  }

  const sendPhoto = async (file: File | undefined) => {
    if (photoInput.current) photoInput.current.value = ''
    if (!file) return
    const problem = imageProblem(file)
    if (problem) return toast.error(problem)
    setSending(true)
    try {
      const { url } = await chatApi.uploadPhoto(file)
      await send(draft.trim(), url)
    } catch (e) {
      toast.error(e)
      setSending(false)
    }
  }

  const messagePrivately = async (userId: string) => {
    try {
      const dm = await chatApi.direct(userId)
      if (dm.id) navigate(`/messages/${dm.id}`)
    } catch (e) {
      toast.error(e)
    }
  }

  const toggleBlock = async () => {
    if (!conversation?.otherUserId) return
    try {
      if (blockedByMe) {
        await chatApi.unblock(conversation.otherUserId)
        toast.success(`You unblocked ${conversation.title ?? 'them'}`)
      } else {
        await chatApi.block(conversation.otherUserId)
        toast.success(`You blocked ${conversation.title ?? 'them'}. They can't message you.`)
      }
      setBlockedByMe(!blockedByMe)
      setConversation(await chatApi.get(conversationId))
    } catch (e) {
      toast.error(e)
    }
  }

  if (error) return <ErrorMessage error={error} onRetry={() => { setError(undefined); void loadLatest().catch(setError) }} />
  if (!conversation) return <Loading />

  const isGroup = conversation.kind === 'GROUP'
  const actionsFor = (m: ChatMessage): MenuAction[] => {
    if (m.deleted) return []
    const mine = m.senderId === user?.id
    const actions: MenuAction[] = []
    if (mine || conversation.canModerate) actions.push({ label: 'Delete', danger: true, onSelect: () => setToDelete(m) })
    if (!mine) {
      if (isGroup) actions.push({ label: 'Message privately', onSelect: () => void messagePrivately(m.senderId) })
      if (isGroup && conversation.canModerate) actions.push({ label: 'Mute in this chat', onSelect: () => setToMute(m) })
      actions.push({ label: 'Report', onSelect: () => { setReportReason(''); setToReport(m) } })
      if (isGroup) {
        actions.push({ label: 'Block', danger: true, onSelect: async () => {
          try {
            await chatApi.block(m.senderId)
            setMessages((current) => current.filter((x) => x.senderId !== m.senderId))
            toast.success(`You blocked ${m.senderName ?? 'them'}. You won't see their messages.`)
          } catch (e) {
            toast.error(e)
          }
        } })
      }
    }
    return actions
  }

  return (
    <div className="chat">
      {header && (
        <div className="chat-header">
          <Avatar name={conversation.title} url={conversation.imageUrl} size={36} />
          <div className="chat-header-main">
            <div className="bold truncate">
              {isGroup && conversation.clubId
                ? <Link to={`/clubs/${conversation.clubId}`}>{conversation.title}</Link>
                : conversation.title ?? 'Member'}
            </div>
            <div className="small muted">{isGroup ? 'Club chat · subscribers only' : 'Direct message'}</div>
          </div>
          {isGroup && conversation.canModerate && conversation.clubId && (
            <Button size="sm" variant="ghost" onClick={() => setMutesOpen(true)}>Muted</Button>
          )}
          {!isGroup && (
            <Button size="sm" variant={blockedByMe ? 'secondary' : 'ghost'} onClick={() => void toggleBlock()}>
              {blockedByMe ? 'Unblock' : 'Block'}
            </Button>
          )}
        </div>
      )}

      <div className="chat-messages" ref={list} onScroll={onScroll} aria-live="polite">
        {hasMore && (
          <div className="center">
            <Button size="sm" variant="ghost" loading={loadingOlder} onClick={() => void loadOlder()}>Earlier messages</Button>
          </div>
        )}
        {messages.length === 0 && (
          <EmptyState title="No messages yet">{isGroup ? 'Say hello to the club.' : 'Start the conversation.'}</EmptyState>
        )}
        {messages.map((m, i) => {
          const mine = m.senderId === user?.id
          const grouped = sameGroup(messages[i - 1], m)
          return (
            <div key={m.id} className={`chat-row${mine ? ' mine' : ''}${grouped ? ' grouped' : ''}`}>
              {!mine && isGroup && (
                <div className="chat-avatar">{!grouped && <Avatar name={m.senderName} url={m.senderPhotoUrl} size={30} />}</div>
              )}
              <div className="chat-bubble-wrap">
                {!mine && isGroup && !grouped && <div className="chat-sender">{m.senderName ?? 'Member'}</div>}
                <div className={`chat-bubble${m.deleted ? ' deleted' : ''}`} title={formatDateTime(m.createdAt)}>
                  {m.deleted ? <em>Message deleted</em> : (
                    <>
                      {m.photoUrl && (
                        <a href={mediaUrl(m.photoUrl)} target="_blank" rel="noopener noreferrer">
                          <img className="chat-photo" src={mediaUrl(m.photoUrl)} alt="" loading="lazy" />
                        </a>
                      )}
                      {m.body && <span className="pre-wrap">{m.body}</span>}
                    </>
                  )}
                  <span className="chat-time">{time(m.createdAt)}</span>
                </div>
              </div>
              <div className="chat-actions"><ActionMenu actions={actionsFor(m)} label="Message actions" align={mine ? 'right' : 'left'} /></div>
            </div>
          )
        })}
      </div>

      {conversation.canSend ? (
        <form className="chat-composer" onSubmit={submit}>
          <input ref={photoInput} type="file" accept={ACCEPTED_IMAGE_TYPES.join(',')} className="visually-hidden"
                 aria-label="Send a photo" onChange={(e) => void sendPhoto(e.target.files?.[0])} />
          <button type="button" className="icon-button" aria-label="Send a photo" disabled={sending}
                  onClick={() => photoInput.current?.click()}><ImageIcon /></button>
          <textarea className="input chat-input" rows={1} maxLength={2000} placeholder="Write a message…" value={draft}
                    aria-label="Message" onChange={(e) => setDraft(e.target.value)} onKeyDown={onKeyDown} />
          <Button type="submit" aria-label="Send" loading={sending} disabled={!draft.trim()}><SendIcon size={18} /></Button>
        </form>
      ) : (
        <div className="chat-composer chat-composer-disabled small muted">
          {CANT_SEND[conversation.reason ?? ''] ?? "You can't send messages here."}
          {conversation.reason === 'MUTED' && conversation.mutedUntil && ` (until ${formatDateTime(conversation.mutedUntil)})`}
        </div>
      )}

      <ConfirmModal open={!!toDelete} title="Delete message?" confirmLabel="Delete" onClose={() => setToDelete(null)}
                    onConfirm={async () => {
                      if (!toDelete) return
                      await chatApi.removeMessage(toDelete.id)
                      setMessages((current) => mergeMessages(current, [{ ...toDelete, deleted: true, body: null, photoUrl: null }]))
                    }}>
        It will be removed for everyone in this conversation.
      </ConfirmModal>

      <Modal open={!!toReport} title="Report message" onClose={() => setToReport(null)}
             footer={<>
               <Button variant="secondary" onClick={() => setToReport(null)}>Cancel</Button>
               <Button variant="danger" onClick={async () => {
                 if (!toReport) return
                 try {
                   await chatApi.report(toReport.id, reportReason.trim())
                   toast.success("Thanks, we'll review it. Block them if you don't want to hear from them.")
                   setToReport(null)
                 } catch (e) {
                   toast.error(e)
                 }
               }}>Report</Button>
             </>}>
        <div className="stack">
          <blockquote className="chat-quote">{toReport?.body ?? 'Photo'}</blockquote>
          <Field label="What's wrong? (optional)" htmlFor="report-reason">
            <textarea id="report-reason" className="input" maxLength={500} value={reportReason}
                      onChange={(e) => setReportReason(e.target.value)} />
          </Field>
          <p className="small muted">The Club's admins see the message and who wrote it. They won't tell them who reported it.</p>
        </div>
      </Modal>

      <Modal open={!!toMute} title={`Mute ${toMute?.senderName ?? 'this member'}?`} onClose={() => setToMute(null)}>
        <div className="stack">
          <p>They can still read the club chat but can't write in it.</p>
          <div className="row">
            {[{ label: '24 hours', hours: 24 }, { label: '7 days', hours: 168 }, { label: 'Until I unmute them', hours: undefined }]
              .map((option) => (
                <Button key={option.label} variant="secondary" onClick={async () => {
                  if (!toMute || !conversation.clubId) return
                  try {
                    await chatApi.mute(conversation.clubId, toMute.senderId, option.hours)
                    toast.success(`${toMute.senderName ?? 'Member'} is muted`)
                    setToMute(null)
                  } catch (e) {
                    toast.error(e)
                  }
                }}>{option.label}</Button>
              ))}
          </div>
        </div>
      </Modal>

      {conversation.clubId && conversation.canModerate && (
        <MutesModal clubId={conversation.clubId} open={mutesOpen} onClose={() => setMutesOpen(false)} />
      )}
    </div>
  )
}
