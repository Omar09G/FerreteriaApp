import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from "axios";

import {
  _resetRefreshingForTests,
  doRefresh,
  puedeRefrescar,
  refreshAccess,
  type RetryMeta,
} from "@/lib/api/refresh";
import type { ApiErrorBody } from "@/lib/api/types";

function fakeHttp() {
  return { post: vi.fn() } as unknown as AxiosInstance;
}

function meta(over: Partial<RetryMeta> = {}): RetryMeta {
  return { retries: 0, refreshed: false, csrfRefreshed: false, ...over };
}

function config(url = "/ventas"): InternalAxiosRequestConfig & { _retry?: RetryMeta } {
  return { url, headers: {} } as unknown as InternalAxiosRequestConfig & {
    _retry?: RetryMeta;
  };
}

function error401(codigo = "TOKEN_EXPIRADO"): AxiosError<ApiErrorBody> {
  return {
    response: {
      status: 401,
      data: {
        success: false,
        data: null,
        errorCode: 401,
        codigo,
        errorMessage: codigo,
      },
      headers: {},
    },
  } as unknown as AxiosError<ApiErrorBody>;
}

beforeEach(() => {
  _resetRefreshingForTests();
});

describe("doRefresh", () => {
  it("hace POST /auth/refresh con body vacío y devuelve el accessToken", async () => {
    const http = fakeHttp();
    (http.post as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { success: true, data: { accessToken: "nuevo-jwt" } },
    });
    await expect(doRefresh(http)).resolves.toBe("nuevo-jwt");
    expect(http.post).toHaveBeenCalledWith("/auth/refresh", {});
  });

  it("deduplica llamadas concurrentes en una sola petición", async () => {
    const http = fakeHttp();
    let resolve!: (v: unknown) => void;
    (http.post as unknown as ReturnType<typeof vi.fn>).mockImplementationOnce(
      () => new Promise((res) => { resolve = res; }),
    );
    const p1 = doRefresh(http);
    const p2 = doRefresh(http);
    resolve({ data: { success: true, data: { accessToken: "t" } } });
    await expect(p1).resolves.toBe("t");
    await expect(p2).resolves.toBe("t");
    expect(http.post).toHaveBeenCalledTimes(1);
  });

  it("tras resolver permite un refresh nuevo", async () => {
    const http = fakeHttp();
    const post = http.post as unknown as ReturnType<typeof vi.fn>;
    post.mockResolvedValue({ data: { success: true, data: { accessToken: "t" } } });
    await doRefresh(http);
    await doRefresh(http);
    expect(post).toHaveBeenCalledTimes(2);
  });

  it("si falla, libera el lock para reintentar después", async () => {
    const http = fakeHttp();
    const post = http.post as unknown as ReturnType<typeof vi.fn>;
    post.mockRejectedValueOnce(new Error("red caída"));
    await expect(doRefresh(http)).rejects.toThrow("red caída");
    post.mockResolvedValueOnce({
      data: { success: true, data: { accessToken: "t2" } },
    });
    await expect(doRefresh(http)).resolves.toBe("t2");
    expect(post).toHaveBeenCalledTimes(2);
  });

  it("refreshAccess es alias de doRefresh", () => {
    expect(refreshAccess).toBe(doRefresh);
  });
});

describe("puedeRefrescar", () => {
  it("401 con TOKEN_EXPIRADO → true", () => {
    expect(puedeRefrescar(error401(), config(), meta())).toBe(true);
  });

  it("401 con CREDENCIALES_INVALIDAS → true", () => {
    expect(
      puedeRefrescar(error401("CREDENCIALES_INVALIDAS"), config(), meta()),
    ).toBe(true);
  });

  it("codigo TOKEN_EXPIRADO sin status 401 → true", () => {
    const err = {
      response: {
        status: 403,
        data: {
          success: false,
          data: null,
          errorCode: 403,
          codigo: "TOKEN_EXPIRADO",
          errorMessage: "x",
        },
        headers: {},
      },
    } as unknown as AxiosError<ApiErrorBody>;
    expect(puedeRefrescar(err, config(), meta())).toBe(true);
  });

  it("ya refrescado → false (evita loops)", () => {
    expect(
      puedeRefrescar(error401(), config(), meta({ refreshed: true })),
    ).toBe(false);
  });

  it("request a /auth/refresh → false", () => {
    expect(
      puedeRefrescar(error401(), config("/auth/refresh"), meta()),
    ).toBe(false);
  });

  it("request a /auth/login → false", () => {
    expect(
      puedeRefrescar(error401(), config("/auth/login"), meta()),
    ).toBe(false);
  });

  it("error no-401 sin codigo de token → false", () => {
    const err = {
      response: {
        status: 500,
        data: {
          success: false,
          data: null,
          errorCode: 500,
          codigo: "ERROR_INTERNO",
          errorMessage: "x",
        },
        headers: {},
      },
    } as unknown as AxiosError<ApiErrorBody>;
    expect(puedeRefrescar(err, config(), meta())).toBe(false);
  });

  it("sin response → false", () => {
    const err = {} as AxiosError<ApiErrorBody>;
    expect(puedeRefrescar(err, config(), meta())).toBe(false);
  });
});
