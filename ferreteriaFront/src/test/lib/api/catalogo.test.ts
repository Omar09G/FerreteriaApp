import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiActualizarAlmacen,
  apiActualizarCliente,
  apiActualizarEstadoAlmacen,
  apiActualizarProducto,
  apiActualizarProveedor,
  apiAlmacenes,
  apiAlmacenesTodos,
  apiBuscarProductos,
  apiCargaMasivaProductos,
  apiCategoriasArbol,
  apiClientes,
  apiCrearAlmacen,
  apiCrearCliente,
  apiCrearProducto,
  apiCrearProveedor,
  apiEliminarCliente,
  apiEliminarProducto,
  apiEliminarProveedor,
  apiGetCliente,
  apiMarcas,
  apiProductos,
  apiProveedores,
  apiProveedoresPaginado,
  apiUnidadesMedida,
} from "@/lib/api/catalogo";
import { ApiError } from "@/lib/api/errors";
import type {
  Almacen,
  Cliente,
  Marca,
  PageEnvelope,
  Producto,
  Proveedor,
  UnidadMedida,
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
  put: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
};

const mock = http as unknown as HttpMock;

const producto = { productoId: 1, nombre: "Martillo" } as unknown as Producto;
const cliente = { clienteId: 2, razonSocial: "Juan" } as unknown as Cliente;
const almacen = { almacenId: 1, nombre: "Central" } as unknown as Almacen;
const proveedor = { proveedorId: 3, razonSocial: "Truper" } as unknown as Proveedor;
const marca = { marcaId: 1, nombre: "Truper" } as unknown as Marca;
const unidad = { unidadId: 1, clave: "PZA" } as unknown as UnidadMedida;

function pagina<T>(rows: T[]): PageEnvelope<T> {
  return {
    success: true,
    data: rows,
    meta: { page: 0, size: 15, totalElements: rows.length, totalPages: 1 },
  };
}

function apiError(codigo = "ERROR_INTERNO"): ApiError {
  return new ApiError({
    success: false,
    data: null,
    errorCode: 500,
    codigo,
    errorMessage: codigo,
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("catalogo: productos", () => {
  it("apiProductos envía todos los filtros", async () => {
    const pag = pagina([producto]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiProductos({
        q: "martillo",
        categoriaId: 4,
        tipo: "PRODUCTO",
        sort: "nombre,asc",
        almacenId: 1,
        page: 0,
        size: 15,
      }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/productos", {
      params: {
        page: 0,
        size: 15,
        q: "martillo",
        categoriaId: 4,
        tipo: "PRODUCTO",
        sort: "nombre,asc",
        almacenId: 1,
      },
    });
  });

  it("apiProductos omite filtros ausentes", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([]) });
    await apiProductos({ page: 0, size: 15 });
    expect(mock.get).toHaveBeenCalledWith("/productos", {
      params: { page: 0, size: 15 },
    });
  });

  it("apiBuscarProductos pide GET /productos/buscar con q y límite", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: [producto] } });
    await expect(
      apiBuscarProductos({ q: "torni", limite: 8, almacenId: 1 }),
    ).resolves.toEqual([producto]);
    expect(mock.get).toHaveBeenCalledWith("/productos/buscar", {
      params: { q: "torni", limite: 8, almacenId: 1 },
    });
  });

  it("apiBuscarProductos omite opcionales ausentes", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: [] } });
    await expect(apiBuscarProductos({ q: "torni" })).resolves.toEqual([]);
    expect(mock.get).toHaveBeenCalledWith("/productos/buscar", {
      params: { q: "torni" },
    });
  });

  it("apiCrearProducto hace POST /productos", async () => {
    const body = { tipo: "PRODUCTO" as const, nombre: "Martillo", categoriaId: 4, unidadMedidaId: 1 };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: producto } });
    await expect(apiCrearProducto(body)).resolves.toEqual(producto);
    expect(mock.post).toHaveBeenCalledWith("/productos", body);
  });

  it("apiActualizarProducto hace PUT /productos/:id", async () => {
    const body = { tipo: "PRODUCTO" as const, nombre: "Martillo Pro", categoriaId: 4, unidadMedidaId: 1 };
    mock.put.mockResolvedValueOnce({ data: { success: true, data: producto } });
    await expect(apiActualizarProducto(1, body)).resolves.toEqual(producto);
    expect(mock.put).toHaveBeenCalledWith("/productos/1", body);
  });

  it("apiEliminarProducto hace DELETE y resuelve void", async () => {
    mock.delete.mockResolvedValueOnce({ data: {} });
    await expect(apiEliminarProducto(1)).resolves.toBeUndefined();
    expect(mock.delete).toHaveBeenCalledWith("/productos/1");
  });

  it("apiCargaMasivaProductos envuelve items", async () => {
    const res = { creados: [producto], errores: [] };
    const items = [{ tipo: "PRODUCTO" as const, nombre: "X", categoriaId: 1, unidadMedidaId: 1 }];
    mock.post.mockResolvedValueOnce({ data: { success: true, data: res } });
    await expect(apiCargaMasivaProductos(items)).resolves.toEqual(res);
    expect(mock.post).toHaveBeenCalledWith("/productos/carga-masiva", {
      items,
    });
  });

  it("propaga error de duplicado al crear", async () => {
    const err = apiError("VALOR_DUPLICADO");
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiCrearProducto({ tipo: "PRODUCTO", nombre: "X", categoriaId: 1, unidadMedidaId: 1 }),
    ).rejects.toBe(err);
  });
});

describe("catalogo: auxiliares de producto", () => {
  it("apiCategoriasArbol pide GET /categorias/arbol", async () => {
    const arbol = [{ categoriaId: 1, nombre: "Herramienta" }];
    mock.get.mockResolvedValueOnce({ data: { success: true, data: arbol } });
    await expect(apiCategoriasArbol()).resolves.toEqual(arbol);
    expect(mock.get).toHaveBeenCalledWith("/categorias/arbol");
  });

  it("apiMarcas pide page 0 size 99 y devuelve el arreglo", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([marca]) });
    await expect(apiMarcas()).resolves.toEqual([marca]);
    expect(mock.get).toHaveBeenCalledWith("/marcas", {
      params: { page: 0, size: 99 },
    });
  });

  it("apiUnidadesMedida pide page 0 size 100 y devuelve el arreglo", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([unidad]) });
    await expect(apiUnidadesMedida()).resolves.toEqual([unidad]);
    expect(mock.get).toHaveBeenCalledWith("/unidades-medida", {
      params: { page: 0, size: 100 },
    });
  });

  it("propaga errores en auxiliares", async () => {
    const err = apiError();
    mock.get.mockRejectedValueOnce(err);
    await expect(apiMarcas()).rejects.toBe(err);
  });
});

describe("catalogo: clientes", () => {
  it("apiClientes envía filtros opcionales", async () => {
    const pag = pagina([cliente]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiClientes({ q: "juan", sort: "razonSocial,asc", almacenId: 1, page: 0, size: 10 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/clientes", {
      params: { page: 0, size: 10, q: "juan", sort: "razonSocial,asc", almacenId: 1 },
    });
  });

  it("apiCrearCliente hace POST /clientes", async () => {
    const body = { razonSocial: "Juan Pérez" };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: cliente } });
    await expect(apiCrearCliente(body)).resolves.toEqual(cliente);
    expect(mock.post).toHaveBeenCalledWith("/clientes", body);
  });

  it("apiActualizarCliente hace PUT /clientes/:id", async () => {
    const body = { razonSocial: "Juan P." };
    mock.put.mockResolvedValueOnce({ data: { success: true, data: cliente } });
    await expect(apiActualizarCliente(2, body)).resolves.toEqual(cliente);
    expect(mock.put).toHaveBeenCalledWith("/clientes/2", body);
  });

  it("apiEliminarCliente hace DELETE y resuelve void", async () => {
    mock.delete.mockResolvedValueOnce({ data: {} });
    await expect(apiEliminarCliente(2)).resolves.toBeUndefined();
    expect(mock.delete).toHaveBeenCalledWith("/clientes/2");
  });

  it("apiGetCliente pide GET /clientes/:id", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: cliente } });
    await expect(apiGetCliente(2)).resolves.toEqual(cliente);
    expect(mock.get).toHaveBeenCalledWith("/clientes/2");
  });

  it("propaga error al obtener cliente inexistente", async () => {
    const err = apiError("RECURSO_NO_ENCONTRADO");
    mock.get.mockRejectedValueOnce(err);
    await expect(apiGetCliente(999)).rejects.toBe(err);
  });
});

describe("catalogo: almacenes", () => {
  it("apiAlmacenes pide page 0 size 50 y devuelve el arreglo", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([almacen]) });
    await expect(apiAlmacenes()).resolves.toEqual([almacen]);
    expect(mock.get).toHaveBeenCalledWith("/almacenes", {
      params: { page: 0, size: 50 },
    });
  });

  it("apiAlmacenesTodos pide con todos=true", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([almacen]) });
    await expect(apiAlmacenesTodos()).resolves.toEqual([almacen]);
    expect(mock.get).toHaveBeenCalledWith("/almacenes", {
      params: { todos: true, page: 0, size: 100 },
    });
  });

  it("apiCrearAlmacen hace POST /almacenes", async () => {
    const req = { nombre: "Sucursal" };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: almacen } });
    await expect(apiCrearAlmacen(req)).resolves.toEqual(almacen);
    expect(mock.post).toHaveBeenCalledWith("/almacenes", req);
  });

  it("apiActualizarAlmacen hace PUT /almacenes/:id", async () => {
    const body = { nombre: "Central" };
    mock.put.mockResolvedValueOnce({ data: { success: true, data: almacen } });
    await expect(apiActualizarAlmacen(1, body)).resolves.toEqual(almacen);
    expect(mock.put).toHaveBeenCalledWith("/almacenes/1", body);
  });

  it("apiActualizarEstadoAlmacen hace PUT con activo", async () => {
    mock.put.mockResolvedValueOnce({ data: { success: true, data: almacen } });
    await expect(apiActualizarEstadoAlmacen(1, false)).resolves.toEqual(almacen);
    expect(mock.put).toHaveBeenCalledWith("/almacenes/1/estado", {
      activo: false,
    });
  });
});

describe("catalogo: proveedores", () => {
  it("apiProveedores envía q cuando se da", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([proveedor]) });
    await expect(apiProveedores("tru")).resolves.toEqual([proveedor]);
    expect(mock.get).toHaveBeenCalledWith("/proveedores", {
      params: { page: 0, size: 100, q: "tru" },
    });
  });

  it("apiProveedores sin q omite el parámetro", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([proveedor]) });
    await apiProveedores();
    expect(mock.get).toHaveBeenCalledWith("/proveedores", {
      params: { page: 0, size: 100 },
    });
  });

  it("apiProveedoresPaginado respeta page/size/q", async () => {
    const pag = pagina([proveedor]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiProveedoresPaginado({ q: "tru", page: 1, size: 5 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/proveedores", {
      params: { page: 1, size: 5, q: "tru" },
    });
  });

  it("apiCrearProveedor hace POST /proveedores", async () => {
    const body = { razonSocial: "Truper" };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: proveedor } });
    await expect(apiCrearProveedor(body)).resolves.toEqual(proveedor);
    expect(mock.post).toHaveBeenCalledWith("/proveedores", body);
  });

  it("apiActualizarProveedor hace PUT /proveedores/:id", async () => {
    const body = { razonSocial: "Truper SA" };
    mock.put.mockResolvedValueOnce({ data: { success: true, data: proveedor } });
    await expect(apiActualizarProveedor(3, body)).resolves.toEqual(proveedor);
    expect(mock.put).toHaveBeenCalledWith("/proveedores/3", body);
  });

  it("apiEliminarProveedor hace DELETE y resuelve void", async () => {
    mock.delete.mockResolvedValueOnce({ data: {} });
    await expect(apiEliminarProveedor(3)).resolves.toBeUndefined();
    expect(mock.delete).toHaveBeenCalledWith("/proveedores/3");
  });

  it("propaga errores en proveedores", async () => {
    const err = apiError("VALOR_DUPLICADO");
    mock.post.mockRejectedValueOnce(err);
    await expect(apiCrearProveedor({ razonSocial: "X" })).rejects.toBe(err);
  });
});
