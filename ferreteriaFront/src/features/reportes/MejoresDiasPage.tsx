import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { rangoFechas, type RangoFechas } from '@/lib/rango'
import { formatoMoneda } from '@/lib/format'
import { apiMejoresDias } from '@/lib/api/reportes'
import { esApiError } from '@/lib/api/client'
import { Card } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'
import { ReporteHeader } from './ReporteHeader'

export default function MejoresDiasPage() {
  useDocumentTitle('Mejores días')
  const { error: mostrarError } = useToast()
  const [rango, setRango] = useState<RangoFechas>(() => rangoFechas())

  const { data, isLoading, error } = useQuery({
    queryKey: ['mejores-dias', rango.inicio, rango.fin],
    queryFn: () => apiMejoresDias(rango.inicio, rango.fin),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  return (
    <div className="space-y-4">
      <ReporteHeader
        titulo="Mejores días de venta"
        subtitulo="Días de la semana con mayor acumulado en el periodo."
        rango={rango}
        onChange={setRango}
      />
      {isLoading && <Spinner />}
      {data && data.length > 0 && (
        <Card titulo="Total acumulado por día de la semana">
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={data} margin={{ left: 8, right: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e7e5e4" />
              <XAxis dataKey="diaSemana" stroke="#57534e" fontSize={12} />
              <YAxis stroke="#57534e" fontSize={12} tickFormatter={(v: number) => formatoMoneda(v)} width={90} />
              <Tooltip formatter={(value, name) => [formatoMoneda(Number(value)), String(name)]} />
              <Bar dataKey="totalAcumulado" fill="#f97316" radius={[3, 3, 0, 0]} name="Total acumulado" />
            </BarChart>
          </ResponsiveContainer>
        </Card>
      )}
      {data && data.length === 0 && !isLoading && (
        <EmptyState title="Sin ventas en el periodo" descripcion="Cambia el rango de fechas." />
      )}
    </div>
  )
}