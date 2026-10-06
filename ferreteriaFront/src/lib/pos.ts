export interface LineaVenta {
  productoId: number;
  cantidad: number;
  precioUnitario: number;
  aplicaIva: boolean;
}

export interface ResumenVenta {
  total: number;
  subtotalSinIva: number;
  ivaEstimado: number;
}

const IVA_TASA = 0.16;

/**
 * Totales estimados del ticket (el backend recalcula los definitivos por
 * trigger). El total arranca en 0: jamás se suma el número de líneas.
 */
export function resumenVenta(lineas: LineaVenta[]): ResumenVenta {
  let total = 0;
  let subtotalSinIva = 0;
  let ivaEstimado = 0;
  for (const l of lineas) {
    const importe = l.cantidad * l.precioUnitario;
    total += importe;
    if (l.aplicaIva) {
      const base = importe / (1 + IVA_TASA);
      subtotalSinIva += base;
      ivaEstimado += importe - base;
    } else {
      subtotalSinIva += importe;
    }
  }
  return { total, subtotalSinIva, ivaEstimado };
}
