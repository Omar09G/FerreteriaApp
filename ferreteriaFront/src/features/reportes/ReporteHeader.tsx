import type React from 'react'

import { DateRangePicker } from '@/components/ui/DateRangePicker'
import type { RangoFechas } from '@/lib/rango'

/** Cabecera presentacional para páginas de reporte; el rango lo controla la página. */
export function ReporteHeader({
  titulo,
  subtitulo,
  rango,
  onChange,
  children,
}: {
  titulo: string
  subtitulo: string
  rango: RangoFechas
  onChange: (r: RangoFechas) => void
  children?: React.ReactNode
}) {
  return (
    <header className="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 className="text-xl font-bold text-ink">{titulo}</h1>
        <p className="text-sm text-muted">{subtitulo}</p>
      </div>
      <DateRangePicker valor={rango} onChange={onChange} />
      {children}
    </header>
  )
}