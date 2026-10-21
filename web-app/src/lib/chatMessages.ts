import type { ChatMessage } from '../api/types'

/**
 * Adds or replaces messages by ID and keeps the list in order. Message IDs are UUIDv7, so sorting them as strings
 * sorts by time. The same message can arrive both from the send call and the live stream.
 */
export function mergeMessages(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  if (!incoming.length) return current
  const byId = new Map(current.map((m) => [m.id, m]))
  for (const m of incoming) byId.set(m.id, m)
  return [...byId.values()].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0))
}

/** Whether two neighbouring messages are by the same person within a few minutes (shown as one group). */
export function sameGroup(previous: ChatMessage | undefined, message: ChatMessage, gapMinutes = 5): boolean {
  if (!previous || previous.senderId !== message.senderId) return false
  return Math.abs(Date.parse(message.createdAt) - Date.parse(previous.createdAt)) <= gapMinutes * 60_000
}
