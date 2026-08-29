import http from './client'
import type { Auditoria, AuditoriaTabla, Envelope, PageEnvelope } from './types'

export interface AuditoriaFiltros {
  esquema?: string
  tabla?: string
  accion?: string
  usuario?: string
  registroId?: number
  fechaInicio?: string
  fechaFin?: string
  texto?: string
  page: number
  size: number
  sort?: string
}

export async function apiAuditoria(f: AuditoriaFiltros): Promise<PageEnvelope<Auditoria>> {
  const params: Record<string, string | number> = { page: f.page, size: f.size }
  if (f.esquema) params.esquema = f.esquema
  if (f.tabla) params.tabla = f.tabla
  if (f.accion) params.accion = f.accion
  if (f.usuario) params.usuario = f.usuario
  if (f.registroId != null) params.registroId = f.registroId
  if (f.fechaInicio) params.fechaInicio = f.fechaInicio
  if (f.fechaFin) params.fechaFin = f.fechaFin
  if (f.texto) params.texto = f.texto
  if (f.sort) params.sort = f.sort
  const { data } = await http.get<PageEnvelope<Auditoria>>('/auditoria', { params })
  return data
}

export async function apiTablasAuditoria(): Promise<AuditoriaTabla[]> {
  const { data } = await http.get<Envelope<AuditoriaTabla[]>>('/auditoria/tablas')
  return data.data
}