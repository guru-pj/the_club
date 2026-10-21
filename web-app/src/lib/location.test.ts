import { describe, expect, it } from 'vitest'
import { formatAway, mapLink, savedLocation } from './location'

describe('location helpers', () => {
  it('formats distances for people, not machines', () => {
    expect(formatAway(null)).toBeNull()
    expect(formatAway(0.04)).toBe('100 m')
    expect(formatAway(0.46)).toBe('500 m')
    expect(formatAway(2.43)).toBe('2.4 km')
    expect(formatAway(23.6)).toBe('24 km')
  })

  it('reads the saved profile location only when both coordinates are there', () => {
    expect(savedLocation({ latitude: 13.08, longitude: 80.27 } as never)).toEqual({ latitude: 13.08, longitude: 80.27 })
    expect(savedLocation({ latitude: 13.08, longitude: null } as never)).toBeNull()
    expect(savedLocation(null)).toBeNull()
  })

  it('links to the point on OpenStreetMap', () => {
    expect(mapLink(13.05, 80.28)).toBe('https://www.openstreetmap.org/?mlat=13.05&mlon=80.28#map=15/13.05/80.28')
  })
})
