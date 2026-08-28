/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react'

import { useT } from '@/i18n'

type TipoToast = 'success' | 'error' | 'info' | 'warning'

interface Toast {
  id: number
  tipo: TipoToast
  mensaje: string
}

interface ToastContextValue {
  toast: (tipo: TipoToast, mensaje: string) => void
  success: (mensaje: string) => void
  error: (mensaje: string) => void
  info: (mensaje: string) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

const ESTILO: Record<TipoToast, { icono: ReactNode; panel: string; chip: string; titulo: string }> = {
  success: {
    icono: <CheckCircle2 className="h-4 w-4 text-white" aria-hidden />,
    panel: 'border-green-200 border-l-green-600 bg-green-50 dark:border-green-900/60 dark:border-l-green-500 dark:bg-green-950/40',
    chip: 'bg-green-600',
    titulo: 'alerta.success',
  },
  error: {
    icono: <XCircle className="h-4 w-4 text-white" aria-hidden />,
    panel: 'border-red-200 border-l-red-600 bg-red-50 dark:border-red-900/60 dark:border-l-red-500 dark:bg-red-950/40',
    chip: 'bg-red-600',
    titulo: 'alerta.error',
  },
  info: {
    icono: <Info className="h-4 w-4 text-white" aria-hidden />,
    panel: 'border-blue-200 border-l-blue-600 bg-blue-50 dark:border-blue-900/60 dark:border-l-blue-500 dark:bg-blue-950/40',
    chip: 'bg-blue-600',
    titulo: 'alerta.info',
  },
  warning: {
    icono: <AlertTriangle className="h-4 w-4 text-white" aria-hidden />,
    panel: 'border-amber-200 border-l-amber-500 bg-amber-50 dark:border-amber-900/60 dark:border-l-amber-500 dark:bg-amber-950/40',
    chip: 'bg-amber-500',
    titulo: 'alerta.warning',
  },
}

let nextId = 1

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const t = useT()

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }, [])

  const toast = useCallback(
    (tipo: TipoToast, mensaje: string) => {
      const id = nextId++
      setToasts((prev) => [...prev.slice(-3), { id, tipo, mensaje }])
      window.setTimeout(() => dismiss(id), tipo === 'error' ? 8000 : 5000)
    },
    [dismiss],
  )

  const value = useMemo<ToastContextValue>(
    () => ({
      toast,
      success: (m) => toast('success', m),
      error: (m) => toast('error', m),
      info: (m) => toast('info', m),
    }),
    [toast],
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed right-4 top-4 z-[60] flex w-[22rem] max-w-[calc(100vw-2rem)] flex-col gap-2">
        {toasts.map((aviso) => {
          const estilo = ESTILO[aviso.tipo]
          return (
            <div
              key={aviso.id}
              role={aviso.tipo === 'error' ? 'alert' : 'status'}
              className={`pointer-events-auto flex items-start gap-3 rounded-md border border-l-4 p-3 shadow-lg ${estilo.panel}`}
            >
              <span className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${estilo.chip}`} aria-hidden>
                {estilo.icono}
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-ink">{t(estilo.titulo)}</p>
                <p className="break-words text-sm text-muted">{aviso.mensaje}</p>
              </div>
              <button
                type="button"
                onClick={() => dismiss(aviso.id)}
                className="shrink-0 text-xs text-muted hover:text-ink"
                aria-label={t('alerta.cerrarAviso')}
              >
                ✕
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast debe usarse dentro de <ToastProvider>')
  return ctx
}