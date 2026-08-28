import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, Trash2 } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiAlmacenes, apiProductos } from '@/lib/api/catalogo'
import { apiCrearTraslado, apiTraslados } from '@/lib/api/inventario'
import type { Producto, Traslado, TrasladoRequest } from '@/lib/api/types'
import { formatoFechaHora } from '@/lib/format'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

interface Partida {
  productoId: number
  nombre: string
  cantidad: number
}

function TrasladoForm({
  guardando,
  onGuardar,
  onClose,
}: {
  guardando: boolean
  onGuardar: (payload: TrasladoRequest) => void
  onClose: () => void
}) {
  const almacenes = useQuery({ queryKey: ['almacenes'], queryFn: apiAlmacenes })
  const [origen, setOrigen] = useState<number | ''>('')
  const [destino, setDestino] = useState<number | ''>('')
  const [partidas, setPartidas] = useState<Partida[]>([])
  const [busqueda, setBusqueda] = useState('')
  const [q, setQ] = useState('')
  const [intento, setIntento] = useState(false)

  const resultados = useQuery({
    queryKey: ['productos-traslado', q],
    queryFn: () => apiProductos({ q: q || undefined, page: 0, size: 20 }),
    enabled: q.length > 0,
  })

  const mismoAlmacen = origen !== '' && destino !== '' && origen === destino

  const agregar = (p: Producto) => {
    setPartidas((prev) => {
      const exist = prev.find((x) => x.productoId === p.productoId)
      if (exist) return prev
      return [...prev, { productoId: p.productoId, nombre: p.nombre, cantidad: 1 }]
    })
  }

  const invalido =
    origen === '' || destino === '' || mismoAlmacen || partidas.length === 0 || partidas.some((x) => x.cantidad <= 0)

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({
      almacenOrigen: Number(origen),
      almacenDestino: Number(destino),
      detalles: partidas.map((x) => ({ productoId: x.productoId, cantidad: x.cantidad })),
    })
  }

  return (
    <form onSubmit={enviar} className="space-y-3" noValidate>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Select label="Almacén origen" required value={origen} onChange={(e) => setOrigen(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Selecciona…</option>
          {almacenes.data?.map((a) => (
            <option key={a.almacenId} value={a.almacenId}>
              {a.nombre}
            </option>
          ))}
        </Select>
        <Select
          label="Almacén destino"
          required
          error={mismoAlmacen ? 'El almacén de destino debe ser distinto al de origen.' : undefined}
          value={destino}
          onChange={(e) => setDestino(e.target.value ? Number(e.target.value) : '')}
        >
          <option value="">Selecciona…</option>
          {almacenes.data?.map((a) => (
            <option key={a.almacenId} value={a.almacenId}>
              {a.nombre}
            </option>
          ))}
        </Select>
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <Input label="Buscar producto" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && setQ(busqueda.trim())} placeholder="Artículo a trasladar" className="w-72" />
        <Button variant="secondary" disabled={resultados.isFetching || busqueda.trim() === q} onClick={() => setQ(busqueda.trim())}>
          <Search className="h-4 w-4" /> Buscar
        </Button>
      </div>
      {q && resultados.data && (
        <div className="max-h-40 overflow-auto rounded-md border border-line">
          {resultados.data.data.length === 0 && <p className="p-3 text-sm text-muted">Sin coincidencias.</p>}
          {resultados.data.data.map((p) => (
            <button key={p.productoId} type="button" onClick={() => agregar(p)} className="flex w-full items-center justify-between gap-3 border-b border-line px-3 py-1.5 text-left hover:bg-primary-50">
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
          {partidas.map((x) => (
            <div key={x.productoId} className="flex items-center gap-2 rounded-md border border-line px-2 py-1.5">
              <button type="button" aria-label="Quitar" className="text-muted hover:text-red-600" onClick={() => setPartidas((prev) => prev.filter((y) => y.productoId !== x.productoId))}>
                <Trash2 className="h-4 w-4" />
              </button>
              <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink">{x.nombre}</span>
              <input
                type="number"
                inputMode="numeric"
                min={1}
                step="1"
                value={x.cantidad}
                onChange={(e) => setPartidas((prev) => prev.map((y) => (y.productoId === x.productoId ? { ...y, cantidad: Number(e.target.value) } : y)))}
                className="w-16 rounded border border-line px-1 py-0.5 text-right text-sm"
                aria-label={`Cantidad de ${x.nombre}`}
              />
            </div>
          ))}
        </div>
      )}

      {intento && invalido && <p className="text-xs text-red-600">Selecciona un almacén de origen y destino distintos y agrega al menos una partida con cantidad válida.</p>}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" disabled={guardando}>
          {guardando ? 'Guardando…' : 'Registrar traslado'}
        </Button>
      </div>
    </form>
  )
}

export default function TrasladosPage() {
  useDocumentTitle('Traslados')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()

  const [estado, setEstado] = useState('')
  const [page, setPage] = useState(0)
  const [dialogoAbierto, setDialogoAbierto] = useState(false)

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['traslados', estado, page],
    queryFn: () => apiTraslados({ estado: estado || undefined, page, size: 15 }),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const crear = useMutation({
    mutationFn: (body: TrasladoRequest) => apiCrearTraslado(body),
    onSuccess: () => {
      mostrarExito('Traslado registrado: existencias actualizadas en ambos almacenes.')
      setDialogoAbierto(false)
      queryClient.invalidateQueries({ queryKey: ['traslados'] })
      queryClient.invalidateQueries({ queryKey: ['stock'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const columnas: Columna<Traslado>[] = [
    { key: 'folio', header: 'Folio', render: (v) => <span className="font-medium text-ink">{v.folio}</span> },
    { key: 'origen', header: 'Almacén origen', render: (v) => v.almacenOrigenNombre },
    { key: 'destino', header: 'Almacén destino', render: (v) => v.almacenDestinoNombre },
    {
      key: 'estado',
      header: 'Estado',
      render: (v) => <Badge tone={v.estado === 'APLICADO' ? 'success' : v.estado === 'CANCELADO' ? 'danger' : 'default'}>{v.estado}</Badge>,
    },
    { key: 'creado', header: 'Creado', render: (v) => <span className="whitespace-nowrap">{formatoFechaHora(v.creadoEn)}</span> },
  ]

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Traslados</h1>
          <p className="text-sm text-muted">Traslado de existencias entre almacenes.</p>
        </div>
        <Button onClick={() => setDialogoAbierto(true)}>
          <Plus className="h-4 w-4" /> Nuevo traslado
        </Button>
      </header>

      <Card>
        <div className="flex flex-wrap items-end gap-2">
          <Select label="Estado" value={estado} onChange={(e) => { setEstado(e.target.value); setPage(0) }} className="w-60">
            <option value="">Todas</option>
            <option value="APLICADO">APLICADO</option>
            <option value="CANCELADO">CANCELADO</option>
          </Select>
        </div>
      </Card>

      {(isLoading || (isFetching && !data)) && <Spinner />}
      {data && (
        <Card titulo={`Traslados (${data.meta.totalElements})`}>
          <DataTable columnas={columnas} items={data.data} rowKey={(v) => v.trasladoId} loading={isFetching} />
          <Pagination meta={data.meta} onPage={setPage} />
        </Card>
      )}

      <Dialog
        open={dialogoAbierto}
        onClose={() => !crear.isPending && setDialogoAbierto(false)}
        title="Nuevo traslado"
        width="max-w-2xl"
      >
        <TrasladoForm guardando={crear.isPending} onGuardar={(body) => crear.mutate(body)} onClose={() => setDialogoAbierto(false)} />
      </Dialog>
    </div>
  )
}
