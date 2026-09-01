import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiActualizarAlmacen, apiActualizarEstadoAlmacen, apiAlmacenesTodos, apiCrearAlmacen } from '@/lib/api/catalogo'
import type { Almacen, AlmacenRequest } from '@/lib/api/types'
import { useTieneRol } from '@/store/auth'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input } from '@/components/ui/Input'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

function AlmacenForm({
  guardando,
  registro,
  onGuardar,
  onClose,
}: {
  guardando: boolean
  registro: Almacen | null
  onGuardar: (body: AlmacenRequest) => void
  onClose: () => void
}) {
  const [nombre, setNombre] = useState(registro?.nombre ?? '')
  const [direccion, setDireccion] = useState(registro?.direccion ?? '')
  const [telefono, setTelefono] = useState(registro?.telefono ?? '')
  const [esPuntoVenta, setEsPuntoVenta] = useState(registro?.esPuntoVenta ?? true)
  const [intento, setIntento] = useState(false)

  const invalido = nombre.trim() === ''

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({
      nombre: nombre.trim(),
      direccion: direccion.trim() || null,
      telefono: telefono.trim() || null,
      esPuntoVenta,
    })
  }

  return (
    <form onSubmit={enviar} className="grid grid-cols-1 gap-3" noValidate>
      <Input label="Nombre del almacén" required value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Ej. Almacén Central" />
      <Input label="Dirección (opcional)" value={direccion} onChange={(e) => setDireccion(e.target.value)} />
      <Input label="Teléfono (opcional)" value={telefono} onChange={(e) => setTelefono(e.target.value)} />
      <label className="flex items-center gap-2 text-sm text-ink">
        <input
          type="checkbox"
          checked={esPuntoVenta}
          onChange={(e) => setEsPuntoVenta(e.target.checked)}
          className="h-4 w-4 accent-orange-600"
        />
        Es punto de venta
      </label>
      {intento && invalido && <p className="text-sm text-red-600">El nombre es obligatorio.</p>}
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

export default function AlmacenesAdminPage() {
  useDocumentTitle('Administrar almacenes')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()
  const puedeAdministrar = useTieneRol(['ADMINISTRADOR'])

  const [dialogoAbierto, setDialogoAbierto] = useState(false)
  const [editing, setEditing] = useState<Almacen | null>(null)
  const [aDesactivar, setADesactivar] = useState<Almacen | null>(null)
  const [aReactivar, setAReactivar] = useState<Almacen | null>(null)

  const almacenes = useQuery({ queryKey: ['almacenes-admin'], queryFn: apiAlmacenesTodos })

  const invalidar = () => {
    queryClient.invalidateQueries({ queryKey: ['almacenes-admin'] })
    queryClient.invalidateQueries({ queryKey: ['almacenes'] })
  }

  const crear = useMutation({
    mutationFn: (body: AlmacenRequest) => apiCrearAlmacen(body),
    onSuccess: () => {
      mostrarExito('Almacén creado.')
      setDialogoAbierto(false)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const editar = useMutation({
    mutationFn: (body: AlmacenRequest) => apiActualizarAlmacen(editing!.almacenId, body),
    onSuccess: () => {
      mostrarExito('Almacén actualizado.')
      setDialogoAbierto(false)
      setEditing(null)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const desactivar = useMutation({
    mutationFn: (id: number) => apiActualizarEstadoAlmacen(id, false),
    onSuccess: () => {
      mostrarExito('Almacén dado de baja.')
      setADesactivar(null)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const reactivar = useMutation({
    mutationFn: (id: number) => apiActualizarEstadoAlmacen(id, true),
    onSuccess: () => {
      mostrarExito('Almacén reactivado.')
      setAReactivar(null)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const ver = almacenes.data ?? []

  const toggleActivo = (a: Almacen) => {
    if (a.activo) {
      setADesactivar(a)
    } else {
      reactivar.mutate(a.almacenId)
    }
  }

  const columnas: Columna<Almacen>[] = [
    { key: 'nombre', header: 'Almacén', render: (a) => <span className="font-medium text-ink">{a.nombre}</span> },
    { key: 'direccion', header: 'Dirección', render: (a) => a.direccion ?? '—' },
    { key: 'telefono', header: 'Teléfono', render: (a) => a.telefono ?? '—' },
    {
      key: 'esPuntoVenta',
      header: 'Punto de venta',
      render: (a) => (a.esPuntoVenta ? 'Sí' : 'No'),
    },
    {
      key: 'activo',
      header: 'Activo',
      render: (a) => (
        <input
          type="checkbox"
          checked={a.activo}
          onChange={() => toggleActivo(a)}
          aria-label={`Almacén ${a.nombre} activo`}
          className="h-4 w-4 accent-orange-600"
        />
      ),
    },
    {
      key: '__acciones',
      header: 'Acciones',
      render: (a) => (
        <div className="flex gap-1">
          <Button variant="ghost" size="sm" aria-label="Editar" onClick={() => { setEditing(a); setDialogoAbierto(true) }}>
            <Pencil className="h-4 w-4" />
          </Button>
        </div>
      ),
    },
  ]

  if (!puedeAdministrar) {
    return <div className="p-6 text-muted">No tienes permisos para administrar almacenes.</div>
  }

  return (
    <div className="space-y-4 p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Administrar almacenes</h1>
          <p className="text-sm text-muted">Da de alta, modifica o da de baja los almacenes de la empresa.</p>
        </div>
        <Button onClick={() => { setEditing(null); setDialogoAbierto(true) }} variant="primary">
          <Plus className="h-4 w-4" /> Nuevo almacén
        </Button>
      </header>

      <Card>
        {almacenes.isLoading ? (
          <Spinner />
        ) : (
          <DataTable
            columnas={columnas}
            items={ver}
            rowKey={(a) => a.almacenId}
            emptyTitle="Sin almacenes"
            emptyDescripcion="No hay almacenes registrados."
          />
        )}
      </Card>

      <Dialog
        open={dialogoAbierto}
        onClose={() => setDialogoAbierto(false)}
        title={editing ? 'Editar almacén' : 'Nuevo almacén'}
        width="max-w-md"
      >
        <AlmacenForm
          guardando={crear.isPending || editar.isPending}
          registro={editing}
          onGuardar={(body) => (editing ? editar.mutate(body) : crear.mutate(body))}
          onClose={() => setDialogoAbierto(false)}
        />
      </Dialog>

      <ConfirmDialog
        open={Boolean(aDesactivar)}
        title="Dar de baja el almacén"
        confirmLabel="Dar de baja"
        busy={desactivar.isPending}
        onCancel={() => setADesactivar(null)}
        onConfirm={() => aDesactivar && desactivar.mutate(aDesactivar.almacenId)}
      >
        ¿Seguro que quieres dar de baja el almacén <strong>{aDesactivar?.nombre}</strong>? Dejará de aparecer en los selectores de almacén.
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(aReactivar)}
        title="Reactivar el almacén"
        confirmLabel="Reactivar"
        busy={reactivar.isPending}
        onCancel={() => setAReactivar(null)}
        onConfirm={() => aReactivar && reactivar.mutate(aReactivar.almacenId)}
      >
        ¿Seguro que quieres reactivar el almacén <strong>{aReactivar?.nombre}</strong>?
      </ConfirmDialog>
    </div>
  )
}