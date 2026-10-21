import { afterEach, describe, expect, it, vi } from 'vitest'
import { currentPosition, LocationError } from './location'

const position = (latitude: number, longitude: number) => ({ coords: { latitude, longitude } })
const failure = (code: number) => ({ code, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 })

function fakeGeolocation(results: ((ok: (p: unknown) => void, fail: (e: unknown) => void) => void)[]) {
  const calls: PositionOptions[] = []
  vi.stubGlobal('navigator', {
    geolocation: {
      getCurrentPosition: (ok: (p: unknown) => void, fail: (e: unknown) => void, options: PositionOptions) => {
        calls.push(options)
        results[calls.length - 1](ok, fail)
      },
    },
  })
  return calls
}

describe('currentPosition', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses the quick low-accuracy fix when there is one', async () => {
    const calls = fakeGeolocation([(ok) => ok(position(13.05, 80.28))])
    await expect(currentPosition()).resolves.toEqual({ latitude: 13.05, longitude: 80.28 })
    expect(calls.map((c) => c.enableHighAccuracy)).toEqual([false])
  })

  it('falls back to GPS when the quick fix times out (phones without network location)', async () => {
    const calls = fakeGeolocation([(_, fail) => fail(failure(3)), (ok) => ok(position(12.99, 80.27))])
    await expect(currentPosition()).resolves.toEqual({ latitude: 12.99, longitude: 80.27 })
    expect(calls.map((c) => c.enableHighAccuracy)).toEqual([false, true])
  })

  it("doesn't ask again after the permission was denied", async () => {
    const calls = fakeGeolocation([(_, fail) => fail(failure(1))])
    await expect(currentPosition()).rejects.toThrow(/permission was denied/)
    expect(calls).toHaveLength(1)
  })

  it('explains when no position can be found at all', async () => {
    fakeGeolocation([(_, fail) => fail(failure(3)), (_, fail) => fail(failure(2))])
    await expect(currentPosition()).rejects.toBeInstanceOf(LocationError)
  })
})
