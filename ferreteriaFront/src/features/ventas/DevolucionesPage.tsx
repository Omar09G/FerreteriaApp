import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PackageOpen } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiCrearDevolucion, apiDevolucionesDeVenta, apiVentas } from '@/lib/api/venta'
import type { DevolucionRequest, Venta, VentaDetalle } from '@/lib/api/types'
import { FORMAS_PAGO } from '@/lib/api/types'
import { formatoFechaHora, formatoMoneda } from '@/lib/format'
import type { RangoFechas } from '@/lib/rango'
import { EstadoBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { RangoFiltro } from '@/components/ui/RangoFiltro'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

interface Partida {
  ventaDetalleId: number
  productoId: number
  productoNombre: string
  cantidad: number
  precioUnitario: number
  marcada: boolean
}

function DevolucionForm({
  venta,
  guardando,
  onGuardar,
  onCancelar,
}: {
  venta: Venta
  guardando: boolean
  onGuardar: (body: DevolucionRequest) => void
  onCancelar: () => void
}) {
  const [motivo, setMotivo] = useState('')
  const [formaDevolucionId, setFormaDevolucionId] = useState(1)
  const [partidas, setPartidas] = useState<Partida[]>(
    venta.detalles.map((d) => ({
      ventaDetalleId: d.ventaDetalleId,
      productoId: d.productoId,
      productoNombre: d.productoNombre,
      cantidad: 1,
      precioUnitario: d.precioUnitario,
      marcada: false,
    })),
  )
  const [intento, setIntento] = useState(false)

  const marcar = (ventaDetalleId: number, marcada: boolean) =>
    setPartidas((prev) => prev.map((p) => (p.ventaDetalleId === ventaDetalleId ? { ...p, marcada } : p)))

  const editar = (ventaDetalleId: number, campo: 'cantidad' | 'precioUnitario', valor: number) =>
    setPartidas((prev) => prev.map((p) => (p.ventaDetalleId === ventaDetalleId ? { ...p, [campo]: valor } : p)))

  const seleccionadas = partidas.filter((p) => p.marcada)
  const total = seleccionadas.reduce((acc, p) => acc + p.cantidad * p.precioUnitario, 0)
  const invalido = motivo.trim() === '' || seleccionadas.length === 0 || seleccionadas.some((p) => p.cantidad < 1)

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({
      ventaId: venta.ventaId,
      motivo: motivo.trim(),
      formaDevolucionId,
      detalles: seleccionadas.map((p) => ({
        productoId: p.productoId,
        ventaDetalleId: p.ventaDetalleId,
        cantidad: p.cantidad,
        precioUnitario: p.precioUnitario,
      })),
    })
  }

  return (
    <form onSubmit={enviar} className="space-y-3" noValidate>
      <Input label="Motivo" required value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej. Producto dañado, cambio de artículo…" />
      <Select label="Forma de devolución" required value={formaDevolucionId} onChange={(e) => setFormaDevolucionId(Number(e.target.value))}>
        {FORMAS_PAGO.map((f) => (
          <option key={f.id} value={f.id}>
            {f.nombre}
          </option>
        ))}
      </Select>

      <div className="space-y-1.5">
        <p className="text-sm font-medium text-ink">Partidas a devolver</p>
        {partidas.map((d) => {
          const detalle = venta.detalles.find((x) => x.ventaDetalleId === d.ventaDetalleId) as VentaDetalle
          return (
            <div key={d.ventaDetalleId} className="flex items-center gap-2 rounded-md border border-line px-2 py-1.5">
              <input type="checkbox" checked={d.marcada} onChange={(e) => marcar(d.ventaDetalleId, e.target.checked)} aria-label={`Devolver ${d.productoNombre}`} className="h-4 w-4 rounded border-line accent-orange-600" />
              <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink">{d.productoNombre}</span>
              <input
                type="number"
                inputMode="numeric"
                min={1}
                max={detalle.cantidad}
                step="1"
                value={d.cantidad}
                onChange={(e) => editar(d.ventaDetalleId, 'cantidad', Number(e.target.value))}
                disabled={!d.marcada}
                className="w-16 rounded border border-line px-1 py-0.5 text-right text-sm disabled:bg-warmbg"
                aria-label={`Cantidad de ${d.productoNombre}`}
              />
              <input
                type="number"
                inputMode="decimal"
                min={0}
                step="0.01"
                value={d.precioUnitario}
                onChange={(e) => editar(d.ventaDetalleId, 'precioUnitario', Number(e.target.value))}
                disabled={!d.marcada}
                className="w-24 rounded border border-line px-1 py-0.5 text-right text-sm disabled:bg-warmbg"
                aria-label={`Precio unitario de ${d.productoNombre}`}
              />
              <span className="w-24 shrink-0 text-right text-sm font-semibold tabular-nums">{formatoMoneda(d.marcada ? d.cantidad * d.precioUnitario : 0)}</span>
            </div>
          )
        })}
        <div className="flex items-center justify-between pt-1 text-sm font-bold text-ink">
          <span>Total a devolver</span>
          <span className="tabular-nums">{formatoMoneda(total)}</span>
        </div>
      </div>

      {intento && invalido && <p className="text-xs text-red-600">Indica un motivo y al menos una partida con cantidad mayor o igual a 1.</p>}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onCancelar}>
          Cancelar
        </Button>
        <Button type="submit" disabled={guardando}>
          {guardando ? 'Registrando…' : 'Registrar devolución'}
        </Button>
      </div>
    </form>
  )
}

function DetalleVenta({
  venta,
  onRegistrar,
}: {
  venta: Venta
  onRegistrar: () => void
}) {
  const { data: devoluciones, isLoading, error } = useQuery({
    queryKey: ['devolucion', venta.ventaId],
    queryFn: () => apiDevolucionesDeVenta(venta.ventaId),
  })

  return (
    <div className="space-y-4">
      <div className="rounded-md bg-canvas px-3 py-2 text-sm">
        <p className="font-medium text-ink">
          {venta.clienteNombre ?? 'Cliente general'} · {formatoFechaHora(venta.fecha)}
        </p>
        <p className="text-muted">
          Total: <span className="font-semibold tabular-nums text-ink">{formatoMoneda(venta.total)}</span> · {venta.formaPagoNombre}
        </p>
      </div>

      <div>
        <p className="mb-1.5 text-sm font-medium text-ink">Detalles de la venta</p>
        <div className="space-y-1">
          {venta.detalles.map((d) => (
            <div key={d.ventaDetalleId} className="flex items-center justify-between rounded-md border border-line px-3 py-1.5 text-sm">
              <span className="min-w-0 flex-1 truncate">{d.productoNombre}</span>
              <span className="mx-3 shrink-0 text-xs text-muted">
                {d.cantidad} × {formatoMoneda(d.precioUnitario)}
              </span>
              <span className="shrink-0 font-semibold tabular-nums">{formatoMoneda(d.totalLinea)}</span>
            </div>
          ))}
        </div>
      </div>

      <div>
        <div className="mb-1.5 flex items-center justify-between">
          <p className="text-sm font-medium text-ink">Historial de devoluciones</p>
          <Button size="sm" onClick={onRegistrar}>
            <PackageOpen className="h-4 w-4" /> Registrar devolución
          </Button>
        </div>
        {isLoading ? (
          <Spinner />
        ) : error ? (
          <p className="text-sm text-red-600">{esApiError(error) ? error.mensajeParaUsuario() : String(error)}</p>
        ) : !devoluciones || devoluciones.length === 0 ? (
          <p className="text-sm text-muted">Sin devoluciones registradas.</p>
        ) : (
          <div className="space-y-2">
            {devoluciones.map((dev) => (
              <div key={dev.devolucionId} className="rounded-md border border-line px-3 py-2 text-sm">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-ink">{dev.folio}</span>
                  <span className="text-xs tabular-nums text-muted">{formatoFechaHora(dev.fecha)}</span>
                </div>
                <p className="text-xs text-muted">{dev.motivo} · {dev.formaDevolucionNombre ?? `Forma ${dev.formaDevolucionId}`}</p>
                <div className="mt-1 space-y-0.5">
                  {dev.detalles.map((dd, i) => (
                    <div key={i} className="flex items-center justify-between text-xs">
                      <span className="min-w-0 flex-1 truncate">{dd.productoNombre}</span>
                      <span className="ml-2 text-muted">
                        {dd.cantidad} × {formatoMoneda(dd.precioUnitario)}
                      </span>
                    </div>
                  ))}
                </div>
                <p className="mt-1 text-right font-semibold tabular-nums">{formatoMoneda(dev.total)}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

export default function DevolucionesPage() {
  useDocumentTitle('Devoluciones')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()

  const [page, setPage] = useState(0)
  const [rango, setRango] = useState<RangoFechas | null>(null)
  const [ventaSeleccionada, setVentaSeleccionada] = useState<Venta | null>(null)
  const [registrando, setRegistrando] = useState(false)

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['ventas', rango?.inicio, rango?.fin, page],
    queryFn: () => apiVentas({ desde: rango?.inicio, hasta: rango?.fin, page, size: 15 }),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const crear = useMutation({
    mutationFn: (body: DevolucionRequest) => apiCrearDevolucion(body),
    onSuccess: () => {
      mostrarExito('Devolución registrada.')
      setRegistrando(false)
      queryClient.invalidateQueries({ queryKey: ['devolucion'] })
      queryClient.invalidateQueries({ queryKey: ['ventas'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      if (ventaSeleccionada) {
        queryClient.invalidateQueries({ queryKey: ['devolucion', ventaSeleccionada.ventaId] })
      }
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const columnas: Columna<Venta>[] = [
    { key: 'fol', header: 'Folio', render: (v) => <span className="font-medium text-ink">{v.folio}</span> },
    { key: 'c', header: 'Cliente', render: (v) => v.clienteNombre ?? 'Cliente general' },
    { key: 'fecha', header: 'Fecha', render: (v) => <span className="whitespace-nowrap">{formatoFechaHora(v.fecha)}</span> },
    { key: 'total', header: 'Total', align: 'right', render: (v) => <span className="tabular-nums font-medium">{formatoMoneda(v.total)}</span> },
    { key: 'estado', header: 'Estado', render: (v) => <EstadoBadge estado={v.estado} /> },
    {
      key: 'acc',
      header: 'Acciones',
      align: 'right',
      render: (v) => (
        <div className="flex justify-end">
          <button
            type="button"
            aria-label={`Devoluciones de ${v.folio}`}
            title="Ver venta / Devoluciones"
            className="rounded p-1.5 text-primary hover:bg-primary-50"
            onClick={() => setVentaSeleccionada(v)}
          >
            <PackageOpen className="h-4 w-4" />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Devoluciones</h1>
          <p className="text-sm text-muted">Registra y consulta devoluciones sobre ventas realizadas.</p>
        </div>
        <RangoFiltro valor={rango} onChange={(siguiente) => { setRango(siguiente); setPage(0) }} />
      </header>

      {(isLoading || (isFetching && !data)) && <Spinner />}
      {data && (
        <Card titulo={`Ventas (${data.meta.totalElements})`}>
          <DataTable columnas={columnas} items={data.data} rowKey={(v) => v.ventaId} loading={isFetching} />
          <Pagination meta={data.meta} onPage={setPage} />
        </Card>
      )}

      <Dialog open={ventaSeleccionada !== null && !registrando} onClose={() => !crear.isPending && setVentaSeleccionada(null)} title={ventaSeleccionada ? `Venta ${ventaSeleccionada.folio}` : ''} width="max-w-2xl">
        {ventaSeleccionada && <DetalleVenta venta={ventaSeleccionada} onRegistrar={() => setRegistrando(true)} />}
      </Dialog>

      <Dialog open={ventaSeleccionada !== null && registrando} onClose={() => !crear.isPending && setRegistrando(false)} title={`Nueva devolución · ${ventaSeleccionada?.folio}`} width="max-w-2xl">
        {ventaSeleccionada && <DevolucionForm venta={ventaSeleccionada} guardando={crear.isPending} onGuardar={(body) => crear.mutate(body)} onCancelar={() => setRegistrando(false)} />}
      </Dialog>
    </div>
  )
}
