import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiConteos,
  apiCrearConteo,
  apiCrearMovimiento,
  apiCrearTraslado,
  apiTraslados,
} from "@/lib/api/inventario";
import { ApiError } from "@/lib/api/errors";
import type {
  ConteoFisico,
  MovimientoInventario,
  PageEnvelope,
  Traslado,
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
  post: ReturnType<typeof vi.fn>;
};

const mock = http as unknown as HttpMock;

const traslado = { trasladoId: 1, folio: "T-001" } as unknown as Traslado;
const conteo = { conteoId: 2, estado: "ABIERTO" } as unknown as ConteoFisico;
const movimiento = { movimientoId: 3, tipo: "ENTRADA" } as unknown as MovimientoInventario;

beforeEach(() => {
  vi.clearAllMocks();
});

describe("traslados", () => {
  it("apiTraslados envía estado cuando se da", async () => {
    const pag: PageEnvelope<Traslado> = {
      success: true,
      data: [traslado],
      meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
    };
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiTraslados({ estado: "PENDIENTE", page: 0, size: 10 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/traslados", {
      params: { page: 0, size: 10, estado: "PENDIENTE" },
    });
  });

  it("apiTraslados omite estado ausente", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [], meta: {} },
    });
    await apiTraslados({ page: 0, size: 10 });
    expect(mock.get).toHaveBeenCalledWith("/traslados", {
      params: { page: 0, size: 10 },
    });
  });

  it("apiCrearTraslado hace POST /traslados", async () => {
    const body = {
      almacenOrigen: 1,
      almacenDestino: 2,
      detalles: [{ productoId: 1, cantidad: 5 }],
    };
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: traslado },
    });
    await expect(apiCrearTraslado(body)).resolves.toEqual(traslado);
    expect(mock.post).toHaveBeenCalledWith("/traslados", body);
  });

  it("propaga errores al crear traslado", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 400,
      codigo: "STOCK_INSUFICIENTE",
      errorMessage: "sin stock",
    });
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiCrearTraslado({
        almacenOrigen: 1,
        almacenDestino: 2,
        detalles: [],
      }),
    ).rejects.toBe(err);
  });
});

describe("conteos físicos", () => {
  it("apiConteos envía todos los filtros presentes", async () => {
    const pag: PageEnvelope<ConteoFisico> = {
      success: true,
      data: [conteo],
      meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
    };
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiConteos({
        almacenId: 1,
        estado: "ABIERTO",
        productoId: 7,
        fechaInicio: "2026-09-01",
        fechaFin: "2026-09-30",
        page: 0,
        size: 10,
      }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/conteos-fisicos", {
      params: {
        page: 0,
        size: 10,
        almacenId: 1,
        estado: "ABIERTO",
        productoId: 7,
        fechaInicio: "2026-09-01",
        fechaFin: "2026-09-30",
      },
    });
  });

  it("apiCrearConteo hace POST /conteos-fisicos", async () => {
    const body = {
      almacenId: 1,
      detalles: [{ productoId: 1, cantidadFisica: 10 }],
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: conteo } });
    await expect(apiCrearConteo(body)).resolves.toEqual(conteo);
    expect(mock.post).toHaveBeenCalledWith("/conteos-fisicos", body);
  });

  it("propaga errores en conteos", async () => {
    const err = new Error("red caída");
    mock.get.mockRejectedValueOnce(err);
    await expect(apiConteos({ page: 0, size: 10 })).rejects.toBe(err);
  });
});

describe("apiCrearMovimiento", () => {
  it("hace POST /movimientos y devuelve data", async () => {
    const body = {
      productoId: 1,
      almacenId: 1,
      tipo: "ENTRADA" as const,
      cantidad: 5,
      motivoId: 6,
    };
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: movimiento },
    });
    await expect(apiCrearMovimiento(body)).resolves.toEqual(movimiento);
    expect(mock.post).toHaveBeenCalledWith("/movimientos", body);
  });

  it("propaga error de stock insuficiente", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 400,
      codigo: "STOCK_INSUFICIENTE",
      errorMessage: "sin stock",
    });
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiCrearMovimiento({
        productoId: 1,
        almacenId: 1,
        tipo: "SALIDA",
        cantidad: 999,
        motivoId: 5,
      }),
    ).rejects.toBe(err);
  });
});
