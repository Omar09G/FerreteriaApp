import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Trash2, UserPlus } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiBajaEmpleado, apiCrearEmpleado, apiEmpleados, apiRoles } from '@/lib/api/admin'
import type { Empleado, EmpleadoCreateRequest } from '@/lib/api/types'
import { PUESTOS } from '@/lib/api/types'
import { formatoFecha, formatoMoneda } from '@/lib/format'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

function EmpleadoForm({
  guardando,
  onGuardar,
  onClose,
}: {
  guardando: boolean
  onGuardar: (payload: EmpleadoCreateRequest) => void
  onClose: () => void
}) {
  const roles = useQuery({ queryKey: ['roles'], queryFn: apiRoles })
  const [puestoId, setPuestoId] = useState<number | ''>('')
  const [nombre, setNombre] = useState('')
  const [apellidoPaterno, setApellidoPaterno] = useState('')
  const [apellidoMaterno, setApellidoMaterno] = useState('')
  const [curp, setCurp] = useState('')
  const [nss, setNss] = useState('')
  const [telefono, setTelefono] = useState('')
  const [email, setEmail] = useState('')
  const [sueldo, setSueldo] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [rolesSel, setRolesSel] = useState<string[]>([])
  const [intento, setIntento] = useState(false)

  const invalido = puestoId === '' || nombre.trim() === '' || apellidoPaterno.trim() === '' || (username.trim() !== '' && password.length < 8)

  const toggleRol = (clave: string) =>
    setRolesSel((prev) => (prev.includes(clave) ? prev.filter((r) => r !== clave) : [...prev, clave]))

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({
      puestoId: Number(puestoId),
      nombre: nombre.trim(),
      apellidoPaterno: apellidoPaterno.trim(),
      apellidoMaterno: apellidoMaterno.trim() || undefined,
      curp: curp.trim().toUpperCase() || undefined,
      nss: nss.trim() || undefined,
      telefono: telefono.trim() || undefined,
      email: email.trim() || undefined,
      sueldoDiario: sueldo.trim() ? Number(sueldo) : undefined,
      ...(username.trim() ? { username: username.trim(), password, roles: rolesSel } : {}),
    })
  }

  return (
    <form onSubmit={enviar} className="grid grid-cols-1 gap-3 sm:grid-cols-2" noValidate>
      <Select label="Puesto" required value={puestoId} onChange={(e) => setPuestoId(e.target.value ? Number(e.target.value) : '')}>
        <option value="">Selecciona…</option>
        {PUESTOS.map((p) => (
          <option key={p.id} value={p.id}>
            {p.nombre}
          </option>
        ))}
      </Select>
      <Input label="Sueldo diario" type="number" inputMode="decimal" step="0.01" min="0" value={sueldo} onChange={(e) => setSueldo(e.target.value)} />
      <Input label="Nombre" required value={nombre} onChange={(e) => setNombre(e.target.value)} />
      <Input label="Apellido paterno" required value={apellidoPaterno} onChange={(e) => setApellidoPaterno(e.target.value)} />
      <Input label="Apellido materno" value={apellidoMaterno} onChange={(e) => setApellidoMaterno(e.target.value)} />
      <Input label="CURP" value={curp} onChange={(e) => setCurp(e.target.value)} className="uppercase" />
      <Input label="NSS" value={nss} onChange={(e) => setNss(e.target.value)} />
      <Input label="Teléfono" value={telefono} onChange={(e) => setTelefono(e.target.value)} />
      <Input label="Correo" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />

      <div className="border-t border-line pt-3 sm:col-span-2">
        <p className="mb-1 text-sm font-semibold text-ink">Usuario del sistema (opcional)</p>
        <p className="mb-2 text-xs text-muted">Si capturas usuario y contraseña, se crea también el acceso al sistema.</p>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Usuario" value={username} onChange={(e) => setUsername(e.target.value)} />
          <Input label="Contraseña (mín. 8)" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <div className="mt-2">
          <span className="text-xs font-medium text-muted">Roles</span>
          <div className="mt-1 flex flex-wrap gap-2">
            {roles.data?.map((r) => (
              <label key={r.clave} className="flex items-center gap-1.5 rounded-md border border-line px-2 py-1 text-xs">
                <input type="checkbox" checked={rolesSel.includes(r.clave)} onChange={() => toggleRol(r.clave)} className="accent-primary" />
                {r.nombre}
              </label>
            ))}
          </div>
        </div>
      </div>

      {intento && invalido && <p className="text-xs text-red-600 sm:col-span-2">Completa los campos obligatorios y revisa la contraseña.</p>}
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

export default function EmpleadosPage() {
  useDocumentTitle('Empleados')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()

  const [page, setPage] = useState(0)
  const [dialogoAbierto, setDialogoAbierto] = useState(false)

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['empleados', page],
    queryFn: () => apiEmpleados(page),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const crear = useMutation({
    mutationFn: (body: EmpleadoCreateRequest) => apiCrearEmpleado(body),
    onSuccess: () => {
      mostrarExito('Empleado registrado.')
      setDialogoAbierto(false)
      queryClient.invalidateQueries({ queryKey: ['empleados'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const baja = useMutation({
    mutationFn: (id: number) => apiBajaEmpleado(id),
    onSuccess: () => {
      mostrarExito('Empleado dado de baja.')
      queryClient.invalidateQueries({ queryKey: ['empleados'] })
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const columnas: Columna<Empleado>[] = [
    {
      key: 'n',
      header: 'Empleado',
      render: (v) => (
        <span className="font-medium text-ink">
          {v.apellidoPaterno} {v.apellidoMaterno ?? ''} {v.nombre}
        </span>
      ),
    },
    { key: 'p', header: 'Puesto', render: (v) => v.puestoNombre },
    { key: 'e', header: 'Correo', render: (v) => v.email ?? '—' },
    { key: 't', header: 'Teléfono', render: (v) => v.telefono ?? '—' },
    { key: 'ing', header: 'Ingreso', render: (v) => (v.fechaIngreso ? <span className="tabular-nums">{formatoFecha(v.fechaIngreso)}</span> : '—') },
    { key: 's', header: 'Sueldo', align: 'right', render: (v) => <span className="tabular-nums">{formatoMoneda(v.sueldoDiario)}/d</span> },
    { key: 'est', header: 'Estado', render: (v) => (v.activo ? <Badge tone="success">Activo</Badge> : <Badge tone="danger">Baja</Badge>) },
    {
      key: 'acc',
      header: 'Acciones',
      align: 'right',
      render: (v) =>
        v.activo && (
          <button
            type="button"
            aria-label={`Baja de ${v.nombre}`}
            className="rounded p-1.5 text-muted hover:bg-red-50 hover:text-red-600"
            onClick={() => {
              if (window.confirm(`¿Dar de baja a ${v.nombre} ${v.apellidoPaterno}?`)) baja.mutate(v.empleadoId)
            }}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        ),
    },
  ]

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Empleados</h1>
          <p className="text-sm text-muted">Plantilla, puestos y altas con usuario del sistema. Solo administrador.</p>
        </div>
        <Button onClick={() => setDialogoAbierto(true)}>
          <UserPlus className="h-4 w-4" /> Nuevo empleado
        </Button>
      </header>

      {(isLoading || (isFetching && !data)) && <Spinner />}
      {data && (
        <Card titulo={`Empleados (${data.meta.totalElements})`}>
          <DataTable columnas={columnas} items={data.data} rowKey={(v) => v.empleadoId} loading={isFetching} />
          <Pagination meta={data.meta} onPage={setPage} />
        </Card>
      )}

      <Dialog open={dialogoAbierto} onClose={() => !crear.isPending && setDialogoAbierto(false)} title="Nuevo empleado" width="max-w-2xl">
        <EmpleadoForm guardando={crear.isPending} onGuardar={(body) => crear.mutate(body)} onClose={() => setDialogoAbierto(false)} />
      </Dialog>
    </div>
  )
}