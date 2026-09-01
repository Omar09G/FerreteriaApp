import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiAlmacenes } from '@/lib/api/catalogo'
import { apiCajas, apiActualizarCaja, apiActualizarEstadoCaja, apiCrearCaja } from '@/lib/api/caja'
import type { Almacen, Caja, CajaRequest } from '@/lib/api/types'
import { useTieneRol } from '@/store/auth'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

function CajaForm({
  almacenes,
  guardando,
  registro,
  onGuardar,
  onClose,
}: {
  almacenes: Almacen[]
  guardando: boolean
  registro: Caja | null
  onGuardar: (body: CajaRequest) => void
  onClose: () => void
}) {
  const [nombre, setNombre] = useState(registro?.nombre ?? '')
  const [almacenId, setAlmacenId] = useState<number | ''>(registro?.almacenId ?? '')
  const [activa, setActiva] = useState(registro?.activa ?? true)
  const [intento, setIntento] = useState(false)

  const invalido = nombre.trim() === '' || almacenId === ''

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({ nombre: nombre.trim(), almacenId: Number(almacenId), activa })
  }

  return (
    <form onSubmit={enviar} className="grid grid-cols-1 gap-3" noValidate>
      <Input label="Nombre de la caja" required value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Ej. Caja Central" />
      <Select label="Almacén" required value={almacenId} onChange={(e) => setAlmacenId(e.target.value ? Number(e.target.value) : '')}>
        <option value="">Selecciona…</option>
        {almacenes.map((a) => (
          <option key={a.almacenId} value={a.almacenId}>
            {a.nombre}
          </option>
        ))}
      </Select>
      <label className="flex items-center gap-2 text-sm text-ink">
        <input
          type="checkbox"
          checked={activa}
          onChange={(e) => setActiva(e.target.checked)}
          className="h-4 w-4 accent-orange-600"
        />
        Activa
      </label>
      {intento && invalido && <p className="text-sm text-red-600">Completa los campos obligatorios.</p>}
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="ghost" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" variant="primary" disabled={guardando}>
          {guardando ? 'Guardando…' : 'Guardar'}
        </Button>
      </div>
    </form>
  )
}

export default function CajasAdminPage() {
  useDocumentTitle('Administrar cajas')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()
  const puedeAdministrar = useTieneRol(['ADMINISTRADOR'])

  const [dialogoAbierto, setDialogoAbierto] = useState(false)
  const [editing, setEditing] = useState<Caja | null>(null)
  const [aDesactivar, setADesactivar] = useState<Caja | null>(null)
  const [aReactivar, setAReactivar] = useState<Caja | null>(null)

  const cajas = useQuery({ queryKey: ['cajas-admin'], queryFn: apiCajas })
  const almacenes = useQuery({ queryKey: ['almacenes-caja'], queryFn: apiAlmacenes })

  const invalidar = () => {
    queryClient.invalidateQueries({ queryKey: ['cajas-admin'] })
    queryClient.invalidateQueries({ queryKey: ['cajas'] })
  }

  const crear = useMutation({
    mutationFn: (body: CajaRequest) => apiCrearCaja(body),
    onSuccess: () => {
      mostrarExito('Caja creada.')
      setDialogoAbierto(false)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const editar = useMutation({
    mutationFn: (body: CajaRequest) => apiActualizarCaja(editing!.cajaId, body),
    onSuccess: () => {
      mostrarExito('Caja actualizada.')
      setDialogoAbierto(false)
      setEditing(null)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const desactivar = useMutation({
    mutationFn: (id: number) => apiActualizarEstadoCaja(id, false),
    onSuccess: () => {
      mostrarExito('Caja dada de baja.')
      setADesactivar(null)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const reactivar = useMutation({
    mutationFn: (id: number) => apiActualizarEstadoCaja(id, true),
    onSuccess: () => {
      mostrarExito('Caja reactivada.')
      setAReactivar(null)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const ver = cajas.data ?? []

  const toggleActiva = (c: Caja) => {
    if (c.activa) {
      setADesactivar(c)
    } else {
      reactivar.mutate(c.cajaId)
    }
  }

  const columnas: Columna<Caja>[] = [
    { key: 'nombre', header: 'Caja', render: (c) => <span className="font-medium text-ink">{c.nombre}</span> },
    { key: 'almacen', header: 'Almacén', render: (c) => c.almacenNombre },
    {
      key: 'activa',
      header: 'Activa',
      render: (c) => (
        <input
          type="checkbox"
          checked={c.activa}
          onChange={() => toggleActiva(c)}
          aria-label={`Caja ${c.nombre} activa`}
          className="h-4 w-4 accent-orange-600"
        />
      ),
    },
    {
      key: '__acciones',
      header: 'Acciones',
      render: (c) => (
        <div className="flex gap-1">
          <Button variant="ghost" size="sm" aria-label="Editar" onClick={() => { setEditing(c); setDialogoAbierto(true) }}>
            <Pencil className="h-4 w-4" />
          </Button>
        </div>
      ),
    },
  ]

  if (!puedeAdministrar) {
    return <div className="p-6 text-muted">No tienes permisos para administrar cajas.</div>
  }

  return (
    <div className="space-y-4 p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Administrar cajas</h1>
          <p className="text-sm text-muted">Da de alta, modifica o da de baja las cajas que usan turnos de caja.</p>
        </div>
        <Button onClick={() => { setEditing(null); setDialogoAbierto(true) }} variant="primary">
          <Plus className="h-4 w-4" /> Nueva caja
        </Button>
      </header>

      <Card>
        {cajas.isLoading ? (
          <Spinner />
        ) : (
          <DataTable
            columnas={columnas}
            items={ver}
            rowKey={(c) => c.cajaId}
            emptyTitle="Sin cajas"
            emptyDescripcion="No hay cajas registradas."
          />
        )}
      </Card>

      <Dialog
        open={dialogoAbierto}
        onClose={() => setDialogoAbierto(false)}
        title={editing ? 'Editar caja' : 'Nueva caja'}
        width="max-w-md"
      >
        <CajaForm
          almacenes={almacenes.data ?? []}
          guardando={crear.isPending || editar.isPending}
          registro={editing}
          onGuardar={(body) => (editing ? editar.mutate(body) : crear.mutate(body))}
          onClose={() => setDialogoAbierto(false)}
        />
      </Dialog>

      <ConfirmDialog
        open={Boolean(aDesactivar)}
        title="Dar de baja la caja"
        confirmLabel="Dar de baja"
        busy={desactivar.isPending}
        onCancel={() => setADesactivar(null)}
        onConfirm={() => aDesactivar && desactivar.mutate(aDesactivar.cajaId)}
      >
        ¿Seguro que quieres dar de baja la caja <strong>{aDesactivar?.nombre}</strong>? Dejará de aparecer en el selector de cajas.
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(aReactivar)}
        title="Reactivar la caja"
        confirmLabel="Reactivar"
        busy={reactivar.isPending}
        onCancel={() => setAReactivar(null)}
        onConfirm={() => aReactivar && reactivar.mutate(aReactivar.cajaId)}
      >
        ¿Seguro que quieres reactivar la caja <strong>{aReactivar?.nombre}</strong>?
      </ConfirmDialog>
    </div>
  )
}
