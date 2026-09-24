import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiBajaEmpleado,
  apiCancelarNomina,
  apiCrearEmpleado,
  apiCrearNomina,
  apiCrearRol,
  apiCrearUsuario,
  apiActualizarRol,
  apiEliminarRol,
  apiEliminarUsuario,
  apiEmpleados,
  apiGenerarQuincena,
  apiNomina,
  apiPagarNomina,
  apiPagarNominaLote,
  apiPermisos,
  apiPermisosDeRol,
  apiResetPassword,
  apiRoles,
  apiRolesPaginado,
  apiSetPermisosRol,
  apiSetRolesUsuario,
  apiUsuarios,
} from "@/lib/api/admin";
import { ApiError } from "@/lib/api/errors";
import type {
  Empleado,
  Nomina,
  PageEnvelope,
  Permiso,
  Rol,
  Usuario,
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
  patch: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
};

const mock = http as unknown as HttpMock;

const empleado = { empleadoId: 7, nombre: "Juan" } as unknown as Empleado;
const usuario = { usuarioId: 3, username: "cajero1" } as unknown as Usuario;
const rol = { rolId: 2, clave: "GERENTE" } as unknown as Rol;
const permiso = { permisoId: 1, clave: "ventas.leer" } as unknown as Permiso;
const nomina = { nominaId: 9, estado: "PENDIENTE" } as unknown as Nomina;

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

describe("admin: empleados", () => {
  it("apiEmpleados pide GET /empleados con page/size", async () => {
    const pag = pagina([empleado]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(apiEmpleados(0, 15)).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/empleados", {
      params: { page: 0, size: 15 },
    });
  });

  it("apiCrearEmpleado hace POST /empleados y devuelve data", async () => {
    const body = { puestoId: 4, nombre: "Ana", apellidoPaterno: "López" };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: empleado } });
    await expect(apiCrearEmpleado(body)).resolves.toEqual(empleado);
    expect(mock.post).toHaveBeenCalledWith("/empleados", body);
  });

  it("apiBajaEmpleado hace DELETE y resuelve void", async () => {
    mock.delete.mockResolvedValueOnce({ data: {} });
    await expect(apiBajaEmpleado(7)).resolves.toBeUndefined();
    expect(mock.delete).toHaveBeenCalledWith("/empleados/7");
  });

  it("propaga ApiError en apiEmpleados", async () => {
    const err = apiError("RECURSO_NO_ENCONTRADO");
    mock.get.mockRejectedValueOnce(err);
    await expect(apiEmpleados(0)).rejects.toBe(err);
  });
});

describe("admin: usuarios", () => {
  it("apiUsuarios pide GET /usuarios con page/size", async () => {
    const pag = pagina([usuario]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(apiUsuarios(1, 10)).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/usuarios", {
      params: { page: 1, size: 10 },
    });
  });

  it("apiCrearUsuario hace POST /usuarios", async () => {
    const body = { username: "nuevo", email: "n@t.com", password: "Secreta1!" };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: usuario } });
    await expect(apiCrearUsuario(body)).resolves.toEqual(usuario);
    expect(mock.post).toHaveBeenCalledWith("/usuarios", body);
  });

  it("apiSetRolesUsuario hace PUT con roles", async () => {
    mock.put.mockResolvedValueOnce({ data: { success: true, data: usuario } });
    await expect(apiSetRolesUsuario(3, ["GERENTE"])).resolves.toEqual(usuario);
    expect(mock.put).toHaveBeenCalledWith("/usuarios/3/roles", {
      roles: ["GERENTE"],
    });
  });

  it("apiResetPassword hace PATCH con nuevaPassword", async () => {
    mock.patch.mockResolvedValueOnce({
      data: { success: true, data: usuario },
    });
    await expect(apiResetPassword(3, "Nueva123!")).resolves.toEqual(usuario);
    expect(mock.patch).toHaveBeenCalledWith("/usuarios/3/password", {
      nuevaPassword: "Nueva123!",
    });
  });

  it("apiEliminarUsuario hace DELETE y devuelve data", async () => {
    mock.delete.mockResolvedValueOnce({
      data: { success: true, data: { ok: true } },
    });
    await expect(apiEliminarUsuario(3)).resolves.toEqual({ ok: true });
    expect(mock.delete).toHaveBeenCalledWith("/usuarios/3");
  });

  it("propaga errores en apiCrearUsuario", async () => {
    const err = apiError("VALOR_DUPLICADO");
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiCrearUsuario({ username: "x", email: "x", password: "y" }),
    ).rejects.toBe(err);
  });
});

describe("admin: roles y permisos", () => {
  it("apiRoles pide page 0 size 100 y devuelve el arreglo", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([rol]) });
    await expect(apiRoles()).resolves.toEqual([rol]);
    expect(mock.get).toHaveBeenCalledWith("/roles", {
      params: { page: 0, size: 100 },
    });
  });

  it("apiRolesPaginado respeta page/size y devuelve la página", async () => {
    const pag = pagina([rol]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(apiRolesPaginado({ page: 2, size: 5 })).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/roles", {
      params: { page: 2, size: 5 },
    });
  });

  it("apiCrearRol hace POST /roles", async () => {
    const body = { clave: "CAJERO", nombre: "Cajero" };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: rol } });
    await expect(apiCrearRol(body)).resolves.toEqual(rol);
    expect(mock.post).toHaveBeenCalledWith("/roles", body);
  });

  it("apiActualizarRol hace PATCH /roles/:id", async () => {
    const body = { clave: "CAJERO", nombre: "Cajero actualizado" };
    mock.patch.mockResolvedValueOnce({ data: { success: true, data: rol } });
    await expect(apiActualizarRol(2, body)).resolves.toEqual(rol);
    expect(mock.patch).toHaveBeenCalledWith("/roles/2", body);
  });

  it("apiEliminarRol hace DELETE y resuelve void", async () => {
    mock.delete.mockResolvedValueOnce({ data: {} });
    await expect(apiEliminarRol(2)).resolves.toBeUndefined();
    expect(mock.delete).toHaveBeenCalledWith("/roles/2");
  });

  it("apiPermisos pide page 0 size 300 y devuelve el arreglo", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([permiso]) });
    await expect(apiPermisos()).resolves.toEqual([permiso]);
    expect(mock.get).toHaveBeenCalledWith("/permisos", {
      params: { page: 0, size: 300 },
    });
  });

  it("apiPermisosDeRol pide GET /roles/:id/permisos", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: ["ventas.leer"] },
    });
    await expect(apiPermisosDeRol(2)).resolves.toEqual(["ventas.leer"]);
    expect(mock.get).toHaveBeenCalledWith("/roles/2/permisos");
  });

  it("apiSetPermisosRol hace PUT con permisos", async () => {
    mock.put.mockResolvedValueOnce({
      data: { success: true, data: ["ventas.leer"] },
    });
    await expect(apiSetPermisosRol(2, ["ventas.leer"])).resolves.toEqual([
      "ventas.leer",
    ]);
    expect(mock.put).toHaveBeenCalledWith("/roles/2/permisos", {
      permisos: ["ventas.leer"],
    });
  });

  it("propaga errores en apiSetPermisosRol", async () => {
    const err = apiError("ACCESO_DENEGADO");
    mock.put.mockRejectedValueOnce(err);
    await expect(apiSetPermisosRol(2, [])).rejects.toBe(err);
  });
});

describe("admin: nómina", () => {
  it("apiNomina envía solo filtros presentes", async () => {
    const pag = pagina([nomina]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiNomina({ estado: "PENDIENTE", page: 0, size: 15 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/nomina", {
      params: { page: 0, size: 15, estado: "PENDIENTE" },
    });
  });

  it("apiNomina sin filtros envía solo page/size", async () => {
    mock.get.mockResolvedValueOnce({ data: pagina([]) });
    await apiNomina({ page: 1, size: 5 });
    expect(mock.get).toHaveBeenCalledWith("/nomina", {
      params: { page: 1, size: 5 },
    });
  });

  it("apiCrearNomina hace POST /nomina", async () => {
    const body = {
      empleadoId: 7,
      periodoIni: "2026-09-01",
      periodoFin: "2026-09-15",
      diasPagados: 15,
      percepciones: 5000,
      deducciones: 500,
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: nomina } });
    await expect(apiCrearNomina(body)).resolves.toEqual(nomina);
    expect(mock.post).toHaveBeenCalledWith("/nomina", body);
  });

  it("apiPagarNomina hace POST /nomina/:id/pagar sin body", async () => {
    mock.post.mockResolvedValueOnce({ data: { success: true, data: nomina } });
    await expect(apiPagarNomina(9)).resolves.toEqual(nomina);
    expect(mock.post).toHaveBeenCalledWith("/nomina/9/pagar");
  });

  it("apiCancelarNomina hace POST /nomina/:id/cancelar sin body", async () => {
    mock.post.mockResolvedValueOnce({ data: { success: true, data: nomina } });
    await expect(apiCancelarNomina(9)).resolves.toEqual(nomina);
    expect(mock.post).toHaveBeenCalledWith("/nomina/9/cancelar");
  });

  it("apiGenerarQuincena hace POST con el periodo", async () => {
    const body = { quincena: "PRIMERA" as const };
    const res = {
      creadas: 2,
      omitidas: 0,
      periodoIni: "2026-09-01",
      periodoFin: "2026-09-15",
      nominas: [nomina],
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: res } });
    await expect(apiGenerarQuincena(body)).resolves.toEqual(res);
    expect(mock.post).toHaveBeenCalledWith("/nomina/generar-quincena", body);
  });

  it("apiPagarNominaLote hace POST con ids", async () => {
    const res = { pagadas: 2, omitidas: 0, nominas: [nomina] };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: res } });
    await expect(apiPagarNominaLote([9, 10])).resolves.toEqual(res);
    expect(mock.post).toHaveBeenCalledWith("/nomina/pagar-lote", {
      ids: [9, 10],
    });
  });

  it("propaga errores en apiPagarNomina", async () => {
    const err = apiError("NOMINA_YA_PAGADA");
    mock.post.mockRejectedValueOnce(err);
    await expect(apiPagarNomina(9)).rejects.toBe(err);
  });
});
