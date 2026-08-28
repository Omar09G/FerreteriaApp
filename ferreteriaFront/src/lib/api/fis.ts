import http from './client'
import type { Envelope, FacturaFis, FacturaFisRequest, FacturaXml, PageEnvelope } from './types'

export async function apiFacturas(p: { tipo?: string; page: number; size: number }): Promise<PageEnvelope<FacturaFis>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.tipo) params.tipo = p.tipo
  const { data } = await http.get<PageEnvelope<FacturaFis>>('/facturas', { params })
  return data
}

export async function apiCrearFactura(body: FacturaFisRequest): Promise<FacturaFis> {
  const { data } = await http.post<Envelope<FacturaFis>>('/facturas', body)
  return data.data
}

export async function apiFacturaXml(id: number): Promise<FacturaXml> {
  const { data } = await http.get<Envelope<FacturaXml>>(`/facturas/${id}/xml`)
  return data.data
}