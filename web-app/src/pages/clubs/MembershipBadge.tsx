import type { MembershipRole } from '../../api/types'
import { Badge } from '../../components/ui'

/** Your relation to a club as people read it: owner, or subscribed (a membership). */
export function MembershipBadge({ role }: { role: MembershipRole | null | undefined }) {
  if (role === 'OWNER') return <Badge tone="accent">Owner</Badge>
  if (role === 'MEMBER') return <Badge tone="primary">Subscribed</Badge>
  return null
}
