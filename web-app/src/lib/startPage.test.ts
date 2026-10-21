import { describe, expect, it } from 'vitest'
import type { User } from '../api/types'
import { startPage } from './startPage'

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
