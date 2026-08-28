import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success'
type Size = 'sm' | 'md' | 'lg'

const VARIANTES: Record<Variant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover disabled:bg-line',
  secondary: 'bg-surface text-ink border border-line hover:bg-warmbg disabled:bg-warmbg',
  ghost: 'text-primary hover:bg-orange-100 disabled:text-muted',
  danger: 'bg-red-600 text-white hover:bg-red-700 disabled:bg-line',
  success: 'bg-green-600 text-white hover:bg-green-700 disabled:bg-line',
}

const TAMANOS: Record<Size, string> = {
  sm: 'px-2.5 py-1.5 text-xs',
  md: 'px-3.5 py-2 text-sm',
  lg: 'px-5 py-2.5 text-base',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  children?: ReactNode
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', className = '', type = 'button', children, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={`inline-flex items-center justify-center gap-1.5 rounded-md font-medium transition-colors disabled:cursor-not-allowed disabled:text-muted ${VARIANTES[variant]} ${TAMANOS[size]} ${className}`}
      {...rest}
    >
      {children}
    </button>
  )
})