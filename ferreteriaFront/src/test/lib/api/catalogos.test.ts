import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiCatalogoActualizar,
  apiCatalogoCrear,
  apiCatalogoDatos,
  apiCatalogoEliminar,
  apiCatalogoOpciones,
  apiCatalogosPaneles,
} from "@/lib/api/catalogos";
import { ApiError } from "@/lib/api/errors";

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

beforeEach(() => {
  vi.clearAllMocks();
});

describe("apiCatalogosPaneles", () => {
  it("pide GET /catalogos y devuelve descriptores", async () => {
    const paneles = [{ clave: "estados", tabla: "cat.estados" }];
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: paneles },
    });
    await expect(apiCatalogosPaneles()).resolves.toEqual(paneles);
    expect(mock.get).toHaveBeenCalledWith("/catalogos");
  });

  it("propaga errores", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 403,
      codigo: "ACCESO_DENEGADO",
      errorMessage: "x",
    });
    mock.get.mockRejectedValueOnce(err);
    await expect(apiCatalogosPaneles()).rejects.toBe(err);
  });
});

describe("apiCatalogoDatos", () => {
  it("traduce filas DTO a claves descriptor con __pk", async () => {
    mock.get.mockResolvedValueOnce({
      data: {
        success: true,
        data: [
          {
            estadoId: 1,
            claveInegi: "01",
            nombre: "Aguascalientes",
            campoExtra: "ignorado",
          },
        ],
        meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
      },
    });
    const res = await apiCatalogoDatos({
      clave: "estados",
      sort: "nombre,asc",
      page: 0,
      size: 10,
    });
    expect(mock.get).toHaveBeenCalledWith("/estados", {
      params: { page: 0, size: 10, sort: "nombre,asc" },
    });
    expect(res.data).toEqual([
      { estado_id: 1, clave_inegi: "01", nombre: "Aguascalientes", __pk: 1 },
    ]);
    expect(res.meta.totalElements).toBe(1);
  });

  it("clave desconocida devuelve página vacía sin llamar HTTP", async () => {
    const res = await apiCatalogoDatos({
      clave: "inexistente",
      page: 0,
      size: 10,
    });
    expect(mock.get).not.toHaveBeenCalled();
    expect(res).toEqual({
      success: true,
      data: [],
      meta: { totalElements: 0, totalPages: 0, page: 0, size: 10 },
    });
  });

  it("tolera data nula del backend", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: null, meta: {} },
    });
    const res = await apiCatalogoDatos({ clave: "estados", page: 0, size: 5 });
    expect(res.data).toEqual([]);
  });

  it("propaga errores HTTP", async () => {
    const err = new Error("500");
    mock.get.mockRejectedValueOnce(err);
    await expect(
      apiCatalogoDatos({ clave: "estados", page: 0, size: 5 }),
    ).rejects.toBe(err);
  });
});

describe("apiCatalogoOpciones", () => {
  it("pide GET /catalogos/:clave/opciones con campo y size", async () => {
    const opts = [{ clave: "01", descripcion: "A" }];
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: opts },
    });
    await expect(apiCatalogoOpciones("estados", "nombre")).resolves.toEqual(
      opts,
    );
    expect(mock.get).toHaveBeenCalledWith("/catalogos/estados/opciones", {
      params: { campo: "nombre", size: 500 },
    });
  });
});

describe("apiCatalogoCrear / Actualizar / Eliminar", () => {
  it("crear traduce payload descriptor→DTO y hace POST", async () => {
    mock.post.mockResolvedValueOnce({ data: {} });
    await apiCatalogoCrear("estados", {
      clave_inegi: "02",
      nombre: "Baja California",
      basura: "ignorada",
    });
    expect(mock.post).toHaveBeenCalledWith("/estados", {
      claveInegi: "02",
      nombre: "Baja California",
    });
  });

  it("actualizar hace PUT a path/:id con DTO", async () => {
    mock.put.mockResolvedValueOnce({ data: {} });
    await apiCatalogoActualizar("estados", 1, { nombre: "Ags" });
    expect(mock.put).toHaveBeenCalledWith("/estados/1", { nombre: "Ags" });
  });

  it("eliminar hace DELETE a path/:id", async () => {
    mock.delete.mockResolvedValueOnce({ data: {} });
    await apiCatalogoEliminar("estados", 1);
    expect(mock.delete).toHaveBeenCalledWith("/estados/1");
  });

  it("clave desconocida no llama HTTP en crear/actualizar/eliminar", async () => {
    await apiCatalogoCrear("inexistente", { a: 1 });
    await apiCatalogoActualizar("inexistente", 1, { a: 1 });
    await apiCatalogoEliminar("inexistente", 1);
    expect(mock.post).not.toHaveBeenCalled();
    expect(mock.put).not.toHaveBeenCalled();
    expect(mock.delete).not.toHaveBeenCalled();
  });

  it("propaga errores al crear", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 409,
      codigo: "VALOR_DUPLICADO",
      errorMessage: "duplicado",
    });
    mock.post.mockRejectedValueOnce(err);
    await expect(apiCatalogoCrear("estados", { nombre: "X" })).rejects.toBe(
      err,
    );
  });
});
