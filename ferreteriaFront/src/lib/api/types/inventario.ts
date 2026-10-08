// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Movimientos de inventario ───────────────────────────────────── */

export interface MovimientoInventario {
  movimientoId: number;
  productoId: number;
  productoNombre: string;
  almacenId: number;
  almacenNombre: string;
  tipo: string;
  cantidad: number;
  costoUnitario?: number;
  motivoId?: number;
  motivoNombre?: string;
  refTabla?: string;
  refId?: number;
  trasladoId?: number;
  nota?: string;
  usuarioId?: number;
  creadoEn: string;
}

export interface MovimientoInventarioRequest {
  productoId: number;
  almacenId: number;
  tipo: "ENTRADA" | "SALIDA";
  cantidad: number;
  costoUnitario?: number;
  motivoId: number;
  refTabla?: string;
  refId?: number;
  nota?: string;
}

/**
 * Catálogo de motivos de movimiento (cat.motivos_movimiento).
 * IDs estables del seed V2__parametria.sql — el backend no expone endpoint para listarlos
 * (son 12 entradas fijas), así que el front los mantiene en línea. Si el seed cambia,
 * actualizar este arreglo.
 */
export const MOTIVOS_MOVIMIENTO: {
  id: number;
  clave: string;
  nombre: string;
  tipoDefault: "ENTRADA" | "SALIDA";
}[] = [
  {
    id: 1,
    clave: "INVENTARIO_INICIAL",
    nombre: "Inventario inicial",
    tipoDefault: "ENTRADA",
  },
  {
    id: 2,
    clave: "COMPRA",
    nombre: "Compra a proveedor",
    tipoDefault: "ENTRADA",
  },
  { id: 3, clave: "VENTA", nombre: "Venta a cliente", tipoDefault: "SALIDA" },
  {
    id: 4,
    clave: "DEVOLUCION_VENTA",
    nombre: "Devolución de cliente",
    tipoDefault: "ENTRADA",
  },
  {
    id: 5,
    clave: "DEVOLUCION_COMPRA",
    nombre: "Devolución a proveedor",
    tipoDefault: "SALIDA",
  },
  {
    id: 6,
    clave: "AJUSTE_INVENTARIO",
    nombre: "Ajuste por conteo físico",
    tipoDefault: "ENTRADA",
  },
  {
    id: 7,
    clave: "TRASLADO_SALIDA",
    nombre: "Traslado entre almacenes (sale)",
    tipoDefault: "SALIDA",
  },
  {
    id: 8,
    clave: "TRASLADO_ENTRADA",
    nombre: "Traslado entre almacenes (entra)",
    tipoDefault: "ENTRADA",
  },
  {
    id: 9,
    clave: "CONTEO_FISICO",
    nombre: "Diferencia de conteo físico",
    tipoDefault: "ENTRADA",
  },
  {
    id: 10,
    clave: "DETERIORO",
    nombre: "Mercancía deteriorada",
    tipoDefault: "SALIDA",
  },
  {
    id: 11,
    clave: "USO_INTERNO",
    nombre: "Consumo interno del negocio",
    tipoDefault: "SALIDA",
  },
  {
    id: 12,
    clave: "MUESTRA",
    nombre: "Muestra gratuita",
    tipoDefault: "SALIDA",
  },
];

export interface Inventario {
  productoId: number;
  productoNombre: string;
  productoCodigo?: string;
  almacenId: number;
  almacenNombre: string;
  stock: number;
  stockMinimo: number;
  stockMaximo?: number;
  reservado?: number;
}
