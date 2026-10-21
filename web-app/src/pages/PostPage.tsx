import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError, mediaUrl } from '../api/client'
import { clubsApi, postsApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { Card, EmptyState, ErrorMessage, LinkButton, Loading, PageHeader } from '../components/ui'
import { timeAgo } from '../lib/format'
import { useToast } from '../components/Toast'
import { useAsync } from '../lib/useAsync'
import { ConfirmModal } from './clubs/ConfirmModal'
import { ClubLogo } from './clubs/ClubLogo'
import { PostCard } from './clubs/PostCard'
import './clubs.css'

/** Someone opened a shared link without being signed in: a preview, and a way in. */
function GuestPreview({ clubId, postId }: { clubId: string; postId: string }) {
  const location = useLocation()
  const preview = useAsync(() => postsApi.preview(clubId, postId), [clubId, postId])
  const from = { from: location.pathname }

  if (preview.error instanceof ApiError && preview.error.status === 404) {
    return <Card><EmptyState title="This post is no longer available" /></Card>
  }
  if (preview.error) return <ErrorMessage error={preview.error} onRetry={preview.reload} />
  if (!preview.data) return <Loading />
  const p = preview.data
  return (
    <Card>
      <div className="stack">
        <div className="row">
          <ClubLogo name={p.clubName} url={p.clubLogoUrl} size={40} />
          <div>
            <div className="bold">{p.clubName}</div>
            <div className="small muted">{p.authorName ? `${p.authorName} · ` : ''}{timeAgo(p.createdAt)}</div>
          </div>
        </div>
        <h2>{p.title}</h2>
        {p.excerpt && <p className="pre-wrap">{p.excerpt}</p>}
        {p.photoUrl && <img className="shared-photo" src={mediaUrl(p.photoUrl)} alt="" />}
        <div className="callout">
          <p className="bold">Join The Club to see the whole post, comment and meet the club.</p>
          <div className="row" style={{ marginTop: 10 }}>
            <LinkButton to="/login" state={from}>Log in</LinkButton>
            <LinkButton to="/register" state={from} variant="secondary">Create an account</LinkButton>
          </div>
        </div>
      </div>
    </Card>
  )
}

function SignedInPost({ clubId, postId }: { clubId: string; postId: string }) {
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [deleting, setDeleting] = useState(false)
  const state = useAsync(async () => {
    const [post, club] = await Promise.all([postsApi.get(clubId, postId), clubsApi.get(clubId)])
    return { post, club }
  }, [clubId, postId])

  if (state.error instanceof ApiError && state.error.status === 404) {
    return <Card><EmptyState title="This post is no longer available" action={<LinkButton to={`/clubs/${clubId}`}>Go to the club</LinkButton>} /></Card>
  }
  if (state.error) return <ErrorMessage error={state.error} onRetry={state.reload} />
  if (!state.data) return <Loading />
  const { post, club } = state.data
  const canManage = club.myRole === 'OWNER' || hasRole('ADMIN')
  return (
    <>
      <PostCard post={post} club={club} canManage={canManage} commentsOpen onDelete={() => setDeleting(true)}
                onChange={(updated) => state.setData((s) => ({ club, ...s, post: updated }))} />
      <ConfirmModal open={deleting} title="Delete post?" confirmLabel="Delete" onClose={() => setDeleting(false)}
                    onConfirm={async () => {
                      await postsApi.remove(clubId, postId)
                      toast.success('Post deleted')
                      navigate(`/clubs/${clubId}`, { replace: true })
                    }}>
        “{post.title}” will be removed from the feed for everyone, with its likes and comments.
      </ConfirmModal>
    </>
  )
}

/** A single post: the page shared links point to. Reachable signed out (as a preview). */
export default function PostPage() {
  const { clubId = '', postId = '' } = useParams()
  const { user } = useAuth()
  if (user === undefined) return <Loading />
  return (
    <div className={user ? 'page page-narrow' : 'auth-shell'}>
      <div className={user ? 'stack-lg' : 'auth-card stack-lg'} style={user ? undefined : { maxWidth: 560 }}>
        {user ? <PageHeader back={{ to: `/clubs/${clubId}`, label: 'Back to the club' }} title="Post" />
          : <Link to="/login" className="brand">The Club</Link>}
        {user ? <SignedInPost clubId={clubId} postId={postId} /> : <GuestPreview clubId={clubId} postId={postId} />}
      </div>
    </div>
  )
}
