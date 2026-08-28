import http from './client'
import type { Caja, CorteCaja, CorteRequest, Envelope, Gasto, GastoRequest, IngresoOtro, IngresoOtroRequest, MovimientoCaja, MovimientoCajaRequest, PageEnvelope, TurnoCaja } from './types'

export async function apiCajas(): Promise<Caja[]> {
  const { data } = await http.get<Envelope<Caja[]>>('/cajas')
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

export async function apiCortes(page: number, size = 10): Promise<PageEnvelope<CorteCaja>> {
  const { data } = await http.get<PageEnvelope<CorteCaja>>('/cortes-caja', { params: { page, size } })
  return data
}

export async function apiGastos(page: number, size = 15): Promise<PageEnvelope<Gasto>> {
  const { data } = await http.get<PageEnvelope<Gasto>>('/gastos', { params: { page, size } })
  return data
}

export async function apiCrearGasto(body: GastoRequest): Promise<Gasto> {
  const { data } = await http.post<Envelope<Gasto>>('/gastos', body)
  return data.data
}

export async function apiIngresosOtros(page: number, size = 15): Promise<PageEnvelope<IngresoOtro>> {
  const { data } = await http.get<PageEnvelope<IngresoOtro>>('/ingresos-otros', { params: { page, size } })
  return data
}

export async function apiCrearIngreso(body: IngresoOtroRequest): Promise<IngresoOtro> {
  const { data } = await http.post<Envelope<IngresoOtro>>('/ingresos-otros', body)
  return data.data
}