// Lets pages tell the header to refresh the unread badge (e.g. after marking notifications read).
const EVENT = 'theclub:notifications-changed'

export function notificationsChanged() {
  window.dispatchEvent(new Event(EVENT))
}

export function onNotificationsChanged(listener: () => void): () => void {
  window.addEventListener(EVENT, listener)
  return () => window.removeEventListener(EVENT, listener)
}
