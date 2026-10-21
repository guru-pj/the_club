import type { Club } from '../../api/types'

/** What a complete public club profile has, in the order owners usually fill it in. */
export function profileChecklist(club: Club): { label: string; done: boolean }[] {
  return [
    { label: 'Description', done: !!club.description?.trim() },
    { label: 'Logo', done: !!club.logoUrl },
    { label: 'Cover photo', done: !!club.coverUrl },
    { label: 'Address and map location', done: !!club.address && club.latitude != null },
    { label: 'Contact email or phone', done: !!(club.contactEmail || club.contactPhone) },
    { label: 'Club rules', done: !!club.rules?.trim() },
    { label: 'Website or social links', done: !!club.website || Object.keys(club.socialLinks ?? {}).length > 0 },
  ]
}
