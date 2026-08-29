import http from './client'
import type { Cotizacion, CotizacionRequest, CuentaCobrar, Devolucion, DevolucionRequest, Envelope, PageEnvelope, PagoClienteRequest, Renta, RentaDevolucionRequest, RentaRequest, Venta, VentaCancelRequest, VentaRequest } from './types'

export async function apiVentas(p: {
  almacenId?: number
  desde?: string
  hasta?: string
  page: number
  size: number
}): Promise<PageEnvelope<Venta>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.almacenId) params.almacenId = p.almacenId
  if (p.desde) params.desde = p.desde
  if (p.hasta) params.hasta = p.hasta
  const { data } = await http.get<PageEnvelope<Venta>>('/ventas', { params })
  return data
}

export async function apiCheckout(body: VentaRequest): Promise<Venta> {
  const { data } = await http.post<Envelope<Venta>>('/ventas', body)
  return data.data
}

export async function apiCancelarVenta(id: number, body: VentaCancelRequest): Promise<Venta> {
  const { data } = await http.patch<Envelope<Venta>>(`/ventas/${id}/cancelar`, body)
  return data.data
}

export async function apiCuentasCobrar(p: {
  estado?: string
  clienteId?: number
  page: number
  size: number
}): Promise<PageEnvelope<CuentaCobrar>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.clienteId) params.clienteId = p.clienteId
  const path = p.clienteId ? `/creditos/${p.clienteId}` : '/creditos/cobranza'
  if (p.estado) params.estado = p.estado
  const { data } = await http.get<PageEnvelope<CuentaCobrar>>(path, { params })
  return data
}

export async function apiPagoCliente(body: PagoClienteRequest): Promise<void> {
  await http.post<Envelope<object>>('/pagos-cliente', body)
}

export async function apiCotizaciones(p: { estado?: string; page: number; size: number }): Promise<PageEnvelope<Cotizacion>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.estado) params.estado = p.estado
  const { data } = await http.get<PageEnvelope<Cotizacion>>('/cotizaciones', { params })
  return data
}

export async function apiCrearCotizacion(body: CotizacionRequest): Promise<Cotizacion> {
  const { data } = await http.post<Envelope<Cotizacion>>('/cotizaciones', body)
  return data.data
}

export async function apiConvertirCotizacion(id: number, almacenId: number, formaPagoId: number, cajaId?: number): Promise<Cotizacion> {
  const { data } = await http.post<Envelope<Cotizacion>>(`/cotizaciones/${id}/convertir`, null, { params: { almacenId, formaPagoId, cajaId } })
  return data.data
}

export async function apiDevolucionesDeVenta(ventaId: number): Promise<Devolucion[]> {
  const { data } = await http.get<Envelope<Devolucion[]>>(`/devoluciones/venta/${ventaId}`)
  return data.data
}

export async function apiCrearDevolucion(body: DevolucionRequest): Promise<Devolucion> {
  const { data } = await http.post<Envelope<Devolucion>>('/devoluciones', body)
  return data.data
}

export async function apiRentas(p: { estado?: string; page: number; size: number }): Promise<PageEnvelope<Renta>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.estado) params.estado = p.estado
  const { data } = await http.get<PageEnvelope<Renta>>('/rentas', { params })
  return data
}

export async function apiCrearRenta(body: RentaRequest): Promise<Renta> {
  const { data } = await http.post<Envelope<Renta>>('/rentas', body)
  return data.data
}

export async function apiDevolucionRenta(id: number, body: RentaDevolucionRequest): Promise<Renta> {
  const { data } = await http.post<Envelope<Renta>>(`/rentas/${id}/devolucion`, body)
  return data.data
}