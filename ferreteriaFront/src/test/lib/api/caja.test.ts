import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiAbrirTurno,
  apiActualizarCaja,
  apiActualizarEstadoCaja,
  apiActualizarGasto,
  apiActualizarIngreso,
  apiCajas,
  apiCerrarTurno,
  apiCortes,
  apiCrearCaja,
  apiCrearGasto,
  apiCrearIngreso,
  apiEliminarGasto,
  apiEliminarIngreso,
  apiEnviarTurnoAbiertoInforme,
  apiEsperadoTurno,
  apiEstadoTurnoAbiertoInforme,
  apiGastos,
  apiIngresosOtros,
  apiMovimientosTurno,
  apiRegistrarMovimiento,
  apiTurnoActual,
  apiTurnos,
} from "@/lib/api/caja";
import { ApiError } from "@/lib/api/errors";
import type {
  Caja,
  CorteCaja,
  Gasto,
  IngresoOtro,
  MovimientoCaja,
  PageEnvelope,
  TurnoCaja,
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

const caja = { cajaId: 1, nombre: "Caja 1" } as unknown as Caja;
const turno = { turnoCajaId: 4, cajaId: 1, estado: "ABIERTO" } as unknown as TurnoCaja;
const movimiento = { movimientoId: 2, monto: 100 } as unknown as MovimientoCaja;
const corte = { corteId: 3, diferencia: 0 } as unknown as CorteCaja;
const gasto = { gastoId: 5, monto: 250 } as unknown as Gasto;
const ingreso = { ingresoOtroId: 6, monto: 300 } as unknown as IngresoOtro;

function pagina<T>(rows: T[]): PageEnvelope<T> {
  return {
    success: true,
    data: rows,
    meta: { page: 0, size: 10, totalElements: rows.length, totalPages: 1 },
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

describe("caja: catálogo de cajas", () => {
  it("apiCajas pide GET /cajas y devuelve data", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: [caja] } });
    await expect(apiCajas()).resolves.toEqual([caja]);
    expect(mock.get).toHaveBeenCalledWith("/cajas");
  });

  it("apiCrearCaja hace POST /cajas", async () => {
    const body = { nombre: "Caja 2", almacenId: 1, activa: true };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: caja } });
    await expect(apiCrearCaja(body)).resolves.toEqual(caja);
    expect(mock.post).toHaveBeenCalledWith("/cajas", body);
  });

  it("apiActualizarCaja hace PUT /cajas/:id", async () => {
    const body = { nombre: "Caja 1", almacenId: 1, activa: true };
    mock.put.mockResolvedValueOnce({ data: { success: true, data: caja } });
    await expect(apiActualizarCaja(1, body)).resolves.toEqual(caja);
    expect(mock.put).toHaveBeenCalledWith("/cajas/1", body);
  });

  it("apiActualizarEstadoCaja hace PUT /cajas/estado/:id con activa", async () => {
    mock.put.mockResolvedValueOnce({ data: { success: true, data: caja } });
    await expect(apiActualizarEstadoCaja(1, false)).resolves.toEqual(caja);
    expect(mock.put).toHaveBeenCalledWith("/cajas/estado/1", {
      activa: false,
    });
  });

  it("propaga ApiError en apiCajas", async () => {
    const err = apiError();
    mock.get.mockRejectedValueOnce(err);
    await expect(apiCajas()).rejects.toBe(err);
  });
});

describe("caja: turnos", () => {
  it("apiTurnoActual pide GET /cajas/:id/turno-actual", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: turno } });
    await expect(apiTurnoActual(1)).resolves.toEqual(turno);
    expect(mock.get).toHaveBeenCalledWith("/cajas/1/turno-actual");
  });

  it("apiTurnos pide historial con page/size", async () => {
    const pag = pagina([turno]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(apiTurnos(1, 0, 10)).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/cajas/1/turnos", {
      params: { page: 0, size: 10 },
    });
  });

  it("apiAbrirTurno hace POST con cajaId y montoApertura", async () => {
    mock.post.mockResolvedValueOnce({ data: { success: true, data: turno } });
    await expect(apiAbrirTurno(1, 500)).resolves.toEqual(turno);
    expect(mock.post).toHaveBeenCalledWith("/cajas/1/turnos", {
      cajaId: 1,
      montoApertura: 500,
    });
  });

  it("apiEnviarTurnoAbiertoInforme hace POST al informe", async () => {
    const r = {
      fecha: "2026-10-05",
      destinatarios: 2,
      emailsEnviados: 2,
      whatsappsEnviados: 1,
      turnos: 3,
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: r } });
    await expect(apiEnviarTurnoAbiertoInforme()).resolves.toEqual(r);
    expect(mock.post).toHaveBeenCalledWith("/reportes/turnos/informe", {});
  });

  it("apiEstadoTurnoAbiertoInforme pide GET al estado", async () => {
    const r = {
      fecha: "2026-10-05",
      yaEnviado: true,
      estado: "ENVIADA",
      enviadoEn: "2026-10-05T21:00:00",
    };
    mock.get.mockResolvedValueOnce({ data: { success: true, data: r } });
    await expect(apiEstadoTurnoAbiertoInforme()).resolves.toEqual(r);
    expect(mock.get).toHaveBeenCalledWith("/reportes/turnos/informe/estado");
  });

  it("apiMovimientosTurno pide GET anidado", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [movimiento] },
    });
    await expect(apiMovimientosTurno(1, 4)).resolves.toEqual([movimiento]);
    expect(mock.get).toHaveBeenCalledWith("/cajas/1/turnos/4/movimientos");
  });

  it("apiRegistrarMovimiento hace POST anidado", async () => {
    const body = { tipo: "ENTRADA", concepto: "Fondo", monto: 100 };
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: movimiento },
    });
    await expect(apiRegistrarMovimiento(1, 4, body)).resolves.toEqual(
      movimiento,
    );
    expect(mock.post).toHaveBeenCalledWith(
      "/cajas/1/turnos/4/movimientos",
      body,
    );
  });

  it("apiCerrarTurno hace POST al corte", async () => {
    const body = { montoContado: 1200, observaciones: "ok" };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: corte } });
    await expect(apiCerrarTurno(1, 4, body)).resolves.toEqual(corte);
    expect(mock.post).toHaveBeenCalledWith("/cajas/1/turnos/4/corte", body);
  });

  it("apiEsperadoTurno pide GET esperado", async () => {
    const esperado = { esperado: 1200 };
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: esperado },
    });
    await expect(apiEsperadoTurno(1, 4)).resolves.toEqual(esperado);
    expect(mock.get).toHaveBeenCalledWith("/cajas/1/turnos/4/esperado");
  });

  it("propaga error al abrir turno con caja inexistente", async () => {
    const err = apiError("RECURSO_NO_ENCONTRADO");
    mock.post.mockRejectedValueOnce(err);
    await expect(apiAbrirTurno(99, 500)).rejects.toBe(err);
  });
});

describe("caja: cortes, gastos e ingresos", () => {
  it("apiCortes pide GET /cortes-caja con filtros", async () => {
    const pag = pagina([corte]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(apiCortes(0, 10, "2026-09-01", "2026-09-30")).resolves.toEqual(
      pag,
    );
    expect(mock.get).toHaveBeenCalledWith("/cortes-caja", {
      params: { page: 0, size: 10, desde: "2026-09-01", hasta: "2026-09-30" },
    });
  });

  it("apiGastos pide GET /gastos con filtros", async () => {
    const pag = pagina([gasto]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(apiGastos(0, 15)).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/gastos", {
      params: { page: 0, size: 15, desde: undefined, hasta: undefined },
    });
  });

  it("apiCrearGasto hace POST /gastos", async () => {
    const body = { tipoGastoId: 14, descripcion: "Varios", monto: 250, formaPagoId: 1 };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: gasto } });
    await expect(apiCrearGasto(body)).resolves.toEqual(gasto);
    expect(mock.post).toHaveBeenCalledWith("/gastos", body);
  });

  it("apiActualizarGasto hace PUT /gastos/:id", async () => {
    const body = { tipoGastoId: 14, descripcion: "Varios", monto: 300, formaPagoId: 1 };
    mock.put.mockResolvedValueOnce({ data: { success: true, data: gasto } });
    await expect(apiActualizarGasto(5, body)).resolves.toEqual(gasto);
    expect(mock.put).toHaveBeenCalledWith("/gastos/5", body);
  });

  it("apiEliminarGasto hace DELETE y resuelve void", async () => {
    mock.delete.mockResolvedValueOnce({ data: {} });
    await expect(apiEliminarGasto(5)).resolves.toBeUndefined();
    expect(mock.delete).toHaveBeenCalledWith("/gastos/5");
  });

  it("apiIngresosOtros pide GET /ingresos-otros", async () => {
    const pag = pagina([ingreso]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(apiIngresosOtros(0, 15)).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/ingresos-otros", {
      params: { page: 0, size: 15, desde: undefined, hasta: undefined },
    });
  });

  it("apiCrearIngreso hace POST /ingresos-otros", async () => {
    const body = { concepto: "Ajuste", monto: 300, formaPagoId: 1 };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: ingreso } });
    await expect(apiCrearIngreso(body)).resolves.toEqual(ingreso);
    expect(mock.post).toHaveBeenCalledWith("/ingresos-otros", body);
  });

  it("apiActualizarIngreso hace PUT /ingresos-otros/:id", async () => {
    const body = { concepto: "Ajuste", monto: 350, formaPagoId: 1 };
    mock.put.mockResolvedValueOnce({ data: { success: true, data: ingreso } });
    await expect(apiActualizarIngreso(6, body)).resolves.toEqual(ingreso);
    expect(mock.put).toHaveBeenCalledWith("/ingresos-otros/6", body);
  });

  it("apiEliminarIngreso hace DELETE y resuelve void", async () => {
    mock.delete.mockResolvedValueOnce({ data: {} });
    await expect(apiEliminarIngreso(6)).resolves.toBeUndefined();
    expect(mock.delete).toHaveBeenCalledWith("/ingresos-otros/6");
  });

  it("propaga error al crear gasto inválido", async () => {
    const err = apiError("DATOS_INVALIDOS");
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiCrearGasto({ tipoGastoId: 1, descripcion: "", monto: -5, formaPagoId: 1 }),
    ).rejects.toBe(err);
  });
});
