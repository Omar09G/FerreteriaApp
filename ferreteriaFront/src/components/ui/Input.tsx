import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'

interface CampoProps {
  label?: string
  error?: string
  required?: boolean
  hint?: string
}

export function CampoWidget({ label, error, required, hint, children }: { label?: string; error?: string; required?: boolean; hint?: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-sm">
      {label && (
        <span className="font-medium text-ink">
          {label}
          {required && <span className="text-red-600"> *</span>}
        </span>
      )}
      {children}
      {hint && !error && <span className="text-xs text-muted">{hint}</span>}
      {error && <span className="text-xs text-red-600">{error}</span>}
    </label>
  )
}

const BASE =
  'rounded-md border border-line bg-surface px-3 py-2 text-sm text-ink placeholder:text-muted focus:outline-2 focus:outline-primary disabled:bg-warmbg'

export function Input({
  label,
  error,
  hint,
  required,
  icono,
  className = '',
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & CampoProps & { icono?: ReactNode }) {
  return (
    <CampoWidget label={label} error={error} hint={hint} required={required}>
      <span className="relative block">
        {icono && <span className="pointer-events-none absolute inset-y-0 left-2.5 flex items-center">{icono}</span>}
        <input
          required={required}
          aria-invalid={Boolean(error)}
          className={`${BASE} ${icono ? 'pl-9' : ''} ${error ? 'border-red-500' : ''} ${className}`}
          {...rest}
        />
      </span>
    </CampoWidget>
  )
}

export function Select({
  label,
  error,
  hint,
  required,
  className = '',
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & CampoProps) {
  return (
    <CampoWidget label={label} error={error} hint={hint} required={required}>
      <select required={required} aria-invalid={Boolean(error)} className={`${BASE} ${error ? 'border-red-500' : ''} ${className}`} {...rest}>
        {children}
      </select>
    </CampoWidget>
  )
}