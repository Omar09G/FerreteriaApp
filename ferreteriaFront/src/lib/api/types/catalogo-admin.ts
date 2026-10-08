// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
// ─── Catálogos genéricos (CRUD de ADMINISTRACIÓN) ───────────────────────────
export type TipoCampo = "TEXT" | "NUMERO" | "DECIMAL" | "BOOLEAN" | "FECHA";

export interface CampoCatalogo {
  nombre: string;
  tipo: TipoCampo;
  requerido: boolean;
  unico: boolean;
  esActivo: boolean;
  clavesEditables: boolean;
  etiqueta: string;
  opcionesTabla?: string | null;
  opcionesColumnas?: string[] | null;
  opcionesValores?: string[] | null;
}

export interface CatalogoDescriptor {
  clave: string;
  tabla: string;
  nombre: string;
  pk: string;
  campos: CampoCatalogo[];
  listasValidas?: Record<string, string[]> | null;
  soportaBajaLogica: boolean;
}

export type FilaCatalogo = Record<string, unknown> & { __pk: string | number };

export const ESTADO_COBRANZA: {
  clave: string;
  nombre: string;
}[] = [
  { clave: "", nombre: "Todos" },
  { clave: "VIGENTE", nombre: "Vigente" },
  { clave: "PARCIAL", nombre: "Parcial" },
  { clave: "LIQUIDADA", nombre: "Liquidada" },
  { clave: "CANCELADO", nombre: "Cancelado" },
];
