import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { rangoFechas, type RangoFechas } from '@/lib/rango'
import { formatoFecha, formatoMoneda, formatoNumero, formatoPorcentaje } from '@/lib/format'
import { apiCierreDiario } from '@/lib/api/reportes'
import { esApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'
import { ReporteHeader } from './ReporteHeader'
import type { CierreDiario } from '@/lib/api/types'

export default function CierreDiarioPage() {
  useDocumentTitle('Cierre diario')
  const { error: mostrarError } = useToast()
  const [rango, setRango] = useState<RangoFechas>(() => rangoFechas())

  const { data, isLoading, error } = useQuery({
    queryKey: ['cierre-diario', rango.inicio, rango.fin],
    queryFn: () => apiCierreDiario(rango.inicio, rango.fin),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const columnas: Columna<CierreDiario>[] = [
    { key: 'f', header: 'Fecha', render: (v) => formatoFecha(v.fecha) },
    { key: 'nc', header: 'Cortes', align: 'right', render: (v) => formatoNumero(v.numCortes) },
    { key: 't', header: 'Tickets', align: 'right', render: (v) => formatoNumero(v.tickets) },
    { key: 'tv', header: 'Total vendido', align: 'right', render: (v) => formatoMoneda(v.totalVendido) },
    { key: 'ub', header: 'Utilidad bruta', align: 'right', render: (v) => formatoMoneda(v.utilidadBruta) },
    { key: 'margen', header: 'Margen', align: 'right', render: (v) => formatoPorcentaje(v.margenPctPromedio) },
    { key: 'ee', header: 'Entradas efectivo', align: 'right', render: (v) => formatoMoneda(v.entradasEfectivo) },
    {
      key: 'estado',
      header: 'Cuadratura',
      render: (v) => (v.todoCuadrado ? <Badge tone="success">Cuadrado</Badge> : <Badge tone="danger">Diferencia</Badge>),
    },
  ]

  return (
    <div className="space-y-4">
      <ReporteHeader
        titulo="Cierre diario"
        subtitulo="Cuadratura de cortes por día en el periodo."
        rango={rango}
        onChange={setRango}
      />
      {isLoading && <Spinner />}
      {data && data.length > 0 && (
        <Card titulo="Cortes del periodo">
          <DataTable columnas={columnas} items={data} rowKey={(v) => v.fecha} caption="Cierre diario" />
        </Card>
      )}
      {data && data.length === 0 && !isLoading && (
        <EmptyState title="Sin cortes en el periodo" descripcion="Cambia el rango de fechas." />
      )}
    </div>
  )
}