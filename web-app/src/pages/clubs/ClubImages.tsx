import { mediaUrl } from '../../api/client'
import { clubsApi } from '../../api/endpoints'
import type { Club } from '../../api/types'
import { ImagePicker } from '../../components/ImagePicker'
import { useToast } from '../../components/Toast'

/** The club's logo and cover photo, uploaded straight away. */
export function ClubImages({ club, onChanged }: { club: Club; onChanged: (club: Club) => void }) {
  const toast = useToast()
  return (
    <div className="stack" style={{ marginBottom: 16 }}>
      <ImagePicker label="Club logo" currentUrl={mediaUrl(club.logoUrl)} onUpload={async (file) => {
        onChanged(await clubsApi.uploadLogo(club.id, file))
        toast.success('Logo updated')
      }} />
      <ImagePicker label="Cover photo" shape="wide" currentUrl={mediaUrl(club.coverUrl)}
                   onUpload={async (file) => {
                     onChanged(await clubsApi.uploadCover(club.id, file))
                     toast.success('Cover photo updated')
                   }}
                   onRemove={async () => {
                     onChanged(await clubsApi.removeCover(club.id))
                     toast.success('Cover photo removed')
                   }} />
    </div>
  )
}
