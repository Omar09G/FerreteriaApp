export interface PageResult {
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PageEnvelope<T> {
  success: boolean
  data: T[]
  meta: PageResult
}

export interface Envelope<T> {
  success: boolean
  data: T
}

export interface FieldError {
  field: string
  error: string
}

export interface ApiErrorBody {
  success: boolean
  data: null
  errorCode: number
  codigo: string
  errorMessage: string
  details?: FieldError[]
  requestId?: string
  instance?: string
}

export interface MeEmpleado {
  empleadoId: number
  nombreCompleto: string
  puestoNombre: string
  email?: string
  telefono?: string
  activo: boolean
}

export interface MeResponse {
  usuarioId: number
  username: string
  empleadoId?: number
  roles: string[]
  ultimoLogin?: string
  empleado?: MeEmpleado
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
  usuario: MeResponse
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface ChangePasswordRequest {
  passwordActual: string
  nuevaPassword: string
}

export interface PasswordOk {
  cambiada: boolean
}

export interface LogoutOk {
  revocado: boolean
}

export interface OperacionOk {
  ok: boolean
}

/* ── Reportes ─────────────────────────────────────────────────────── */

export interface RangoFechasParam {
  fechaInicio?: string
  fechaFin?: string
}

export interface TopProducto {
  mes: string
  productoId: number
  codigo?: string
  producto: string
  categoria: string
  unidadesVendidas: number
  ingresoTotal: number
  costoTotal: number
  utilidad: number
  rankingMes: number
  rankingUnidades: number
}

export interface MejorCliente {
  mes: string
  clienteId: number
  cliente: string
  numCompras: number
  totalComprado: number
  ticketPromedio: number
  rankingMes: number
  rankingHistorico: number
}

export interface VentaTotal {
  fecha: string
  numVentas: number
  subtotal: number
  iva: number
  descuentos: number
  totalVendido: number
  ticketPromedio: number
  costoVentas: number
  utilidadBruta: number
}

export interface MejorVendedor {
  mes: string
  usuarioId: number
  vendedor: string
  numVentas: number
  totalVendido: number
  ticketPromedio: number
  utilidadGenerada: number
  rankingMes: number
  rankingHistorico: number
}

export interface VentaPorHora {
  hora: number
  numVentas: number
  totalAcumulado: number
  ticketPromedio: number
  rankingHorario: number
}

export interface MejorDiaVenta {
  diaNum: number
  diaSemana: string
  diasConVenta: number
  numVentas: number
  totalAcumulado: number
  promedioPorDia: number
  ranking: number
}

export interface ResumenDashboard {
  ventasEnRango: number
  ticketsEnRango: number
  ticketPromedioEnRango: number
  saldoPorCobrar: number
  cobranzaVencida: number
  valorInventario: number
  productosAgotados: number
  promocionesActivas: number
  cajasAbiertas: number
}

export interface CierreDiario {
  fecha: string
  numCortes: number
  tickets: number
  totalVendido: number
  utilidadBruta: number
  margenPctPromedio: number
  perdidas: number
  entradasEfectivo: number
  salidasEfectivo: number
  efectivoDepositado: number
  diferenciaTotal: number
  ingresosDigitales: number
  todoCuadrado: boolean
}

/* ── Movimientos de inventario ───────────────────────────────────── */

export interface MovimientoInventario {
  movimientoId: number
  productoId: number
  productoNombre: string
  almacenId: number
  almacenNombre: string
  tipo: string
  cantidad: number
  costoUnitario?: number
  motivoId?: number
  motivoNombre?: string
  refTabla?: string
  refId?: number
  trasladoId?: number
  nota?: string
  usuarioId?: number
  creadoEn: string
}

export interface Inventario {
  productoId: number
  productoNombre: string
  productoCodigo?: string
  almacenId: number
  almacenNombre: string
  stock: number
  stockMinimo: number
  stockMaximo?: number
  reservado?: number
}

/* ── Catálogos ───────────────────────────────────────────────────── */

export const TIPOS_PRODUCTO = ['PRODUCTO', 'SERVICIO', 'HERRAMIENTA_RENTA'] as const
export type TipoProducto = (typeof TIPOS_PRODUCTO)[number]

export interface Producto {
  productoId: number
  codigo: string | null
  tipo: string
  nombre: string
  descripcion: string | null
  categoriaId: number
  categoriaNombre: string
  marcaId: number | null
  marcaNombre: string | null
  unidadMedidaId: number
  unidadMedidaClave: string
  costoActual: number
  precioMenudeo: number
  precioMayoreo: number | null
  aplicaIva: boolean
}

export interface ProductoRequest {
  codigo?: string
  tipo: TipoProducto
  nombre: string
  descripcion?: string
  categoriaId: number
  marcaId?: number | null
  unidadMedidaId: number
  costoActual?: number | null
  precioMenudeo?: number | null
  precioMayoreo?: number | null
  aplicaIva?: boolean
}

export interface Categoria {
  categoriaId: number
  nombre: string
  categoriaPadreId: number | null
  ruta: string
  nivel: number
  hijos?: Categoria[]
}

export interface Marca {
  marcaId: number
  nombre: string
}

export interface UnidadMedida {
  unidadId: number
  clave: string
  nombre: string
  permiteFraccion: boolean
}

export interface Cliente {
  clienteId: number
  tipoPersona: string
  razonSocial: string
  nombreComercial: string | null
  rfc: string | null
  telefono: string | null
  email: string | null
  limiteCredito: number | null
  diasCredito: number | null
  esMayorista: boolean
}

export interface Proveedor {
  proveedorId: number
  razonSocial: string
  rfc: string | null
  regimenFiscal: string | null
  email: string | null
  telefono: string | null
  diasCredito: number | null
  limiteCredito: number | null
}

export interface ProveedorRequest {
  razonSocial: string
  rfc?: string
  regimenFiscal?: string
  email?: string
  telefono?: string
  diasCredito?: number
  limiteCredito?: number
}

export interface ClienteRequest {
  tipoPersona?: 'FISICA' | 'MORAL'
  razonSocial: string
  nombreComercial?: string
  rfc?: string
  telefono?: string
  email?: string
  limiteCredito?: number | null
  diasCredito?: number | null
  esMayorista?: boolean
}

export interface Almacen {
  almacenId: number
  nombre: string
  direccion: string | null
  telefono: string | null
  esPuntoVenta: boolean
  activo: boolean
}

/* ── Ventas / POS ────────────────────────────────────────────────── */

/**
 * Formas de pago de parametría (cat.formas_pago). No tienen endpoint REST,
 * por lo que se mantienen alineadas con el seed de V2__parametria.sql.
 */
export const FORMAS_PAGO: { id: number; clave: string; nombre: string; esEfectivo: boolean; requiereReferencia: boolean }[] = [
  { id: 1, clave: 'EFECTIVO', nombre: 'Efectivo', esEfectivo: true, requiereReferencia: false },
  { id: 2, clave: 'TARJETA_DEBITO', nombre: 'Tarjeta de débito', esEfectivo: false, requiereReferencia: true },
  { id: 3, clave: 'TARJETA_CREDITO', nombre: 'Tarjeta de crédito', esEfectivo: false, requiereReferencia: true },
  { id: 4, clave: 'TRANSFERENCIA', nombre: 'Transferencia SPEI', esEfectivo: false, requiereReferencia: true },
  { id: 5, clave: 'CHEQUE', nombre: 'Cheque', esEfectivo: false, requiereReferencia: true },
  { id: 6, clave: 'CREDITO', nombre: 'Crédito interno', esEfectivo: false, requiereReferencia: false },
]

export interface VentaDetalle {
  ventaDetalleId: number
  productoId: number
  productoNombre: string
  cantidad: number
  precioUnitario: number
  costoUnitario: number
  descuentoLinea: number
  totalLinea: number
}

export interface VentaPago {
  pagoClienteId: number
  formaPagoId: number
  referencia: string | null
  monto: number
  fecha: string
}

export interface Venta {
  ventaId: number
  folio: string
  clienteId: number | null
  clienteNombre: string | null
  almacenId: number
  almacenNombre: string
  fecha: string
  fechaLocal: string
  formaPagoId: number
  formaPagoNombre: string
  ivaTasa: number
  ivaIncluido: boolean
  subtotal: number
  iva: number
  descuentoTotal: number
  total: number
  estado: string
  usuarioId: number
  turnoCajaId: number | null
  notas: string | null
  detalles: VentaDetalle[]
  pagos: VentaPago[]
}

export interface VentaRequest {
  almacenId: number
  cajaId?: number
  clienteId?: number
  cotizacionId?: number
  formaPagoId: number
  detalles: { productoId: number; cantidad: number; precioUnitario: number }[]
  pagos: { formaPagoId: number; monto: number; referencia?: string }[]
  notas?: string
}

export interface VentaCancelRequest {
  motivo: string
}

/* ── Cobranza (crédito a clientes) ─────────────────────────────── */

export interface PagoCliente {
  pagoClienteId: number
  formaPagoId: number
  referencia: string | null
  monto: number
  fecha: string
}

export interface CuentaCobrar {
  cuentaCobrarId: number
  ventaId: number
  ventaFolio: string
  clienteId: number
  clienteNombre: string
  montoTotal: number
  montoPagado: number
  saldo: number
  fechaVencimiento: string
  estado: string
  creadoEn: string
  pagos: PagoCliente[]
}

export interface PagoClienteRequest {
  cuentaCobrarId: number
  formaPagoId: number
  monto: number
  referencia?: string
  turnoCajaId?: number
}

/* ── Catálogos internos (sin endpoint REST, alineados con V2 seed) ── */

/** cat.puestos del seed: (puesto_id autoincremental 1..6). */
export const PUESTOS: { id: number; nombre: string }[] = [
  { id: 1, nombre: 'Administrador General' },
  { id: 2, nombre: 'Gerente' },
  { id: 3, nombre: 'Encargado de caja' },
  { id: 4, nombre: 'Vendedor' },
  { id: 5, nombre: 'Almacenista' },
  { id: 6, nombre: 'Auxiliar administrativo' },
]

/** cat.tipos_gasto del seed: (tipo_gasto_id autoincremental 1..14). */
export const TIPOS_GASTO: { id: number; clave: string; nombre: string; esFijo: boolean }[] = [
  { id: 1, clave: 'RENTA_LOCAL', nombre: 'Renta del local', esFijo: true },
  { id: 2, clave: 'LUZ', nombre: 'Electricidad', esFijo: true },
  { id: 3, clave: 'AGUA', nombre: 'Agua', esFijo: true },
  { id: 4, clave: 'INTERNET_TELEFONO', nombre: 'Internet y teléfono', esFijo: true },
  { id: 5, clave: 'TRANSPORTE', nombre: 'Transporte y fletes', esFijo: false },
  { id: 6, clave: 'MANTENIMIENTO', nombre: 'Mantenimiento', esFijo: false },
  { id: 7, clave: 'IMPUESTOS', nombre: 'Impuestos y derechos', esFijo: false },
  { id: 8, clave: 'PUBLICIDAD', nombre: 'Publicidad', esFijo: false },
  { id: 9, clave: 'PAPELERIA', nombre: 'Papelería e insumos', esFijo: false },
  { id: 10, clave: 'SEGURIDAD', nombre: 'Seguridad y vigilancia', esFijo: true },
  { id: 11, clave: 'COMISIONES', nombre: 'Comisiones de venta', esFijo: false },
  { id: 12, clave: 'LIMPIEZA', nombre: 'Limpieza', esFijo: false },
  { id: 13, clave: 'NOMINA', nombre: 'Pago de nómina', esFijo: false },
  { id: 14, clave: 'OTROS', nombre: 'Otros gastos', esFijo: false },
]

/* ── Caja / cortes ───────────────────────────────────────────────── */

export interface Caja {
  cajaId: number
  nombre: string
  almacenId: number
  almacenNombre: string
  activa: boolean
}

export interface TurnoCaja {
  turnoCajaId: number
  cajaId: number
  cajaNombre: string
  usuarioId: number
  aperturaEn: string
  montoApertura: number
  cierreEn: string | null
  montoEsperado: number | null
  montoContado: number | null
  diferencia: number | null
  estado: string
  observaciones: string | null
}

export interface MovimientoCaja {
  movimientoId: number
  turnoCajaId: number
  tipo: string
  concepto: string
  monto: number
  formaPagoId: number | null
  formaPagoNombre: string | null
  refTabla: string | null
  refId: number | null
  creadoEn: string
}

export interface MovimientoCajaRequest {
  tipo: string
  concepto: string
  monto: number
  formaPagoId?: number
}

export interface CorteRequest {
  montoContado: number
  observaciones?: string
}

export interface CorteCaja {
  corteId: number
  turnoCajaId: number
  cajaId: number
  cajaNombre: string
  almacenId: number
  almacenNombre: string
  usuarioId: number
  usuarioCierreId: number
  fecha: string
  aperturaEn: string
  cierreEn: string
  numVentas: number
  subtotal: number
  iva: number
  descuentos: number
  totalVendido: number
  costoVentas: number
  utilidadBruta: number
  margenPct: number
  fondoApertura: number
  entradasEfectivo: number
  salidasEfectivo: number
  dineroEsperado: number
  dineroContado: number
  diferencia: number
  resultadoCaja: string
  ingresosNoEfectivo: number
  egresosNoEfectivo: number
  perdidasInventario: number
  desgloseEntradas: string
  desgloseSalidas: string
  desgloseFormasPago: string
  observaciones: string | null
}

/* ── Gastos e ingresos de caja ──────────────────────────────────── */

export interface Gasto {
  gastoId: number
  folio: string | null
  tipoGastoId: number
  tipoGastoNombre: string | null
  descripcion: string
  monto: number
  fechaGasto: string
  formaPagoId: number
  formaPagoNombre: string | null
  proveedorId: number | null
  turnoCajaId: number | null
  facturaUuid: string | null
  usuarioId: number
  creadoEn: string
}

export interface GastoRequest {
  tipoGastoId: number
  descripcion: string
  monto: number
  fechaGasto?: string
  formaPagoId: number
  proveedorId?: number
  turnoCajaId?: number
  facturaUuid?: string
}

export interface IngresoOtro {
  ingresoOtroId: number
  concepto: string
  monto: number
  fecha: string
  formaPagoId: number
  formaPagoNombre: string | null
  turnoCajaId: number | null
  usuarioId: number
  creadoEn: string
}

export interface IngresoOtroRequest {
  concepto: string
  monto: number
  fecha?: string
  formaPagoId: number
  turnoCajaId?: number
}

/* ── Compras / cuentas por pagar ─────────────────────────────────── */

export interface Compra {
  compraId: number
  folio: string
  facturaProveedor: string | null
  proveedorId: number
  proveedor: string
  almacenId: number
  almacen: string
  fecha: string
  formaPagoId: number
  formaPago: string
  subtotal: number
  iva: number
  descuentoTotal: number
  total: number
  estado: string
  usuarioId: number
  turnoCajaId: number | null
  notas: string | null
  detalles: { compraDetalleId: number; productoId: number; producto: string; cantidad: number; costoUnitario: number; importeLinea: number }[]
}

export interface CompraRequest {
  proveedorId: number
  almacenId: number
  formaPagoId: number
  facturaProveedor?: string
  notas?: string
  detalles: { productoId: number; cantidad: number; costoUnitario: number }[]
}

export interface CuentasPagar {
  cuentaPagarId: number
  compraFolio: string
  proveedor: string
  montoTotal: number
  montoPagado: number
  saldo: number
  fechaVencimiento: string
  diasVencido: number
  estado: string
}

export interface FacturaPendiente {
  cuentaPagarId: number
  compraFolio: string
  facturaProveedor: string | null
  proveedorId: number
  proveedor: string
  fechaCompra: string
  montoTotal: number
  montoPagado: number
  saldo: number
  estadoPago: string
  fechaVencimiento: string
  diasParaVencer: number
  alerta: string
}

export interface FacturaVencida extends Omit<FacturaPendiente, 'diasParaVencer' | 'alerta'> {
  contactoTelefono: string | null
  diasVencido: number
  antiguedad: string
}

/* ── Empleados / usuarios ────────────────────────────────────────── */

export interface Empleado {
  empleadoId: number
  puestoId: number
  puestoNombre: string
  nombre: string
  apellidoPaterno: string
  apellidoMaterno: string
  curp: string | null
  nss: string | null
  telefono: string | null
  email: string | null
  calle: string | null
  colonia: string | null
  ciudadId: number | null
  cp: string | null
  fechaIngreso: string | null
  fechaBaja: string | null
  sueldoDiario: number
  activo: boolean
}

export interface EmpleadoCreateRequest {
  puestoId: number
  nombre: string
  apellidoPaterno: string
  apellidoMaterno?: string
  curp?: string
  nss?: string
  telefono?: string
  email?: string
  sueldoDiario?: number
  username?: string
  password?: string
  roles?: string[]
}

export interface Usuario {
  usuarioId: number
  username: string
  email: string
  empleadoId: number | null
  activo: boolean
  roles: string[]
  empleado: { empleadoId: number; nombreCompleto: string; puestoNombre: string; email: string | null; telefono: string | null; activo: boolean } | null
  ultimoLogin: string | null
  creadoEn: string
}

export interface UsuarioCreateRequest {
  username: string
  email: string
  password: string
  empleadoId?: number
  roles?: string[]
}

export interface Rol {
  rolId: number
  clave: string
  nombre: string
  descripcion: string | null
  activo: boolean
  permisos: string[]
}

export interface RolRequest {
  clave: string
  nombre: string
  descripcion?: string
  activo?: boolean
}

export interface Permiso {
  permisoId: number
  clave: string
  descripcion: string
}

/* ── Cotizaciones ───────────────────────────────────────────────── */

export interface CotizacionDetalle {
  productoId: number
  productoNombre: string
  cantidad: number
  precioUnitario: number
  importeLinea: number
}

export interface Cotizacion {
  cotizacionId: number
  folio: string
  clienteId: number | null
  clienteNombre: string | null
  fecha: string
  vigenciaHasta: string | null
  subtotal: number
  iva: number
  total: number
  estado: string
  ventaGeneradaId: number | null
  usuarioId: number
  detalles: CotizacionDetalle[]
}

export interface CotizacionRequest {
  clienteId?: number
  vigenciaHasta?: string
  detalles: { productoId: number; cantidad: number; precioUnitario: number }[]
}

/* ── Devoluciones ───────────────────────────────────────────────── */

export interface DevolucionDetalle {
  productoId: number
  productoNombre: string
  ventaDetalleId: number | null
  cantidad: number
  precioUnitario: number
  importeLinea: number
}

export interface Devolucion {
  devolucionId: number
  folio: string
  ventaId: number
  ventaFolio: string
  fecha: string
  motivo: string
  total: number
  formaDevolucionId: number
  formaDevolucionNombre: string | null
  usuarioId: number
  detalles: DevolucionDetalle[]
}

export interface DevolucionRequest {
  ventaId: number
  motivo: string
  formaDevolucionId: number
  detalles: { productoId: number; ventaDetalleId?: number; cantidad: number; precioUnitario: number }[]
}

/* ── Rentas ─────────────────────────────────────────────────────── */

export interface RentaDetalle {
  productoId: number
  productoNombre: string
  cantidad: number
  costoDia: number
  diasCobrados: number
  subtotal: number
}

export interface Renta {
  rentaId: number
  folio: string
  clienteId: number
  clienteNombre: string
  almacenId: number
  almacenNombre: string
  fechaRenta: string
  fechaDevEsperada: string
  fechaDevReal: string | null
  deposito: number
  costoTotal: number
  estado: string
  usuarioId: number
  detalles: RentaDetalle[]
}

export interface RentaRequest {
  clienteId: number
  almacenId: number
  fechaDevEsperada: string
  deposito: number
  detalles: { productoId: number; cantidad: number; costoDia: number }[]
}

export interface RentaDevolucionRequest {
  detalles: { productoId: number; diasCobrados: number }[]
}

/* ── Traslados y conteos físicos ────────────────────────────────── */

export interface TrasladoDetalle {
  productoId: number
  productoNombre: string
  cantidad: number
}

export interface Traslado {
  trasladoId: number
  folio: string
  almacenOrigen: number
  almacenOrigenNombre: string
  almacenDestino: number
  almacenDestinoNombre: string
  estado: string
  usuarioId: number
  creadoEn: string
  detalles: TrasladoDetalle[]
}

export interface TrasladoRequest {
  almacenOrigen: number
  almacenDestino: number
  detalles: { productoId: number; cantidad: number }[]
}

export interface ConteoFisico {
  conteoId: number
  almacenId: number
  almacenNombre: string
  estado: string
  usuarioId: number
  observaciones: string | null
}

export interface ConteoFisicoRequest {
  almacenId: number
  observaciones?: string
  detalles: { productoId: number; cantidadFisica: number }[]
}

/* ── Nómina ─────────────────────────────────────────────────────── */

export interface Nomina {
  nominaId: number
  empleadoId: number
  empleado: string
  periodoIni: string
  periodoFin: string
  diasPagados: number
  percepciones: number
  deducciones: number
  netoPagar: number
  estado: string
  fechaPago: string | null
  usuarioRegistraId: number
  notas: string | null
}

export interface NominaRequest {
  empleadoId: number
  periodoIni: string
  periodoFin: string
  diasPagados: number
  percepciones: number
  deducciones: number
  notas?: string
}

/* ── Facturas CFDI ──────────────────────────────────────────────── */

export interface FacturaFis {
  facturaId: number
  tipo: string
  serie: string | null
  folio: string
  uuid: string | null
  emisorRfc: string
  receptorRfc: string
  subtotal: number
  iva: number
  total: number
  fechaTimbrado: string
  estado: string
  ventaId: number | null
  usuarioId: number
  creadoEn: string
}

export interface FacturaFisRequest {
  tipo: string
  serie?: string
  folio: string
  uuid?: string
  emisorRfc: string
  receptorRfc: string
  subtotal: number
  iva: number
  cfdiXml?: string
  ventaId?: number
}

export interface FacturaXml {
  facturaId: number
  folio: string
  uuid: string | null
  tipo: string
  cfdiXml: string | null
}