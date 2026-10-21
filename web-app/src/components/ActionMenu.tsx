import { useEffect, useRef, useState } from 'react'
import { MoreIcon } from './icons'

export interface MenuAction {
  label: string
  onSelect: () => void
  danger?: boolean
}

/** A "⋯" button with a short list of actions. Renders nothing without actions. */
export function ActionMenu({ actions, label = 'More actions', align = 'right' }:
  { actions: MenuAction[]; label?: string; align?: 'left' | 'right' }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onClick); document.removeEventListener('keydown', onKey) }
  }, [open])

  if (!actions.length) return null
  return (
    <div className="menu" ref={ref}>
      <button className="icon-button icon-button-sm" aria-label={label} aria-haspopup="menu" aria-expanded={open}
              onClick={() => setOpen((o) => !o)}>
        <MoreIcon size={18} />
      </button>
      {open && (
        <div className={`menu-panel menu-panel-compact${align === 'left' ? ' menu-panel-left' : ''}`} role="menu">
          {actions.map((a) => (
            <button key={a.label} role="menuitem" className={a.danger ? 'danger-text' : undefined}
                    onClick={() => { setOpen(false); a.onSelect() }}>
              {a.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
