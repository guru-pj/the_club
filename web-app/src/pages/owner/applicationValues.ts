import type { ApplyVerificationRequest, ClubType, VerificationRequest } from '../../api/types'

export interface ApplicationValues {
  clubName: string
  clubType: ClubType | ''
  city: string
  address: string
  latitude: string
  longitude: string
  contactEmail: string
  contactPhone: string
  website: string
  socialLinks: Record<string, string>
  description: string
  proofFiles: string[]
}

export function applicationValues(r?: VerificationRequest): ApplicationValues {
  return {
    clubName: r?.clubName ?? '',
    clubType: r?.clubType ?? '',
    city: r?.city ?? '',
    address: r?.address ?? '',
    latitude: r?.latitude != null ? String(r.latitude) : '',
    longitude: r?.longitude != null ? String(r.longitude) : '',
    contactEmail: r?.contactEmail ?? '',
    contactPhone: r?.contactPhone ?? '',
    website: r?.website ?? '',
    socialLinks: { ...(r?.socialLinks ?? {}) },
    description: r?.description ?? '',
    proofFiles: [...(r?.proofFiles ?? [])],
  }
}

const isHttpUrl = (url: string) => /^https?:\/\/\S+$/i.test(url.trim())

/** What's missing or wrong before sending, or null. The server checks the same. */
export function applicationProblem(v: ApplicationValues): string | null {
  if (!v.clubName.trim()) return "Enter the club's name"
  if (!v.clubType) return 'Choose what kind of club it is'
  if (!v.city.trim()) return "Enter the club's city"
  if (!v.contactEmail.trim() && !v.contactPhone.trim()) return 'Give a contact email or phone for the club'
  if (!!v.latitude.trim() !== !!v.longitude.trim()) return 'Enter both latitude and longitude, or neither'
  if (v.website.trim() && !isHttpUrl(v.website)) return 'The website must be a full http(s) link'
  if (Object.values(v.socialLinks).some((url) => url.trim() && !isHttpUrl(url))) return 'Social links must be full http(s) links'
  return null
}

export function toApplication(v: ApplicationValues): ApplyVerificationRequest {
  const lat = v.latitude.trim()
  const lng = v.longitude.trim()
  return {
    clubName: v.clubName.trim(),
    clubType: v.clubType as ClubType,
    city: v.city.trim(),
    address: v.address.trim() || undefined,
    ...(lat && lng ? { latitude: Number(lat), longitude: Number(lng) } : {}),
    contactEmail: v.contactEmail.trim() || undefined,
    contactPhone: v.contactPhone.trim() || undefined,
    website: v.website.trim() || undefined,
    socialLinks: Object.fromEntries(Object.entries(v.socialLinks).map(([k, url]) => [k, url.trim()]).filter(([, url]) => url)),
    description: v.description.trim() || undefined,
    proofFiles: v.proofFiles,
  }
}
