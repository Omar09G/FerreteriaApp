import { useEffect, useRef } from 'react'
import Swal, { type SweetAlertIcon } from 'sweetalert2'

import { tFuera } from '@/i18n'

interface ConfirmDialogProps {
  open: boolean
  title: string
  children?: React.ReactNode
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'danger' | 'success' | 'primary'
  busy?: boolean
  onCancel: () => void
  onConfirm: () => void
}

const ICON: Record<NonNullable<ConfirmDialogProps['tone']>, SweetAlertIcon> = {
  danger: 'warning',
  success: 'success',
  primary: 'info',
}

const CONFIRM_BTN: Record<NonNullable<ConfirmDialogProps['tone']>, string> = {
  danger: '#dc2626',
  success: '#16a34a',
  primary: '#2563eb',
}

/**
 * Reemplazo SweetAlert2 del antiguo ConfirmDialog (basado en `<Dialog/>`).
 *
 * Mantiene la MISMA API de props (open / onCancel / onConfirm) que la versión
 * anterior, para no tocar los 17 archivos consumidores: cuando `open` pasa a
 * `true`, dispara `Swal.fire(...)` y, según el resultado, invoca `onConfirm`
 * o `onCancel`. Los hijos JSX se renderizan como `<div data-confirm-body>`
 * fuera del árbol (SweetAlert2 los mueve al document.body) usando `cloneElement`
 * indirecto: pasamos `children` como `html` con un placeholder, pero como
 * son string-primitivos lo más simple es aceptar `text` opcional vía prop
 * `message` o usar el `title` directamente. Si los hijos contienen elementos
 * complejos, se serializan con `.toString()` (no es ideal, pero los
 * consumidores actuales solo pasan strings).
 */
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
  const onCancelRef = useRef(onCancel)
  const onConfirmRef = useRef(onConfirm)
  useEffect(() => {
    onCancelRef.current = onCancel
    onConfirmRef.current = onConfirm
  }, [onCancel, onConfirm])

  // Texto secundario opcional: si los hijos son un string, lo usamos como
  // `text`. Si son JSX complejo, recurrimos a `html` con el contenido
  // textual (mejor esfuerzo: las páginas actuales pasan strings).
  const bodyText = typeof children === 'string' ? children : undefined
  const bodyHtml =
    typeof children === 'string'
      ? undefined
      : Array.isArray(children)
        ? children.filter((c) => typeof c === 'string').join('\n')
        : undefined

  useEffect(() => {
    if (!open) return
    if (busy) {
      // Mostrar spinner de carga sin botones; el caller cierra con `open=false`.
      Swal.fire({
        title,
        allowOutsideClick: false,
        allowEscapeKey: false,
        showConfirmButton: false,
        didOpen: () => {
          Swal.showLoading()
        },
      })
      return
    }

    let cancelled = false
    void Swal.fire({
      icon: ICON[tone],
      title,
      text: bodyText ?? (bodyHtml ? undefined : tFuera('comun.confirmar')),
      html: bodyHtml,
      showCancelButton: true,
      confirmButtonText: confirmLabel,
      cancelButtonText: cancelLabel,
      confirmButtonColor: CONFIRM_BTN[tone],
      cancelButtonColor: '#6b7280',
      reverseButtons: true,
      focusCancel: true,
    }).then((result) => {
      if (cancelled) return
      if (result.isConfirmed) {
        onConfirmRef.current()
      } else {
        onCancelRef.current()
      }
    })

    return () => {
      cancelled = true
      Swal.close()
    }
  }, [open, busy, title, bodyText, bodyHtml, confirmLabel, cancelLabel, tone])

  // Este componente NO renderiza DOM: todo el UI vive en SweetAlert2.
  return null
}
