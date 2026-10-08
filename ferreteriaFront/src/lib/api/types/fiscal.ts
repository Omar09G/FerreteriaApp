// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Facturas CFDI ──────────────────────────────────────────────── */

export interface FacturaFis {
  facturaId: number;
  tipo: string;
  serie: string | null;
  folio: string;
  uuid: string | null;
  emisorRfc: string;
  receptorRfc: string;
  subtotal: number;
  iva: number;
  total: number;
  fechaTimbrado: string;
  estado: string;
  ventaId: number | null;
  usuarioId: number;
  creadoEn: string;
}

export interface FacturaFisRequest {
  tipo: string;
  serie?: string;
  folio: string;
  uuid?: string;
  emisorRfc: string;
  receptorRfc: string;
  subtotal: number;
  iva: number;
  cfdiXml?: string;
  ventaId?: number;
}

export interface FacturaXml {
  facturaId: number;
  folio: string;
  uuid: string | null;
  tipo: string;
  cfdiXml: string | null;
}
