import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PackagePlus, Pencil, Search, Trash2 } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiActualizarProducto, apiCategoriasArbol, apiCrearProducto, apiEliminarProducto, apiMarcas, apiProductos, apiUnidadesMedida } from '@/lib/api/catalogo'
import type { Categoria, Producto, ProductoRequest } from '@/lib/api/types'
import { TIPOS_PRODUCTO } from '@/lib/api/types'
import { formatoMoneda } from '@/lib/format'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

function TipoBadge({ tipo }: { tipo: string }) {
  if (tipo === 'HERRAMIENTA_RENTA') return <Badge tone="warning">Renta</Badge>
  if (tipo === 'SERVICIO') return <Badge tone="info">Servicio</Badge>
  return <Badge>Producto</Badge>
}

function aplanarCategorias(cats: Categoria[], prefijo = ''): { id: number; label: string }[] {
  return cats.flatMap((c) => [
    { id: c.categoriaId, label: `${prefijo}${c.nombre}` },
    ...(c.hijos?.length ? aplanarCategorias(c.hijos, `${prefijo}${c.nombre} · `) : []),
  ])
}

function campoNumero(valor: string): number | null {
  const limpio = valor.trim()
  if (!limpio) return null
  const n = Number(limpio)
  return Number.isFinite(n) ? n : null
}

function ProductoForm({
  producto,
  categorias,
  marcas,
  unidades,
  guardando,
  onGuardar,
  onClose,
}: {
  producto: Producto | null
  categorias: Categoria[]
  marcas: { marcaId: number; nombre: string }[]
  unidades: { unidadId: number; clave: string; nombre: string }[]
  guardando: boolean
  onGuardar: (payload: ProductoRequest) => void
  onClose: () => void
}) {
  const [codigo, setCodigo] = useState(producto?.codigo ?? '')
  const [tipo, setTipo] = useState<string>(producto?.tipo ?? 'PRODUCTO')
  const [nombre, setNombre] = useState(producto?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(producto?.descripcion ?? '')
  const [categoriaId, setCategoriaId] = useState<number | ''>(producto?.categoriaId ?? '')
  const [marcaId, setMarcaId] = useState<string>(producto?.marcaId ? String(producto.marcaId) : '')
  const [unidadId, setUnidadId] = useState<number | ''>(producto?.unidadMedidaId ?? '')
  const [costo, setCosto] = useState(producto ? String(producto.costoActual) : '')
  const [menudeo, setMenudeo] = useState(producto ? String(producto.precioMenudeo) : '')
  const [mayoreo, setMayoreo] = useState(producto?.precioMayoreo != null ? String(producto.precioMayoreo) : '')
  const [aplicaIva, setAplicaIva] = useState(producto?.aplicaIva ?? true)
  const [intento, setIntento] = useState(false)

  const invalido = nombre.trim() === '' || categoriaId === '' || unidadId === ''

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({
      codigo: codigo.trim() || undefined,
      tipo: tipo as ProductoRequest['tipo'],
      nombre: nombre.trim(),
      descripcion: descripcion.trim() || undefined,
      categoriaId: Number(categoriaId),
      marcaId: marcaId ? Number(marcaId) : null,
      unidadMedidaId: Number(unidadId),
      costoActual: campoNumero(costo),
      precioMenudeo: campoNumero(menudeo),
      precioMayoreo: campoNumero(mayoreo),
      aplicaIva,
    })
  }

  return (
    <form onSubmit={enviar} className="grid grid-cols-1 gap-3 sm:grid-cols-2" noValidate>
      <div className="sm:col-span-2">
        <Input label="Nombre" required value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Ej. Taladro percutor 1/2 650W" />
      </div>
      <Input label="Código" value={codigo} onChange={(e) => setCodigo(e.target.value)} placeholder="Ej. TAL-005" hint="Opcional; si lo omites se asigna automáticamente" />
      <Select label="Tipo" required value={tipo} onChange={(e) => setTipo(e.target.value)}>
        {TIPOS_PRODUCTO.map((t) => (
          <option key={t} value={t}>
            {t === 'PRODUCTO' ? 'Producto' : t === 'SERVICIO' ? 'Servicio' : 'Herramienta en renta'}
          </option>
        ))}
      </Select>
      <Select label="Categoría" required value={categoriaId} onChange={(e) => setCategoriaId(e.target.value ? Number(e.target.value) : '')}>
        <option value="">Selecciona…</option>
        {aplanarCategorias(categorias).map((c) => (
          <option key={c.id} value={c.id}>
            {c.label}
          </option>
        ))}
      </Select>
      <Select label="Marca" value={marcaId} onChange={(e) => setMarcaId(e.target.value)}>
        <option value="">— Sin marca —</option>
        {marcas.map((m) => (
          <option key={m.marcaId} value={m.marcaId}>
            {m.nombre}
          </option>
        ))}
      </Select>
      <Select label="Unidad de medida" required value={unidadId} onChange={(e) => setUnidadId(e.target.value ? Number(e.target.value) : '')}>
        <option value="">Selecciona…</option>
        {unidades.map((u) => (
          <option key={u.unidadId} value={u.unidadId}>
            {u.nombre} ({u.clave})
          </option>
        ))}
      </Select>
      <div className="sm:col-span-2">
        <Input label="Descripción" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      </div>
      <Input label="Costo actual" type="number" inputMode="decimal" step="0.01" min="0" value={costo} onChange={(e) => setCosto(e.target.value)} />
      <Input label="Precio menudeo" type="number" inputMode="decimal" step="0.01" min="0" value={menudeo} onChange={(e) => setMenudeo(e.target.value)} />
      <Input label="Precio mayoreo" type="number" inputMode="decimal" step="0.01" min="0" value={mayoreo} onChange={(e) => setMayoreo(e.target.value)} />
      <label className="flex items-center gap-2 text-sm pt-2">
        <input type="checkbox" checked={aplicaIva} onChange={(e) => setAplicaIva(e.target.checked)} className="h-4 w-4 accent-primary" />
        <span className="font-medium text-ink">Aplica IVA</span>
      </label>
      {intento && invalido && (
        <p className="text-xs text-red-600 sm:col-span-2">Completa nombre, categoría y unidad de medida.</p>
      )}
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" disabled={guardando}>
          {guardando ? 'Guardando…' : 'Guardar'}
        </Button>
      </div>
    </form>
  )
}

export default function ProductosPage() {
  useDocumentTitle('Productos')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()

  const [busqueda, setBusqueda] = useState('')
  const [filtroQ, setFiltroQ] = useState('')
  const [filtroTipo, setFiltroTipo] = useState('')
  const [page, setPage] = useState(0)
  const [dialogoAbierto, setDialogoAbierto] = useState(false)
  const [editando, setEditando] = useState<Producto | null>(null)
  const [eliminarConfirmacion, setEliminarConfirmacion] = useState<Producto | null>(null)

  const aplicarBusqueda = () => {
    setFiltroTipo('')
    setFiltroQ(busqueda.trim())
    setPage(0)
  }
  const aplicarTipo = (tipo: string) => {
    setBusqueda('')
    setFiltroQ('')
    setFiltroTipo(tipo)
    setPage(0)
  }

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['productos', filtroQ, filtroTipo, page],
    queryFn: () => apiProductos({ q: filtroQ || undefined, tipo: filtroTipo || undefined, page, size: 20 }),
  })

  const categorias = useQuery({ queryKey: ['categorias-arbol'], queryFn: apiCategoriasArbol })
  const marcas = useQuery({ queryKey: ['marcas'], queryFn: apiMarcas })
  const unidades = useQuery({ queryKey: ['unidades-medida'], queryFn: apiUnidadesMedida })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const invalidar = () => queryClient.invalidateQueries({ queryKey: ['productos'] })

  const mutation = useMutation({
    mutationFn: (payload: { id: number | null; body: ProductoRequest }) =>
      payload.id == null ? apiCrearProducto(payload.body) : apiActualizarProducto(payload.id, payload.body),
    onSuccess: (_, vars) => {
      mostrarExito(vars.id == null ? 'Producto creado.' : 'Producto actualizado.')
      setDialogoAbierto(false)
      setEditando(null)
      invalidar()
    },
    onError: (err) => {
      mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err))
    },
  })

  const eliminar = useMutation({
    mutationFn: (id: number) => apiEliminarProducto(id),
    onSuccess: () => {
      mostrarExito('Producto desactivado.')
      setEliminarConfirmacion(null)
      invalidar()
    },
    onError: (err) => {
      mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err))
    },
  })

  const columnas: Columna<Producto>[] = [
    { key: 'c', header: 'Código', render: (v) => <span className="font-mono text-xs text-muted">{v.codigo ?? '—'}</span> },
    { key: 'n', header: 'Nombre', render: (v) => <span className="font-medium text-ink">{v.nombre}</span> },
    { key: 't', header: 'Tipo', render: (v) => <TipoBadge tipo={v.tipo} /> },
    { key: 'cat', header: 'Categoría', render: (v) => v.categoriaNombre },
    { key: 'm', header: 'Marca', render: (v) => v.marcaNombre ?? '—' },
    { key: 'u', header: 'U.M.', render: (v) => v.unidadMedidaClave },
    { key: 'costo', header: 'Costo', align: 'right', render: (v) => formatoMoneda(v.costoActual) },
    { key: 'precio', header: 'Menudeo', align: 'right', render: (v) => formatoMoneda(v.precioMenudeo) },
    {
      key: 'acc',
      header: 'Acciones',
      align: 'right',
      render: (v) => (
        <div className="flex justify-end gap-1">
          <button
            type="button"
            aria-label={`Editar ${v.nombre}`}
            className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
            onClick={() => {
              setEditando(v)
              setDialogoAbierto(true)
            }}
          >
            <Pencil className="h-4 w-4" />
          </button>
          <button
            type="button"
            aria-label={`Desactivar ${v.nombre}`}
            className="rounded p-1.5 text-muted hover:bg-red-50 hover:text-red-600"
            onClick={() => setEliminarConfirmacion(v)}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      ),
    },
  ]

  const guardar = (payload: ProductoRequest) => {
    mutation.mutate({ id: editando?.productoId ?? null, body: payload })
  }

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Productos</h1>
          <p className="text-sm text-muted">Catálogo de artículos en venta, servicios y herramientas en renta.</p>
        </div>
        <Button
          onClick={() => {
            setEditando(null)
            setDialogoAbierto(true)
          }}
        >
          <PackagePlus className="h-4 w-4" /> Nuevo producto
        </Button>
      </header>

      <Card>
        <div className="flex flex-wrap items-end gap-2">
          <Input label="Buscar" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && aplicarBusqueda()} placeholder="Nombre del producto" className="w-64" />
          <Button onClick={aplicarBusqueda} disabled={isFetching || busqueda === filtroQ}>
            <Search className="h-4 w-4" /> Buscar
          </Button>
          <Select label="Tipo" value={filtroTipo} onChange={(e) => aplicarTipo(e.target.value)} className="w-44">
            <option value="">Todos</option>
            <option value="PRODUCTO">Producto</option>
            <option value="SERVICIO">Servicio</option>
            <option value="HERRAMIENTA_RENTA">Herramienta en renta</option>
          </Select>
          {(filtroQ || filtroTipo) && (
            <Button
              variant="ghost"
              onClick={() => {
                setBusqueda('')
                setFiltroQ('')
                setFiltroTipo('')
                setPage(0)
              }}
            >
              Limpiar
            </Button>
          )}
        </div>
      </Card>

      {(isLoading || (isFetching && !data)) && <Spinner />}
      {data && (
        <Card titulo={`Resultados (${data.meta.totalElements})`}>
          <DataTable columnas={columnas} items={data.data} rowKey={(v) => v.productoId} loading={isFetching} />
          <Pagination meta={data.meta} onPage={setPage} />
        </Card>
      )}

      <Dialog
        open={dialogoAbierto}
        onClose={() => !mutation.isPending && setDialogoAbierto(false)}
        title={editando ? `Editar: ${editando.nombre}` : 'Nuevo producto'}
        width="max-w-2xl"
      >
        {categorias.isLoading || marcas.isLoading || unidades.isLoading ? (
          <Spinner />
        ) : (
          <ProductoForm
            producto={editando}
            categorias={categorias.data ?? []}
            marcas={marcas.data ?? []}
            unidades={unidades.data ?? []}
            guardando={mutation.isPending}
            onGuardar={guardar}
            onClose={() => setDialogoAbierto(false)}
          />
        )}
      </Dialog>

      <ConfirmDialog
        open={eliminarConfirmacion !== null}
        title="Confirmar desactivación"
        confirmLabel="Sí, desactivar"
        busy={eliminar.isPending}
        onCancel={() => setEliminarConfirmacion(null)}
        onConfirm={() => eliminarConfirmacion && eliminar.mutate(eliminarConfirmacion.productoId)}
      >
        <p className="text-sm text-ink">
          ¿Desactivar el producto <span className="font-semibold">&quot;{eliminarConfirmacion?.nombre}&quot;</span>? Dejará de estar disponible en la venta, pero se conserva su historial.
        </p>
      </ConfirmDialog>
    </div>
  )
}