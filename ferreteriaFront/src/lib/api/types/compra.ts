// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Compras / cuentas por pagar ─────────────────────────────────── */

export interface Compra {
  compraId: number;
  folio: string;
  facturaProveedor: string | null;
  proveedorId: number;
  proveedor: string;
  almacenId: number;
  almacen: string;
  fecha: string;
  formaPagoId: number;
  formaPago: string;
  subtotal: number;
  iva: number;
  descuentoTotal: number;
  total: number;
  estado: string;
  usuarioId: number;
  turnoCajaId: number | null;
  notas: string | null;
  detalles: {
    compraDetalleId: number;
    productoId: number;
    producto: string;
    cantidad: number;
    costoUnitario: number;
    importeLinea: number;
  }[];
}

export interface CompraRequest {
  proveedorId: number;
  almacenId: number;
  formaPagoId: number;
  cajaId: number;
  facturaProveedor?: string;
  notas?: string;
  detalles: { productoId: number; cantidad: number; costoUnitario: number }[];
}

export interface CuentasPagar {
  cuentaPagarId: number;
  compraFolio: string;
  proveedor: string;
  montoTotal: number;
  montoPagado: number;
  saldo: number;
  fechaVencimiento: string;
  diasVencido: number;
  estado: string;
  fotoUrl?: string | null;
}

export interface AbonoProveedorRequest {
  monto: number;
  formaPagoId: number;
  cajaId: number;
  referencia?: string;
}

export interface AbonoProveedorResponse {
  pagoProveedorId: number;
  estado: string;
  montoTotal: number;
  montoPagado: number;
  saldo: number;
  compraFolio: string;
  formaPagoId: number;
  turnoCajaId: number | null;
  monto: number;
}

export interface FacturaPendiente {
  cuentaPagarId: number;
  compraFolio: string;
  facturaProveedor: string | null;
  proveedorId: number;
  proveedor: string;
  fechaCompra: string;
  montoTotal: number;
  montoPagado: number;
  saldo: number;
  estadoPago: string;
  fechaVencimiento: string;
  diasParaVencer: number;
  alerta: string;
  fotoUrl?: string | null;
}

export interface FacturaVencida extends Omit<
  FacturaPendiente,
  "diasParaVencer" | "alerta"
> {
  contactoTelefono: string | null;
  diasVencido: number;
  antiguedad: string;
  fotoUrl?: string | null;
}
