import { describe, expect, it } from 'vitest'
import type { User } from '../api/types'
import { afterProfileSetup, startPage } from './startPage'

const user = (profileComplete?: boolean) => ({ id: 'u', name: 'A', roles: [], createdAt: '', profileComplete }) as User

describe('startPage', () => {
  it('sends new accounts to profile setup, wherever they were going', () => {
    expect(startPage(user(false))).toBe('/welcome')
    expect(startPage(user(false), '/clubs/1')).toBe('/welcome')
  })

  it('otherwise returns to where the user was going, or home', () => {
    expect(startPage(user(true), '/clubs/1')).toBe('/clubs/1')
    expect(startPage(user(true))).toBe('/')
    expect(startPage(user(true), '/login')).toBe('/')
    // older API responses without the flag behave like a finished profile
    expect(startPage(user(undefined))).toBe('/')
  })
})

describe('club owner sign-ups', () => {
  const owner = (roles: string[]) => ({ ...user(true), clubOwnerSignup: true, roles }) as User

  it('go to their club application until a club is verified, then to the owner dashboard', () => {
    expect(startPage(owner(['USER']))).toBe('/become-owner')
    expect(startPage(owner(['USER', 'CLUB_OWNER']))).toBe('/owner')
    expect(startPage(owner(['USER']), '/events/1')).toBe('/events/1')
    expect(afterProfileSetup(owner(['USER']))).toBe('/become-owner')
    expect(afterProfileSetup(user(true))).toBe('/clubs')
  })

  it('still set up their profile first', () => {
    expect(startPage({ ...owner(['USER']), profileComplete: false } as User)).toBe('/welcome')
  })
})
