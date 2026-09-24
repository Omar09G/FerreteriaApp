import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiCrearFactura,
  apiFacturaXml,
  apiFacturas,
} from "@/lib/api/fis";
import { ApiError } from "@/lib/api/errors";
import type { FacturaFis, PageEnvelope } from "@/lib/api/types";

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

const factura = { facturaId: 1, folio: "F-001" } as unknown as FacturaFis;

beforeEach(() => {
  vi.clearAllMocks();
});

describe("apiFacturas", () => {
  it("envía tipo cuando se da", async () => {
    const pag: PageEnvelope<FacturaFis> = {
      success: true,
      data: [factura],
      meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
    };
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiFacturas({ tipo: "INGRESO", page: 0, size: 10 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/facturas", {
      params: { page: 0, size: 10, tipo: "INGRESO" },
    });
  });

  it("omite tipo cuando no se da", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [], meta: {} },
    });
    await apiFacturas({ page: 0, size: 10 });
    expect(mock.get).toHaveBeenCalledWith("/facturas", {
      params: { page: 0, size: 10 },
    });
  });

  it("propaga ApiError", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 403,
      codigo: "ACCESO_DENEGADO",
      errorMessage: "x",
    });
    mock.get.mockRejectedValueOnce(err);
    await expect(apiFacturas({ page: 0, size: 10 })).rejects.toBe(err);
  });
});

describe("apiCrearFactura", () => {
  it("hace POST /facturas y devuelve data", async () => {
    const body = {
      tipo: "INGRESO",
      folio: "F-001",
      emisorRfc: "AAA010101AAA",
      receptorRfc: "BBB010101BBB",
      subtotal: 100,
      iva: 16,
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: factura } });
    await expect(apiCrearFactura(body)).resolves.toEqual(factura);
    expect(mock.post).toHaveBeenCalledWith("/facturas", body);
  });

  it("propaga errores de validación", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 400,
      codigo: "DATOS_INVALIDOS",
      errorMessage: "rfc inválido",
    });
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiCrearFactura({
        tipo: "INGRESO",
        folio: "F",
        emisorRfc: "X",
        receptorRfc: "Y",
        subtotal: 0,
        iva: 0,
      }),
    ).rejects.toBe(err);
  });
});

describe("apiFacturaXml", () => {
  it("pide GET /facturas/:id/xml", async () => {
    const xml = { facturaId: 1, cfdiXml: "<cfdi/>" };
    mock.get.mockResolvedValueOnce({ data: { success: true, data: xml } });
    await expect(apiFacturaXml(1)).resolves.toEqual(xml);
    expect(mock.get).toHaveBeenCalledWith("/facturas/1/xml");
  });

  it("propaga error cuando no existe", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 404,
      codigo: "RECURSO_NO_ENCONTRADO",
      errorMessage: "x",
    });
    mock.get.mockRejectedValueOnce(err);
    await expect(apiFacturaXml(999)).rejects.toBe(err);
  });
});
