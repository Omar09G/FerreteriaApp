import { describe, expect, it } from "vitest";
import type { AxiosError } from "axios";

import {
  ApiError,
  esApiError,
  mensajeError,
  transformar,
} from "@/lib/api/errors";
import type { ApiErrorBody } from "@/lib/api/types";

function axiosError(opts: {
  status?: number;
  data?: ApiErrorBody | null;
}): AxiosError<ApiErrorBody> {
  const response =
    opts.status === undefined
      ? undefined
      : ({ status: opts.status, data: opts.data, headers: {} } as never);
  return { response, isAxiosError: true } as AxiosError<ApiErrorBody>;
}

function cuerpo(over: Partial<ApiErrorBody> = {}): ApiErrorBody {
  return {
    success: false,
    data: null,
    errorCode: 400,
    codigo: "DATOS_INVALIDOS",
    errorMessage: "datos inválidos",
    ...over,
  };
}

describe("ApiError", () => {
  it("expone codigo, status, details, requestId e instance", () => {
    const err = new ApiError(
      cuerpo({
        errorCode: 404,
        codigo: "RECURSO_NO_ENCONTRADO",
        errorMessage: "no existe",
        details: [{ field: "id", error: "requerido" }],
        requestId: "req-1",
        instance: "/api/v1/x",
      }),
    );
    expect(err).toBeInstanceOf(Error);
    expect(err.name).toBe("ApiError");
    expect(err.codigo).toBe("RECURSO_NO_ENCONTRADO");
    expect(err.status).toBe(404);
    expect(err.message).toBe("no existe");
    expect(err.details).toEqual([{ field: "id", error: "requerido" }]);
    expect(err.requestId).toBe("req-1");
    expect(err.instance).toBe("/api/v1/x");
  });

  it("usa fallbacks cuando faltan codigo/errorMessage/errorCode", () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 0,
      codigo: "",
      errorMessage: "",
    } as unknown as ApiErrorBody);
    expect(err.codigo).toBe("ERROR_INTERNO");
    expect(err.status).toBe(0);
    expect(typeof err.message).toBe("string");
  });

  it("mensajeParaUsuario agrega folio cuando hay requestId", () => {
    const err = new ApiError(cuerpo({ requestId: "abc-123" }));
    expect(err.mensajeParaUsuario()).toContain("folio: abc-123");
  });

  it("mensajeParaUsuario usa instance cuando no hay requestId", () => {
    const err = new ApiError(cuerpo({ instance: "/api/v1/ventas" }));
    expect(err.mensajeParaUsuario()).toContain("/api/v1/ventas");
  });

  it("mensajeParaUsuario devuelve el mensaje base sin referencias", () => {
    const err = new ApiError(cuerpo({ errorMessage: "base" }));
    expect(err.mensajeParaUsuario()).toBe("base");
  });
});

describe("esApiError / mensajeError", () => {
  it("esApiError distingue ApiError de otros errores", () => {
    expect(esApiError(new ApiError(cuerpo()))).toBe(true);
    expect(esApiError(new Error("x"))).toBe(false);
    expect(esApiError("texto")).toBe(false);
    expect(esApiError(null)).toBe(false);
  });

  it("mensajeError usa el mensaje de usuario del ApiError", () => {
    const err = new ApiError(cuerpo({ errorMessage: "hola", requestId: "r1" }));
    expect(mensajeError(err)).toBe(err.mensajeParaUsuario());
  });

  it("mensajeError usa message de Error genérico", () => {
    expect(mensajeError(new Error("falló algo"))).toBe("falló algo");
  });

  it("mensajeError devuelve genérico para valores desconocidos", () => {
    expect(typeof mensajeError(undefined)).toBe("string");
    expect(typeof mensajeError(42)).toBe("string");
  });
});

describe("transformar", () => {
  it("convierte envelope con codigo en ApiError", () => {
    const out = transformar(axiosError({ status: 422, data: cuerpo() }));
    expect(out).toBeInstanceOf(ApiError);
    expect((out as ApiError).codigo).toBe("DATOS_INVALIDOS");
  });

  it("sin response (red caída) devuelve Error genérico", () => {
    const out = transformar(axiosError({}));
    expect(out).toBeInstanceOf(Error);
    expect(out).not.toBeInstanceOf(ApiError);
  });

  it("con response pero sin codigo devuelve Error con el status", () => {
    const out = transformar(axiosError({ status: 503, data: null }));
    expect(out).toBeInstanceOf(Error);
    expect(out).not.toBeInstanceOf(ApiError);
    expect(out.message).toContain("503");
  });
});
