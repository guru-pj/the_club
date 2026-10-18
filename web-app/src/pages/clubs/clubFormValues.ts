import type { Club, ClubRequest, ClubType } from '../../api/types'

export interface ClubFormValues {
  name: string
  description: string
  city: string
  clubType: ClubType | ''
  logoUrl: string
}

export function clubFormValues(club?: Partial<Club> | null): ClubFormValues {
  return {
    name: club?.name ?? '',
    description: club?.description ?? '',
    city: club?.city ?? '',
    clubType: club?.clubType ?? '',
    logoUrl: club?.logoUrl ?? '',
  }
}

export function toClubRequest(v: ClubFormValues): ClubRequest {
  return {
    name: v.name.trim(),
    description: v.description.trim() || undefined,
    city: v.city.trim() || undefined,
    clubType: v.clubType || undefined,
    logoUrl: v.logoUrl.trim() || undefined,
  }
}
