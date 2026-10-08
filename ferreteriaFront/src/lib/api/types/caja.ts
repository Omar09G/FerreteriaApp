// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Catálogos internos (sin endpoint REST, alineados con V2 seed) ── */

/** cat.puestos del seed: (puesto_id autoincremental 1..6). */
export const PUESTOS: { id: number; nombre: string }[] = [
  { id: 1, nombre: "Administrador General" },
  { id: 2, nombre: "Gerente" },
  { id: 3, nombre: "Encargado de caja" },
  { id: 4, nombre: "Vendedor" },
  { id: 5, nombre: "Almacenista" },
  { id: 6, nombre: "Auxiliar administrativo" },
];

/** cat.tipos_gasto del seed: (tipo_gasto_id autoincremental 1..14). */
export const TIPOS_GASTO: {
  id: number;
  clave: string;
  nombre: string;
  esFijo: boolean;
}[] = [
  { id: 1, clave: "RENTA_LOCAL", nombre: "Renta del local", esFijo: true },
  { id: 2, clave: "LUZ", nombre: "Electricidad", esFijo: true },
  { id: 3, clave: "AGUA", nombre: "Agua", esFijo: true },
  {
    id: 4,
    clave: "INTERNET_TELEFONO",
    nombre: "Internet y teléfono",
    esFijo: true,
  },
  { id: 5, clave: "TRANSPORTE", nombre: "Transporte y fletes", esFijo: false },
  { id: 6, clave: "MANTENIMIENTO", nombre: "Mantenimiento", esFijo: false },
  { id: 7, clave: "IMPUESTOS", nombre: "Impuestos y derechos", esFijo: false },
  { id: 8, clave: "PUBLICIDAD", nombre: "Publicidad", esFijo: false },
  { id: 9, clave: "PAPELERIA", nombre: "Papelería e insumos", esFijo: false },
  {
    id: 10,
    clave: "SEGURIDAD",
    nombre: "Seguridad y vigilancia",
    esFijo: true,
  },
  { id: 11, clave: "COMISIONES", nombre: "Comisiones de venta", esFijo: false },
  { id: 12, clave: "LIMPIEZA", nombre: "Limpieza", esFijo: false },
  { id: 13, clave: "NOMINA", nombre: "Pago de nómina", esFijo: false },
  { id: 14, clave: "OTROS", nombre: "Otros gastos", esFijo: false },
];

/* ── Caja / cortes ───────────────────────────────────────────────── */

export interface Caja {
  cajaId: number;
  nombre: string;
  almacenId: number;
  almacenNombre: string;
  activa: boolean;
}

export interface CajaRequest {
  nombre: string;
  almacenId: number;
  activa: boolean;
}

export interface TurnoCaja {
  turnoCajaId: number;
  cajaId: number;
  cajaNombre: string;
  usuarioId: number;
  aperturaEn: string;
  montoApertura: number;
  cierreEn: string | null;
  montoEsperado: number | null;
  montoContado: number | null;
  diferencia: number | null;
  estado: string;
  observaciones: string | null;
}

export interface MovimientoCaja {
  movimientoId: number;
  turnoCajaId: number;
  tipo: string;
  concepto: string;
  monto: number;
  formaPagoId: number | null;
  formaPagoNombre: string | null;
  refTabla: string | null;
  refId: number | null;
  creadoEn: string;
  refDescripcion: string | null;
}

export interface MovimientoCajaRequest {
  tipo: string;
  concepto: string;
  monto: number;
  formaPagoId?: number;
}

export interface CorteRequest {
  montoContado: number;
  observaciones?: string;
}

export interface EsperadoCaja {
  montoApertura: number;
  entradasEfectivo: number;
  salidasEfectivo: number;
  esperado: number;
}

export interface CorteCaja {
  corteId: number;
  turnoCajaId: number;
  cajaId: number;
  cajaNombre: string;
  almacenId: number;
  almacenNombre: string;
  usuarioId: number;
  usuarioCierreId: number;
  fecha: string;
  aperturaEn: string;
  cierreEn: string;
  numVentas: number;
  subtotal: number;
  iva: number;
  descuentos: number;
  totalVendido: number;
  costoVentas: number;
  utilidadBruta: number;
  margenPct: number;
  fondoApertura: number;
  entradasEfectivo: number;
  salidasEfectivo: number;
  dineroEsperado: number;
  dineroContado: number;
  diferencia: number;
  resultadoCaja: string;
  ingresosNoEfectivo: number;
  egresosNoEfectivo: number;
  perdidasInventario: number;
  desgloseEntradas: string;
  desgloseSalidas: string;
  desgloseFormasPago: string;
  observaciones: string | null;
}

/* ── Gastos e ingresos de caja ──────────────────────────────────── */

export interface Gasto {
  gastoId: number;
  folio: string | null;
  tipoGastoId: number;
  tipoGastoNombre: string | null;
  descripcion: string;
  monto: number;
  fechaGasto: string;
  formaPagoId: number;
  formaPagoNombre: string | null;
  proveedorId: number | null;
  turnoCajaId: number | null;
  facturaUuid: string | null;
  usuarioId: number;
  creadoEn: string;
}

export interface GastoRequest {
  tipoGastoId: number;
  descripcion: string;
  monto: number;
  fechaGasto?: string;
  formaPagoId: number;
  proveedorId?: number;
  turnoCajaId?: number;
  facturaUuid?: string;
}

export interface IngresoOtro {
  ingresoOtroId: number;
  concepto: string;
  monto: number;
  fecha: string;
  formaPagoId: number;
  formaPagoNombre: string | null;
  turnoCajaId: number | null;
  usuarioId: number;
  creadoEn: string;
}

export interface IngresoOtroRequest {
  concepto: string;
  monto: number;
  fecha?: string;
  formaPagoId: number;
  turnoCajaId?: number;
}
