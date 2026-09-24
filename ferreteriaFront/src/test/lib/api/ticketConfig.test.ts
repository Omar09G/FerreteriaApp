import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiGetTicketConfig, apiPutTicketConfig } from "@/lib/api/ticketConfig";
import { ApiError } from "@/lib/api/errors";
import type { TicketConfig } from "@/lib/api/types";

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
  put: ReturnType<typeof vi.fn>;
};

const mock = http as unknown as HttpMock;

const config = {
  ticketConfigId: 1,
  nombreNegocio: "Ferretería El Tornillo",
} as unknown as TicketConfig;

beforeEach(() => {
  vi.clearAllMocks();
});

describe("apiGetTicketConfig", () => {
  it("sin almacenId pide GET con params vacío", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: config } });
    await expect(apiGetTicketConfig()).resolves.toEqual(config);
    expect(mock.get).toHaveBeenCalledWith("/configuracion/ticket", {
      params: {},
    });
  });

  it("con almacenId lo envía como param", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: config } });
    await expect(apiGetTicketConfig(2)).resolves.toEqual(config);
    expect(mock.get).toHaveBeenCalledWith("/configuracion/ticket", {
      params: { almacenId: 2 },
    });
  });

  it("almacenId null se trata como ausente", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: config } });
    await apiGetTicketConfig(null);
    expect(mock.get).toHaveBeenCalledWith("/configuracion/ticket", {
      params: {},
    });
  });

  it("propaga ApiError", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 404,
      codigo: "RECURSO_NO_ENCONTRADO",
      errorMessage: "x",
    });
    mock.get.mockRejectedValueOnce(err);
    await expect(apiGetTicketConfig()).rejects.toBe(err);
  });
});

describe("apiPutTicketConfig", () => {
  it("hace PUT con el body y devuelve data", async () => {
    const body = { nombreNegocio: "Nuevo nombre", anchoPapelMm: 80 as const };
    mock.put.mockResolvedValueOnce({ data: { success: true, data: config } });
    await expect(apiPutTicketConfig(body)).resolves.toEqual(config);
    expect(mock.put).toHaveBeenCalledWith("/configuracion/ticket", body);
  });

  it("propaga errores de validación", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 400,
      codigo: "DATOS_INVALIDOS",
      errorMessage: "nombre requerido",
    });
    mock.put.mockRejectedValueOnce(err);
    await expect(apiPutTicketConfig({})).rejects.toBe(err);
  });
});
