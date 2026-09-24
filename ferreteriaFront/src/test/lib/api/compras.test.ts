import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiAbonarCuentaPagar,
  apiCompras,
  apiCrearCompra,
  apiCuentasPagar,
  apiFacturasPendientes,
  apiFacturasVencidas,
} from "@/lib/api/compras";
import { ApiError } from "@/lib/api/errors";
import type { Compra, PageEnvelope } from "@/lib/api/types";

vi.mock("@/lib/api/client", () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

import http from "@/lib/api/client";

type HttpMock = {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
};

const mock = http as unknown as HttpMock;

const compra = { compraId: 1, folio: "C-001" } as unknown as Compra;

beforeEach(() => {
  vi.clearAllMocks();
});

describe("apiCompras", () => {
  it("envía todos los filtros presentes", async () => {
    const pag: PageEnvelope<Compra> = {
      success: true,
      data: [compra],
      meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
    };
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiCompras({
        almacenId: 1,
        proveedorId: 3,
        desde: "2026-09-01",
        hasta: "2026-09-30",
        page: 0,
        size: 10,
      }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/compras", {
      params: {
        page: 0,
        size: 10,
        almacenId: 1,
        proveedorId: 3,
        desde: "2026-09-01",
        hasta: "2026-09-30",
      },
    });
  });

  it("omite filtros ausentes", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [], meta: {} },
    });
    await apiCompras({ page: 0, size: 10 });
    expect(mock.get).toHaveBeenCalledWith("/compras", {
      params: { page: 0, size: 10 },
    });
  });

  it("propaga ApiError", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 500,
      codigo: "ERROR_INTERNO",
      errorMessage: "x",
    });
    mock.get.mockRejectedValueOnce(err);
    await expect(apiCompras({ page: 0, size: 10 })).rejects.toBe(err);
  });
});

describe("apiCrearCompra", () => {
  it("hace POST /compras con el body y devuelve data", async () => {
    const body = {
      proveedorId: 3,
      almacenId: 1,
      formaPagoId: 1,
      cajaId: 1,
      detalles: [{ productoId: 1, cantidad: 10, costoUnitario: 50 }],
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: compra } });
    await expect(apiCrearCompra(body)).resolves.toEqual(compra);
    expect(mock.post).toHaveBeenCalledWith("/compras", body);
  });

  it("propaga error de validación", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 400,
      codigo: "DATOS_INVALIDOS",
      errorMessage: "detalle vacío",
    });
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiCrearCompra({
        proveedorId: 3,
        almacenId: 1,
        formaPagoId: 1,
        cajaId: 1,
        detalles: [],
      }),
    ).rejects.toBe(err);
  });
});

describe("cuentas por pagar y facturas", () => {
  it("apiCuentasPagar pide GET /cuentas-pagar", async () => {
    const cuentas = [{ cuentaPagarId: 1, saldo: 500 }];
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: cuentas },
    });
    await expect(apiCuentasPagar()).resolves.toEqual(cuentas);
    expect(mock.get).toHaveBeenCalledWith("/cuentas-pagar");
  });

  it("apiFacturasPendientes pide el reporte correspondiente", async () => {
    const rows = [{ cuentaPagarId: 1 }];
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: rows },
    });
    await expect(apiFacturasPendientes()).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/facturas-pendientes");
  });

  it("apiFacturasVencidas pide el reporte correspondiente", async () => {
    const rows = [{ cuentaPagarId: 2 }];
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: rows },
    });
    await expect(apiFacturasVencidas()).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/facturas-vencidas");
  });

  it("apiAbonarCuentaPagar hace POST anidado", async () => {
    const body = { monto: 200, formaPagoId: 1, cajaId: 1 };
    const res = { pagoProveedorId: 7, saldo: 300 };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: res } });
    await expect(apiAbonarCuentaPagar(1, body)).resolves.toEqual(res);
    expect(mock.post).toHaveBeenCalledWith("/cuentas-pagar/1/abonos", body);
  });

  it("propaga errores en cuentas por pagar", async () => {
    const err = new Error("red caída");
    mock.get.mockRejectedValueOnce(err);
    await expect(apiCuentasPagar()).rejects.toBe(err);
  });
});
