import type { Club, ClubRequest, ClubType } from '../../api/types'

export interface ClubFormValues {
  name: string
  description: string
  city: string
  clubType: ClubType | ''
  /** a pasted http(s) link; uploaded logos ("/media/…") are changed with the upload control instead */
  logoUrl: string
  address: string
  /** as typed; both empty = no map position */
  latitude: string
  longitude: string
  subscriptionsEnabled: boolean
  subscriptionBenefits: string
  /** the club had a map position when the form opened (clearing both fields then removes it) */
  hadLocation: boolean
}

export function clubFormValues(club?: Partial<Club> | null): ClubFormValues {
  const logo = club?.logoUrl ?? ''
  return {
    name: club?.name ?? '',
    description: club?.description ?? '',
    city: club?.city ?? '',
    clubType: club?.clubType ?? '',
    logoUrl: /^https?:\/\//i.test(logo) ? logo : '',
    address: club?.address ?? '',
    latitude: club?.latitude != null ? String(club.latitude) : '',
    longitude: club?.longitude != null ? String(club.longitude) : '',
    subscriptionsEnabled: club?.subscriptionsEnabled ?? true,
    subscriptionBenefits: club?.subscriptionBenefits ?? '',
    hadLocation: club?.latitude != null && club?.longitude != null,
  }
}

/** A problem with the typed map position, or null when it's fine (both empty also counts as fine). */
export function locationProblem(v: Pick<ClubFormValues, 'latitude' | 'longitude'>): string | null {
  const lat = v.latitude.trim()
  const lng = v.longitude.trim()
  if (!lat && !lng) return null
  if (!lat || !lng) return 'Enter both latitude and longitude, or neither'
  const la = Number(lat)
  const lo = Number(lng)
  if (!Number.isFinite(la) || la < -90 || la > 90) return 'Latitude must be between -90 and 90'
  if (!Number.isFinite(lo) || lo < -180 || lo > 180) return 'Longitude must be between -180 and 180'
  return null
}

export function toClubRequest(v: ClubFormValues): ClubRequest {
  const lat = v.latitude.trim()
  const lng = v.longitude.trim()
  return {
    name: v.name.trim(),
    description: v.description.trim() || undefined,
    city: v.city.trim() || undefined,
    clubType: v.clubType || undefined,
    logoUrl: v.logoUrl.trim() || undefined,
    // send '' rather than leaving it out, so an owner can clear the address
    address: v.address.trim(),
    ...(lat && lng ? { latitude: Number(lat), longitude: Number(lng) } : v.hadLocation ? { clearLocation: true } : {}),
    subscriptionsEnabled: v.subscriptionsEnabled,
    subscriptionBenefits: v.subscriptionBenefits.trim(),
  }
}
