import type { ReactNode } from 'react'

import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'

interface ConfirmDialogProps {
  open: boolean
  title: string
  children: ReactNode
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'danger' | 'success' | 'primary'
  busy?: boolean
  onCancel: () => void
  onConfirm: () => void
}

export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  tone = 'danger',
  busy = false,
  onCancel,
  onConfirm,
}: ConfirmDialogProps) {
  const variant = tone === 'danger' ? 'danger' : tone === 'success' ? 'success' : 'primary'
  return (
    <Dialog
      open={open}
      onClose={() => !busy && onCancel()}
      title={title}
      width="max-w-md"
      footer={
        <>
          <Button variant="ghost" disabled={busy} onClick={onCancel}>
            {cancelLabel}
          </Button>
          <Button variant={variant} disabled={busy} onClick={onConfirm}>
            {busy ? 'Procesando…' : confirmLabel}
          </Button>
        </>
      }
    >
      {children}
    </Dialog>
  )
}