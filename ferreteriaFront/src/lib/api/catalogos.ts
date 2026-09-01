import http from './client'
import type { CatalogoDescriptor, Envelope, FilaCatalogo, PageEnvelope } from './types'
import {
  catalogoEndpoint, clavePayloadADTO, dtoFilaAClave,
} from '@/features/catalogo/catalogoEndpointMap'

export async function apiCatalogosPaneles(): Promise<CatalogoDescriptor[]> {
  const { data } = await http.get<Envelope<CatalogoDescriptor[]>>('/catalogos')
  return data.data
}

export async function apiCatalogoDatos(p: {
  clave: string
  q?: string
  page: number
  size: number
  sort?: string
}): Promise<PageEnvelope<FilaCatalogo>> {
  const ep = catalogoEndpoint(p.clave)
  if (!ep) return { success: true, data: [], meta: { totalElements: 0, totalPages: 0, page: 0, size: p.size } }

  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.sort) params.sort = p.sort

  const { data } = await http.get<PageEnvelope<Record<string, unknown>>>(ep.path, { params })
  return {
    ...data,
    data: (data.data ?? []).map((fila) => dtoFilaAClave(p.clave, fila)),
  }
}

export async function apiCatalogoOpciones(clave: string, campo: string): Promise<Record<string, unknown>[]> {
  const { data } = await http.get<Envelope<Record<string, unknown>[]>>(`/catalogos/${clave}/opciones`, {
    params: { campo, size: 500 },
  })
  return data.data
}

export async function apiCatalogoCrear(clave: string, cuerpo: Record<string, unknown>): Promise<void> {
  const ep = catalogoEndpoint(clave)
  if (!ep) return
  await http.post(ep.path, clavePayloadADTO(clave, cuerpo))
}

export async function apiCatalogoActualizar(clave: string, id: string | number, cuerpo: Record<string, unknown>): Promise<void> {
  const ep = catalogoEndpoint(clave)
  if (!ep) return
  await http.put(`${ep.path}/${id}`, clavePayloadADTO(clave, cuerpo))
}

export async function apiCatalogoEliminar(clave: string, id: string | number): Promise<void> {
  const ep = catalogoEndpoint(clave)
  if (!ep) return
  await http.delete(`${ep.path}/${id}`)
}
