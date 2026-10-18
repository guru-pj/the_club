import { useNavigate } from 'react-router-dom'
import { clubsApi } from '../api/endpoints'
import { useToast } from '../components/Toast'
import { Card, PageHeader } from '../components/ui'
import { ClubForm } from './clubs/ClubForm'
import './clubs.css'

export default function CreateClubPage() {
  const navigate = useNavigate()
  const toast = useToast()
  return (
    <div className="page page-narrow">
      <PageHeader title="Create a club" subtitle="Members can find and join your club once it's created."
                  back={{ to: '/clubs', label: 'Clubs' }} />
      <Card>
        <ClubForm submitLabel="Create club" onCancel={() => navigate('/clubs')} onSubmit={async (request) => {
          const club = await clubsApi.create(request)
          toast.success(`${club.name} is live`)
          navigate(`/clubs/${club.id}`)
        }} />
      </Card>
    </div>
  )
}
