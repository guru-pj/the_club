import { useEffect, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { Link, type LinkProps } from 'react-router-dom'
import { humanize, initials } from '../lib/format'
import { ChevronLeftIcon, CloseIcon } from './icons'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'accent'
type Size = 'sm' | 'md' | 'lg'

function btnClass(variant: Variant = 'primary', size: Size = 'md', block?: boolean, extra?: string) {
  return ['btn', `btn-${variant}`, size !== 'md' && `btn-${size}`, block && 'btn-block', extra].filter(Boolean).join(' ')
}

export function Button({ variant, size, block, loading, children, className, disabled, type = 'button', ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: Size; block?: boolean; loading?: boolean }) {
  return (
    <button type={type} className={btnClass(variant, size, block, className)} disabled={disabled || loading} {...rest}>
      {loading && <span className="spinner spinner-sm" aria-hidden="true" />}
      {children}
    </button>
  )
}

export function LinkButton({ variant, size, block, className, ...rest }: LinkProps & { variant?: Variant; size?: Size; block?: boolean }) {
  return <Link className={btnClass(variant, size, block, className)} {...rest} />
}

export function Card({ title, actions, children, flush, className }:
  { title?: ReactNode; actions?: ReactNode; children: ReactNode; flush?: boolean; className?: string }) {
  return (
    <section className={['card', flush && 'card-flush', className].filter(Boolean).join(' ')}>
      {(title || actions) && (
        <div className="card-header" style={flush ? { padding: '16px 20px 0' } : undefined}>
          {title && <h2 className="card-title">{title}</h2>}
          {actions && <div className="row">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  )
}

export function PageHeader({ title, subtitle, actions, back }:
  { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode; back?: { to: string; label: string } }) {
  return (
    <div>
      {back && <Link to={back.to} className="back-link"><ChevronLeftIcon size={16} />{back.label}</Link>}
      <div className="page-header">
        <div>
          <h1>{title}</h1>
          {subtitle && <div className="subtitle">{subtitle}</div>}
        </div>
        {actions && <div className="row">{actions}</div>}
      </div>
    </div>
  )
}

export function Field({ label, error, hint, children, htmlFor }:
  { label: string; error?: string; hint?: ReactNode; children: ReactNode; htmlFor?: string }) {
  return (
    <div className="field">
      <label className="field-label" htmlFor={htmlFor}>{label}</label>
      {children}
      {error ? <span className="field-error" role="alert">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </div>
  )
}

export type Tone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'accent'

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={tone === 'neutral' ? 'badge' : `badge badge-${tone}`}>{children}</span>
}

const STATUS_TONES: Record<string, Tone> = {
  CONFIRMED: 'success', CHECKED_IN: 'primary', PENDING_PAYMENT: 'warning', CANCELLED: 'danger',
  SCHEDULED: 'info', COMPLETED: 'neutral',
  PENDING: 'warning', UNDER_REVIEW: 'info', NEEDS_INFO: 'accent', APPROVED: 'success', REJECTED: 'danger',
  SUCCEEDED: 'success', FAILED: 'danger', CREATED: 'warning',
  OWNER: 'accent', MEMBER: 'primary', STRAVA: 'accent', MANUAL: 'neutral',
}

const STATUS_LABELS: Record<string, string> = {
  CHECKED_IN: 'Checked in', PENDING_PAYMENT: 'Awaiting payment', NEEDS_INFO: 'More info needed', UNDER_REVIEW: 'Under review',
}

export function StatusBadge({ status }: { status: string | null | undefined }) {
  if (!status) return null
  return <Badge tone={STATUS_TONES[status] ?? 'neutral'}>{STATUS_LABELS[status] ?? humanize(status)}</Badge>
}

export function Spinner({ small }: { small?: boolean }) {
  return <span className={small ? 'spinner spinner-sm' : 'spinner'} role="status" aria-label="Loading" />
}

export function Loading() {
  return <div className="loading"><Spinner /></div>
}

export function ErrorMessage({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : String(error)
  return (
    <div className="alert alert-error" role="alert">
      <div style={{ flex: 1 }}>{message}</div>
      {onRetry && <Button size="sm" variant="secondary" onClick={onRetry}>Retry</Button>}
    </div>
  )
}

export function EmptyState({ icon, title, children, action }:
  { icon?: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      {icon}
      <div className="empty-title">{title}</div>
      {children && <div>{children}</div>}
      {action}
    </div>
  )
}

export function Pagination({ page, size, totalItems, onChange }:
  { page: number; size: number; totalItems: number; onChange: (page: number) => void }) {
  const pages = Math.max(1, Math.ceil(totalItems / size))
  if (pages <= 1) return null
  return (
    <div className="pagination">
      <Button size="sm" variant="secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>Previous</Button>
      <span className="muted small">Page {page + 1} of {pages}</span>
      <Button size="sm" variant="secondary" disabled={page + 1 >= pages} onClick={() => onChange(page + 1)}>Next</Button>
    </div>
  )
}

export function Tabs<T extends string>({ tabs, active, onChange }:
  { tabs: { id: T; label: ReactNode }[]; active: T; onChange: (id: T) => void }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button key={t.id} role="tab" aria-selected={active === t.id}
                className={active === t.id ? 'tab active' : 'tab'} onClick={() => onChange(t.id)}>
          {t.label}
        </button>
      ))}
    </div>
  )
}

export function Modal({ open, title, onClose, children, footer }:
  { open: boolean; title: ReactNode; onClose: () => void; children: ReactNode; footer?: ReactNode }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])
  if (!open) return null
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={typeof title === 'string' ? title : undefined}>
        <div className="modal-header">
          <h2>{title}</h2>
          <button className="icon-button" onClick={onClose} aria-label="Close"><CloseIcon /></button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  )
}

export function Avatar({ name, url, size = 36 }: { name?: string | null; url?: string | null; size?: number }) {
  return (
    <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.38 }}>
      {url ? <img src={url} alt="" /> : initials(name)}
    </span>
  )
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: ReactNode }) {
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {hint && <span className="muted small">{hint}</span>}
    </div>
  )
}

export function Rank({ rank }: { rank: number }) {
  return <span className={rank <= 3 ? `rank rank-${rank}` : 'rank'}>{rank}</span>
}
