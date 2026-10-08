// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Auditoría ─────────────────────────────────────────────────── */

export interface Auditoria {
  auditoriaId: number;
  esquema: string;
  tabla: string;
  registroId: number;
  accion: "INSERT" | "UPDATE" | "DELETE";
  datosAnteriores?: string;
  datosNuevos?: string;
  usuarioId?: number;
  usuario?: string;
  creadoEn: string;
}

export interface AuditoriaTabla {
  esquema: string;
  tabla: string;
}

export interface MejoresCategorias {
  mes: string;
  categoriaId: number;
  categoria: string;
  unidadesVendidas: number;
  ingreso: number;
  utilidad: number;
  rankingMes: number;
  rankingHistorico: number;
}

export interface ProductosSinMovimiento {
  productoId: number;
  codigo: string;
  producto: string;
  categoria: string;
  stock: number;
  costoActual: number;
  dineroDetenidoEnEstante: number;
  ultimaVenta: string;
  diasSinVender: number;
  prioridadPromocion: string;
  imagenUrl?: string | null;
}

/* ── Configuración de ticket ─────────────────────────────────────── */

export interface TicketConfig {
  ticketConfigId: number | null;
  almacenId: number | null;
  logotipoUrl: string | null;
  mostrarLogotipo: boolean;
  nombreNegocio: string;
  direccion: string | null;
  cp: string | null;
  rfc: string | null;
  telefono: string | null;
  email: string | null;
  sitioWeb: string | null;
  tituloDocumento: string;
  mostrarDatosCliente: boolean;
  mostrarNumeroFactura: boolean;
  mostrarCaja: boolean;
  mostrarFechaHora: boolean;
  mostrarVendedor: boolean;
  mostrarDesgloseIva: boolean;
  mostrarDescuento: boolean;
  mostrarCambio: boolean;
  mensajePie: string | null;
  pieSecundario: string | null;
  anchoPapelMm: 58 | 80;
  fontSizePt: number;
  actualizadoEn: string | null;
  actualizadoPor: number | null;
}

export interface TicketConfigRequest {
  logotipoUrl?: string | null;
  mostrarLogotipo?: boolean;
  nombreNegocio?: string;
  direccion?: string | null;
  cp?: string | null;
  rfc?: string | null;
  telefono?: string | null;
  email?: string | null;
  sitioWeb?: string | null;
  tituloDocumento?: string;
  mostrarDatosCliente?: boolean;
  mostrarNumeroFactura?: boolean;
  mostrarCaja?: boolean;
  mostrarFechaHora?: boolean;
  mostrarVendedor?: boolean;
  mostrarDesgloseIva?: boolean;
  mostrarDescuento?: boolean;
  mostrarCambio?: boolean;
  mensajePie?: string | null;
  pieSecundario?: string | null;
  anchoPapelMm?: 58 | 80;
  fontSizePt?: number;
  almacenId?: number | null;
}
