import type { ReactNode } from 'react'
import { Brand } from './Brand'

/** Centered card used by the signed-out pages (login, register, password reset, email verification). */
export function AuthCard({ subtitle, children, footer }: { subtitle?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="auth-shell">
      <div className="auth-card">
        <div className="card">
          <Brand />
          {subtitle && <p className="muted" style={{ marginBottom: 20 }}>{subtitle}</p>}
          {children}
        </div>
        {footer && <p className="auth-footer muted">{footer}</p>}
      </div>
    </div>
  )
}
