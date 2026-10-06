import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiCancelarRenta,
  apiCancelarVenta,
  apiCheckout,
  apiConvertirCotizacion,
  apiCotizaciones,
  apiCrearCotizacion,
  apiCrearDevolucion,
  apiCrearRenta,
  apiCuentasCobrar,
  apiDevolucionRenta,
  apiDevolucionesDeVenta,
  apiEnviarCobranzaInforme,
  apiEnviarRentasInforme,
  apiEnviarTicketWhatsapp,
  apiEstadoCobranzaInforme,
  apiEstadoRentasInforme,
  apiPagoCliente,
  apiRentas,
  apiVentas,
} from "@/lib/api/venta";
import { ApiError } from "@/lib/api/errors";
import type {
  Cotizacion,
  CuentaCobrar,
  Devolucion,
  PageEnvelope,
  Renta,
  Venta,
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
  patch: ReturnType<typeof vi.fn>;
};

const mock = http as unknown as HttpMock;

const venta = { ventaId: 1, folio: "V-001" } as unknown as Venta;
const cuenta = { cuentaCobrarId: 1, saldo: 100 } as unknown as CuentaCobrar;
const cotizacion = { cotizacionId: 1, folio: "COT-001" } as unknown as Cotizacion;
const devolucion = { devolucionId: 1, folio: "DEV-001" } as unknown as Devolucion;
const renta = { rentaId: 1, folio: "R-001" } as unknown as Renta;

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

describe("ventas", () => {
  it("apiVentas envía filtros opcionales", async () => {
    const pag = pagina([venta]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiVentas({ almacenId: 1, desde: "2026-09-01", hasta: "2026-09-30", page: 0, size: 10 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/ventas", {
      params: { page: 0, size: 10, almacenId: 1, desde: "2026-09-01", hasta: "2026-09-30" },
    });
  });

  it("apiCheckout hace POST /ventas", async () => {
    const body = {
      almacenId: 1,
      formaPagoId: 1,
      detalles: [{ productoId: 1, cantidad: 2, precioUnitario: 100 }],
      pagos: [{ formaPagoId: 1, monto: 232 }],
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: venta } });
    await expect(apiCheckout(body)).resolves.toEqual(venta);
    expect(mock.post).toHaveBeenCalledWith("/ventas", body);
  });

  it("apiCancelarVenta hace PATCH /ventas/:id/cancelar", async () => {
    const body = { motivo: "Error de captura" };
    mock.patch.mockResolvedValueOnce({ data: { success: true, data: venta } });
    await expect(apiCancelarVenta(1, body)).resolves.toEqual(venta);
    expect(mock.patch).toHaveBeenCalledWith("/ventas/1/cancelar", body);
  });

  it("propaga error de stock al hacer checkout", async () => {
    const err = apiError("STOCK_INSUFICIENTE");
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiCheckout({ almacenId: 1, formaPagoId: 1, detalles: [], pagos: [] }),
    ).rejects.toBe(err);
  });
});

describe("cobranza", () => {
  it("apiCuentasCobrar sin cliente usa /creditos/cobranza", async () => {
    const pag = pagina([cuenta]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiCuentasCobrar({ estado: "VIGENTE", page: 0, size: 10 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/creditos/cobranza", {
      params: { page: 0, size: 10, estado: "VIGENTE" },
    });
  });

  it("apiCuentasCobrar con clienteId usa /creditos/:id", async () => {
    const pag = pagina([cuenta]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiCuentasCobrar({ clienteId: 5, desde: "2026-09-01", hasta: "2026-09-30", page: 0, size: 10 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/creditos/5", {
      params: {
        page: 0,
        size: 10,
        clienteId: 5,
        desde: "2026-09-01",
        hasta: "2026-09-30",
      },
    });
  });

  it("apiPagoCliente hace POST /pagos-cliente y resuelve void", async () => {
    const body = { cuentaCobrarId: 1, formaPagoId: 1, monto: 100 };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: {} } });
    await expect(apiPagoCliente(body)).resolves.toBeUndefined();
    expect(mock.post).toHaveBeenCalledWith("/pagos-cliente", body);
  });

  it("propaga errores en cobranza", async () => {
    const err = apiError("MONTO_INVALIDO");
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiPagoCliente({ cuentaCobrarId: 1, formaPagoId: 1, monto: -5 }),
    ).rejects.toBe(err);
  });

  it("apiEnviarCobranzaInforme hace POST al informe", async () => {
    const res = {
      fecha: "2026-10-05",
      destinatarios: 2,
      emailsEnviados: 2,
      whatsappsEnviados: 1,
      vencidas: 1,
      pendientes: 1,
      totalVencido: 500,
      totalPendiente: 300,
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: res } });
    await expect(apiEnviarCobranzaInforme()).resolves.toEqual(res);
    expect(mock.post).toHaveBeenCalledWith("/reportes/cobranza/informe", {});
  });

  it("apiEstadoCobranzaInforme pide GET al estado", async () => {
    const estado = {
      fecha: "2026-10-05",
      yaEnviado: true,
      estado: "ENVIADA",
      enviadoEn: "2026-10-05T09:05:00",
    };
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: estado },
    });
    await expect(apiEstadoCobranzaInforme()).resolves.toEqual(estado);
    expect(mock.get).toHaveBeenCalledWith("/reportes/cobranza/informe/estado");
  });

  it("apiEnviarRentasInforme hace POST al informe", async () => {
    const res = {
      fecha: "2026-10-05",
      destinatarios: 2,
      emailsEnviados: 2,
      whatsappsEnviados: 2,
      vencidas: 1,
      proximas: 1,
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: res } });
    await expect(apiEnviarRentasInforme()).resolves.toEqual(res);
    expect(mock.post).toHaveBeenCalledWith("/reportes/rentas/informe", {});
  });

  it("apiEstadoRentasInforme pide GET al estado", async () => {
    const estado = {
      fecha: "2026-10-05",
      yaEnviado: false,
      estado: null,
      enviadoEn: null,
    };
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: estado },
    });
    await expect(apiEstadoRentasInforme()).resolves.toEqual(estado);
    expect(mock.get).toHaveBeenCalledWith("/reportes/rentas/informe/estado");
  });
});

describe("cotizaciones", () => {
  it("apiCotizaciones envía filtros opcionales", async () => {
    const pag = pagina([cotizacion]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiCotizaciones({ estado: "VIGENTE", page: 0, size: 10 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/cotizaciones", {
      params: { page: 0, size: 10, estado: "VIGENTE" },
    });
  });

  it("apiCrearCotizacion hace POST /cotizaciones", async () => {
    const body = {
      detalles: [{ productoId: 1, cantidad: 1, precioUnitario: 50 }],
    };
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: cotizacion },
    });
    await expect(apiCrearCotizacion(body)).resolves.toEqual(cotizacion);
    expect(mock.post).toHaveBeenCalledWith("/cotizaciones", body);
  });

  it("apiConvertirCotizacion hace POST con params", async () => {
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: cotizacion },
    });
    await expect(apiConvertirCotizacion(1, 2, 3, 4)).resolves.toEqual(
      cotizacion,
    );
    expect(mock.post).toHaveBeenCalledWith("/cotizaciones/1/convertir", null, {
      params: { almacenId: 2, formaPagoId: 3, cajaId: 4 },
    });
  });

  it("apiConvertirCotizacion sin cajaId envía undefined", async () => {
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: cotizacion },
    });
    await apiConvertirCotizacion(1, 2, 3);
    expect(mock.post).toHaveBeenCalledWith("/cotizaciones/1/convertir", null, {
      params: { almacenId: 2, formaPagoId: 3, cajaId: undefined },
    });
  });

  it("propaga errores en cotizaciones", async () => {
    const err = apiError("COTIZACION_VENCIDA");
    mock.post.mockRejectedValueOnce(err);
    await expect(apiConvertirCotizacion(1, 2, 3)).rejects.toBe(err);
  });
});

describe("devoluciones", () => {
  it("apiDevolucionesDeVenta pide GET /devoluciones/venta/:id", async () => {
    mock.get.mockResolvedValueOnce({
      data: { success: true, data: [devolucion] },
    });
    await expect(apiDevolucionesDeVenta(1)).resolves.toEqual([devolucion]);
    expect(mock.get).toHaveBeenCalledWith("/devoluciones/venta/1");
  });

  it("apiCrearDevolucion hace POST /devoluciones", async () => {
    const body = {
      ventaId: 1,
      motivo: "Defecto",
      formaDevolucionId: 1,
      detalles: [{ productoId: 1, cantidad: 1, precioUnitario: 100 }],
    };
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: devolucion },
    });
    await expect(apiCrearDevolucion(body)).resolves.toEqual(devolucion);
    expect(mock.post).toHaveBeenCalledWith("/devoluciones", body);
  });

  it("propaga errores en devoluciones", async () => {
    const err = apiError("DEVOLUCION_FUERA_PLAZO");
    mock.get.mockRejectedValueOnce(err);
    await expect(apiDevolucionesDeVenta(1)).rejects.toBe(err);
  });
});

describe("ticket por WhatsApp", () => {
  it("apiEnviarTicketWhatsapp hace POST con el teléfono", async () => {
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: { enviado: true } },
    });
    await expect(
      apiEnviarTicketWhatsapp(1, "5215500000001"),
    ).resolves.toEqual({ enviado: true });
    expect(mock.post).toHaveBeenCalledWith("/ventas/1/ticket-whatsapp", {
      telefono: "5215500000001",
    });
  });
});

describe("rentas", () => {
  it("apiRentas envía filtros opcionales", async () => {
    const pag = pagina([renta]);
    mock.get.mockResolvedValueOnce({ data: pag });
    await expect(
      apiRentas({ estado: "ACTIVA", page: 0, size: 10 }),
    ).resolves.toEqual(pag);
    expect(mock.get).toHaveBeenCalledWith("/rentas", {
      params: { page: 0, size: 10, estado: "ACTIVA" },
    });
  });

  it("apiCrearRenta hace POST /rentas", async () => {
    const body = {
      clienteId: 1,
      almacenId: 1,
      cajaId: 1,
      formaPagoId: 1,
      fechaDevEsperada: "2026-10-01",
      deposito: 500,
      detalles: [{ productoId: 1, cantidad: 1, costoDia: 100 }],
    };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: renta } });
    await expect(apiCrearRenta(body)).resolves.toEqual(renta);
    expect(mock.post).toHaveBeenCalledWith("/rentas", body);
  });

  it("apiDevolucionRenta hace POST /rentas/:id/devolucion", async () => {
    const body = { detalles: [{ productoId: 1, diasCobrados: 3 }] };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: renta } });
    await expect(apiDevolucionRenta(1, body)).resolves.toEqual(renta);
    expect(mock.post).toHaveBeenCalledWith("/rentas/1/devolucion", body);
  });

  it("apiCancelarRenta hace POST /rentas/:id/cancelar sin body", async () => {
    mock.post.mockResolvedValueOnce({ data: { success: true, data: renta } });
    await expect(apiCancelarRenta(1)).resolves.toEqual(renta);
    expect(mock.post).toHaveBeenCalledWith("/rentas/1/cancelar");
  });

  it("propaga errores en rentas", async () => {
    const err = apiError("RENTA_NO_DEVUELTA");
    mock.post.mockRejectedValueOnce(err);
    await expect(apiCancelarRenta(1)).rejects.toBe(err);
  });
});
