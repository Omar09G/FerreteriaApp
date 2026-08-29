import { useEffect, useState } from 'react'
import { RotateCcw, Search, Plus, Trash2, X } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiCajas, apiTurnoActual } from '@/lib/api/caja'
import { apiAlmacenes, apiClientes, apiProductos } from '@/lib/api/catalogo'
import { apiCancelarRenta, apiCrearRenta, apiDevolucionRenta, apiRentas } from '@/lib/api/venta'
import { FORMAS_PAGO, type Producto, type Renta, type RentaDevolucionRequest, type RentaRequest } from '@/lib/api/types'
import { aLocalDate, formatoFecha, formatoFechaHora, formatoMoneda } from '@/lib/format'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

const TONO_RENTA: Record<string, 'success' | 'info' | 'warning' | 'danger' | 'default'> = {
  ABIERTA: 'success',
  DEVUELTA: 'info',
  VENCIDA: 'warning',
  CANCELADA: 'danger',
}

const hoyMasTres = () => {
  const d = new Date()
  d.setDate(d.getDate() + 3)
  return aLocalDate(d)
}

interface PartidaRenta {
  productoId: number
  nombre: string
  cantidad: number
  costoDia: number
}

function RentaForm({ guardando, onGuardar, onClose }: { guardando: boolean; onGuardar: (body: RentaRequest) => void; onClose: () => void }) {
  const clientes = useQuery({ queryKey: ['clientes-renta'], queryFn: () => apiClientes({ page: 0, size: 50 }) })
  const almacenes = useQuery({ queryKey: ['almacenes-renta'], queryFn: apiAlmacenes })
  const cajas = useQuery({ queryKey: ['cajas'], queryFn: apiCajas })

  const [clienteId, setClienteId] = useState<number | ''>('')
  const [almacenId, setAlmacenId] = useState<number | ''>('')
  const [cajaId, setCajaId] = useState<number | ''>('')
  const [formaPagoId, setFormaPagoId] = useState<number>(1)
  const [fechaDevEsperada, setFechaDevEsperada] = useState(hoyMasTres())
  const [deposito, setDeposito] = useState('0')
  const [partidas, setPartidas] = useState<PartidaRenta[]>([])
  const [busqueda, setBusqueda] = useState('')
  const [q, setQ] = useState('')
  const [intento, setIntento] = useState(false)

  const resultados = useQuery({
    queryKey: ['productos-renta', q],
    queryFn: () => apiProductos({ q: q || undefined, page: 0, size: 20 }),
    enabled: q.length > 0,
  })

  const agregar = (p: Producto) => {
    setPartidas((prev) => {
      const exist = prev.find((x) => x.productoId === p.productoId)
      if (exist) return prev
      return [...prev, { productoId: p.productoId, nombre: p.nombre, cantidad: 1, costoDia: 0 }]
    })
    setBusqueda('')
    setQ('')
  }

  const depositoNum = Number(deposito)
  const totalPartidas = partidas.reduce((acc, x) => acc + x.cantidad * x.costoDia, 0)

  const cajasDeAlmacen = cajas.data?.filter((c) => c.almacenId === (almacenId === '' ? -1 : Number(almacenId))) ?? []
  const turno = useQuery({
    queryKey: ['turnoActual', cajaId],
    queryFn: () => apiTurnoActual(Number(cajaId)),
    enabled: cajaId !== '',
    retry: false,
  })
  const cajaConTurno = cajaId !== '' && turno.isSuccess
  const cajaSinTurno = cajaId !== '' && turno.isError

  const invalido =
    clienteId === '' || almacenId === '' || cajaId === '' || cajaSinTurno || fechaDevEsperada === '' || partidas.length === 0 || partidas.some((x) => x.cantidad <= 0 || x.costoDia < 0) || depositoNum < 0

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({
      clienteId: Number(clienteId),
      almacenId: Number(almacenId),
      cajaId: Number(cajaId),
      formaPagoId,
      fechaDevEsperada,
      deposito: depositoNum,
      detalles: partidas.map((x) => ({ productoId: x.productoId, cantidad: x.cantidad, costoDia: x.costoDia })),
    })
  }

  return (
    <form onSubmit={enviar} className="space-y-3" noValidate>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Select label="Cliente" required value={clienteId} onChange={(e) => setClienteId(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Selecciona…</option>
          {clientes.data?.data.map((c) => (
            <option key={c.clienteId} value={c.clienteId}>
              {c.razonSocial}
            </option>
          ))}
        </Select>
        <Select label="Almacén" required value={almacenId} onChange={(e) => setAlmacenId(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Selecciona…</option>
          {almacenes.data?.map((a) => (
            <option key={a.almacenId} value={a.almacenId}>
              {a.nombre}
            </option>
          ))}
        </Select>
        <Input label="Devolución esperada" type="date" required value={fechaDevEsperada} onChange={(e) => setFechaDevEsperada(e.target.value)} />
        <Input label="Depósito" type="number" inputMode="decimal" min="0" step="0.01" value={deposito} onChange={(e) => setDeposito(e.target.value)} />
        <Select label="Forma de pago" required value={formaPagoId} onChange={(e) => setFormaPagoId(Number(e.target.value))}>
          {FORMAS_PAGO.map((f) => (
            <option key={f.id} value={f.id}>
              {f.nombre}
            </option>
          ))}
        </Select>
        <Select label="Caja" required value={cajaId} onChange={(e) => setCajaId(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Selecciona…</option>
          {cajasDeAlmacen.map((c) => (
            <option key={c.cajaId} value={c.cajaId}>
              {c.nombre}
            </option>
          ))}
        </Select>
      </div>

      {cajaId !== '' && turno.isLoading && <p className="text-xs text-muted">Verificando turno abierto…</p>}
      {cajaConTurno && <p className="text-xs text-emerald-600">Turno abierto: el depósito se registrará a esa caja.</p>}
      {cajaSinTurno && <p className="text-xs text-red-600">Esta caja no tiene un turno abierto. Ábrelo en el POS para poder registrar la renta.</p>}

      <div className="flex flex-wrap items-end gap-2">
        <Input label="Buscar producto" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && setQ(busqueda.trim())} placeholder="Artículo a rentar" className="w-72" />
        <Button variant="secondary" disabled={resultados.isFetching || busqueda.trim() === q} onClick={() => setQ(busqueda.trim())}>
          <Search className="h-4 w-4" /> Buscar
        </Button>
      </div>
      {q && resultados.data && (
        <div className="max-h-40 overflow-auto rounded-md border border-line">
          {resultados.data.data.length === 0 && <p className="p-3 text-sm text-muted">Sin coincidencias.</p>}
          {resultados.data.data.map((p) => (
            <button key={p.productoId} type="button" onClick={() => agregar(p)} className="flex w-full items-center justify-between gap-3 border-b border-line px-3 py-1.5 text-left hover:bg-orange-50">
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium text-ink">{p.nombre}</span>
                <span className="text-xs text-muted">{p.codigo ?? '—'}</span>
              </span>
              <Plus className="h-4 w-4 shrink-0 text-primary" />
            </button>
          ))}
        </div>
      )}

      {partidas.length > 0 && (
        <div className="space-y-1.5">
          <div aria-hidden className="flex items-center gap-2 px-2 text-[11px] font-medium uppercase tracking-wide text-muted">
            <span className="w-4 shrink-0" />
            <span className="min-w-0 flex-1" />
            <span className="w-16 shrink-0 text-right">Cant.</span>
            <span className="w-24 shrink-0 text-right">Costo día</span>
            <span className="w-24 shrink-0 text-right">Monto</span>
          </div>
          {partidas.map((x) => (
            <div key={x.productoId} className="flex items-center gap-2 rounded-md border border-line px-2 py-1.5">
              <button type="button" aria-label="Quitar" className="text-muted hover:text-red-600" onClick={() => setPartidas((prev) => prev.filter((y) => y.productoId !== x.productoId))}>
                <Trash2 className="h-4 w-4" />
              </button>
              <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink">{x.nombre}</span>
              <input
                type="number"
                inputMode="decimal"
                min={1}
                step="1"
                value={x.cantidad}
                onChange={(e) => setPartidas((prev) => prev.map((y) => (y.productoId === x.productoId ? { ...y, cantidad: Number(e.target.value) } : y)))}
                className="w-16 rounded border border-line px-1 py-0.5 text-right text-sm"
                aria-label={`Cantidad de ${x.nombre}`}
              />
              <input
                type="number"
                inputMode="decimal"
                min={0}
                step="0.01"
                value={x.costoDia}
                onChange={(e) => setPartidas((prev) => prev.map((y) => (y.productoId === x.productoId ? { ...y, costoDia: Number(e.target.value) } : y)))}
                className="w-24 rounded border border-line px-1 py-0.5 text-right text-sm"
                aria-label={`Costo por día de ${x.nombre}`}
              />
              <span className="w-24 shrink-0 text-right text-sm font-semibold tabular-nums">{formatoMoneda(x.cantidad * x.costoDia)}</span>
            </div>
          ))}
          <div className="flex items-center justify-between pt-1 text-sm text-muted">
            <span>Total partidas</span>
            <span className="font-semibold tabular-nums text-ink">{formatoMoneda(totalPartidas)}</span>
          </div>
          <div className="flex items-center justify-between text-sm text-muted">
            <span>Depósito</span>
            <span className="font-semibold tabular-nums text-ink">{formatoMoneda(depositoNum)}</span>
          </div>
          <div className="flex items-center justify-between pt-1 text-sm font-bold text-ink">
            <span>Total estimado</span>
            <span className="tabular-nums">{formatoMoneda(totalPartidas + depositoNum)}</span>
          </div>
        </div>
      )}

      {intento && invalido && <p className="text-xs text-red-600">Completa cliente, almacén, caja con turno abierto, fecha de devolución y al menos una partida.</p>}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" disabled={guardando}>
          {guardando ? 'Registrando…' : 'Registrar renta'}
        </Button>
      </div>
    </form>
  )
}

function DevolucionForm({ renta, guardando, onGuardar, onClose }: { renta: Renta; guardando: boolean; onGuardar: (body: RentaDevolucionRequest) => void; onClose: () => void }) {
  const [dias, setDias] = useState<Record<number, number>>(() => Object.fromEntries(renta.detalles.map((d) => [d.productoId, 1])))
  const [intento, setIntento] = useState(false)

  const invalido = renta.detalles.some((d) => (dias[d.productoId] ?? 0) < 0)

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({ detalles: renta.detalles.map((d) => ({ productoId: d.productoId, diasCobrados: dias[d.productoId] ?? 0 })) })
  }

  return (
    <form onSubmit={enviar} className="space-y-3" noValidate>
      <div className="rounded-md bg-canvas px-3 py-2 text-sm">
        <p className="font-medium text-ink">
          {renta.folio} · {renta.clienteNombre}
        </p>
        <p className="text-muted">Devolución esperada {formatoFecha(renta.fechaDevEsperada)} · depósito {formatoMoneda(renta.deposito)}</p>
      </div>
      <div className="space-y-1.5">
        {renta.detalles.map((d) => (
          <div key={d.productoId} className="flex items-center gap-2 rounded-md border border-line px-2 py-1.5">
            <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink">{d.productoNombre}</span>
            <span className="text-xs text-muted">x{d.cantidad}</span>
            <label className="flex items-center gap-1 text-xs text-muted">
              días
              <input
                type="number"
                inputMode="numeric"
                min={0}
                step="1"
                value={dias[d.productoId] ?? 0}
                onChange={(e) => setDias((prev) => ({ ...prev, [d.productoId]: Number(e.target.value) }))}
                className="w-16 rounded border border-line px-1 py-0.5 text-right text-sm text-ink"
                aria-label={`Días cobrados de ${d.productoNombre}`}
              />
            </label>
          </div>
        ))}
      </div>
      {intento && invalido && <p className="text-xs text-red-600">Los días cobrados no pueden ser negativos.</p>}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" disabled={guardando}>
          {guardando ? 'Registrando…' : 'Registrar devolución'}
        </Button>
      </div>
    </form>
  )
}

export default function RentasPage() {
  useDocumentTitle('Rentas')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()

  const [estado, setEstado] = useState('')
  const [page, setPage] = useState(0)
  const [nuevaAbierta, setNuevaAbierta] = useState(false)
  const [devolviendo, setDevolviendo] = useState<Renta | null>(null)

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['rentas', estado, page],
    queryFn: () => apiRentas({ estado: estado || undefined, page, size: 15 }),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const crear = useMutation({
    mutationFn: (body: RentaRequest) => apiCrearRenta(body),
    onSuccess: () => {
      mostrarExito('Renta registrada.')
      setNuevaAbierta(false)
      queryClient.invalidateQueries({ queryKey: ['rentas'] })
      queryClient.invalidateQueries({ queryKey: ['stock'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const devolver = useMutation({
    mutationFn: ({ id, body }: { id: number; body: RentaDevolucionRequest }) => apiDevolucionRenta(id, body),
    onSuccess: () => {
      mostrarExito('Devolución registrada.')
      setDevolviendo(null)
      queryClient.invalidateQueries({ queryKey: ['rentas'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const cancelar = useMutation({
    mutationFn: (id: number) => apiCancelarRenta(id),
    onSuccess: () => {
      mostrarExito('Renta cancelada.')
      queryClient.invalidateQueries({ queryKey: ['rentas'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const columnas: Columna<Renta>[] = [
    { key: 'fol', header: 'Folio', render: (v) => <span className="font-medium text-ink">{v.folio}</span> },
    { key: 'cli', header: 'Cliente', render: (v) => v.clienteNombre },
    { key: 'alm', header: 'Almacén', render: (v) => v.almacenNombre },
    { key: 'fecha', header: 'Fecha renta', render: (v) => <span className="whitespace-nowrap">{formatoFechaHora(v.fechaRenta)}</span> },
    { key: 'esp', header: 'Devolución esperada', render: (v) => <span className="whitespace-nowrap">{formatoFecha(v.fechaDevEsperada)}</span> },
    { key: 'dep', header: 'Depósito', align: 'right', render: (v) => <span className="tabular-nums">{formatoMoneda(v.deposito)}</span> },
    { key: 'costo', header: 'Costo total', align: 'right', render: (v) => <span className="tabular-nums font-medium">{formatoMoneda(v.costoTotal)}</span> },
    { key: 'estado', header: 'Estado', render: (v) => <Badge tone={TONO_RENTA[v.estado] ?? 'default'}>{v.estado}</Badge> },
    {
      key: 'acc',
      header: 'Acciones',
      align: 'right',
      render: (v) => (
        <div className="flex justify-end gap-1">
          {(v.estado === 'ABIERTA' || v.estado === 'VENCIDA') && (
            <button type="button" aria-label="Registrar devolución" title="Registrar devolución" className="rounded p-1.5 text-primary hover:bg-orange-50" onClick={() => setDevolviendo(v)}>
              <RotateCcw className="h-4 w-4" />
            </button>
          )}
          {(v.estado === 'ABIERTA' || v.estado === 'VENCIDA') && (
            <button
              type="button"
              aria-label="Cancelar renta"
              title="Cancelar renta"
              className="rounded p-1.5 text-muted hover:bg-red-50 hover:text-red-600"
              onClick={() => {
                if (window.confirm(`¿Cancelar la renta ${v.folio}?`)) cancelar.mutate(v.rentaId)
              }}
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
          <h1 className="text-xl font-bold text-ink">Rentas</h1>
          <p className="text-sm text-muted">Renta de herramientas y su devolución con días cobrados.</p>
        </div>
        <Button onClick={() => setNuevaAbierta(true)}>
          <Plus className="h-4 w-4" /> Nueva renta
        </Button>
      </header>

      <Card>
        <div className="flex flex-wrap items-end gap-2">
          <Select label="Estado" value={estado} onChange={(e) => { setEstado(e.target.value); setPage(0) }} className="w-48">
            <option value="">Todas</option>
            <option value="ABIERTA">ABIERTA</option>
            <option value="DEVUELTA">DEVUELTA</option>
            <option value="VENCIDA">VENCIDA</option>
            <option value="CANCELADA">CANCELADA</option>
          </Select>
          {estado !== '' && (
            <Button variant="ghost" onClick={() => { setEstado(''); setPage(0) }}>
              Limpiar
            </Button>
          )}
        </div>
      </Card>

      {(isLoading || (isFetching && !data)) && <Spinner />}
      {data && (
        <Card titulo={`Rentas (${data.meta.totalElements})`}>
          <DataTable columnas={columnas} items={data.data} rowKey={(v) => v.rentaId} loading={isFetching} />
          <Pagination meta={data.meta} onPage={setPage} />
        </Card>
      )}

      <Dialog open={nuevaAbierta} onClose={() => !crear.isPending && setNuevaAbierta(false)} title="Nueva renta" width="max-w-2xl">
        <RentaForm guardando={crear.isPending} onGuardar={(body) => crear.mutate(body)} onClose={() => setNuevaAbierta(false)} />
      </Dialog>

      <Dialog open={devolviendo !== null} onClose={() => !devolver.isPending && setDevolviendo(null)} title="Registrar devolución" width="max-w-lg">
        {devolviendo && <DevolucionForm renta={devolviendo} guardando={devolver.isPending} onGuardar={(body) => devolver.mutate({ id: devolviendo.rentaId, body })} onClose={() => setDevolviendo(null)} />}
      </Dialog>
    </div>
  )
}