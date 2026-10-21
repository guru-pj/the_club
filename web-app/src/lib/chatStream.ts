import { apiUrl, refreshTokens, tokenStore } from '../api/client'
import type { ChatMessage } from '../api/types'
import { createSseParser } from './sse'

type MessageListener = (message: ChatMessage) => void
type ReconnectListener = () => void

const messageListeners = new Set<MessageListener>()
const reconnectListeners = new Set<ReconnectListener>()
let controller: AbortController | null = null
const MAX_BACKOFF_MS = 30_000
/** The server sends a keep-alive every 20 seconds; this long without anything means the connection is dead. */
const SILENCE_LIMIT_MS = 50_000
/** Back from this long in the background (phones suspend apps): start a fresh connection straight away. */
const BACKGROUND_LIMIT_MS = 30_000

const sleep = (ms: number, signal: AbortSignal) => new Promise<void>((resolve) => {
  const timer = setTimeout(resolve, ms)
  signal.addEventListener('abort', () => { clearTimeout(timer); resolve() }, { once: true })
})

/**
 * Live chat updates over Server-Sent Events. Uses fetch rather than EventSource so it can send the access token
 * (EventSource can't set headers), and works the same in the browser and the native apps. It reconnects on its own;
 * after a reconnect, open conversations reload, since messages may have arrived while it was disconnected.
 */
async function run(signal: AbortSignal) {
  let backoff = 1_000
  let connectedBefore = false
  // A suspended app (or a sleeping laptop) can leave a connection that never errors but never delivers either:
  // drop it when it goes quiet, or when the page comes back after a while in the background.
  let connection: AbortController | null = null
  let lastData = Date.now()
  let hiddenSince: number | null = null
  const watchdog = setInterval(() => {
    if (connection && Date.now() - lastData > SILENCE_LIMIT_MS) connection.abort()
  }, 10_000)
  const onVisibility = () => {
    if (document.visibilityState === 'hidden') {
      hiddenSince = Date.now()
    } else {
      if (hiddenSince !== null && Date.now() - hiddenSince > BACKGROUND_LIMIT_MS) connection?.abort()
      hiddenSince = null
    }
  }
  document.addEventListener('visibilitychange', onVisibility)
  signal.addEventListener('abort', () => {
    clearInterval(watchdog)
    document.removeEventListener('visibilitychange', onVisibility)
    connection?.abort()
  }, { once: true })

  while (!signal.aborted) {
    const token = tokenStore.get()
    if (!token) {
      await sleep(2_000, signal)
      continue
    }
    connection = new AbortController()
    lastData = Date.now()
    try {
      const res = await fetch(apiUrl('/api/v1/chat/stream'), {
        headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
        credentials: 'same-origin',
        cache: 'no-store',
        signal: connection.signal,
      })
      if (res.status === 401) {
        if (!(await refreshTokens())) await sleep(backoff, signal)
        continue
      }
      if (!res.ok || !res.body) throw new Error(`chat stream: ${res.status}`)
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      const feed = createSseParser((event) => {
        if (event.event === 'ready') {
          backoff = 1_000
          if (connectedBefore) reconnectListeners.forEach((l) => l())
          connectedBefore = true
        } else if (event.event === 'message') {
          try {
            const message = (JSON.parse(event.data) as { message: ChatMessage }).message
            messageListeners.forEach((l) => l(message))
          } catch {
            // malformed event
          }
        }
      })
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        lastData = Date.now()
        feed(decoder.decode(value, { stream: true }))
      }
      // the server ends each stream after a while; reconnect straight away
    } catch {
      if (signal.aborted) return
      // dropped by the watchdog: reconnect at once; a real failure waits a little longer each time
      if (connection.signal.aborted) continue
      await sleep(backoff, signal)
      backoff = Math.min(backoff * 2, MAX_BACKOFF_MS)
    }
  }
}

export const chatStream = {
  start() {
    if (controller) return
    controller = new AbortController()
    void run(controller.signal)
  },
  stop() {
    controller?.abort()
    controller = null
  },
  onMessage(listener: MessageListener): () => void {
    messageListeners.add(listener)
    return () => { messageListeners.delete(listener) }
  },
  onReconnect(listener: ReconnectListener): () => void {
    reconnectListeners.add(listener)
    return () => { reconnectListeners.delete(listener) }
  },
}
