import http from './client'
import type { Empleado, EmpleadoCreateRequest, Envelope, Nomina, NominaRequest, OperacionOk, PageEnvelope, Permiso, Rol, RolRequest, Usuario, UsuarioCreateRequest } from './types'

export async function apiEmpleados(page: number, size = 15): Promise<PageEnvelope<Empleado>> {
  const { data } = await http.get<PageEnvelope<Empleado>>('/empleados', { params: { page, size } })
  return data
}

export async function apiCrearEmpleado(body: EmpleadoCreateRequest): Promise<Empleado> {
  const { data } = await http.post<Envelope<Empleado>>('/empleados', body)
  return data.data
}

export async function apiBajaEmpleado(id: number): Promise<void> {
  await http.delete(`/empleados/${id}`)
}

export async function apiUsuarios(page: number, size = 15): Promise<PageEnvelope<Usuario>> {
  const { data } = await http.get<PageEnvelope<Usuario>>('/usuarios', { params: { page, size } })
  return data
}

export async function apiCrearUsuario(body: UsuarioCreateRequest): Promise<Usuario> {
  const { data } = await http.post<Envelope<Usuario>>('/usuarios', body)
  return data.data
}

export async function apiSetRolesUsuario(id: number, roles: string[]): Promise<Usuario> {
  const { data } = await http.put<Envelope<Usuario>>(`/usuarios/${id}/roles`, { roles })
  return data.data
}

export async function apiResetPassword(id: number, nuevaPassword: string): Promise<Usuario> {
  const { data } = await http.patch<Envelope<Usuario>>(`/usuarios/${id}/password`, { nuevaPassword })
  return data.data
}

export async function apiEliminarUsuario(id: number): Promise<OperacionOk> {
  const { data } = await http.delete<Envelope<OperacionOk>>(`/usuarios/${id}`)
  return data.data
}

export async function apiRoles(): Promise<Rol[]> {
  const { data } = await http.get<PageEnvelope<Rol>>('/roles', { params: { page: 0, size: 100 } })
  return data.data
}

export async function apiRolesPaginado(p: { page: number; size: number }): Promise<PageEnvelope<Rol>> {
  const { data } = await http.get<PageEnvelope<Rol>>('/roles', { params: { page: p.page, size: p.size } })
  return data
}

export async function apiCrearRol(body: RolRequest): Promise<Rol> {
  const { data } = await http.post<Envelope<Rol>>('/roles', body)
  return data.data
}

export async function apiActualizarRol(id: number, body: RolRequest): Promise<Rol> {
  const { data } = await http.patch<Envelope<Rol>>(`/roles/${id}`, body)
  return data.data
}

export async function apiEliminarRol(id: number): Promise<void> {
  await http.delete(`/roles/${id}`)
}

export async function apiPermisos(): Promise<Permiso[]> {
  const { data } = await http.get<PageEnvelope<Permiso>>('/permisos', { params: { page: 0, size: 300 } })
  return data.data
}

export async function apiPermisosDeRol(id: number): Promise<string[]> {
  const { data } = await http.get<Envelope<string[]>>(`/roles/${id}/permisos`)
  return data.data
}

export async function apiSetPermisosRol(id: number, permisos: string[]): Promise<string[]> {
  const { data } = await http.put<Envelope<string[]>>(`/roles/${id}/permisos`, { permisos })
  return data.data
}

export async function apiNomina(p: { estado?: string; desde?: string; hasta?: string; page: number; size: number }): Promise<PageEnvelope<Nomina>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.estado) params.estado = p.estado
  if (p.desde) params.desde = p.desde
  if (p.hasta) params.hasta = p.hasta
  const { data } = await http.get<PageEnvelope<Nomina>>('/nomina', { params })
  return data
}

export async function apiCrearNomina(body: NominaRequest): Promise<Nomina> {
  const { data } = await http.post<Envelope<Nomina>>('/nomina', body)
  return data.data
}

export async function apiPagarNomina(id: number): Promise<Nomina> {
  const { data } = await http.post<Envelope<Nomina>>(`/nomina/${id}/pagar`)
  return data.data
}

export async function apiCancelarNomina(id: number): Promise<Nomina> {
  const { data } = await http.post<Envelope<Nomina>>(`/nomina/${id}/cancelar`)
  return data.data
}