import http from './client'
import type { Compra, CompraRequest, CuentasPagar, Envelope, FacturaPendiente, FacturaVencida, PageEnvelope } from './types'

export async function apiCompras(p: {
  almacenId?: number
  proveedorId?: number
  desde?: string
  hasta?: string
  page: number
  size: number
}): Promise<PageEnvelope<Compra>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.almacenId) params.almacenId = p.almacenId
  if (p.proveedorId) params.proveedorId = p.proveedorId
  if (p.desde) params.desde = p.desde
  if (p.hasta) params.hasta = p.hasta
  const { data } = await http.get<PageEnvelope<Compra>>('/compras', { params })
  return data
}

export async function apiCrearCompra(body: CompraRequest): Promise<Compra> {
  const { data } = await http.post<Envelope<Compra>>('/compras', body)
  return data.data
}

export async function apiCuentasPagar(): Promise<CuentasPagar[]> {
  const { data } = await http.get<Envelope<CuentasPagar[]>>('/cuentas-pagar')
  return data.data
}

export async function apiFacturasPendientes(): Promise<FacturaPendiente[]> {
  const { data } = await http.get<Envelope<FacturaPendiente[]>>('/reportes/facturas-pendientes')
  return data.data
}

export async function apiFacturasVencidas(): Promise<FacturaVencida[]> {
  const { data } = await http.get<Envelope<FacturaVencida[]>>('/reportes/facturas-vencidas')
  return data.data
}