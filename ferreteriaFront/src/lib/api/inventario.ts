import http from './client'
import type {
  ConteoFisico,
  ConteoFisicoRequest,
  Envelope,
  MovimientoInventario,
  MovimientoInventarioRequest,
  PageEnvelope,
  Traslado,
  TrasladoRequest,
} from './types'

export async function apiTraslados(p: { estado?: string; page: number; size: number }): Promise<PageEnvelope<Traslado>> {
  const params: Record<string, string | number> = { page: p.page, size: p.size }
  if (p.estado) params.estado = p.estado
  const { data } = await http.get<PageEnvelope<Traslado>>('/traslados', { params })
  return data
}

export async function apiCrearTraslado(body: TrasladoRequest): Promise<Traslado> {
  const { data } = await http.post<Envelope<Traslado>>('/traslados', body)
  return data.data
}

export async function apiConteos(p: { page: number; size: number }): Promise<PageEnvelope<ConteoFisico>> {
  const { data } = await http.get<PageEnvelope<ConteoFisico>>('/conteos-fisicos', { params: { page: p.page, size: p.size } })
  return data
}

export async function apiCrearConteo(body: ConteoFisicoRequest): Promise<ConteoFisico> {
  const { data } = await http.post<Envelope<ConteoFisico>>('/conteos-fisicos', body)
  return data.data
}

/**
 * Registra un movimiento manual de inventario (entrada o salida).
 * El trigger `trg_mov_stock` en BD actualiza automáticamente `inv.inventario.stock`.
 */
export async function apiCrearMovimiento(body: MovimientoInventarioRequest): Promise<MovimientoInventario> {
  const { data } = await http.post<Envelope<MovimientoInventario>>('/movimientos', body)
  return data.data
}
