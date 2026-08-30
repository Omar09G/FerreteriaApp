import { CalendarRange } from 'lucide-react'

import { rangoFechas, type RangoFechas } from '@/lib/rango'
import { Button } from './Button'
import { DateRangePicker } from './DateRangePicker'

interface RangoFiltroProps {
  valor: RangoFechas | null
  onChange: (rango: RangoFechas | null) => void
}

/**
 * Filtro de rango de fechas opcional: sin rango (Todos) por defecto;
 * al activarlo muestra el DateRangePicker con presets rápidos.
 */
export function RangoFiltro({ valor, onChange }: RangoFiltroProps) {
  if (valor) {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <DateRangePicker valor={valor} onChange={onChange} />
        <Button variant="ghost" size="sm" onClick={() => onChange(null)}>
          Todos
        </Button>
      </div>
    )
  }
  return (
    <Button variant="secondary" size="sm" onClick={() => onChange(rangoFechas())}>
      <CalendarRange className="h-4 w-4" /> Filtrar por rango
    </Button>
  )
}