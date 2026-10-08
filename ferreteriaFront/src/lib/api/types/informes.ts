// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Reportes ─────────────────────────────────────────────────────── */

export interface RangoFechasParam {
  fechaInicio?: string;
  fechaFin?: string;
}

export interface TopProducto {
  mes: string;
  productoId: number;
  codigo?: string;
  producto: string;
  categoria: string;
  unidadesVendidas: number;
  ingresoTotal: number;
  costoTotal: number;
  utilidad: number;
  rankingMes: number;
  rankingUnidades: number;
}

export interface MejorCliente {
  mes: string;
  clienteId: number;
  cliente: string;
  numCompras: number;
  totalComprado: number;
  ticketPromedio: number;
  rankingMes: number;
  rankingHistorico: number;
}

export interface VentaTotal {
  fecha: string;
  numVentas: number;
  subtotal: number;
  iva: number;
  descuentos: number;
  totalVendido: number;
  ticketPromedio: number;
  costoVentas: number;
  utilidadBruta: number;
}

export interface MejorVendedor {
  mes: string;
  usuarioId: number;
  vendedor: string;
  numVentas: number;
  totalVendido: number;
  ticketPromedio: number;
  utilidadGenerada: number;
  rankingMes: number;
  rankingHistorico: number;
}

export interface VentaPorHora {
  hora: number;
  numVentas: number;
  totalAcumulado: number;
  ticketPromedio: number;
  rankingHorario: number;
}

export interface MejorDiaVenta {
  diaNum: number;
  diaSemana: string;
  diasConVenta: number;
  numVentas: number;
  totalAcumulado: number;
  promedioPorDia: number;
  ranking: number;
}

export interface ResumenDashboard {
  ventasEnRango: number;
  ticketsEnRango: number;
  ticketPromedioEnRango: number;
  saldoPorCobrar: number;
  cobranzaVencida: number;
  valorInventario: number;
  productosAgotados: number;
  promocionesActivas: number;
  cajasAbiertas: number;
  devolucionesEnRango: number;
  totalDevueltoEnRango: number;
}

export interface CierreDiario {
  fecha: string;
  numCortes: number;
  tickets: number;
  totalVendido: number;
  utilidadBruta: number;
  margenPctPromedio: number;
  perdidas: number;
  entradasEfectivo: number;
  salidasEfectivo: number;
  efectivoDepositado: number;
  diferenciaTotal: number;
  ingresosDigitales: number;
  todoCuadrado: boolean;
}

export interface InformeEnvio {
  fechaInicio: string;
  fechaFin: string;
  destinatarios: number;
  emailsEnviados: number;
  whatsappEnviados: number;
}

/** Recordatorio de cuentas por pagar (JOB 09:00 + botón manual). */
export interface CuentasPagarInformeEnvio {
  fecha: string;
  destinatarios: number;
  emailsEnviados: number;
  whatsappsEnviados: number;
  vencidas: number;
  pendientes: number;
  totalVencido: number;
  totalPendiente: number;
}

export interface CuentasPagarInformeEstado {
  fecha: string;
  yaEnviado: boolean;
  estado: string | null;
  enviadoEn: string | null;
}

/** Recordatorios diarios de cobranza y rentas (JOB + botón manual). */
export interface CobranzaInformeEnvio {
  fecha: string;
  destinatarios: number;
  emailsEnviados: number;
  whatsappsEnviados: number;
  vencidas: number;
  pendientes: number;
  totalVencido: number;
  totalPendiente: number;
}

export interface CobranzaInformeEstado {
  fecha: string;
  yaEnviado: boolean;
  estado: string | null;
  enviadoEn: string | null;
}

export interface RentasInformeEnvio {
  fecha: string;
  destinatarios: number;
  emailsEnviados: number;
  whatsappsEnviados: number;
  vencidas: number;
  proximas: number;
}

export interface RentasInformeEstado {
  fecha: string;
  yaEnviado: boolean;
  estado: string | null;
  enviadoEn: string | null;
}

/** Narrativa del día: hoy vs ayer + producto estrella. */
export interface Narrativa {
  fecha: string;
  ventasHoy: number;
  ventasAyer: number;
  cambioPct: number | null;
  ticketsHoy: number;
  ticketPromedioHoy: number;
  productoEstrella: string | null;
  estrellaIngreso: number | null;
  estrellaUnidades: number | null;
}

/** Recordatorio diario de stock bajo (JOB + botón manual). */
export interface StockBajoInformeEnvio {
  fecha: string;
  destinatarios: number;
  emailsEnviados: number;
  whatsappsEnviados: number;
  productos: number;
  agotados: number;
  almacenes: number;
}

export interface StockBajoInformeEstado {
  fecha: string;
  yaEnviado: boolean;
  estado: string | null;
  enviadoEn: string | null;
}

export interface TicketWhatsappResponse {
  enviado: boolean;
}

/** Aviso nocturno de turnos abiertos (JOB + botón manual). */
export interface TurnoAbiertoInformeEnvio {
  fecha: string;
  destinatarios: number;
  emailsEnviados: number;
  whatsappsEnviados: number;
  turnos: number;
}

export interface TurnoAbiertoInformeEstado {
  fecha: string;
  yaEnviado: boolean;
  estado: string | null;
  enviadoEn: string | null;
}

export interface InformeEstado {
  fechaInicio: string;
  fechaFin: string;
  yaEnviado: boolean;
  estado: string | null;
  enviadoEn: string | null;
}
