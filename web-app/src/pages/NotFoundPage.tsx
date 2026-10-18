import { EmptyState, LinkButton } from '../components/ui'

export default function NotFoundPage() {
  return (
    <div className="page">
      <EmptyState title="Page not found" action={<LinkButton to="/">Go home</LinkButton>}>
        The page you are looking for doesn't exist.
      </EmptyState>
    </div>
  )
}
