import { useEffect, type ReactNode } from 'react'
import { X } from 'lucide-react'

import { useT } from '@/i18n'

interface DialogProps {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
  footer?: ReactNode
  width?: string
}

export function Dialog({ open, onClose, title, children, footer, width = 'max-w-lg' }: DialogProps) {
  const t = useT()
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`w-full ${width} rounded-lg border border-line bg-surface shadow-xl`}
      >
        <header className="flex items-center justify-between border-b border-line px-4 py-3">
          <h2 className="text-base font-semibold text-ink">{title}</h2>
          <button type="button" onClick={onClose} aria-label={t('comun.cerrar')} className="rounded p-1 text-muted hover:bg-warmbg">
            <X className="h-5 w-5" />
          </button>
        </header>
        <div className="max-h-[70vh] overflow-auto px-4 py-4">{children}</div>
        {footer && <footer className="flex justify-end gap-2 border-t border-line px-4 py-3">{footer}</footer>}
      </section>
    </div>
  )
}