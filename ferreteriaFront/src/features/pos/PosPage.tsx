import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, Barcode, Ban, Eraser, Minus, Plus, Search, ShoppingBasket, Store, Trash2, User } from 'lucide-react'
import { Link } from 'react-router-dom'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiProductos, apiAlmacenes, apiClientes } from '@/lib/api/catalogo'
import { apiCajas, apiTurnoActual } from '@/lib/api/caja'
import { apiCheckout, apiVentas } from '@/lib/api/venta'
import { FORMAS_PAGO, type Caja, type Producto, type TurnoCaja, type Venta } from '@/lib/api/types'
import { formatoFechaHora, formatoMoneda, hoyLocal } from '@/lib/format'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

interface Linea {
  productoId: number
  codigo: string | null
  nombre: string
  cantidad: number
  precioUnitario: number
  aplicaIva: boolean
}

const IVA_TASA = 0.16

/** Heurística: sólo dígitos, ≥6 caracteres. Cubre EAN-8/13, UPC, code128 numéricos y códigos internos. */
const PATRON_CODIGO_BARRAS = /^\d{6,}$/

function pareceCodigoBarras(texto: string): boolean {
  return PATRON_CODIGO_BARRAS.test(texto.trim())
}

const STORAGE_POS = 'ferreteria-pos'

interface PosPreferencias {
  cajaId: number | null
  almacenId: number | null
}

function cargarPreferencias(): PosPreferencias {
  try {
    const raw = localStorage.getItem(STORAGE_POS)
    if (!raw) return { cajaId: null, almacenId: null }
    const parsed = JSON.parse(raw) as Partial<PosPreferencias>
    return {
      cajaId: typeof parsed.cajaId === 'number' ? parsed.cajaId : null,
      almacenId: typeof parsed.almacenId === 'number' ? parsed.almacenId : null,
    }
  } catch {
    return { cajaId: null, almacenId: null }
  }
}

interface Resumen {
  total: number
  subtotalSinIva: number
  ivaEstimado: number
}

function resumenVenta(lineas: Linea[]): Resumen {
  let total = 0
  let subtotalSinIva = 0
  let ivaEstimado = 0
  for (const l of lineas) {
    const importe = l.cantidad * l.precioUnitario
    total += importe
    if (l.aplicaIva) {
      const base = importe / (1 + IVA_TASA)
      subtotalSinIva += base
      ivaEstimado += importe - base
    } else {
      subtotalSinIva += importe
    }
  }
  return { total, subtotalSinIva, ivaEstimado }
}

export default function PosPage() {
  useDocumentTitle('Punto de venta')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()
  const buscadorRef = useRef<HTMLInputElement>(null)
  const cobrarRef = useRef<HTMLButtonElement>(null)
  const confirmarRef = useRef<HTMLButtonElement>(null)

  const [almacenId, setAlmacenId] = useState<number | ''>(() => cargarPreferencias().almacenId ?? '')
  const [cajaId, setCajaId] = useState<number | ''>(() => cargarPreferencias().cajaId ?? '')
  const [clienteId, setClienteId] = useState<string>('')
  const [busqueda, setBusqueda] = useState('')
  const [q, setQ] = useState('')
  const [lineas, setLineas] = useState<Linea[]>([])
  const [formaPagoId, setFormaPagoId] = useState<number>(1)
  const [recibido, setRecibido] = useState('')
  const [referencia, setReferencia] = useState('')
  const [notas, setNotas] = useState('')
  const [confirmAbierto, setConfirmAbierto] = useState(false)
  const [cancelarAbierto, setCancelarAbierto] = useState(false)
  const [ventasDiaAbierto, setVentasDiaAbierto] = useState(false)
  const [ventaResultado, setVentaResultado] = useState<Venta | null>(null)

  const almacenes = useQuery({ queryKey: ['almacenes'], queryFn: apiAlmacenes })
  const cajas = useQuery({ queryKey: ['cajas-pos'], queryFn: apiCajas })
  const clientes = useQuery({ queryKey: ['clientes-pos'], queryFn: () => apiClientes({ page: 0, size: 50 }) })
  const ultimoAutoAddRef = useRef<string | null>(null)
  const busquedaTrim = busqueda.trim()
  const modoBarcode = pareceCodigoBarras(busquedaTrim)
  /** La búsqueda efectiva: si parece código de barras, dispara al vuelo; si no, usa la búsqueda manual (Enter / Buscar). */
  const qEfectivo = modoBarcode ? busquedaTrim : q

  const resultados = useQuery({
    queryKey: ['productos-pos', qEfectivo],
    queryFn: () => apiProductos({ q: qEfectivo || undefined, page: 0, size: 20 }),
    enabled: qEfectivo.length > 0,
  })

  const ventasHoy = useQuery({
    queryKey: ['ventas-pos-hoy'],
    queryFn: () => apiVentas({ desde: hoyLocal(), hasta: hoyLocal(), page: 0, size: 100 }),
    enabled: ventasDiaAbierto,
  })

  /**
   * Turno abierto de la caja seleccionada. Si la caja no tiene turno ABIERTO,
   * la API responde 404 → bloqueamos el cobro y mostramos el aviso al usuario.
   */
  const turnoActual = useQuery<TurnoCaja>({
    queryKey: ['turno-actual', cajaId],
    queryFn: () => apiTurnoActual(Number(cajaId)),
    enabled: typeof cajaId === 'number',
    retry: false,
    refetchOnWindowFocus: true,
    staleTime: 15_000,
  })

  /** Persiste caja/almacén en localStorage para no tener que re-seleccionar en cada sesión. */
  useEffect(() => {
    const payload: PosPreferencias = {
      cajaId: typeof cajaId === 'number' ? cajaId : null,
      almacenId: typeof almacenId === 'number' ? almacenId : null,
    }
    try {
      localStorage.setItem(STORAGE_POS, JSON.stringify(payload))
    } catch {
      // ignorar errores de cuota o de modo privado
    }
  }, [almacenId, cajaId])

  /** Construye la línea para el ticket a partir de un producto. */
  const lineaDeProducto = (p: Producto): Linea => ({
    productoId: p.productoId,
    codigo: p.codigo,
    nombre: p.nombre,
    cantidad: 1,
    precioUnitario: p.precioMenudeo,
    aplicaIva: p.aplicaIva,
  })

  const agregar = (p: Producto, mensaje?: string) => {
    setLineas((prev) => {
      const exist = prev.find((l) => l.productoId === p.productoId)
      if (exist) return prev.map((l) => (l.productoId === p.productoId ? { ...l, cantidad: l.cantidad + 1 } : l))
      return [...prev, lineaDeProducto(p)]
    })
    mostrarExito(mensaje ?? `Agregado: ${p.nombre}`)
    // Cierra la lista y limpia el input para que el siguiente escaneo/búsqueda empiece limpio.
    setBusqueda('')
    setQ('')
    // Devuelve el foco al buscador para que el siguiente escaneo o escritura sea inmediato.
    window.setTimeout(() => buscadorRef.current?.focus(), 0)
  }

  /** Limpia el ticket en construcción (líneas, buscador, pago, notas). Conserva almacén/caja/cliente. */
  const limpiarTicket = () => {
    setLineas([])
    setBusqueda('')
    setQ('')
    setRecibido('')
    setReferencia('')
    setNotas('')
  }

  /** Cancela la venta en curso y reinicia todo, manteniendo preferencias de sesión (almacén/caja). */
  const cancelarVenta = () => {
    limpiarTicket()
    setClienteId('')
    setFormaPagoId(1)
    setConfirmAbierto(false)
    setCancelarAbierto(false)
    mostrarExito('Venta cancelada.')
    window.setTimeout(() => buscadorRef.current?.focus(), 0)
  }

  /** Si un escaneo devuelve un único producto, lo añade al ticket y deja listo para el siguiente. */
  // Efecto intencional: reacciona a una respuesta de la API (sistema externo) y reinicia el input.
  // No se puede derivar: depende del resultado asíncrono de la búsqueda, no de otro estado del componente.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!resultados.data) return
    const limpio = busqueda.trim()
    if (!pareceCodigoBarras(limpio)) return
    if (ultimoAutoAddRef.current === limpio) return
    if (resultados.data.data.length !== 1) return
    ultimoAutoAddRef.current = limpio
    setLineas((prev) => {
      const unico = resultados.data!.data[0]
      const exist = prev.find((l) => l.productoId === unico.productoId)
      if (exist) return prev.map((l) => (l.productoId === unico.productoId ? { ...l, cantidad: l.cantidad + 1 } : l))
      return [...prev, lineaDeProducto(unico)]
    })
    mostrarExito(`Escaneado: ${resultados.data.data[0].nombre}`)
    setBusqueda('')
    setQ('')
  }, [resultados.data, busqueda, mostrarExito])
  /* eslint-enable react-hooks/set-state-in-effect */

  const checkout = useMutation({
    mutationFn: () => {
      const monto = Number(recibido) > 0 ? Number(recibido) : 0
      return apiCheckout({
        almacenId: Number(almacenId),
        cajaId: typeof cajaId === 'number' ? cajaId : undefined,
        clienteId: clienteId ? Number(clienteId) : undefined,
        formaPagoId,
        detalles: lineas.map((l) => ({ productoId: l.productoId, cantidad: l.cantidad, precioUnitario: l.precioUnitario })),
        pagos: [{ formaPagoId, monto, referencia: referencia.trim() || undefined }],
        notas: notas.trim() || undefined,
      })
    },
    onSuccess: (venta) => {
      mostrarExito(`Venta ${venta.folio} registrada.`)
      setVentaResultado(venta)
      limpiarTicket()
      setVentasDiaAbierto(false) // fuerza refetch la próxima vez
      queryClient.invalidateQueries({ queryKey: ['ventas-pos-hoy'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const total = lineas.reduce((acc, l) => acc + l.cantidad * l.precioUnitario, 0)
  const resumen = resumenVenta(lineas)
  const forma = FORMAS_PAGO.find((f) => f.id === formaPagoId) ?? FORMAS_PAGO[0]
  const esEfectivo = forma.esEfectivo
  const cambio = esEfectivo && Number(recibido) >= total ? Number(recibido) - total : 0

  const cajaSeleccionada = typeof cajaId === 'number'
  const turnoCargando = cajaSeleccionada && turnoActual.isLoading
  const turnoAbierto = cajaSeleccionada && turnoActual.isSuccess
  const turnoFalta = cajaSeleccionada && turnoActual.isError

  const puedeVender =
    almacenId !== '' &&
    turnoAbierto &&
    lineas.length > 0 &&
    lineas.every((l) => l.cantidad > 0 && l.precioUnitario >= 0) &&
    (!esEfectivo || Number(recibido) >= total)

  const puedeLimpiar = lineas.length > 0 || busqueda !== '' || recibido !== '' || referencia !== '' || notas !== ''
  const puedeCancelar = puedeLimpiar || clienteId !== '' || formaPagoId !== 1

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (ventaResultado || confirmAbierto) return
      if (e.key === 'F1') {
        e.preventDefault()
        buscadorRef.current?.focus()
        buscadorRef.current?.select()
        return
      }
      if (e.key === 'F2') {
        if (!puedeVender || checkout.isPending) return
        e.preventDefault()
        setConfirmAbierto(true)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [puedeVender, checkout, ventaResultado, confirmAbierto])

  const cambiarCantidad = (id: number, n: number) => setLineas((prev) => prev.map((l) => (l.productoId === id ? { ...l, cantidad: Math.max(0, n) } : l)))
  const cambiarPrecio = (id: number, p: number) => setLineas((prev) => prev.map((l) => (l.productoId === id ? { ...l, precioUnitario: Math.max(0, p) } : l)))

  const columnasVentasHoy: Columna<Venta>[] = [
    { key: 'f', header: 'Folio', render: (v) => <span className="font-mono text-xs font-medium text-ink">{v.folio}</span> },
    { key: 'fe', header: 'Hora', render: (v) => <span className="whitespace-nowrap tabular-nums">{formatoFechaHora(v.fecha)}</span> },
    { key: 'c', header: 'Cliente', render: (v) => v.clienteNombre ?? <span className="text-muted">Consumidor final</span> },
    { key: 'p', header: 'Pago', render: (v) => v.formaPagoNombre },
    { key: 't', header: 'Total', align: 'right', render: (v) => <span className="font-semibold tabular-nums">{formatoMoneda(v.total)}</span> },
    {
      key: 'e',
      header: 'Estado',
      render: (v) => v.estado === 'CANCELADA'
        ? <Badge tone="danger">Cancelada</Badge>
        : v.estado === 'COMPLETADA'
          ? <Badge tone="success">Completada</Badge>
          : <Badge>{v.estado}</Badge>,
    },
  ]

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Punto de venta</h1>
          <p className="text-sm text-muted">Registra ventas al instante: busca el producto, cobra y entrega el ticket.</p>
        </div>
      </header>

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="space-y-4 lg:col-span-3">
          <Card>
            <div className="grid gap-3 sm:grid-cols-2">
              <Select label="Almacén / punto de venta" required value={almacenId} onChange={(e) => setAlmacenId(e.target.value ? Number(e.target.value) : '')}>
                <option value="">Selecciona…</option>
                {almacenes.data?.map((a) => (
                  <option key={a.almacenId} value={a.almacenId}>
                    {a.nombre}
                  </option>
                ))}
              </Select>
              <Select
                label="Caja donde operas"
                required
                value={cajaId}
                onChange={(e) => setCajaId(e.target.value ? Number(e.target.value) : '')}
                hint="La venta se asocia al turno abierto de esta caja."
              >
                <option value="">Selecciona…</option>
                {cajas.data?.filter((c) => c.activa).map((c: Caja) => (
                  <option key={c.cajaId} value={c.cajaId}>
                    {c.nombre} · {c.almacenNombre}
                  </option>
                ))}
              </Select>
            </div>
            <div className="mt-3">
              <Select label="Cliente (opcional)" value={clienteId} onChange={(e) => setClienteId(e.target.value)}>
                <option value="">Consumidor final</option>
                {clientes.data?.data.map((c) => (
                  <option key={c.clienteId} value={c.clienteId}>
                    {c.razonSocial}
                  </option>
                ))}
              </Select>
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button variant="secondary" disabled={!puedeLimpiar} onClick={limpiarTicket} aria-label="Limpiar ticket">
                <Eraser className="h-4 w-4" /> Limpiar
              </Button>
              <Button variant="ghost" disabled={!puedeCancelar || checkout.isPending} onClick={() => setCancelarAbierto(true)} aria-label="Cancelar venta">
                <Ban className="h-4 w-4" /> Cancelar venta
              </Button>
              <Button variant="ghost" onClick={() => setVentasDiaAbierto(true)} aria-label="Ver ventas del día">
                Ventas del día
              </Button>
            </div>

            {cajaSeleccionada && (
              <div
                role={turnoFalta ? 'alert' : 'status'}
                aria-live="polite"
                className={`mt-3 flex items-start gap-2 rounded-md border p-2.5 text-sm ${
                  turnoFalta
                    ? 'border-amber-300 bg-amber-50 text-amber-900'
                    : turnoCargando
                      ? 'border-line bg-canvas text-muted'
                      : 'border-green-200 bg-green-50 text-green-800'
                }`}
              >
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                <div className="flex-1">
                  {turnoCargando && <span>Verificando turno abierto…</span>}
                  {turnoFalta && (
                    <>
                      <p className="font-medium">Esta caja no tiene un turno abierto.</p>
                      <p className="text-xs">No puedes registrar ventas hasta que abras un turno desde Cajas y turnos.</p>
                    </>
                  )}
                  {turnoAbierto && turnoActual.data && (
                    <p className="text-xs">
                      Turno <span className="font-semibold">#{turnoActual.data.turnoCajaId}</span> abierto desde{' '}
                      <span className="tabular-nums">{formatoFechaHora(turnoActual.data.aperturaEn)}</span>.
                    </p>
                  )}
                </div>
                {turnoFalta && (
                  <Link
                    to="/caja/cajas"
                    className="shrink-0 rounded-md border border-amber-300 bg-white px-2.5 py-1 text-xs font-medium text-amber-900 hover:bg-amber-100"
                  >
                    Abrir turno
                  </Link>
                )}
              </div>
            )}

            <div className="mt-3 flex flex-wrap items-end gap-2">
              <div className="flex-1 min-w-[16rem]">
                <Input
                  label="Buscar producto"
                  value={busqueda}
                  onChange={(e) => setBusqueda(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      const limpio = busqueda.trim()
                      if (limpio) setQ(limpio)
                    }
                  }}
                  placeholder="Escanea el código o escribe el nombre"
                  className="w-full"
                  ref={buscadorRef}
                  autoFocus
                  hint={
                    modoBarcode
                      ? 'Código de barras detectado: se añade directo al ticket.'
                      : 'F1 enfoca · Enter busca · Escáner añade directo'
                  }
                />
              </div>
              <Button
                onClick={() => busqueda.trim() && setQ(busqueda.trim())}
                disabled={resultados.isFetching || busqueda.trim() === '' || busqueda.trim() === q}
              >
                <Search className="h-4 w-4" /> Buscar
              </Button>
              {modoBarcode && (
                <span
                  className="inline-flex h-9 items-center gap-1 rounded-md border border-primary/40 bg-orange-100 px-2 text-xs font-medium text-primary"
                  aria-live="polite"
                >
                  <Barcode className="h-3.5 w-3.5" /> código
                </span>
              )}
            </div>

            {qEfectivo && resultados.isLoading && <Spinner />}
            {qEfectivo && resultados.data && (
              <div className="mt-3 max-h-64 overflow-auto rounded-md border border-line" role="listbox" aria-label="Resultados de búsqueda">
                {resultados.data.data.length === 0 && (
                  <p className="p-3 text-sm text-muted">Sin coincidencias para &ldquo;{qEfectivo}&rdquo;.</p>
                )}
                {resultados.data.data.map((p) => (
                  <button
                    key={p.productoId}
                    type="button"
                    onClick={() => agregar(p)}
                    className="flex w-full items-center justify-between gap-3 border-b border-line px-3 py-2 text-left hover:bg-orange-50 focus:bg-orange-50 focus:outline-none"
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium text-ink">{p.nombre}</span>
                      <span className="text-xs text-muted">
                        {p.codigo ?? '—'} · {p.unidadMedidaClave} · {p.categoriaNombre}
                      </span>
                    </span>
                    <span className="shrink-0 text-sm font-semibold text-primary">{formatoMoneda(p.precioMenudeo)}</span>
                  </button>
                ))}
              </div>
            )}
          </Card>

          {resultados.error && (
            <p className="text-sm text-red-600">{esApiError(resultados.error) ? resultados.error.mensajeParaUsuario() : String(resultados.error)}</p>
          )}
        </div>

        <div className="space-y-4 lg:col-span-2">
          <Card titulo={`Ticket (${lineas.length})`}>
            {lineas.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted">Agrega productos con el buscador.</p>
            ) : (
              <div className="space-y-2">
                {lineas.map((l) => (
                  <div key={l.productoId} className="flex items-center gap-2 rounded-md border border-line px-2 py-1.5">
                    <button type="button" aria-label="Quitar" className="text-muted hover:text-red-600" onClick={() => setLineas((prev) => prev.filter((x) => x.productoId !== l.productoId))}>
                      <Trash2 className="h-4 w-4" />
                    </button>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-ink">{l.nombre}</p>
                      <div className="flex items-center gap-2">
                        <button type="button" aria-label="Menos" onClick={() => cambiarCantidad(l.productoId, l.cantidad - 1)} className="rounded bg-warmbg p-0.5 hover:bg-warmbg">
                          <Minus className="h-3 w-3" />
                        </button>
                        <input
                          type="number"
                          inputMode="decimal"
                          min={0}
                          step="1"
                          value={l.cantidad}
                          onChange={(e) => cambiarCantidad(l.productoId, Number(e.target.value))}
                          className="w-14 rounded border border-line px-1 py-0.5 text-center text-sm"
                          aria-label={`Cantidad de ${l.nombre}`}
                        />
                        <button type="button" aria-label="Más" onClick={() => cambiarCantidad(l.productoId, l.cantidad + 1)} className="rounded bg-warmbg p-0.5 hover:bg-warmbg">
                          <Plus className="h-3 w-3" />
                        </button>
                        <span className="text-xs text-muted">×</span>
                        <input
                          type="number"
                          inputMode="decimal"
                          min={0}
                          step="0.01"
                          value={l.precioUnitario}
                          onChange={(e) => cambiarPrecio(l.productoId, Number(e.target.value))}
                          className="w-24 rounded border border-line px-1 py-0.5 text-right text-sm"
                          aria-label={`Precio de ${l.nombre}`}
                        />
                      </div>
                    </div>
                    <span className="shrink-0 text-sm font-semibold tabular-nums">{formatoMoneda(l.cantidad * l.precioUnitario)}</span>
                  </div>
                ))}
                <div className="flex items-center justify-between pt-2 text-base font-bold text-ink">
                  <span>Total</span>
                  <span className="tabular-nums">{formatoMoneda(total)}</span>
                </div>
              </div>
            )}
          </Card>

          <Card titulo="Cobro">
            <div className="grid grid-cols-2 gap-3">
              <Select label="Forma de pago" value={formaPagoId} onChange={(e) => setFormaPagoId(Number(e.target.value))}>
                {FORMAS_PAGO.map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.nombre}
                  </option>
                ))}
              </Select>
              {esEfectivo ? (
                <Input
                  label="Recibido"
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  value={recibido}
                  onChange={(e) => setRecibido(e.target.value)}
                  placeholder={String(total)}
                />
              ) : (
                <Input
                  label="Referencia"
                  value={referencia}
                  onChange={(e) => setReferencia(e.target.value)}
                  required={forma.requiereReferencia}
                  placeholder={forma.requiereReferencia ? 'Últimos 4 dígitos / SPEI' : ''}
                  disabled={!forma.requiereReferencia}
                />
              )}
            </div>
            {esEfectivo && (
              <div className="mt-2 flex items-center justify-between rounded-md bg-canvas px-3 py-2 text-sm">
                <span className="text-muted">Cambio</span>
                <span className="font-semibold tabular-nums text-ink">{formatoMoneda(cambio)}</span>
              </div>
            )}
            <Input label="Notas (opcional)" value={notas} onChange={(e) => setNotas(e.target.value)} className="mt-3" />
            <Button
              ref={cobrarRef}
              type="button"
              disabled={!puedeVender || checkout.isPending}
              onClick={() => setConfirmAbierto(true)}
              className="mt-3 w-full"
              size="lg"
              title="Atajo: F2"
            >
              <ShoppingBasket className="h-5 w-5" />
              {checkout.isPending ? 'Registrando…' : `Cobrar ${formatoMoneda(total)}`}
            </Button>
          </Card>
        </div>
      </div>

      <Dialog
        open={confirmAbierto}
        onClose={() => !checkout.isPending && setConfirmAbierto(false)}
        title="Confirmar venta"
        width="max-w-2xl"
      >
        {confirmAbierto && (() => {
          const almacenNombre = almacenes.data?.find((a) => a.almacenId === Number(almacenId))?.nombre ?? `#${almacenId}`
          const clienteNombre = clienteId
            ? clientes.data?.data.find((c) => c.clienteId === Number(clienteId))?.razonSocial ?? `Cliente #${clienteId}`
            : 'Consumidor final'
          const recibidoNum = Number(recibido)
          return (
            <div className="space-y-3 text-sm">
              <div className="grid grid-cols-1 gap-2 rounded-md bg-canvas p-3 sm:grid-cols-2">
                <p className="flex items-center gap-1.5 text-muted">
                  <Store className="h-4 w-4" />
                  <span className="text-muted">Almacén:</span>
                  <span className="font-medium text-ink">{almacenNombre}</span>
                </p>
                <p className="flex items-center gap-1.5 text-muted">
                  <User className="h-4 w-4" />
                  <span className="text-muted">Cliente:</span>
                  <span className="font-medium text-ink">{clienteNombre}</span>
                </p>
              </div>

              <div>
                <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">
                  Artículos ({lineas.reduce((n, l) => n + l.cantidad, 0)})
                </p>
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
                      {lineas.map((l) => (
                        <tr key={l.productoId}>
                          <td className="px-2 py-1.5">
                            <span className="font-medium text-ink">{l.nombre}</span>
                            {l.codigo && <span className="ml-2 font-mono text-xs text-muted">{l.codigo}</span>}
                            {!l.aplicaIva && (
                              <Badge tone="info" className="ml-2">Sin IVA</Badge>
                            )}
                          </td>
                          <td className="px-2 py-1.5 text-right tabular-nums">{l.cantidad}</td>
                          <td className="px-2 py-1.5 text-right tabular-nums">{formatoMoneda(l.precioUnitario)}</td>
                          <td className="px-2 py-1.5 text-right font-medium tabular-nums">
                            {formatoMoneda(l.cantidad * l.precioUnitario)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 rounded-md bg-canvas p-3 sm:grid-cols-4">
                <div>
                  <p className="text-xs text-muted">Subtotal</p>
                  <p className="font-medium tabular-nums">{formatoMoneda(resumen.subtotalSinIva)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted">IVA (16%)</p>
                  <p className="font-medium tabular-nums">{formatoMoneda(resumen.ivaEstimado)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted">Forma de pago</p>
                  <p className="font-medium text-ink">{forma.nombre}</p>
                </div>
                <div>
                  <p className="text-xs text-muted">Total</p>
                  <p className="text-base font-bold tabular-nums text-primary">{formatoMoneda(resumen.total)}</p>
                </div>
              </div>

              {esEfectivo ? (
                <div className="grid grid-cols-2 gap-2 rounded-md border border-line p-3 text-sm">
                  <div>
                    <p className="text-xs text-muted">Recibido</p>
                    <p className="font-medium tabular-nums">{formatoMoneda(Number.isFinite(recibidoNum) ? recibidoNum : 0)}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted">Cambio</p>
                    <p className="font-bold tabular-nums text-green-700">{formatoMoneda(cambio)}</p>
                  </div>
                </div>
              ) : (
                referencia.trim() && (
                  <div className="rounded-md border border-line p-3 text-sm">
                    <p className="text-xs text-muted">Referencia</p>
                    <p className="font-medium text-ink">{referencia.trim()}</p>
                  </div>
                )
              )}

              {notas.trim() && (
                <div className="rounded-md border border-line p-3 text-sm">
                  <p className="text-xs text-muted">Notas</p>
                  <p className="text-ink">{notas.trim()}</p>
                </div>
              )}

              <p className="text-xs text-muted">
                Verifica los importes antes de cobrar. Una vez confirmada, la venta se registra, descuenta inventario y
                aparece en el ticket de salida.
              </p>

              <div className="flex justify-end gap-2">
                <Button variant="ghost" disabled={checkout.isPending} onClick={() => setConfirmAbierto(false)}>
                  Cancelar
                </Button>
                <Button
                  ref={confirmarRef}
                  disabled={checkout.isPending}
                  onClick={() => checkout.mutate()}
                  title="Enter"
                >
                  <ShoppingBasket className="h-4 w-4" />
                  {checkout.isPending ? 'Registrando…' : `Confirmar y cobrar ${formatoMoneda(resumen.total)}`}
                </Button>
              </div>
            </div>
          )
        })()}
      </Dialog>

      <Dialog open={ventaResultado !== null} onClose={() => setVentaResultado(null)} title="Venta registrada" width="max-w-md">
        {ventaResultado && (
          <div className="space-y-3 text-center">
            <Badge tone="success">Completada</Badge>
            <div>
              <p className="text-sm text-muted">Folio</p>
              <p className="text-lg font-bold text-ink">{ventaResultado.folio}</p>
            </div>
            <div className="grid grid-cols-2 gap-2 rounded-md bg-canvas p-3 text-left text-sm">
              <span className="text-muted">Total</span>
              <span className="text-right font-semibold tabular-nums">{formatoMoneda(ventaResultado.total)}</span>
              <span className="text-muted">Pago</span>
              <span className="text-right font-medium">{ventaResultado.formaPagoNombre}</span>
              <span className="text-muted">Fecha</span>
              <span className="text-right tabular-nums">{new Date(ventaResultado.fecha).toLocaleString('es-MX')}</span>
            </div>
            <div className="flex justify-center gap-2">
              <Link to="/ventas/cobranza" className="text-sm text-primary hover:underline">
                Ver cobranza
              </Link>
              <Link to="/dashboard" className="text-sm text-primary hover:underline">
                Ir al inicio
              </Link>
            </div>
          </div>
        )}
      </Dialog>

      <Dialog
        open={cancelarAbierto}
        onClose={() => setCancelarAbierto(false)}
        title="¿Cancelar la venta en curso?"
        width="max-w-md"
      >
        <div className="space-y-3 text-sm">
          <p className="text-muted">
            Se descartarán <span className="font-medium text-ink">{lineas.length}</span> líneas,
            los pagos parciales y las notas. El almacén y la caja se conservan para la siguiente venta.
          </p>
          <div className="flex justify-end gap-2 pt-1">
            <Button variant="ghost" onClick={() => setCancelarAbierto(false)}>
              Volver
            </Button>
            <Button variant="danger" onClick={cancelarVenta}>
              <Ban className="h-4 w-4" /> Sí, cancelar
            </Button>
          </div>
        </div>
      </Dialog>

      <Dialog
        open={ventasDiaAbierto}
        onClose={() => setVentasDiaAbierto(false)}
        title={`Ventas de hoy (${hoyLocal()})`}
        width="max-w-3xl"
      >
        <div className="space-y-3">
          {ventasHoy.isLoading && <Spinner label="Cargando ventas del día…" />}
          {ventasHoy.error && (
            <p className="text-sm text-red-600">
              {esApiError(ventasHoy.error) ? ventasHoy.error.mensajeParaUsuario() : String(ventasHoy.error)}
            </p>
          )}
          {ventasHoy.data && (
            <>
              <div className="grid grid-cols-2 gap-2 rounded-md bg-canvas p-3 text-sm sm:grid-cols-3">
                <div>
                  <p className="text-xs text-muted">Tickets</p>
                  <p className="text-base font-bold tabular-nums">{ventasHoy.data.meta.totalElements}</p>
                </div>
                <div>
                  <p className="text-xs text-muted">Total vendido</p>
                  <p className="text-base font-bold tabular-nums text-primary">
                    {formatoMoneda(ventasHoy.data.data.reduce((acc, v) => acc + v.total, 0))}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted">Promedio</p>
                  <p className="text-base font-bold tabular-nums">
                    {formatoMoneda(
                      ventasHoy.data.meta.totalElements > 0
                        ? ventasHoy.data.data.reduce((acc, v) => acc + v.total, 0) / ventasHoy.data.meta.totalElements
                        : 0,
                    )}
                  </p>
                </div>
              </div>
              {ventasHoy.data.data.length === 0 ? (
                <p className="py-6 text-center text-sm text-muted">Aún no hay ventas hoy.</p>
              ) : (
                <DataTable<Venta>
                  columnas={columnasVentasHoy}
                  items={ventasHoy.data.data}
                  rowKey={(v) => v.ventaId}
                  loading={ventasHoy.isFetching}
                />
              )}
            </>
          )}
          <div className="flex justify-end pt-1">
            <Button variant="ghost" onClick={() => setVentasDiaAbierto(false)}>
              Cerrar
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  )
}