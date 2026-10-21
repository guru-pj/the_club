import { mediaUrl } from '../../api/client'
import { Avatar } from '../../components/ui'

export function ClubLogo({ name, url, size = 48 }: { name: string; url?: string | null; size?: number }) {
  if (url) return <img className="club-logo" src={mediaUrl(url)} alt="" width={size} height={size} style={{ width: size, height: size }} />
  return <Avatar name={name} size={size} />
}
