import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { chatApi } from '../../api/endpoints'
import type { ChatMessage } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { chatStream } from '../../lib/chatStream'

interface ChatState {
  /** unread messages across all conversations */
  unread: number
  refreshUnread: () => void
}

const ChatContext = createContext<ChatState>({ unread: 0, refreshUnread: () => undefined })

/** Keeps the live chat connection open while signed in, and the unread count for the header. */
export function ChatProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [unread, setUnread] = useState(0)
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined)

  const load = useCallback(() => {
    chatApi.inbox()
      .then((inbox) => setUnread(inbox.reduce((sum, c) => sum + c.unread, 0)))
      .catch(() => undefined)
  }, [])

  // several messages in a row cause one reload
  const refreshUnread = useCallback(() => {
    clearTimeout(timer.current)
    timer.current = setTimeout(load, 400)
  }, [load])

  useEffect(() => {
    if (!user) return
    chatStream.start()
    load()
    const offMessage = chatStream.onMessage((m) => { if (m.senderId !== user.id) refreshUnread() })
    const offReconnect = chatStream.onReconnect(load)
    const poll = setInterval(load, 120_000)
    return () => {
      offMessage()
      offReconnect()
      clearInterval(poll)
      clearTimeout(timer.current)
      chatStream.stop()
    }
  }, [user, load, refreshUnread])

  return <ChatContext.Provider value={{ unread, refreshUnread }}>{children}</ChatContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useChat(): ChatState {
  return useContext(ChatContext)
}

/** Calls `onMessage` for every live message (new or deleted), and `onReconnect` after the connection came back. */
// eslint-disable-next-line react-refresh/only-export-components
export function useChatEvents(onMessage: (message: ChatMessage) => void, onReconnect?: () => void) {
  const handlers = useRef({ onMessage, onReconnect })
  useEffect(() => { handlers.current = { onMessage, onReconnect } })
  useEffect(() => {
    const offMessage = chatStream.onMessage((m) => handlers.current.onMessage(m))
    const offReconnect = chatStream.onReconnect(() => handlers.current.onReconnect?.())
    return () => { offMessage(); offReconnect() }
  }, [])
}
