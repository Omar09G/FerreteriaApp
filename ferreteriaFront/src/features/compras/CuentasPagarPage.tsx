import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CalendarClock, FileWarning } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiCuentasPagar, apiFacturasPendientes, apiFacturasVencidas } from '@/lib/api/compras'
import type { CuentasPagar, FacturaPendiente, FacturaVencida } from '@/lib/api/types'
import { formatoFecha, formatoMoneda } from '@/lib/format'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

export default function CuentasPagarPage() {
  useDocumentTitle('Cuentas por pagar')
  const { error: mostrarError } = useToast()
  const [tab, setTab] = useState<'todas' | 'pendientes' | 'vencidas'>('todas')

  const cuentas = useQuery({ queryKey: ['cuentas-pagar'], queryFn: apiCuentasPagar })
  const pendientes = useQuery({ queryKey: ['facturas-pendientes'], queryFn: apiFacturasPendientes })
  const vencidas = useQuery({ queryKey: ['facturas-vencidas'], queryFn: apiFacturasVencidas })

  useEffect(() => {
    if (cuentas.error) mostrarError(esApiError(cuentas.error) ? cuentas.error.mensajeParaUsuario() : String(cuentas.error))
  }, [cuentas.error, mostrarError])
  useEffect(() => {
    if (pendientes.error) mostrarError(esApiError(pendientes.error) ? pendientes.error.mensajeParaUsuario() : String(pendientes.error))
  }, [pendientes.error, mostrarError])
  useEffect(() => {
    if (vencidas.error) mostrarError(esApiError(vencidas.error) ? vencidas.error.mensajeParaUsuario() : String(vencidas.error))
  }, [vencidas.error, mostrarError])

  const columnas: Columna<CuentasPagar>[] = [
    { key: 'c', header: 'Compra', render: (v) => <span className="font-medium text-ink">{v.compraFolio}</span> },
    { key: 'p', header: 'Proveedor', render: (v) => v.proveedor },
    { key: 'tot', header: 'Total', align: 'right', render: (v) => <span className="tabular-nums">{formatoMoneda(v.montoTotal)}</span> },
    { key: 'pag', header: 'Pagado', align: 'right', render: (v) => <span className="tabular-nums text-muted">{formatoMoneda(v.montoPagado)}</span> },
    { key: 'sal', header: 'Saldo', align: 'right', render: (v) => <span className="font-medium tabular-nums">{formatoMoneda(v.saldo)}</span> },
    { key: 'vto', header: 'Vence', render: (v) => <span className="whitespace-nowrap tabular-nums">{formatoFecha(v.fechaVencimiento)}</span> },
    {
      key: 'est',
      header: 'Estado',
      render: (v) =>
        v.estado === 'PAGADA' ? (
          <Badge tone="success">Pagada</Badge>
        ) : v.diasVencido > 0 ? (
          <Badge tone="danger">Vencida {v.diasVencido}d</Badge>
        ) : (
          <Badge tone="warning">Pendiente</Badge>
        ),
    },
  ]

  const resumenPendiente = (v: FacturaPendiente) => (
    <div key={v.cuentaPagarId} className="flex items-center justify-between gap-3 rounded-md border border-line px-3 py-2 text-sm">
      <span className="min-w-0">
        <span className="block truncate font-medium text-ink">{v.proveedor}</span>
        <span className="text-xs text-muted">
          {v.compraFolio} · vence {formatoFecha(v.fechaVencimiento)} · {v.alerta}
        </span>
      </span>
      <span className="shrink-0 font-semibold tabular-nums">{formatoMoneda(v.saldo)}</span>
    </div>
  )

  const resumenVencida = (v: FacturaVencida) => (
    <div key={v.cuentaPagarId} className="flex items-center justify-between gap-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm">
      <span className="min-w-0">
        <span className="block truncate font-medium text-red-900">{v.proveedor}</span>
        <span className="text-xs text-red-700">
          {v.compraFolio} · {v.diasVencido}d de retraso · {v.antiguedad}
        </span>
      </span>
      <span className="shrink-0 font-semibold tabular-nums text-red-900">{formatoMoneda(v.saldo)}</span>
    </div>
  )

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-xl font-bold text-ink">Cuentas por pagar</h1>
        <p className="text-sm text-muted">Compromisos con proveedores: pendientes, próximos a vencer y vencidos.</p>
      </header>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card titulo="Próximas a vencer">
          {pendientes.isLoading ? (
            <Spinner />
          ) : pendientes.data && pendientes.data.length > 0 ? (
            <div className="space-y-2">
              <p className="text-xs text-muted">
                <CalendarClock className="mr-1 inline h-3.5 w-3.5" />
                {pendientes.data.length} factura(s) con saldo y fecha de vencimiento futura o reciente.
              </p>
              {pendientes.data.map(resumenPendiente)}
            </div>
          ) : (
            <p className="py-4 text-center text-sm text-muted">Sin facturas por vencer.</p>
          )}
        </Card>
        <Card titulo="Vencidas — prioridad de pago">
          {vencidas.isLoading ? (
            <Spinner />
          ) : vencidas.data && vencidas.data.length > 0 ? (
            <div className="space-y-2">
              <p className="text-xs text-muted">
                <FileWarning className="mr-1 inline h-3.5 w-3.5" />
                {vencidas.data.length} factura(s) con retraso. Contacta al proveedor y regulariza.
              </p>
              {vencidas.data.map(resumenVencida)}
            </div>
          ) : (
            <p className="py-4 text-center text-sm text-muted">Sin facturas vencidas.</p>
          )}
        </Card>
      </div>

      <div className="flex gap-2">
        {(['todas', 'pendientes', 'vencidas'] as const).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-primary text-white' : 'border border-line bg-surface text-ink hover:bg-warmbg'}`}
          >
            {t === 'todas' ? 'Todas' : t === 'pendientes' ? 'Pendientes' : 'Vencidas'}
          </button>
        ))}
      </div>

      {cuentas.isLoading ? (
        <Spinner />
      ) : cuentas.data && (
        <Card titulo={`Cuentas (${cuentas.data.length})`}>
          <DataTable
            columnas={columnas}
            items={tab === 'todas' ? cuentas.data : tab === 'pendientes' ? cuentas.data.filter((c) => c.estado !== 'PAGADA' && c.diasVencido <= 0) : cuentas.data.filter((c) => c.diasVencido > 0)}
            rowKey={(v) => v.cuentaPagarId}
          />
        </Card>
      )}
    </div>
  )
}