import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, X } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiAlmacenes } from '@/lib/api/catalogo'
import { apiCancelarVenta, apiVentas } from '@/lib/api/venta'
import type { Venta } from '@/lib/api/types'
import { FORMAS_PAGO } from '@/lib/api/types'
import { formatoFecha, formatoFechaHora, formatoMoneda } from '@/lib/format'
import { rangoFechas, type RangoFechas } from '@/lib/rango'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { DateRangePicker } from '@/components/ui/DateRangePicker'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

function EstadoVenta({ estado }: { estado: string }) {
  if (estado === 'COMPLETADA') return <Badge tone="success">Completada</Badge>
  if (estado === 'CANCELADA') return <Badge tone="danger">Cancelada</Badge>
  return <Badge>{estado}</Badge>
}

function CancelarForm({
  venta,
  guardando,
  onGuardar,
  onClose,
}: {
  venta: Venta
  guardando: boolean
  onGuardar: (motivo: string) => void
  onClose: () => void
}) {
  const [motivo, setMotivo] = useState('')
  const invalido = motivo.trim().length < 5
  return (
    <div className="space-y-3">
      <p className="text-sm text-muted">
        Vas a cancelar la venta <span className="font-medium text-ink">{venta.folio}</span> por {formatoMoneda(venta.total)}. Esta acción devuelve el stock y registra el movimiento correspondiente.
      </p>
      <Input
        label="Motivo de cancelación"
        required
        value={motivo}
        onChange={(e) => setMotivo(e.target.value)}
        placeholder="Mínimo 5 caracteres: error de captura, cliente desistió, etc."
        hint={invalido ? 'Mínimo 5 caracteres.' : `${motivo.trim().length} caracteres.`}
      />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" disabled={guardando} onClick={onClose}>
          Cancelar
        </Button>
        <Button variant="danger" disabled={invalido || guardando} onClick={() => onGuardar(motivo.trim())}>
          <X className="h-4 w-4" /> Confirmar cancelación
        </Button>
      </div>
    </div>
  )
}

function DetalleVenta({ venta }: { venta: Venta }) {
  const forma = FORMAS_PAGO.find((f) => f.id === venta.formaPagoId)?.nombre ?? venta.formaPagoNombre
  return (
    <div className="space-y-3 text-sm">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <div>
          <p className="text-xs text-muted">Folio</p>
          <p className="font-medium text-ink">{venta.folio}</p>
        </div>
        <div>
          <p className="text-xs text-muted">Fecha</p>
          <p className="font-medium tabular-nums text-ink">{formatoFecha(venta.fechaLocal)}</p>
          <p className="text-xs tabular-nums text-muted">{formatoFechaHora(venta.fecha)}</p>
        </div>
        <div>
          <p className="text-xs text-muted">Estado</p>
          <EstadoVenta estado={venta.estado} />
        </div>
        <div>
          <p className="text-xs text-muted">Cliente</p>
          <p className="font-medium text-ink">{venta.clienteNombre ?? 'Consumidor final'}</p>
        </div>
        <div>
          <p className="text-xs text-muted">Almacén</p>
          <p className="font-medium text-ink">{venta.almacenNombre}</p>
        </div>
        <div>
          <p className="text-xs text-muted">Forma de pago</p>
          <p className="font-medium text-ink">{forma}</p>
        </div>
      </div>

      <div>
        <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">Artículos</p>
        <div className="overflow-x-auto rounded-md border border-line">
          <table className="w-full text-sm">
            <thead className="bg-canvas text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-2 py-1 text-left">Producto</th>
                <th scope="col" className="px-2 py-1 text-right">Cant.</th>
                <th scope="col" className="px-2 py-1 text-right">Precio</th>
                <th scope="col" className="px-2 py-1 text-right">Importe</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {venta.detalles.map((d) => (
                <tr key={d.ventaDetalleId}>
                  <td className="px-2 py-1.5">{d.productoNombre}</td>
                  <td className="px-2 py-1.5 text-right tabular-nums">{d.cantidad}</td>
                  <td className="px-2 py-1.5 text-right tabular-nums">{formatoMoneda(d.precioUnitario)}</td>
                  <td className="px-2 py-1.5 text-right font-medium tabular-nums">{formatoMoneda(d.totalLinea)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 rounded-md bg-canvas p-3 text-sm sm:grid-cols-4">
        <div>
          <p className="text-xs text-muted">Subtotal</p>
          <p className="font-medium tabular-nums">{formatoMoneda(venta.subtotal)}</p>
        </div>
        <div>
          <p className="text-xs text-muted">IVA</p>
          <p className="font-medium tabular-nums">{formatoMoneda(venta.iva)}</p>
        </div>
        <div>
          <p className="text-xs text-muted">Descuento</p>
          <p className="font-medium tabular-nums text-muted">−{formatoMoneda(venta.descuentoTotal)}</p>
        </div>
        <div>
          <p className="text-xs text-muted">Total</p>
          <p className="text-base font-bold tabular-nums text-primary">{formatoMoneda(venta.total)}</p>
        </div>
      </div>

      {venta.pagos.length > 0 && (
        <div>
          <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">Pagos</p>
          <ul className="space-y-1">
            {venta.pagos.map((p) => (
              <li key={p.pagoClienteId} className="flex items-center justify-between rounded-md border border-line px-3 py-1.5 text-sm">
                <span>
                  <span className="font-medium">{formatoMoneda(p.monto)}</span>
                  <span className="ml-2 text-xs text-muted">{FORMAS_PAGO.find((f) => f.id === p.formaPagoId)?.nombre ?? `Forma ${p.formaPagoId}`}</span>
                  {p.referencia && <span className="ml-2 text-xs text-muted">· ref: {p.referencia}</span>}
                </span>
                <span className="text-xs tabular-nums text-muted">{formatoFecha(p.fecha)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {venta.notas && (
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-muted">Notas</p>
          <p className="rounded-md border border-line px-3 py-2 text-sm">{venta.notas}</p>
        </div>
      )}
    </div>
  )
}

export default function VentasPage() {
  useDocumentTitle('Historial de ventas')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()

  const [rango, setRango] = useState<RangoFechas>(() => rangoFechas())
  const [almacenId, setAlmacenId] = useState<number | ''>('')
  const [page, setPage] = useState(0)
  const [detalle, setDetalle] = useState<Venta | null>(null)
  const [cancelando, setCancelando] = useState<Venta | null>(null)

  const almacenes = useQuery({ queryKey: ['almacenes'], queryFn: apiAlmacenes })

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['ventas', rango.inicio, rango.fin, almacenId, page],
    queryFn: () =>
      apiVentas({
        desde: rango.inicio,
        hasta: rango.fin,
        almacenId: almacenId || undefined,
        page,
        size: 20,
      }),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const cambiarRango = (siguiente: RangoFechas) => {
    setRango(siguiente)
    setPage(0)
  }
  const cambiarAlmacen = (siguiente: number | '') => {
    setAlmacenId(siguiente)
    setPage(0)
  }
  const limpiarAlmacen = () => {
    setAlmacenId('')
    setPage(0)
  }

  const cancelar = useMutation({
    mutationFn: (v: { id: number; motivo: string }) => apiCancelarVenta(v.id, { motivo: v.motivo }),
    onSuccess: () => {
      mostrarExito('Venta cancelada.')
      setCancelando(null)
      queryClient.invalidateQueries({ queryKey: ['ventas'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const columnas: Columna<Venta>[] = [
    { key: 'f', header: 'Folio', render: (v) => <span className="font-mono text-xs font-medium text-ink">{v.folio}</span> },
    { key: 'fe', header: 'Fecha', render: (v) => <span className="whitespace-nowrap tabular-nums">{formatoFechaHora(v.fecha)}</span> },
    { key: 'c', header: 'Cliente', render: (v) => v.clienteNombre ?? <span className="text-muted">Consumidor final</span> },
    { key: 'a', header: 'Almacén', render: (v) => v.almacenNombre },
    { key: 'p', header: 'Forma', render: (v) => v.formaPagoNombre },
    { key: 't', header: 'Total', align: 'right', render: (v) => <span className="font-semibold tabular-nums">{formatoMoneda(v.total)}</span> },
    { key: 'e', header: 'Estado', render: (v) => <EstadoVenta estado={v.estado} /> },
    {
      key: 'acc',
      header: 'Acciones',
      align: 'right',
      render: (v) => (
        <div className="flex justify-end gap-1">
          <button
            type="button"
            aria-label={`Ver detalle de ${v.folio}`}
            title="Ver detalle"
            className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
            onClick={() => setDetalle(v)}
          >
            <Eye className="h-4 w-4" />
          </button>
          {v.estado !== 'CANCELADA' && (
            <button
              type="button"
              aria-label={`Cancelar venta ${v.folio}`}
              title="Cancelar venta"
              className="rounded p-1.5 text-muted hover:bg-red-50 hover:text-red-600"
              onClick={() => setCancelando(v)}
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Historial de ventas</h1>
          <p className="text-sm text-muted">Consulta, revisa el detalle y cancela ventas del periodo seleccionado.</p>
        </div>
        <DateRangePicker valor={rango} onChange={cambiarRango} />
      </header>

      <Card>
        <div className="flex flex-wrap items-end gap-2">
          <Select
            label="Almacén"
            value={almacenId}
            onChange={(e) => cambiarAlmacen(e.target.value ? Number(e.target.value) : '')}
            className="w-64"
          >
            <option value="">Todos</option>
            {almacenes.data?.map((a) => (
              <option key={a.almacenId} value={a.almacenId}>
                {a.nombre}
              </option>
            ))}
          </Select>
          {almacenId !== '' && (
            <Button variant="ghost" onClick={limpiarAlmacen}>
              Limpiar
            </Button>
          )}
        </div>
      </Card>

      {(isLoading || (isFetching && !data)) && <Spinner />}
      {data && (
        <Card titulo={`Ventas (${data.meta.totalElements})`}>
          <DataTable columnas={columnas} items={data.data} rowKey={(v) => v.ventaId} loading={isFetching} />
          <Pagination meta={data.meta} onPage={setPage} />
        </Card>
      )}

      <Dialog open={detalle !== null} onClose={() => setDetalle(null)} title={detalle ? `Detalle de venta ${detalle.folio}` : ''} width="max-w-2xl">
        {detalle && <DetalleVenta venta={detalle} />}
      </Dialog>

      <Dialog open={cancelando !== null} onClose={() => !cancelar.isPending && setCancelando(null)} title="Cancelar venta" width="max-w-md">
        {cancelando && (
          <CancelarForm
            venta={cancelando}
            guardando={cancelar.isPending}
            onGuardar={(motivo) => cancelar.mutate({ id: cancelando.ventaId, motivo })}
            onClose={() => setCancelando(null)}
          />
        )}
      </Dialog>
    </div>
  )
}
