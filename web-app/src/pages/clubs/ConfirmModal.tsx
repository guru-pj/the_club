import { useState, type ReactNode } from 'react'
import { useToast } from '../../components/Toast'
import { Button, Modal } from '../../components/ui'

/** Confirmation dialog for destructive actions; shows a toast if the action fails. */
export function ConfirmModal({ open, title, children, confirmLabel, onConfirm, onClose }: {
  open: boolean
  title: string
  children: ReactNode
  confirmLabel: string
  onConfirm: () => Promise<void>
  onClose: () => void
}) {
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const confirm = async () => {
    setBusy(true)
    try {
      await onConfirm()
      onClose()
    } catch (e) {
      toast.error(e)
    } finally {
      setBusy(false)
    }
  }
  return (
    <Modal open={open} title={title} onClose={onClose} footer={
      <>
        <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="danger" onClick={confirm} loading={busy}>{confirmLabel}</Button>
      </>
    }>
      {children}
    </Modal>
  )
}
