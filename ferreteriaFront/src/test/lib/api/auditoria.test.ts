import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiAuditoria, apiTablasAuditoria } from "@/lib/api/auditoria";
import { ApiError } from "@/lib/api/errors";
import type { Auditoria, PageEnvelope } from "@/lib/api/types";

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

const fila = { auditoriaId: 1, tabla: "ventas" } as unknown as Auditoria;

beforeEach(() => {
  vi.clearAllMocks();
});

describe("apiAuditoria", () => {
  it("envía page/size más todos los filtros presentes", async () => {
    const pag: PageEnvelope<Auditoria> = {
      success: true,
      data: [fila],
      meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
    };
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiAuditoria({
        esquema: "ven",
        tabla: "ventas",
        accion: "INSERT",
        usuario: "admin",
        registroId: 5,
        fechaInicio: "2026-09-01",
        fechaFin: "2026-09-30",
        texto: "folio",
        sort: "creadoEn,desc",
        page: 0,
        size: 10,
      }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/auditoria", {
      params: {
        page: 0,
        size: 10,
        esquema: "ven",
        tabla: "ventas",
        accion: "INSERT",
        usuario: "admin",
        registroId: 5,
        fechaInicio: "2026-09-01",
        fechaFin: "2026-09-30",
        texto: "folio",
        sort: "creadoEn,desc",
      },
    });
  });

  it("omite filtros ausentes y acepta registroId 0", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [], meta: {} },
    });
    await apiAuditoria({ registroId: 0, page: 1, size: 5 });
    expect(mock.get).toHaveBeenCalledWith("/auditoria", {
      params: { page: 1, size: 5, registroId: 0 },
    });
  });

  it("propaga ApiError del backend", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 403,
      codigo: "ACCESO_DENEGADO",
      errorMessage: "sin permiso",
    });
    mock.get.mockRejectedValueOnce(err);
    await expect(apiAuditoria({ page: 0, size: 10 })).rejects.toBe(err);
  });
});

describe("apiTablasAuditoria", () => {
  it("pide GET /auditoria/tablas y devuelve data", async () => {
    const tablas = [{ esquema: "ven", tabla: "ventas" }];
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: tablas },
    });
    await expect(apiTablasAuditoria()).resolves.toEqual(tablas);
    expect(mock.get).toHaveBeenCalledWith("/auditoria/tablas");
  });

  it("propaga errores", async () => {
    const err = new Error("red caída");
    mock.get.mockRejectedValueOnce(err);
    await expect(apiTablasAuditoria()).rejects.toBe(err);
  });
});
