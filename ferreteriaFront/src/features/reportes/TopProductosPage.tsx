import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Medal } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { rangoFechas, type RangoFechas } from '@/lib/rango'
import { formatoFecha, formatoMoneda, formatoNumero } from '@/lib/format'
import { apiTopProductos } from '@/lib/api/reportes'
import { esApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'
import { ReporteHeader } from './ReporteHeader'
import type { TopProducto } from '@/lib/api/types'

export default function TopProductosPage() {
  useDocumentTitle('Top productos')
  const { error: mostrarError } = useToast()
  const [rango, setRango] = useState<RangoFechas>(() => rangoFechas())

  const { data, isLoading, error } = useQuery({
    queryKey: ['top-productos', rango.inicio, rango.fin],
    queryFn: () => apiTopProductos(rango.inicio, rango.fin),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const posicion = (ranking: number) =>
    ranking === 1 ? (
      <Badge tone="warning">
        <Medal className="h-3 w-3" /> 1°
      </Badge>
    ) : (
      <Badge tone="default">{ranking}°</Badge>
    )

  const columnas: Columna<TopProducto>[] = [
    { key: 'p', header: 'Posición', render: (v) => posicion(v.rankingMes) },
    { key: 'c', header: 'Código', render: (v) => v.codigo ?? '—' },
    { key: 'n', header: 'Producto', render: (v) => <span className="font-medium text-ink">{v.producto}</span> },
    { key: 'cat', header: 'Categoría', render: (v) => v.categoria },
    { key: 'u', header: 'Unidades', align: 'right', render: (v) => formatoNumero(v.unidadesVendidas) },
    { key: 'i', header: 'Ingreso', align: 'right', render: (v) => formatoMoneda(v.ingresoTotal) },
    { key: 'c2', header: 'Costo', align: 'right', render: (v) => formatoMoneda(v.costoTotal) },
    { key: 'ut', header: 'Utilidad', align: 'right', render: (v) => formatoMoneda(v.utilidad) },
  ]

  return (
    <div className="space-y-4">
      <ReporteHeader
        titulo="Productos más vendidos"
        subtitulo={`Periodo: ${formatoFecha(rango.inicio)} – ${formatoFecha(rango.fin)}.`}
        rango={rango}
        onChange={setRango}
      />
      {isLoading && <Spinner />}
      {data && data.length > 0 && (
        <Card titulo="Ranking del periodo">
          <DataTable columnas={columnas} items={data} rowKey={(v) => v.productoId} caption="Top productos" />
        </Card>
      )}
      {data && data.length === 0 && !isLoading && (
        <EmptyState title="Sin ventas en el periodo" descripcion="Cambia el rango de fechas." />
      )}
    </div>
  )
}