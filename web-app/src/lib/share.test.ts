import { describe, expect, it } from 'vitest'
import { postPath, publicUrl } from './share'

describe('publicUrl', () => {
  const path = postPath('c1', 'p1')

  it('uses the page origin in the browser', () => {
    expect(publicUrl(path, 'https://club.example', false, '', '')).toBe('https://club.example/clubs/c1/posts/p1')
  })

  it('prefers the configured web app address', () => {
    expect(publicUrl(path, 'https://localhost', true, 'https://theclub.app', 'https://api.example'))
      .toBe('https://theclub.app/clubs/c1/posts/p1')
  })

  it("falls back to the server's address in the native apps", () => {
    expect(publicUrl(path, 'capacitor://localhost', true, '', 'https://club.example/'))
      .toBe('https://club.example/clubs/c1/posts/p1')
  })
})
