import { describe, expect, it } from 'vitest'
import { createSseParser, type SseEvent } from './sse'

function parse(...chunks: string[]): SseEvent[] {
  const events: SseEvent[] = []
  const feed = createSseParser((e) => events.push(e))
  chunks.forEach(feed)
  return events
}

describe('createSseParser', () => {
  it('reads events with a name, an id and data', () => {
    expect(parse('event:message\nid:42\ndata:{"a":1}\n\n')).toEqual([{ event: 'message', id: '42', data: '{"a":1}' }])
  })

  it('handles events split across chunks and CRLF line endings', () => {
    expect(parse('event: rea', 'dy\r\ndata: {}\r', '\n\r\nevent:message\ndata:x\n', '\n')).toEqual([
      { event: 'ready', data: '{}', id: undefined },
      { event: 'message', data: 'x', id: undefined },
    ])
  })

  it('skips keep-alive comments and joins multi-line data', () => {
    expect(parse(':keep-alive\n\ndata:one\ndata:two\n\n')).toEqual([{ event: 'message', data: 'one\ntwo', id: undefined }])
  })

  it('waits for the blank line before dispatching', () => {
    expect(parse('event:message\ndata:x\n')).toEqual([])
  })
})
