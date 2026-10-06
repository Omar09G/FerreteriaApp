import { describe, expect, it } from "vitest";

import { resumenVenta } from "@/lib/pos";

describe("resumenVenta (regresión: el total arranca en 0, nunca en N)", () => {
  it("ticket vacío suma 0", () => {
    const r = resumenVenta([]);
    expect(r.total).toBe(0);
    expect(r.subtotalSinIva).toBe(0);
    expect(r.ivaEstimado).toBe(0);
  });

  it("una línea de 1×$50 suma $50 (no $51)", () => {
    expect(
      resumenVenta([
        { productoId: 1, cantidad: 1, precioUnitario: 50, aplicaIva: true },
      ]).total,
    ).toBe(50);
  });

  it("dos líneas suman solo sus importes", () => {
    expect(
      resumenVenta([
        { productoId: 1, cantidad: 1, precioUnitario: 50, aplicaIva: true },
        { productoId: 2, cantidad: 2, precioUnitario: 25, aplicaIva: false },
      ]).total,
    ).toBe(100);
  });
});
