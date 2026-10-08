// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Catálogos ───────────────────────────────────────────────────── */

export const TIPOS_PRODUCTO = [
  "PRODUCTO",
  "SERVICIO",
  "HERRAMIENTA_RENTA",
] as const;
export type TipoProducto = (typeof TIPOS_PRODUCTO)[number];

export interface Producto {
  productoId: number;
  codigo: string | null;
  tipo: string;
  nombre: string;
  descripcion: string | null;
  categoriaId: number;
  categoriaNombre: string;
  marcaId: number | null;
  marcaNombre: string | null;
  unidadMedidaId: number;
  unidadMedidaClave: string;
  costoActual: number;
  precioMenudeo: number;
  precioMayoreo: number | null;
  aplicaIva: boolean;
  stockActual?: number;
  /**
   * URL pública de la foto (MinIO). Ausente/null cuando el producto no tiene.
   * Se sube con POST /archivos/imagen y se guarda vía create/update.
   */
  imagenUrl?: string | null;
  /**
   * Códigos de barras del producto (tabla inv.producto_codigos_barras).
   * Ausente hasta que el backend los incluya en la respuesta.
   */
  codigosBarras?: string[];
  /**
   * Factor del código escaneado (unidades por pitido) cuando el match de
   * búsqueda fue por barras. Solo presente en ese caso.
   */
  factorEscaneo?: number | null;
}

export interface CodigoBarrasRequest {
  codigo: string;
  factor?: number | null;
}

export interface ProductoRequest {
  codigo?: string;
  tipo: TipoProducto;
  nombre: string;
  descripcion?: string;
  categoriaId: number;
  marcaId?: number | null;
  unidadMedidaId: number;
  costoActual?: number | null;
  precioMenudeo?: number | null;
  precioMayoreo?: number | null;
  aplicaIva?: boolean;
  /**
   * Códigos de barras del producto (factor 1 por defecto). El backend los
   * acepta en create/update y carga masiva (duplicado contra otro producto
   * → 409 VALOR_DUPLICADO).
   */
  codigosBarras?: CodigoBarrasRequest[];
  /**
   * URL pública de la foto (devuelta por POST /archivos/imagen).
   * Solo https:// o data:image/ (el backend valida formato).
   */
  imagenUrl?: string | null;
}

export interface CargaMasivaFilaError {
  fila: number;
  codigo: string;
  mensaje: string;
}

export interface CargaMasivaResultado {
  creados: Producto[];
  errores: CargaMasivaFilaError[];
}

export interface Categoria {
  categoriaId: number;
  nombre: string;
  categoriaPadreId: number | null;
  ruta: string;
  nivel: number;
  hijos?: Categoria[];
}

export interface Marca {
  marcaId: number;
  nombre: string;
}

export interface UnidadMedida {
  unidadId: number;
  clave: string;
  nombre: string;
  permiteFraccion: boolean;
}

export interface Cliente {
  clienteId: number;
  tipoPersona: string;
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
  ciudadId: number | null;
  ciudadNombre: string | null;
  cp: string | null;
  limiteCredito: number | null;
  diasCredito: number | null;
  esMayorista: boolean;
  activo?: boolean;
  /** URL pública de la foto (MinIO). Null cuando el cliente no tiene. */
  fotoUrl?: string | null;
}

export interface Proveedor {
  proveedorId: number;
  razonSocial: string;
  rfc: string | null;
  regimenFiscal: string | null;
  email: string | null;
  telefono: string | null;
  diasCredito: number | null;
  limiteCredito: number | null;
  /** URL pública de la foto (MinIO). Null cuando el proveedor no tiene. */
  fotoUrl?: string | null;
}

export interface ProveedorRequest {
  razonSocial: string;
  rfc?: string;
  regimenFiscal?: string;
  email?: string;
  telefono?: string;
  diasCredito?: number;
  limiteCredito?: number;
  fotoUrl?: string | null;
}

export interface ClienteRequest {
  tipoPersona?: "FISICA" | "MORAL";
  razonSocial: string;
  nombreComercial?: string;
  rfc?: string;
  telefono?: string;
  email?: string;
  limiteCredito?: number | null;
  diasCredito?: number | null;
  esMayorista?: boolean;
  fotoUrl?: string | null;
}

export interface Almacen {
  almacenId: number;
  nombre: string;
  direccion: string | null;
  telefono: string | null;
  esPuntoVenta: boolean;
  activo: boolean;
}

export interface AlmacenRequest {
  nombre: string;
  direccion?: string | null;
  telefono?: string | null;
  esPuntoVenta?: boolean | null;
}
