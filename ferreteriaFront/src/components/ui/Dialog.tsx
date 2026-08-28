import { useEffect, useRef, type ReactNode } from 'react'
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

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

export function Dialog({ open, onClose, title, children, footer, width = 'max-w-lg' }: DialogProps) {
  const t = useT()
  const sectionRef = useRef<HTMLElement>(null)
  const previouslyFocused = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!open) return
    previouslyFocused.current = document.activeElement as HTMLElement | null
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
        return
      }
      if (e.key === 'Tab' && sectionRef.current) {
        const focusables = sectionRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)
        if (focusables.length === 0) {
          e.preventDefault()
          sectionRef.current.focus()
          return
        }
        const primero = focusables[0]
        const ultimo = focusables[focusables.length - 1]
        const activo = document.activeElement as HTMLElement | null
        if (e.shiftKey && (activo === primero || !sectionRef.current.contains(activo))) {
          e.preventDefault()
          ultimo.focus()
        } else if (!e.shiftKey && activo === ultimo) {
          e.preventDefault()
          primero.focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const t = window.setTimeout(() => {
      if (!sectionRef.current) return
      const focusables = sectionRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)
      const objetivo = focusables[0] ?? sectionRef.current
      objetivo.focus()
    }, 0)
    return () => {
      window.clearTimeout(t)
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
      previouslyFocused.current?.focus()
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
        ref={sectionRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className={`w-full ${width} rounded-lg border border-line bg-surface shadow-xl outline-none`}
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