import http from './client'
import type { CatalogoDescriptor, Envelope, FilaCatalogo, PageEnvelope } from './types'

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
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.q) params.q = p.q
  if (p.sort) params.sort = p.sort
  const { data } = await http.get<PageEnvelope<FilaCatalogo>>(`/catalogos/${p.clave}/datos`, { params })
  return data
}

export async function apiCatalogoOpciones(clave: string, campo: string): Promise<Record<string, unknown>[]> {
  const { data } = await http.get<Envelope<Record<string, unknown>[]>>(`/catalogos/${clave}/opciones`, {
    params: { campo, size: 500 },
  })
  return data.data
}

export async function apiCatalogoCrear(clave: string, cuerpo: Record<string, unknown>): Promise<void> {
  await http.post(`/catalogos/${clave}`, cuerpo)
}

export async function apiCatalogoActualizar(clave: string, id: string | number, cuerpo: Record<string, unknown>): Promise<void> {
  await http.put(`/catalogos/${clave}/${id}`, cuerpo)
}

export async function apiCatalogoEliminar(clave: string, id: string | number): Promise<void> {
  await http.delete(`/catalogos/${clave}/${id}`)
}
