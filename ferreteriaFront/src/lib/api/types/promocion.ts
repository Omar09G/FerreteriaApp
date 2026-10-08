// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Promociones ─────────────────────────────────────────────────── */

export type TipoPromocion =
  | "DESCUENTO_PRODUCTO"
  | "DESCUENTO_TOTAL_VENTA"
  | "POR_CANTIDAD"
  | "NXM"
  | "PRECIO_ESPECIAL";

export type EstadoPromocion =
  | "ACTIVA"
  | "PROGRAMADA"
  | "FINALIZADA"
  | "CANCELADA";

export interface Promocion {
  promocionId: number;
  nombre: string;
  descripcion?: string;
  tipo: TipoPromocion;
  valorPct?: number;
  valorMonto?: number;
  precioEspecial?: number;
  compraMinTotal?: number;
  compraMinCantidad?: number;
  lleva?: number;
  paga?: number;
  maxUsosTotal?: number;
  maxUsosCliente?: number;
  usosActual: number;
  vigenciaDesde: string;
  vigenciaHasta?: string;
  diasSemana: number[];
  horaDesde?: string;
  horaHasta?: string;
  soloMayoristas: boolean;
  estado: EstadoPromocion;
  productos: number[];
  categorias: number[];
  usuarioId: number;
  creadoEn: string;
}

export interface PromocionRequest {
  nombre: string;
  descripcion?: string;
  tipo: TipoPromocion;
  valorPct?: number;
  valorMonto?: number;
  precioEspecial?: number;
  compraMinTotal?: number;
  compraMinCantidad?: number;
  lleva?: number;
  paga?: number;
  maxUsosTotal?: number;
  maxUsosCliente?: number;
  vigenciaDesde?: string;
  vigenciaHasta?: string;
  diasSemana: number[];
  horaDesde?: string;
  horaHasta?: string;
  soloMayoristas?: boolean;
  estado?: EstadoPromocion;
  productos: number[];
  categorias: number[];
}

export interface PromocionEvaluarItem {
  productoId: number;
  cantidad: number;
  precioUnitario: number;
}
export interface PromocionEvaluarRequest {
  clienteId?: number;
  items: PromocionEvaluarItem[];
}
export interface PromocionEvaluacion {
  promocionId: number;
  nombre: string;
  tipo: TipoPromocion;
  estado: EstadoPromocion;
  aplica: boolean;
  motivo: string;
  beneficioEstimado: number;
  valorPct?: number | null;
  valorMonto?: number | null;
  compraMinTotal?: number | null;
  compraMinCantidad?: number | null;
  maxUsosTotal?: number | null;
  maxUsosCliente?: number | null;
  usosActual: number;
  vigenciaDesde: string;
  vigenciaHasta?: string | null;
  diasSemana: number[];
  horaDesde?: string | null;
  horaHasta?: string | null;
  soloMayoristas: boolean;
  productos: number[];
  categorias: number[];
}
