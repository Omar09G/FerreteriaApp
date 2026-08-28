import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, ShieldCheck, Trash2, UserPlus } from 'lucide-react'

import { useDocumentTitle } from '@/hooks/useDocumentTitle'
import { esApiError } from '@/lib/api/client'
import { apiCrearUsuario, apiEliminarUsuario, apiResetPassword, apiRoles, apiSetRolesUsuario, apiUsuarios } from '@/lib/api/admin'
import type { Usuario, UsuarioCreateRequest } from '@/lib/api/types'
import { formatoFechaHora } from '@/lib/format'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { DataTable, type Columna } from '@/components/ui/DataTable'
import { Dialog } from '@/components/ui/Dialog'
import { Input } from '@/components/ui/Input'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/Toast'

function UsuarioForm({
  guardando,
  onGuardar,
  onClose,
}: {
  guardando: boolean
  onGuardar: (payload: UsuarioCreateRequest) => void
  onClose: () => void
}) {
  const roles = useQuery({ queryKey: ['roles'], queryFn: apiRoles })
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [rolesSel, setRolesSel] = useState<string[]>([])
  const [intento, setIntento] = useState(false)

  const invalido = username.trim() === '' || email.trim() === '' || password.length < 8

  const toggleRol = (clave: string) =>
    setRolesSel((prev) => (prev.includes(clave) ? prev.filter((r) => r !== clave) : [...prev, clave]))

  const enviar = (e: { preventDefault: () => void }) => {
    e.preventDefault()
    setIntento(true)
    if (invalido) return
    onGuardar({ username: username.trim(), email: email.trim(), password, roles: rolesSel })
  }

  return (
    <form onSubmit={enviar} className="grid grid-cols-1 gap-3 sm:grid-cols-2" noValidate>
      <Input label="Usuario" required value={username} onChange={(e) => setUsername(e.target.value)} />
      <Input label="Correo" required type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      <div className="sm:col-span-2">
        <Input label="Contraseña (mín. 8)" required type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </div>
      <div className="sm:col-span-2">
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
      {intento && invalido && <p className="text-xs text-red-600 sm:col-span-2">Usuario, correo y contraseña (mín. 8) son obligatorios.</p>}
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

export default function UsuariosPage() {
  useDocumentTitle('Usuarios y roles')
  const { error: mostrarError, success: mostrarExito } = useToast()
  const queryClient = useQueryClient()

  const [page, setPage] = useState(0)
  const [dialogoAbierto, setDialogoAbierto] = useState(false)
  const [editandoRoles, setEditandoRoles] = useState<Usuario | null>(null)
  const [rolesSel, setRolesSel] = useState<string[]>([])

  const roles = useQuery({ queryKey: ['roles'], queryFn: apiRoles })
  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['usuarios', page],
    queryFn: () => apiUsuarios(page),
  })

  useEffect(() => {
    if (error) mostrarError(esApiError(error) ? error.mensajeParaUsuario() : String(error))
  }, [error, mostrarError])

  const invalidar = () => queryClient.invalidateQueries({ queryKey: ['usuarios'] })

  const crear = useMutation({
    mutationFn: (body: UsuarioCreateRequest) => apiCrearUsuario(body),
    onSuccess: () => {
      mostrarExito('Usuario creado.')
      setDialogoAbierto(false)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const setRoles = useMutation({
    mutationFn: (v: { id: number; roles: string[] }) => apiSetRolesUsuario(v.id, v.roles),
    onSuccess: () => {
      mostrarExito('Roles actualizados.')
      setEditandoRoles(null)
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const reset = useMutation({
    mutationFn: (v: { id: number; password: string }) => apiResetPassword(v.id, v.password),
    onSuccess: () => {
      mostrarExito('Contraseña restablecida.')
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const eliminar = useMutation({
    mutationFn: (id: number) => apiEliminarUsuario(id),
    onSuccess: () => {
      mostrarExito('Usuario eliminado.')
      invalidar()
    },
    onError: (err) => mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
  })

  const columnas: Columna<Usuario>[] = [
    { key: 'u', header: 'Usuario', render: (v) => <span className="font-medium text-ink">{v.username}</span> },
    { key: 'e', header: 'Correo', render: (v) => v.email },
    { key: 'emp', header: 'Empleado', render: (v) => v.empleado?.nombreCompleto ?? '—' },
    {
      key: 'ro',
      header: 'Roles',
      render: (v) => (
        <div className="flex flex-wrap gap-1">
          {v.roles.map((r) => (
            <Badge key={r}>{r}</Badge>
          ))}
        </div>
      ),
    },
    { key: 'act', header: 'Estado', render: (v) => (v.activo ? <Badge tone="success">Activo</Badge> : <Badge tone="danger">Inactivo</Badge>) },
    { key: 'login', header: 'Último login', render: (v) => (v.ultimoLogin ? <span className="tabular-nums">{formatoFechaHora(v.ultimoLogin)}</span> : '—') },
    {
      key: 'acc',
      header: 'Acciones',
      align: 'right',
      render: (v) => (
        <div className="flex justify-end gap-1">
          <button
            type="button"
            aria-label={`Editar roles de ${v.username}`}
            className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
            onClick={() => {
              setEditandoRoles(v)
              setRolesSel(v.roles)
            }}
          >
            <ShieldCheck className="h-4 w-4" />
          </button>
          <button
            type="button"
            aria-label={`Restablecer contraseña de ${v.username}`}
            className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
            onClick={() => {
              const nueva = window.prompt(`Nueva contraseña para ${v.username} (mín. 8 caracteres):`)
              if (nueva && nueva.length >= 8) reset.mutate({ id: v.usuarioId, password: nueva })
              else if (nueva) mostrarError('La contraseña debe tener al menos 8 caracteres.')
            }}
          >
            <KeyRound className="h-4 w-4" />
          </button>
          <button
            type="button"
            aria-label={`Eliminar ${v.username}`}
            className="rounded p-1.5 text-muted hover:bg-red-50 hover:text-red-600"
            onClick={() => {
              if (window.confirm(`¿Eliminar el usuario "${v.username}"?`)) eliminar.mutate(v.usuarioId)
            }}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ink">Usuarios y roles</h1>
          <p className="text-sm text-muted">Altas, roles y contraseñas del sistema. Solo administrador.</p>
        </div>
        <Button onClick={() => setDialogoAbierto(true)}>
          <UserPlus className="h-4 w-4" /> Nuevo usuario
        </Button>
      </header>

      {(isLoading || (isFetching && !data)) && <Spinner />}
      {data && (
        <Card titulo={`Usuarios (${data.meta.totalElements})`}>
          <DataTable columnas={columnas} items={data.data} rowKey={(v) => v.usuarioId} loading={isFetching} />
          <Pagination meta={data.meta} onPage={setPage} />
        </Card>
      )}

      <Dialog open={dialogoAbierto} onClose={() => !crear.isPending && setDialogoAbierto(false)} title="Nuevo usuario" width="max-w-lg">
        <UsuarioForm guardando={crear.isPending} onGuardar={(body) => crear.mutate(body)} onClose={() => setDialogoAbierto(false)} />
      </Dialog>

      <Dialog open={editandoRoles !== null} onClose={() => setEditandoRoles(null)} title={`Roles de ${editandoRoles?.username ?? ''}`} width="max-w-lg">
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            {roles.data?.map((r) => (
              <label key={r.clave} className="flex items-center gap-1.5 rounded-md border border-line px-2 py-1 text-xs">
                <input type="checkbox" checked={rolesSel.includes(r.clave)} onChange={() => setRolesSel((prev) => (prev.includes(r.clave) ? prev.filter((x) => x !== r.clave) : [...prev, r.clave]))} className="accent-primary" />
                {r.nombre}
              </label>
            ))}
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setEditandoRoles(null)}>
              Cancelar
            </Button>
            <Button
              disabled={setRoles.isPending || editandoRoles === null}
              onClick={() => setRoles.mutate({ id: editandoRoles!.usuarioId, roles: rolesSel })}
            >
              <ShieldCheck className="h-4 w-4" /> Guardar roles
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  )
}