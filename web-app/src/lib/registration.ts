import type { ClubEvent, RegistrationState } from '../api/types'
import type { Tone } from '../components/ui'

/**
 * The status chip on event cards and pages: your own registration first, otherwise whether you can register
 * (and if not, why).
 */
export function registrationBadge(event: Pick<ClubEvent, 'registrationState' | 'myRegistration' | 'spotsLeft' | 'subscribersOnly'>):
  { label: string; tone: Tone } {
  switch (event.myRegistration) {
    case 'CONFIRMED': return { label: 'Registered', tone: 'success' }
    case 'CHECKED_IN': return { label: 'Checked in', tone: 'success' }
    case 'PENDING_PAYMENT': return { label: 'Payment pending', tone: 'warning' }
    default: break
  }
  const labels: Record<RegistrationState, { label: string; tone: Tone }> = {
    OPEN: event.spotsLeft != null && event.spotsLeft <= 5
      ? { label: `Open · ${event.spotsLeft} ${event.spotsLeft === 1 ? 'spot' : 'spots'} left`, tone: 'warning' }
      : { label: 'Registration open', tone: 'primary' },
    DRAFT: { label: 'Draft', tone: 'neutral' },
    NOT_YET_OPEN: { label: 'Opens soon', tone: 'neutral' },
    CLOSED: { label: 'Registration closed', tone: 'neutral' },
    FULL: { label: 'Full', tone: 'danger' },
    PAST: { label: 'Ended', tone: 'neutral' },
    CANCELLED: { label: 'Cancelled', tone: 'danger' },
  }
  return labels[event.registrationState] ?? { label: 'Registration closed', tone: 'neutral' }
}
