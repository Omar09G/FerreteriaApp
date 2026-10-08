// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Cotizaciones ───────────────────────────────────────────────── */

export interface CotizacionDetalle {
  productoId: number;
  productoNombre: string;
  cantidad: number;
  precioUnitario: number;
  importeLinea: number;
}

export interface Cotizacion {
  cotizacionId: number;
  folio: string;
  clienteId: number | null;
  clienteNombre: string | null;
  fecha: string;
  vigenciaHasta: string | null;
  subtotal: number;
  iva: number;
  total: number;
  estado: string;
  ventaGeneradaId: number | null;
  usuarioId: number;
  evidenciaUrl?: string | null;
  detalles: CotizacionDetalle[];
}

export interface CotizacionRequest {
  clienteId?: number;
  vigenciaHasta?: string;
  detalles: { productoId: number; cantidad: number; precioUnitario: number }[];
  evidenciaUrl?: string;
}

/* ── Devoluciones ───────────────────────────────────────────────── */

export interface DevolucionDetalle {
  productoId: number;
  productoNombre: string;
  ventaDetalleId: number | null;
  cantidad: number;
  precioUnitario: number;
  importeLinea: number;
}

export interface Devolucion {
  devolucionId: number;
  folio: string;
  ventaId: number;
  ventaFolio: string;
  fecha: string;
  motivo: string;
  total: number;
  formaDevolucionId: number;
  formaDevolucionNombre: string | null;
  usuarioId: number;
  detalles: DevolucionDetalle[];
}

export interface DevolucionRequest {
  ventaId: number;
  motivo: string;
  formaDevolucionId: number;
  detalles: {
    productoId: number;
    ventaDetalleId?: number;
    cantidad: number;
    precioUnitario: number;
  }[];
}

/* ── Rentas ─────────────────────────────────────────────────────── */

export interface RentaDetalle {
  productoId: number;
  productoNombre: string;
  cantidad: number;
  costoDia: number;
  diasCobrados: number;
  subtotal: number;
}

export interface Renta {
  rentaId: number;
  folio: string;
  clienteId: number;
  clienteNombre: string;
  almacenId: number;
  almacenNombre: string;
  fechaRenta: string;
  fechaDevEsperada: string;
  fechaDevReal: string | null;
  deposito: number;
  costoTotal: number;
  formaPagoId: number | null;
  turnoCajaId: number | null;
  estado: string;
  usuarioId: number;
  detalles: RentaDetalle[];
}

export interface RentaRequest {
  clienteId: number;
  almacenId: number;
  cajaId: number;
  formaPagoId: number;
  fechaDevEsperada: string;
  deposito: number;
  detalles: { productoId: number; cantidad: number; costoDia: number }[];
}

export interface RentaDevolucionRequest {
  detalles: { productoId: number; diasCobrados: number }[];
}

/* ── Traslados y conteos físicos ────────────────────────────────── */

export interface TrasladoDetalle {
  productoId: number;
  productoNombre: string;
  cantidad: number;
}

export interface Traslado {
  trasladoId: number;
  folio: string;
  almacenOrigen: number;
  almacenOrigenNombre: string;
  almacenDestino: number;
  almacenDestinoNombre: string;
  estado: string;
  usuarioId: number;
  creadoEn: string;
  detalles: TrasladoDetalle[];
}

export interface TrasladoRequest {
  almacenOrigen: number;
  almacenDestino: number;
  detalles: { productoId: number; cantidad: number }[];
}

export interface ConteoFisicoDetalle {
  productoId: number;
  productoCodigo: string | null;
  productoNombre: string | null;
  cantidadSistema: number;
  cantidadFisica: number;
  diferencia: number;
}

export interface ConteoFisico {
  conteoId: number;
  almacenId: number;
  almacenNombre: string | null;
  fecha: string | null;
  estado: string;
  usuarioId: number;
  usuarioNombre: string | null;
  observaciones: string | null;
  totalPartidas: number;
  diferenciaTotal: number;
  detalles: ConteoFisicoDetalle[];
}

export interface ConteoFisicoRequest {
  almacenId: number;
  observaciones?: string;
  detalles: { productoId: number; cantidadFisica: number }[];
}
