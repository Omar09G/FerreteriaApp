import http from './client'
import type { Caja, CajaRequest, CorteCaja, CorteRequest, Envelope, EsperadoCaja, Gasto, GastoRequest, IngresoOtro, IngresoOtroRequest, MovimientoCaja, MovimientoCajaRequest, PageEnvelope, TurnoCaja } from './types'

export async function apiCajas(): Promise<Caja[]> {
  const { data } = await http.get<Envelope<Caja[]>>('/cajas')
  return data.data
}

export async function apiCrearCaja(body: CajaRequest): Promise<Caja> {
  const { data } = await http.post<Envelope<Caja>>('/cajas', body)
  return data.data
}

export async function apiActualizarCaja(id: number, body: CajaRequest): Promise<Caja> {
  const { data } = await http.put<Envelope<Caja>>(`/cajas/${id}`, body)
  return data.data
}

export async function apiActualizarEstadoCaja(id: number, activa: boolean): Promise<Caja> {
  const { data } = await http.put<Envelope<Caja>>(`/cajas/estado/${id}`, { activa })
  return data.data
}

/**
 * Devuelve el turno actualmente ABIERTO de la caja o lanza ApiError(RECURSO_NO_ENCONTRADO) si no existe.
 * Útil para que el POS pregunte antes de permitir ventas.
 */
export async function apiTurnoActual(cajaId: number): Promise<TurnoCaja> {
  const { data } = await http.get<Envelope<TurnoCaja>>(`/cajas/${cajaId}/turno-actual`)
  return data.data
}

export async function apiTurnos(cajaId: number, page: number, size = 10): Promise<PageEnvelope<TurnoCaja>> {
  const { data } = await http.get<PageEnvelope<TurnoCaja>>(`/cajas/${cajaId}/turnos`, { params: { page, size } })
  return data
}

export async function apiAbrirTurno(cajaId: number, montoApertura: number): Promise<TurnoCaja> {
  const { data } = await http.post<Envelope<TurnoCaja>>(`/cajas/${cajaId}/turnos`, { cajaId, montoApertura })
  return data.data
}

export async function apiMovimientosTurno(cajaId: number, turnoId: number): Promise<MovimientoCaja[]> {
  const { data } = await http.get<Envelope<MovimientoCaja[]>>(`/cajas/${cajaId}/turnos/${turnoId}/movimientos`)
  return data.data
}

export async function apiRegistrarMovimiento(cajaId: number, turnoId: number, body: MovimientoCajaRequest): Promise<MovimientoCaja> {
  const { data } = await http.post<Envelope<MovimientoCaja>>(`/cajas/${cajaId}/turnos/${turnoId}/movimientos`, body)
  return data.data
}

export async function apiCerrarTurno(cajaId: number, turnoId: number, body: CorteRequest): Promise<CorteCaja> {
  const { data } = await http.post<Envelope<CorteCaja>>(`/cajas/${cajaId}/turnos/${turnoId}/corte`, body)
  return data.data
}

export async function apiEsperadoTurno(cajaId: number, turnoId: number): Promise<EsperadoCaja> {
  const { data } = await http.get<Envelope<EsperadoCaja>>(`/cajas/${cajaId}/turnos/${turnoId}/esperado`)
  return data.data
}

export async function apiCortes(page: number, size = 10, desde?: string, hasta?: string): Promise<PageEnvelope<CorteCaja>> {
  const { data } = await http.get<PageEnvelope<CorteCaja>>('/cortes-caja', { params: { page, size, desde, hasta } })
  return data
}

export async function apiGastos(page: number, size = 15, desde?: string, hasta?: string): Promise<PageEnvelope<Gasto>> {
  const { data } = await http.get<PageEnvelope<Gasto>>('/gastos', { params: { page, size, desde, hasta } })
  return data
}

export async function apiCrearGasto(body: GastoRequest): Promise<Gasto> {
  const { data } = await http.post<Envelope<Gasto>>('/gastos', body)
  return data.data
}

export async function apiActualizarGasto(id: number, body: GastoRequest): Promise<Gasto> {
  const { data } = await http.put<Envelope<Gasto>>(`/gastos/${id}`, body)
  return data.data
}

export async function apiEliminarGasto(id: number): Promise<void> {
  await http.delete(`/gastos/${id}`)
}

export async function apiIngresosOtros(page: number, size = 15, desde?: string, hasta?: string): Promise<PageEnvelope<IngresoOtro>> {
  const { data } = await http.get<PageEnvelope<IngresoOtro>>('/ingresos-otros', { params: { page, size, desde, hasta } })
  return data
}

export async function apiCrearIngreso(body: IngresoOtroRequest): Promise<IngresoOtro> {
  const { data } = await http.post<Envelope<IngresoOtro>>('/ingresos-otros', body)
  return data.data
}

export async function apiActualizarIngreso(id: number, body: IngresoOtroRequest): Promise<IngresoOtro> {
  const { data } = await http.put<Envelope<IngresoOtro>>(`/ingresos-otros/${id}`, body)
  return data.data
}

export async function apiEliminarIngreso(id: number): Promise<void> {
  await http.delete(`/ingresos-otros/${id}`)
}