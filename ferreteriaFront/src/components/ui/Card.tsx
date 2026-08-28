import type { HTMLAttributes, ReactNode } from 'react'

interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  titulo?: ReactNode
  actions?: ReactNode
  children: ReactNode
}

export function Card({ titulo, actions, children, className = '', ...rest }: CardProps) {
  return (
    <section className={`rounded-lg border border-line bg-surface shadow-sm ${className}`} {...rest}>
      {(titulo || actions) && (
        <header className="flex items-center justify-between gap-2 border-b border-line px-4 py-3">
          <h2 className="text-sm font-semibold text-ink">{titulo}</h2>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}