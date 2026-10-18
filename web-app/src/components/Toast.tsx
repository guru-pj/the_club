import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

type ToastKind = 'info' | 'success' | 'error'
interface ToastItem { id: number; kind: ToastKind; message: string }

interface ToastApi {
  info: (message: string) => void
  success: (message: string) => void
  /** Accepts an Error (e.g. ApiError) or a message. */
  error: (error: unknown) => void
}

const ToastContext = createContext<ToastApi | null>(null)
let nextId = 1

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  const push = useCallback((kind: ToastKind, message: string) => {
    const id = nextId++
    setItems((all) => [...all, { id, kind, message }])
    setTimeout(() => setItems((all) => all.filter((t) => t.id !== id)), kind === 'error' ? 6000 : 3500)
  }, [])

  const api = useMemo<ToastApi>(() => ({
    info: (m) => push('info', m),
    success: (m) => push('success', m),
    error: (e) => push('error', e instanceof Error ? e.message : String(e)),
  }), [push])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts" aria-live="polite">
        {items.map((t) => <div key={t.id} className={`toast toast-${t.kind}`}>{t.message}</div>)}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside ToastProvider')
  return ctx
}
