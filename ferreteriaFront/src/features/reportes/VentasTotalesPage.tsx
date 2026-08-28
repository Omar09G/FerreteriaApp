import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { rangoFechas, type RangoFechas } from '@/lib/rango'
import { formatoFecha, formatoMoneda, formatoNumero } from '@/lib/format'
import { apiVentasTotales } from '@/lib/api/reportes'
import { esApiError } from '@/lib/api/client'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'
import { ReporteHeader } from './ReporteHeader'
import type { VentaTotal } from '@/lib/api/types'

export default function VentasTotalesPage() {
  useDocumentTitle('Ventas totales')
  const { error: mostrarError } = useToast()
  const [rango, setRango] = useState<RangoFechas>(() => rangoFechas())

  const { data, isLoading, error } = useQuery({
    queryKey: ['ventas-totales', rango.inicio, rango.fin],
    queryFn: () => apiVentasTotales(rango.inicio, rango.fin),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const columnas: Columna<VentaTotal>[] = [
    { key: 'f', header: 'Fecha', render: (v) => formatoFecha(v.fecha) },
    { key: 'n', header: 'Ventas', align: 'right', render: (v) => formatoNumero(v.numVentas) },
    { key: 's', header: 'Subtotal', align: 'right', render: (v) => formatoMoneda(v.subtotal) },
    { key: 'iv', header: 'IVA', align: 'right', render: (v) => formatoMoneda(v.iva) },
    { key: 'd', header: 'Desc.', align: 'right', render: (v) => formatoMoneda(v.descuentos) },
    { key: 't', header: 'Total', align: 'right', render: (v) => formatoMoneda(v.totalVendido) },
    { key: 'c', header: 'Costo', align: 'right', render: (v) => formatoMoneda(v.costoVentas) },
    { key: 'u', header: 'Utilidad', align: 'right', render: (v) => formatoMoneda(v.utilidadBruta) },
  ]

  return (
    <div className="space-y-4">
      <ReporteHeader
        titulo="Ventas totales"
        subtitulo="Tendencia y desglose diario del periodo."
        rango={rango}
        onChange={setRango}
      />
      {isLoading && <Spinner />}
      {data && data.length > 0 && (
        <>
          <Card titulo="Total vendido y utilidad bruta por día">
            <ResponsiveContainer width="100%" height={280}>
              <AreaChart data={data} margin={{ left: 8, right: 8 }}>
                <defs>
                  <linearGradient id="gTotal" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#ea580c" stopOpacity={0.25} />
                    <stop offset="95%" stopColor="#ea580c" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#e7e5e4" />
                <XAxis dataKey="fecha" tickFormatter={formatoFecha} stroke="#57534e" fontSize={12} />
                <YAxis stroke="#57534e" fontSize={12} tickFormatter={(v: number) => formatoMoneda(v)} width={90} />
                <Tooltip formatter={(value, name) => [formatoMoneda(Number(value)), String(name)]} labelFormatter={(label) => formatoFecha(String(label))} />
                <Area type="monotone" dataKey="totalVendido" stroke="#ea580c" fill="url(#gTotal)" name="Total vendido" />
                <Area type="monotone" dataKey="utilidadBruta" stroke="#16a34a" fill="#16a34a22" name="Utilidad bruta" />
              </AreaChart>
            </ResponsiveContainer>
          </Card>
          <Card titulo="Detalle diario">
            <DataTable columnas={columnas} items={data} rowKey={(v) => v.fecha} caption="Ventas totales diarias" />
          </Card>
        </>
      )}
      {data && data.length === 0 && !isLoading && (
        <EmptyState title="Sin ventas en el periodo" descripcion="Ajusta el rango de fechas para ver resultados." />
      )}
    </div>
  )
}