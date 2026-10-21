import type { User } from '../api/types'

export interface Coordinates {
  latitude: number
  longitude: number
}

/** Why the device's location isn't available, in words for the user. */
export class LocationError extends Error {}

function locate(options: PositionOptions): Promise<Coordinates> {
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
      reject,
      options,
    )
  })
}

function explain(err: GeolocationPositionError): LocationError {
  return new LocationError(
    err.code === err.PERMISSION_DENIED
      ? 'Location permission was denied. Allow it in your browser or phone settings, or enter your city instead.'
      : err.code === err.TIMEOUT
        ? 'Finding your location took too long. Try again, or enter your city instead.'
        : "Your location isn't available right now. Try again, or enter your city instead.")
}

/**
 * The device's current position, after the browser or phone asks for permission. Tries a quick, battery-friendly
 * fix first (network/Wi-Fi, which is precise enough for "nearby"), then GPS when that isn't available, as on
 * phones without network location. Rejects with a {@link LocationError} explaining what happened.
 */
export async function currentPosition(timeoutMs = 8_000): Promise<Coordinates> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    throw new LocationError("This device can't share its location. Enter your city instead.")
  }
  try {
    return await locate({ enableHighAccuracy: false, timeout: timeoutMs, maximumAge: 10 * 60_000 })
  } catch (e) {
    const err = e as GeolocationPositionError
    if (err.code === err.PERMISSION_DENIED) throw explain(err)
  }
  try {
    return await locate({ enableHighAccuracy: true, timeout: timeoutMs * 2, maximumAge: 10 * 60_000 })
  } catch (e) {
    throw explain(e as GeolocationPositionError)
  }
}

/** The location saved on the profile, if any. */
export function savedLocation(user: User | null | undefined): Coordinates | null {
  return user?.latitude != null && user?.longitude != null ? { latitude: user.latitude, longitude: user.longitude } : null
}

export function formatAway(km: number | null | undefined): string | null {
  if (km == null) return null
  if (km < 1) return `${Math.max(100, Math.round(km * 10) * 100)} m`
  return `${km < 10 ? km.toFixed(1) : Math.round(km)} km`
}

/** OpenStreetMap link for a point (no map library or API key needed). */
export function mapLink(latitude: number, longitude: number): string {
  return `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=15/${latitude}/${longitude}`
}
