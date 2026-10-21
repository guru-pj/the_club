import { chatApi } from '../../api/endpoints'
import type { Club } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ChatView } from '../../components/chat/ChatView'
import { ChatIcon } from '../../components/icons'
import { Card, EmptyState, ErrorMessage, Loading } from '../../components/ui'
import { useAsync } from '../../lib/useAsync'

/** The club's group chat: for its subscribers and owner. */
export function ChatTab({ club, onSubscribe }: { club: Club; onSubscribe?: () => void }) {
  const { hasRole } = useAuth()
  const allowed = !!club.myRole || hasRole('ADMIN')
  const chat = useAsync(() => (allowed ? chatApi.club(club.id) : Promise.resolve(null)), [club.id, allowed])

  if (!allowed) {
    return (
      <Card>
        <EmptyState icon={<ChatIcon size={28} />} title="The club chat is for subscribers"
                    action={club.subscriptionsEnabled && onSubscribe
                      ? <button className="btn btn-accent" onClick={onSubscribe}>Subscribe to join</button> : undefined}>
          {club.subscriptionsEnabled ? 'Subscribe to the club to talk with its members.' : "This club isn't taking subscribers right now."}
        </EmptyState>
      </Card>
    )
  }
  if (chat.error) return <ErrorMessage error={chat.error} onRetry={chat.reload} />
  if (!chat.data?.id) return <Loading />
  return (
    <Card flush className="chat-card">
      <ChatView key={chat.data.id} conversationId={chat.data.id} header={false} />
    </Card>
  )
}
