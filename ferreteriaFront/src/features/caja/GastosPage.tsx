import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDownToLine, Edit, ReceiptText, Trash2 } from 'lucide-react'
import { useLocation } from 'react-router-dom'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiGastos, apiCrearGasto, apiActualizarGasto, apiEliminarGasto, apiIngresosOtros, apiCrearIngreso, apiActualizarIngreso, apiEliminarIngreso } from '@/lib/api/caja'
import { apiProveedores } from '@/lib/api/catalogo'
import type { Gasto, GastoRequest, IngresoOtro, IngresoOtroRequest } from '@/lib/api/types'
import { FORMAS_PAGO, TIPOS_GASTO } from '@/lib/api/types'
import { formatoFecha, formatoFechaHora, formatoMoneda } from '@/lib/format'
import { useTieneRol } from '@/store/auth'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

const tipoGastoDe = (id: number) => TIPOS_GASTO.find((t) => t.id === id)?.nombre ?? `Tipo ${id}`
const formaDe = (id: number) => FORMAS_PAGO.find((f) => f.id === id)?.nombre ?? `Forma ${id}`

function GastoForm({
  guardando,
  onGuardar,
  onClose,
  inicial,
}: {
  guardando: boolean
  onGuardar: (body: GastoRequest) => void
  onClose: () => void
  inicial?: Gasto
}) {
  const proveedores = useQuery({ queryKey: ['proveedores-gasto'], queryFn: () => apiProveedores() })
  const [tipoGastoId, setTipoGastoId] = useState<number | ''>(inicial?.tipoGastoId ?? '')
  const [descripcion, setDescripcion] = useState(inicial?.descripcion ?? '')
  const [monto, setMonto] = useState(inicial ? String(inicial.monto) : '')
  const [formaPagoId, setFormaPagoId] = useState(inicial?.formaPagoId ?? 1)
  const [proveedorId, setProveedorId] = useState<number | ''>(inicial?.proveedorId ?? '')
  const [fecha, setFecha] = useState(inicial?.fechaGasto ?? new Date().toISOString().slice(0, 10))
  const [intento, setIntento] = useState(false)

  const invalido = tipoGastoId === '' || descripcion.trim() === '' || Number(monto) <= 0

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({
      tipoGastoId: Number(tipoGastoId),
      descripcion: descripcion.trim(),
      monto: Number(monto),
      formaPagoId,
      fechaGasto: fecha || undefined,
      proveedorId: proveedorId === '' ? undefined : Number(proveedorId),
    })
  }

  return (
    <form onSubmit={enviar} className="grid grid-cols-1 gap-3 sm:grid-cols-2" noValidate>
      <Select label="Tipo de gasto" required value={tipoGastoId} onChange={(e) => setTipoGastoId(e.target.value ? Number(e.target.value) : '')}>
        <option value="">Selecciona…</option>
        {TIPOS_GASTO.map((t) => (
          <option key={t.id} value={t.id}>
            {t.nombre}
          </option>
        ))}
      </Select>
      <Input label="Monto" type="number" inputMode="decimal" min="0.01" step="0.01" required value={monto} onChange={(e) => setMonto(e.target.value)} />
      <div className="sm:col-span-2">
        <Input label="Descripción" required value={descripcion} onChange={(e) => setDescripcion(e.target.value)} placeholder="Ej. Flete de pedido a Culiacán" />
      </div>
      <Select label="Forma de pago" required value={formaPagoId} onChange={(e) => setFormaPagoId(Number(e.target.value))}>
        {FORMAS_PAGO.filter((f) => f.id !== 6).map((f) => (
          <option key={f.id} value={f.id}>
            {f.nombre}
          </option>
        ))}
      </Select>
      <Select label="Proveedor (opcional)" value={proveedorId} onChange={(e) => setProveedorId(e.target.value ? Number(e.target.value) : '')}>
        <option value="">Sin proveedor</option>
        {proveedores.data?.map((p) => (
          <option key={p.proveedorId} value={p.proveedorId}>
            {p.razonSocial}
          </option>
        ))}
      </Select>
      <div className="sm:col-span-2">
        <Input label="Fecha del gasto" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
      </div>
      {intento && invalido && <p className="text-xs text-red-600 sm:col-span-2">Completa tipo, monto y descripción.</p>}
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" disabled={guardando} variant={inicial ? 'primary' : 'primary'}>
          {guardando ? 'Guardando…' : inicial ? 'Guardar cambios' : 'Registrar gasto'}
        </Button>
      </div>
    </form>
  )
}

function IngresoForm({
  guardando,
  onGuardar,
  onClose,
  inicial,
}: {
  guardando: boolean
  onGuardar: (body: IngresoOtroRequest) => void
  onClose: () => void
  inicial?: IngresoOtro
}) {
  const [concepto, setConcepto] = useState(inicial?.concepto ?? '')
  const [monto, setMonto] = useState(inicial ? String(inicial.monto) : '')
  const [formaPagoId, setFormaPagoId] = useState(inicial?.formaPagoId ?? 1)
  const [fecha, setFecha] = useState(inicial?.fecha ?? new Date().toISOString().slice(0, 10))
  const [intento, setIntento] = useState(false)

  const invalido = concepto.trim() === '' || Number(monto) <= 0

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({ concepto: concepto.trim(), monto: Number(monto), formaPagoId, fecha: fecha || undefined })
  }

  return (
    <form onSubmit={enviar} className="grid grid-cols-1 gap-3 sm:grid-cols-2" noValidate>
      <Input label="Concepto" required value={concepto} onChange={(e) => setConcepto(e.target.value)} placeholder="Ej. Venta de chatarra, devolución del proveedor…" />
      <Input label="Monto" type="number" inputMode="decimal" min="0.01" step="0.01" required value={monto} onChange={(e) => setMonto(e.target.value)} />
      <Select label="Forma de pago" required value={formaPagoId} onChange={(e) => setFormaPagoId(Number(e.target.value))}>
        {FORMAS_PAGO.filter((f) => f.id !== 6).map((f) => (
          <option key={f.id} value={f.id}>
            {f.nombre}
          </option>
        ))}
      </Select>
      <Input label="Fecha" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
      {intento && invalido && <p className="text-xs text-red-600 sm:col-span-2">Completa concepto y monto.</p>}
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" disabled={guardando}>
          {guardando ? 'Guardando…' : inicial ? 'Guardar cambios' : 'Registrar ingreso'}
        </Button>
      </div>
    </form>
  )
}

export default function GastosPage() {
  useDocumentTitle('Gastos e ingresos')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()
  const location = useLocation()
  const tabInicial: 'gastos' | 'ingresos' = location.pathname.endsWith('/ingresos') ? 'ingresos' : 'gastos'

  const [tab, setTab] = useState<'gastos' | 'ingresos'>(tabInicial)
  const [page, setPage] = useState(0)
  const [dialogo, setDialogo] = useState<'gasto' | 'ingreso' | null>(null)
  const [editandoGasto, setEditandoGasto] = useState<Gasto | null>(null)
  const [editandoIngreso, setEditandoIngreso] = useState<IngresoOtro | null>(null)
  const [borrandoGasto, setBorrandoGasto] = useState<Gasto | null>(null)
  const [borrandoIngreso, setBorrandoIngreso] = useState<IngresoOtro | null>(null)
  const esAdmin = useTieneRol(['ADMINISTRADOR'])

  const gastos = useQuery({ queryKey: ['gastos', page], queryFn: () => apiGastos(page) })
  const ingresos = useQuery({ queryKey: ['ingresos-otros', page], queryFn: () => apiIngresosOtros(page) })

  useEffect(() => {
    if (gastos.error) mostrarError(esApiError(gastos.error) ? gastos.error.mensajeParaUsuario() : String(gastos.error))
  }, [gastos.error, mostrarError])
  useEffect(() => {
    if (ingresos.error) mostrarError(esApiError(ingresos.error) ? ingresos.error.mensajeParaUsuario() : String(ingresos.error))
  }, [ingresos.error, mostrarError])

  const crearGasto = useMutation({
    mutationFn: (body: GastoRequest) => apiCrearGasto(body),
    onSuccess: () => {
      mostrarExito('Gasto registrado.')
      setDialogo(null)
      queryClient.invalidateQueries({ queryKey: ['gastos'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const crearIngreso = useMutation({
    mutationFn: (body: IngresoOtroRequest) => apiCrearIngreso(body),
    onSuccess: () => {
      mostrarExito('Ingreso registrado.')
      setDialogo(null)
      queryClient.invalidateQueries({ queryKey: ['ingresos-otros'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const actualizarGasto = useMutation({
    mutationFn: ({ id, body }: { id: number; body: GastoRequest }) => apiActualizarGasto(id, body),
    onSuccess: () => {
      mostrarExito('Gasto actualizado.')
      setEditandoGasto(null)
      queryClient.invalidateQueries({ queryKey: ['gastos'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const eliminarGasto = useMutation({
    mutationFn: (id: number) => apiEliminarGasto(id),
    onSuccess: () => {
      mostrarExito('Gasto eliminado.')
      setBorrandoGasto(null)
      queryClient.invalidateQueries({ queryKey: ['gastos'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const actualizarIngreso = useMutation({
    mutationFn: ({ id, body }: { id: number; body: IngresoOtroRequest }) => apiActualizarIngreso(id, body),
    onSuccess: () => {
      mostrarExito('Ingreso actualizado.')
      setEditandoIngreso(null)
      queryClient.invalidateQueries({ queryKey: ['ingresos-otros'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const eliminarIngreso = useMutation({
    mutationFn: (id: number) => apiEliminarIngreso(id),
    onSuccess: () => {
      mostrarExito('Ingreso eliminado.')
      setBorrandoIngreso(null)
      queryClient.invalidateQueries({ queryKey: ['ingresos-otros'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const accionesGasto = (v: Gasto) => {
    if (!esAdmin) return null
    const congelado = v.turnoCajaId != null
    return (
      <div className="flex justify-end gap-1">
        <Button
          variant="ghost"
          size="sm"
          disabled={congelado}
          title={congelado ? 'Ligado a un turno de caja: no modificable' : 'Editar'}
          onClick={() => setEditandoGasto(v)}
        >
          <Edit className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          disabled={congelado}
          title={congelado ? 'Ligado a un turno de caja: no eliminable' : 'Eliminar'}
          onClick={() => setBorrandoGasto(v)}
        >
          <Trash2 className="h-4 w-4 text-red-600" />
        </Button>
      </div>
    )
  }

  const accionesIngreso = (v: IngresoOtro) => {
    if (!esAdmin) return null
    const congelado = v.turnoCajaId != null
    return (
      <div className="flex justify-end gap-1">
        <Button
          variant="ghost"
          size="sm"
          disabled={congelado}
          title={congelado ? 'Ligado a un turno de caja: no modificable' : 'Editar'}
          onClick={() => setEditandoIngreso(v)}
        >
          <Edit className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          disabled={congelado}
          title={congelado ? 'Ligado a un turno de caja: no eliminable' : 'Eliminar'}
          onClick={() => setBorrandoIngreso(v)}
        >
          <Trash2 className="h-4 w-4 text-red-600" />
        </Button>
      </div>
    )
  }

  const colGastos: Columna<Gasto>[] = [
    { key: 't', header: 'Tipo', render: (v) => <span className="font-medium text-ink">{tipoGastoDe(v.tipoGastoId)}</span> },
    { key: 'd', header: 'Descripción', render: (v) => <span className="max-w-[24rem] truncate">{v.descripcion}</span> },
    { key: 'p', header: 'Proveedor', render: (v) => (v.proveedorId ? `#${v.proveedorId}` : '—') },
    { key: 'f', header: 'Forma', render: (v) => formaDe(v.formaPagoId) },
    { key: 'fc', header: 'Fecha', render: (v) => <span className="tabular-nums">{formatoFecha(v.fechaGasto)}</span> },
    { key: 'c', header: 'Registrado', render: (v) => <span className="text-xs tabular-nums text-muted">{formatoFechaHora(v.creadoEn)}</span> },
    { key: 'm', header: 'Monto', align: 'right', render: (v) => <span className="font-medium tabular-nums text-red-700">−{formatoMoneda(v.monto)}</span> },
  ]
  if (esAdmin) colGastos.push({ key: 'acc', header: '', align: 'right', render: accionesGasto })

  const colIngresos: Columna<IngresoOtro>[] = [
    { key: 'c', header: 'Concepto', render: (v) => <span className="font-medium text-ink">{v.concepto}</span> },
    { key: 'f', header: 'Forma', render: (v) => formaDe(v.formaPagoId) },
    { key: 'fc', header: 'Fecha', render: (v) => <span className="tabular-nums">{formatoFecha(v.fecha)}</span> },
    { key: 'c2', header: 'Registrado', render: (v) => <span className="text-xs tabular-nums text-muted">{formatoFechaHora(v.creadoEn)}</span> },
    { key: 'm', header: 'Monto', align: 'right', render: (v) => <span className="font-medium tabular-nums text-green-700">+{formatoMoneda(v.monto)}</span> },
  ]
  if (esAdmin) colIngresos.push({ key: 'acc', align: 'right', header: '', render: accionesIngreso })

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">
            {tab === 'gastos' ? 'Gastos de caja' : 'Ingresos de caja'}
          </h1>
          <p className="text-sm text-muted">
            {tab === 'gastos' ? 'Egresos registrados en caja.' : 'Entradas no provenientes de venta (cobros especiales, ventas de activo, etc.).'}
          </p>
        </div>
        <Button onClick={() => setDialogo(tab === 'gastos' ? 'gasto' : 'ingreso')}>
          {tab === 'gastos' ? <ReceiptText className="h-4 w-4" /> : <ArrowDownToLine className="h-4 w-4" />} {tab === 'gastos' ? 'Registrar gasto' : 'Registrar ingreso'}
        </Button>
      </header>

      <div className="flex gap-2">
        {(['gastos', 'ingresos'] as const).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => { setTab(t); setPage(0) }}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${tab === t ? 'bg-primary text-white' : 'border border-line bg-surface text-ink hover:bg-warmbg'}`}
          >
            {t === 'gastos' ? 'Gastos' : 'Ingresos'}
          </button>
        ))}
      </div>

      {tab === 'gastos' && (
        <>
          {gastos.isLoading ? (
            <Spinner />
          ) : gastos.data && (
            <Card titulo={`Gastos (${gastos.data.meta.totalElements})`}>
              <DataTable columnas={colGastos} items={gastos.data.data} rowKey={(v) => v.gastoId} loading={gastos.isFetching} />
              <Pagination meta={gastos.data.meta} onPage={setPage} />
            </Card>
          )}
        </>
      )}

      {tab === 'ingresos' && (
        <>
          {ingresos.isLoading ? (
            <Spinner />
          ) : ingresos.data && (
            <Card titulo={`Ingresos (${ingresos.data.meta.totalElements})`}>
              <DataTable columnas={colIngresos} items={ingresos.data.data} rowKey={(v) => v.ingresoOtroId} loading={ingresos.isFetching} />
              <Pagination meta={ingresos.data.meta} onPage={setPage} />
            </Card>
          )}
        </>
      )}

      <Dialog open={dialogo === 'gasto'} onClose={() => !crearGasto.isPending && setDialogo(null)} title="Registrar gasto" width="max-w-lg">
        <GastoForm guardando={crearGasto.isPending} onGuardar={(body) => crearGasto.mutate(body)} onClose={() => setDialogo(null)} />
      </Dialog>

      <Dialog open={dialogo === 'ingreso'} onClose={() => !crearIngreso.isPending && setDialogo(null)} title="Registrar ingreso" width="max-w-lg">
        <IngresoForm guardando={crearIngreso.isPending} onGuardar={(body) => crearIngreso.mutate(body)} onClose={() => setDialogo(null)} />
      </Dialog>

      <Dialog open={editandoGasto != null} onClose={() => !actualizarGasto.isPending && setEditandoGasto(null)} title="Editar gasto" width="max-w-lg">
        {editandoGasto && (
          <GastoForm
            key={editandoGasto.gastoId}
            inicial={editandoGasto}
            guardando={actualizarGasto.isPending}
            onGuardar={(body) => actualizarGasto.mutate({ id: editandoGasto.gastoId, body })}
            onClose={() => setEditandoGasto(null)}
          />
        )}
      </Dialog>

      <Dialog open={editandoIngreso != null} onClose={() => !actualizarIngreso.isPending && setEditandoIngreso(null)} title="Editar ingreso" width="max-w-lg">
        {editandoIngreso && (
          <IngresoForm
            key={editandoIngreso.ingresoOtroId}
            inicial={editandoIngreso}
            guardando={actualizarIngreso.isPending}
            onGuardar={(body) => actualizarIngreso.mutate({ id: editandoIngreso.ingresoOtroId, body })}
            onClose={() => setEditandoIngreso(null)}
          />
        )}
      </Dialog>

      <ConfirmDialog
        open={borrandoGasto != null}
        title="Eliminar gasto"
        confirmLabel="Eliminar"
        busy={eliminarGasto.isPending}
        onCancel={() => setBorrandoGasto(null)}
        onConfirm={() => borrandoGasto && eliminarGasto.mutate(borrandoGasto.gastoId)}
      >
        {borrandoGasto && (
          <p>
            Se eliminará el gasto {borrandoGasto.folio} (<span className="font-medium">−{formatoMoneda(borrandoGasto.monto)}</span>).
            Esta acción no se puede deshacer. Solo es posible mientras el gasto no esté ligado a un turno de caja.
          </p>
        )}
      </ConfirmDialog>

      <ConfirmDialog
        open={borrandoIngreso != null}
        title="Eliminar ingreso"
        confirmLabel="Eliminar"
        busy={eliminarIngreso.isPending}
        onCancel={() => setBorrandoIngreso(null)}
        onConfirm={() => borrandoIngreso && eliminarIngreso.mutate(borrandoIngreso.ingresoOtroId)}
      >
        {borrandoIngreso && (
          <p>
            Se eliminará el ingreso <span className="font-medium">“{borrandoIngreso.concepto}”</span>{' '}
            (<span className="font-medium">+{formatoMoneda(borrandoIngreso.monto)}</span>). Esta acción no se puede deshacer.
            Solo es posible mientras el ingreso no esté ligado a un turno de caja.
          </p>
        )}
      </ConfirmDialog>
    </div>
  )
}