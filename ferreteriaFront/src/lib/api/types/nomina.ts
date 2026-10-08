// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Nómina ─────────────────────────────────────────────────────── */

export interface Nomina {
  nominaId: number;
  empleadoId: number;
  empleado: string;
  periodoIni: string;
  periodoFin: string;
  diasPagados: number;
  percepciones: number;
  deducciones: number;
  netoPagar: number;
  estado: string;
  fechaPago: string | null;
  usuarioRegistraId: number;
  notas: string | null;
}

export interface NominaRequest {
  empleadoId: number;
  periodoIni: string;
  periodoFin: string;
  diasPagados: number;
  percepciones: number;
  deducciones: number;
  notas?: string;
}
