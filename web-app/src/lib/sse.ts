export interface SseEvent {
  event: string
  data: string
  id?: string
}

/**
 * Incremental Server-Sent Events parser: feed it text as it arrives; it calls `onEvent` for each complete event.
 * Comments (lines starting with ":", the server's keep-alives) are skipped.
 */
export function createSseParser(onEvent: (event: SseEvent) => void): (chunk: string) => void {
  let buffer = ''
  let event = ''
  let id: string | undefined
  let data: string[] = []

  const dispatch = () => {
    if (data.length) onEvent({ event: event || 'message', data: data.join('\n'), id })
    event = ''
    id = undefined
    data = []
  }

  return (chunk: string) => {
    buffer += chunk
    const lines = buffer.split(/\r\n|\r|\n/)
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line === '') {
        dispatch()
        continue
      }
      if (line.startsWith(':')) continue
      const colon = line.indexOf(':')
      const field = colon === -1 ? line : line.slice(0, colon)
      let value = colon === -1 ? '' : line.slice(colon + 1)
      if (value.startsWith(' ')) value = value.slice(1)
      if (field === 'event') event = value
      else if (field === 'data') data.push(value)
      else if (field === 'id') id = value
    }
  }
}
