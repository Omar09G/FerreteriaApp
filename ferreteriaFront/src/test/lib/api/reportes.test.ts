import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiCierreDiario,
  apiDashboard,
  apiHorasPico,
  apiMejoresCategorias,
  apiMejoresClientes,
  apiMejoresDias,
  apiMejoresVendedores,
  apiMovimientos,
  apiProductosSinMovimiento,
  apiStock,
  apiTopProductos,
  apiVentasTotales,
} from "@/lib/api/reportes";
import { ApiError } from "@/lib/api/errors";
import type {
  MovimientoInventario,
  PageEnvelope,
  ResumenDashboard,
} from "@/lib/api/types";

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
};

const mock = http as unknown as HttpMock;

const INICIO = "2026-09-01";
const FIN = "2026-09-30";
const RANGO = { fechaInicio: INICIO, fechaFin: FIN };

beforeEach(() => {
  vi.clearAllMocks();
});

describe("reportes de rango de fechas", () => {
  it("apiDashboard pide GET con rango y devuelve data", async () => {
    const dash = { ventasEnRango: 10 } as unknown as ResumenDashboard;
    mock.get.mockResolvedValueOnce({ data: { success: true, data: dash } });
    await expect(apiDashboard(INICIO, FIN)).resolves.toEqual(dash);
    expect(mock.get).toHaveBeenCalledWith("/reportes/dashboard", {
      params: RANGO,
    });
  });

  it("apiVentasTotales pide su endpoint con rango", async () => {
    const rows = [{ fecha: INICIO, totalVendido: 100 }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: rows } });
    await expect(apiVentasTotales(INICIO, FIN)).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/ventas-totales", {
      params: RANGO,
    });
  });

  it("apiTopProductos pide su endpoint con rango", async () => {
    const rows = [{ productoId: 1 }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: rows } });
    await expect(apiTopProductos(INICIO, FIN)).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/top-productos", {
      params: RANGO,
    });
  });

  it("apiHorasPico pide su endpoint con rango", async () => {
    const rows = [{ hora: 12 }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: rows } });
    await expect(apiHorasPico(INICIO, FIN)).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/horas-pico", {
      params: RANGO,
    });
  });

  it("apiMejoresDias pide su endpoint con rango", async () => {
    const rows = [{ diaNum: 1 }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: rows } });
    await expect(apiMejoresDias(INICIO, FIN)).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/mejores-dias", {
      params: RANGO,
    });
  });

  it("apiMejoresClientes pide su endpoint con rango", async () => {
    const rows = [{ clienteId: 1 }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: rows } });
    await expect(apiMejoresClientes(INICIO, FIN)).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/mejores-clientes", {
      params: RANGO,
    });
  });

  it("apiMejoresVendedores pide su endpoint con rango", async () => {
    const rows = [{ usuarioId: 1 }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: rows } });
    await expect(apiMejoresVendedores(INICIO, FIN)).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/mejores-vendedores", {
      params: RANGO,
    });
  });

  it("apiCierreDiario pide su endpoint con rango", async () => {
    const rows = [{ fecha: INICIO }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: rows } });
    await expect(apiCierreDiario(INICIO, FIN)).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/cierre-diario", {
      params: RANGO,
    });
  });

  it("apiMejoresCategorias pide su endpoint con rango", async () => {
    const rows = [{ categoriaId: 1 }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: rows } });
    await expect(apiMejoresCategorias(INICIO, FIN)).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith("/reportes/mejores-categorias", {
      params: RANGO,
    });
  });

  it("apiProductosSinMovimiento pide GET sin params", async () => {
    const rows = [{ productoId: 9 }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: rows } });
    await expect(apiProductosSinMovimiento()).resolves.toEqual(rows);
    expect(mock.get).toHaveBeenCalledWith(
      "/reportes/productos-sin-movimiento",
    );
  });

  it("propaga ApiError en reportes", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 403,
      codigo: "ACCESO_DENEGADO",
      errorMessage: "x",
    });
    mock.get.mockRejectedValueOnce(err);
    await expect(apiDashboard(INICIO, FIN)).rejects.toBe(err);
  });
});

describe("apiMovimientos", () => {
  it("mapea inicio/fin a fechaInicio/fechaFin más filtros", async () => {
    const mov = { movimientoId: 1 } as unknown as MovimientoInventario;
    const pag: PageEnvelope<MovimientoInventario> = {
      success: true,
      data: [mov],
      meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
    };
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiMovimientos({
        inicio: INICIO,
        fin: FIN,
        productoId: 1,
        almacenId: 2,
        sort: "creadoEn,desc",
        page: 0,
        size: 10,
      }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/movimientos", {
      params: {
        fechaInicio: INICIO,
        fechaFin: FIN,
        page: 0,
        size: 10,
        productoId: 1,
        almacenId: 2,
        sort: "creadoEn,desc",
      },
    });
  });

  it("omite filtros opcionales ausentes", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [], meta: {} },
    });
    await apiMovimientos({ inicio: INICIO, fin: FIN, page: 0, size: 10 });
    expect(mock.get).toHaveBeenCalledWith("/movimientos", {
      params: { fechaInicio: INICIO, fechaFin: FIN, page: 0, size: 10 },
    });
  });
});

describe("apiStock", () => {
  it("envía almacenId y soloBajoStock cuando se dan", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [], meta: {} },
    });
    await apiStock({ almacenId: 1, soloBajoStock: true, page: 0, size: 20 });
    expect(mock.get).toHaveBeenCalledWith("/inventario", {
      params: { page: 0, size: 20, almacenId: 1, soloBajoStock: true },
    });
  });

  it("omite opcionales ausentes", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [], meta: {} },
    });
    await apiStock({ page: 0, size: 20 });
    expect(mock.get).toHaveBeenCalledWith("/inventario", {
      params: { page: 0, size: 20 },
    });
  });

  it("propaga errores", async () => {
    const err = new Error("red caída");
    mock.get.mockRejectedValueOnce(err);
    await expect(apiStock({ page: 0, size: 5 })).rejects.toBe(err);
  });
});
