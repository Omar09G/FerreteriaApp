// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Ventas / POS ────────────────────────────────────────────────── */

/**
 * Formas de pago de parametría (cat.formas_pago). No tienen endpoint REST,
 * por lo que se mantienen alineadas con el seed de V2__parametria.sql.
 */
export const FORMAS_PAGO: {
  id: number;
  clave: string;
  nombre: string;
  esEfectivo: boolean;
  requiereReferencia: boolean;
}[] = [
  {
    id: 1,
    clave: "EFECTIVO",
    nombre: "Efectivo",
    esEfectivo: true,
    requiereReferencia: false,
  },
  {
    id: 2,
    clave: "TARJETA_DEBITO",
    nombre: "Tarjeta de débito",
    esEfectivo: false,
    requiereReferencia: true,
  },
  {
    id: 3,
    clave: "TARJETA_CREDITO",
    nombre: "Tarjeta de crédito",
    esEfectivo: false,
    requiereReferencia: true,
  },
  {
    id: 4,
    clave: "TRANSFERENCIA",
    nombre: "Transferencia SPEI",
    esEfectivo: false,
    requiereReferencia: true,
  },
  {
    id: 5,
    clave: "CHEQUE",
    nombre: "Cheque",
    esEfectivo: false,
    requiereReferencia: true,
  },
  {
    id: 6,
    clave: "CREDITO",
    nombre: "Crédito interno",
    esEfectivo: false,
    requiereReferencia: false,
  },
];

export interface VentaDetalle {
  ventaDetalleId: number;
  productoId: number;
  productoNombre: string;
  cantidad: number;
  precioUnitario: number;
  costoUnitario: number;
  descuentoLinea: number;
  totalLinea: number;
  promocionId?: number | null;
}

export interface VentaPago {
  pagoClienteId: number;
  formaPagoId: number;
  referencia: string | null;
  monto: number;
  fecha: string;
}

export interface ClienteVentaInfo {
  clienteId: number;
  razonSocial: string;
  nombreComercial: string | null;
  rfc: string | null;
  curp: string | null;
  regimenFiscal: string | null;
  telefono: string | null;
  whatsapp: string | null;
  email: string | null;
  calle: string | null;
  colonia: string | null;
  cp: string | null;
  ciudadNombre: string | null;
}

export interface Venta {
  ventaId: number;
  folio: string;
  clienteId: number | null;
  clienteNombre: string | null;
  cliente: ClienteVentaInfo | null;
  almacenId: number;
  almacenNombre: string;
  fecha: string;
  fechaLocal: string;
  formaPagoId: number;
  formaPagoNombre: string;
  ivaTasa: number;
  ivaIncluido: boolean;
  subtotal: number;
  iva: number;
  descuentoTotal: number;
  total: number;
  estado: string;
  usuarioId: number;
  turnoCajaId: number | null;
  notas: string | null;
  detalles: VentaDetalle[];
  pagos: VentaPago[];
}

export interface VentaRequest {
  almacenId: number;
  cajaId?: number;
  clienteId?: number;
  cotizacionId?: number;
  formaPagoId: number;
  detalles: { productoId: number; cantidad: number; precioUnitario: number }[];
  pagos: { formaPagoId: number; monto: number; referencia?: string }[];
  notas?: string;
  promocionId?: number | null;
}

export interface VentaCancelRequest {
  motivo: string;
}

/* ── Cobranza (crédito a clientes) ─────────────────────────────── */

export interface PagoCliente {
  pagoClienteId: number;
  formaPagoId: number;
  referencia: string | null;
  monto: number;
  fecha: string;
}

export interface CuentaCobrar {
  cuentaCobrarId: number;
  ventaId: number;
  ventaFolio: string;
  clienteId: number;
  clienteNombre: string;
  montoTotal: number;
  montoPagado: number;
  saldo: number;
  fechaVencimiento: string;
  estado: string;
  creadoEn: string;
  pagos: PagoCliente[];
}

export interface PagoClienteRequest {
  cuentaCobrarId: number;
  formaPagoId: number;
  monto: number;
  referencia?: string;
  turnoCajaId?: number;
}
