import { describe, expect, it } from 'vitest'
import type { ChatMessage } from '../api/types'
import { mergeMessages, sameGroup } from './chatMessages'

const msg = (id: string, overrides: Partial<ChatMessage> = {}): ChatMessage => ({
  id, conversationId: 'c', senderId: 'u1', senderName: 'Asha', senderPhotoUrl: null, body: id, photoUrl: null,
  deleted: false, createdAt: '2026-10-21T10:00:00', ...overrides,
})

describe('mergeMessages', () => {
  it('appends new messages in ID order and ignores duplicates', () => {
    const merged = mergeMessages([msg('0193-a'), msg('0193-c')], [msg('0193-b'), msg('0193-c')])
    expect(merged.map((m) => m.id)).toEqual(['0193-a', '0193-b', '0193-c'])
  })

  it('replaces a message that was deleted since', () => {
    const merged = mergeMessages([msg('a'), msg('b')], [msg('a', { deleted: true, body: null })])
    expect(merged[0]).toMatchObject({ id: 'a', deleted: true, body: null })
    expect(merged).toHaveLength(2)
  })
})

describe('sameGroup', () => {
  it('groups messages by the same sender a few minutes apart', () => {
    expect(sameGroup(msg('a'), msg('b', { createdAt: '2026-10-21T10:04:00' }))).toBe(true)
    expect(sameGroup(msg('a'), msg('b', { createdAt: '2026-10-21T10:06:00' }))).toBe(false)
    expect(sameGroup(msg('a'), msg('b', { senderId: 'u2' }))).toBe(false)
    expect(sameGroup(undefined, msg('b'))).toBe(false)
  })
})
