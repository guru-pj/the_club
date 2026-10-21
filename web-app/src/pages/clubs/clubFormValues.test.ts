import { describe, expect, it } from 'vitest'
import type { Club } from '../../api/types'
import { clubFormValues, locationProblem, toClubRequest } from './clubFormValues'

describe('club form values', () => {
  it('keeps uploaded logos out of the link field, so saving never sends them back as a URL', () => {
    expect(clubFormValues({ logoUrl: '/media/clubs/1/logo.png' }).logoUrl).toBe('')
    expect(clubFormValues({ logoUrl: 'https://example.com/logo.png' }).logoUrl).toBe('https://example.com/logo.png')
    expect(toClubRequest(clubFormValues({ name: 'A', logoUrl: '/media/x.png' })).logoUrl).toBeUndefined()
  })

  it('sends a map position only with both coordinates, and clears one the club had', () => {
    const values = clubFormValues({ name: 'A', latitude: 13.05, longitude: 80.28 } as Partial<Club>)
    expect(toClubRequest(values)).toMatchObject({ latitude: 13.05, longitude: 80.28 })
    expect(toClubRequest({ ...values, latitude: '', longitude: '' })).toMatchObject({ clearLocation: true })
    expect(toClubRequest(clubFormValues({ name: 'B' }))).not.toHaveProperty('clearLocation')
  })

  it('checks typed coordinates', () => {
    expect(locationProblem({ latitude: '', longitude: '' })).toBeNull()
    expect(locationProblem({ latitude: '13.05', longitude: '' })).toBe('Enter both latitude and longitude, or neither')
    expect(locationProblem({ latitude: '95', longitude: '80' })).toBe('Latitude must be between -90 and 90')
    expect(locationProblem({ latitude: '13', longitude: 'east' })).toBe('Longitude must be between -180 and 180')
  })

  it('defaults new clubs to offering subscriptions', () => {
    expect(clubFormValues().subscriptionsEnabled).toBe(true)
    expect(toClubRequest(clubFormValues({ name: 'C', subscriptionsEnabled: false } as Partial<Club>)).subscriptionsEnabled).toBe(false)
  })
})
