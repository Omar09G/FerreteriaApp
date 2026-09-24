import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiActualizarPromocion,
  apiCrearPromocion,
  apiEliminarPromocion,
  apiEvaluarPromociones,
  apiPromocion,
  apiPromociones,
} from "@/lib/api/promociones";
import { ApiError } from "@/lib/api/errors";
import type { PageEnvelope, Promocion } from "@/lib/api/types";

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
  put: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
};

const mock = http as unknown as HttpMock;

const promo = { promocionId: 1, nombre: "2x1" } as unknown as Promocion;

beforeEach(() => {
  vi.clearAllMocks();
});

describe("apiPromociones", () => {
  it("envía todos los filtros presentes", async () => {
    const pag: PageEnvelope<Promocion> = {
      success: true,
      data: [promo],
      meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
    };
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiPromociones({
        nombre: "2x1",
        tipo: "NXM",
        estado: "ACTIVA",
        desde: "2026-09-01",
        hasta: "2026-09-30",
        sort: "nombre,asc",
        page: 0,
        size: 10,
      }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/promociones", {
      params: {
        page: 0,
        size: 10,
        nombre: "2x1",
        tipo: "NXM",
        estado: "ACTIVA",
        desde: "2026-09-01",
        hasta: "2026-09-30",
        sort: "nombre,asc",
      },
    });
  });

  it("omite filtros ausentes", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [], meta: {} },
    });
    await apiPromociones({ page: 0, size: 10 });
    expect(mock.get).toHaveBeenCalledWith("/promociones", {
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
    await expect(apiPromociones({ page: 0, size: 10 })).rejects.toBe(err);
  });
});

describe("CRUD de promoción", () => {
  it("apiPromocion pide GET /promociones/:id", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: promo } });
    await expect(apiPromocion(1)).resolves.toEqual(promo);
    expect(mock.get).toHaveBeenCalledWith("/promociones/1");
  });

  it("apiCrearPromocion hace POST /promociones", async () => {
    const body = {
      nombre: "2x1",
      tipo: "NXM" as const,
      diasSemana: [1, 2, 3],
      productos: [1],
      categorias: [],
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: promo } });
    await expect(apiCrearPromocion(body)).resolves.toEqual(promo);
    expect(mock.post).toHaveBeenCalledWith("/promociones", body);
  });

  it("apiActualizarPromocion hace PUT /promociones/:id", async () => {
    const body = {
      nombre: "2x1",
      tipo: "NXM" as const,
      diasSemana: [1],
      productos: [1],
      categorias: [],
    };
    mock.put.mockResolvedValueOnce({ data: { success: true, data: promo } });
    await expect(apiActualizarPromocion(1, body)).resolves.toEqual(promo);
    expect(mock.put).toHaveBeenCalledWith("/promociones/1", body);
  });

  it("apiEliminarPromocion hace DELETE y resuelve void", async () => {
    mock.delete.mockResolvedValueOnce({ data: {} });
    await expect(apiEliminarPromocion(1)).resolves.toBeUndefined();
    expect(mock.delete).toHaveBeenCalledWith("/promociones/1");
  });

  it("propaga error al eliminar inexistente", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 404,
      codigo: "RECURSO_NO_ENCONTRADO",
      errorMessage: "x",
    });
    mock.delete.mockRejectedValueOnce(err);
    await expect(apiEliminarPromocion(999)).rejects.toBe(err);
  });
});

describe("apiEvaluarPromociones", () => {
  const body = { items: [{ productoId: 1, cantidad: 2, precioUnitario: 100 }] };

  it("devuelve el arreglo cuando el backend responde lista directa", async () => {
    const evals = [{ promocionId: 1, aplica: true }];
    mock.post.mockResolvedValueOnce({ data: evals });
    await expect(apiEvaluarPromociones(body)).resolves.toEqual(evals);
    expect(mock.post).toHaveBeenCalledWith("/promociones/evaluar", body);
  });

  it("desenvuelve { data } cuando el backend responde envelope", async () => {
    const evals = [{ promocionId: 1, aplica: false }];
    mock.post.mockResolvedValueOnce({ data: { data: evals } });
    await expect(apiEvaluarPromociones(body)).resolves.toEqual(evals);
  });

  it("devuelve [] cuando no hay data", async () => {
    mock.post.mockResolvedValueOnce({ data: {} });
    await expect(apiEvaluarPromociones(body)).resolves.toEqual([]);
  });

  it("propaga errores", async () => {
    const err = new Error("red caída");
    mock.post.mockRejectedValueOnce(err);
    await expect(apiEvaluarPromociones(body)).rejects.toBe(err);
  });
});
