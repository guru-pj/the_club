import { describe, expect, it } from 'vitest'
import { applicationProblem, applicationValues, toApplication } from './applicationValues'

const filled = () => ({
  ...applicationValues(), clubName: ' Marina Runners ', clubType: 'RUNNING' as const, city: 'Chennai',
  contactEmail: 'hi@marina.run',
})

describe('applicationProblem', () => {
  it('needs the name, category, city and a way to contact the club', () => {
    expect(applicationProblem(applicationValues())).toMatch(/name/)
    expect(applicationProblem({ ...filled(), clubType: '' })).toMatch(/kind of club/)
    expect(applicationProblem({ ...filled(), city: ' ' })).toMatch(/city/)
    expect(applicationProblem({ ...filled(), contactEmail: '' })).toMatch(/contact/)
    expect(applicationProblem({ ...filled(), contactEmail: '', contactPhone: '+91 98765 43210' })).toBeNull()
  })

  it('checks the location pair and links', () => {
    expect(applicationProblem({ ...filled(), latitude: '13.05' })).toMatch(/both/)
    expect(applicationProblem({ ...filled(), website: 'marina.run' })).toMatch(/website/)
    expect(applicationProblem({ ...filled(), socialLinks: { instagram: 'instagram.com/x' } })).toMatch(/Social/)
  })
})

describe('toApplication', () => {
  it('trims, drops empty links and sends the location only as a pair', () => {
    const request = toApplication({ ...filled(), latitude: '13.05', longitude: '80.28',
      socialLinks: { instagram: ' https://instagram.com/marina ', facebook: '' } })
    expect(request).toMatchObject({ clubName: 'Marina Runners', latitude: 13.05, longitude: 80.28,
      socialLinks: { instagram: 'https://instagram.com/marina' } })
    expect(request.contactPhone).toBeUndefined()
  })
})
