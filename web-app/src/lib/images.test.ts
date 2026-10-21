import { describe, expect, it } from 'vitest'
import { imageProblem, MAX_IMAGE_BYTES } from './images'

describe('imageProblem', () => {
  it('accepts JPEG, PNG and WebP up to 5 MB', () => {
    expect(imageProblem({ type: 'image/jpeg', size: 1000 })).toBeNull()
    expect(imageProblem({ type: 'image/png', size: MAX_IMAGE_BYTES })).toBeNull()
    expect(imageProblem({ type: 'image/webp', size: 1 })).toBeNull()
  })

  it('refuses other formats, including SVG and GIF', () => {
    expect(imageProblem({ type: 'image/svg+xml', size: 10 })).toBe('Choose a JPEG, PNG or WebP image')
    expect(imageProblem({ type: 'image/gif', size: 10 })).toBe('Choose a JPEG, PNG or WebP image')
    expect(imageProblem({ type: '', size: 10 })).toBe('Choose a JPEG, PNG or WebP image')
  })

  it('refuses files over 5 MB', () => {
    expect(imageProblem({ type: 'image/jpeg', size: MAX_IMAGE_BYTES + 1 })).toBe('Images can be at most 5 MB')
  })
})
