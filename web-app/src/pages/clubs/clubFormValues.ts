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
  contactEmail: string
  contactPhone: string
  website: string
  /** social network → link */
  socialLinks: Record<SocialNetwork, string>
  rules: string
}

export const SOCIAL_NETWORKS = [
  { key: 'instagram', label: 'Instagram' },
  { key: 'facebook', label: 'Facebook' },
  { key: 'strava', label: 'Strava club' },
  { key: 'youtube', label: 'YouTube' },
] as const
export type SocialNetwork = (typeof SOCIAL_NETWORKS)[number]['key']

function socialValues(links: Record<string, string> | null | undefined): Record<SocialNetwork, string> {
  return Object.fromEntries(SOCIAL_NETWORKS.map(({ key }) => [key, links?.[key] ?? ''])) as Record<SocialNetwork, string>
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
    contactEmail: club?.contactEmail ?? '',
    contactPhone: club?.contactPhone ?? '',
    website: club?.website ?? '',
    socialLinks: socialValues(club?.socialLinks),
    rules: club?.rules ?? '',
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

/** Whether a typed website or social link isn't a full http(s) URL. */
export function linkProblem(v: Pick<ClubFormValues, 'website' | 'socialLinks'>): boolean {
  const bad = (url: string) => url.trim() !== '' && !/^https?:\/\/\S+$/i.test(url.trim())
  return bad(v.website) || Object.values(v.socialLinks).some(bad)
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
    // '' clears a field; empty links are dropped
    contactEmail: v.contactEmail.trim(),
    contactPhone: v.contactPhone.trim(),
    website: v.website.trim(),
    socialLinks: Object.fromEntries(Object.entries(v.socialLinks).map(([k, url]) => [k, url.trim()]).filter(([, url]) => url)),
    rules: v.rules.trim(),
  }
}
