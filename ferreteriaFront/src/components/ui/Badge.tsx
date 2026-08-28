import type { HTMLAttributes, ReactNode } from 'react'

type Tone = 'default' | 'success' | 'danger' | 'warning' | 'info'

const TONE: Record<Tone, string> = {
  default: 'bg-warmbg text-ink border-line',
  success: 'bg-green-50 text-green-700 border-green-200 dark:bg-green-950/40 dark:text-green-400 dark:border-green-900/60',
  danger: 'bg-red-50 text-red-700 border-red-200 dark:bg-red-950/40 dark:text-red-400 dark:border-red-900/60',
  warning: 'bg-amber-50 text-amber-800 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-900/60',
  info: 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-400 dark:border-blue-900/60',
}

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone
  children: ReactNode
}

export function Badge({ tone = 'default', className = '', children, ...rest }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2 py-0.5 text-xs font-medium ${TONE[tone]} ${className}`}
      {...rest}
    >
      {children}
    </span>
  )
}

const SEMAFORO_ESTADOS: Record<string, Tone> = {
  COMPLETADA: 'success',
  ACTIVA: 'success',
  ABIERTA: 'success',
  LIQUIDADA: 'success',
  CERRADO: 'success',
  ABIERTO: 'info',
  VIGENTE: 'info',
  EN_PROCESO: 'info',
  BORRADOR: 'default',
  PENDIENTE: 'warning',
  PARCIAL: 'warning',
  PROGRAMADA: 'warning',
  EMITIDA: 'info',
  CANCELADA: 'danger',
  VENCIDA: 'danger',
  EXPIRADA: 'danger',
  APLICADO: 'success',
  CONVERTIDA: 'success',
  FINALIZADA: 'default',
}

export function EstadoBadge({ estado }: { estado: string }) {
  const tone = SEMAFORO_ESTADOS[estado.toUpperCase()] ?? 'default'
  return <Badge tone={tone}>{estado}</Badge>
}