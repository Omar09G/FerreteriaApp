import { afterEach, describe, expect, it, vi } from "vitest";
import type {
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios";

import http, { ensureCsrfCookie } from "@/lib/api/client-base";

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function header(
  config: InternalAxiosRequestConfig,
  name: string,
): string | null {
  const h = config.headers as unknown as {
    get?: (n: string) => string | string[] | null;
  } & Record<string, unknown>;
  if (h && typeof h.get === "function") {
    const v = h.get(name);
    if (Array.isArray(v)) return v[0] ?? null;
    return (v as string | null) ?? null;
  }
  const v = h?.[name];
  return typeof v === "string" ? v : null;
}

/** Ejecuta una request sin red: el adapter captura el config final. */

async function requestConCaptura(
  method: "get" | "post",
  url = "/ping",
): Promise<InternalAxiosRequestConfig> {
  let capturada: InternalAxiosRequestConfig | undefined;
  const adapter = async (
    config: InternalAxiosRequestConfig,
  ): Promise<AxiosResponse> => {
    capturada = config;
    return {
      data: { ok: true },
      status: 200,
      statusText: "OK",
      headers: {},
      config,
    } as AxiosResponse;
  };
  if (method === "post") {
    await http.post(url, {}, { adapter });
  } else {
    await http.get(url, { adapter });
  }
  if (!capturada) throw new Error("adapter no capturó el config");
  return capturada;
}

function limpiarCookies(): void {
  for (const parte of document.cookie.split(";")) {
    const nombre = parte.split("=")[0]?.trim();
    if (nombre) {
      document.cookie = `${nombre}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

afterEach(() => {
  limpiarCookies();
  vi.restoreAllMocks();
});

describe("client-base: instancia", () => {
  it("apunta a /api/v1 con credenciales y timeout", () => {
    expect(http.defaults.baseURL).toContain("/api/v1");
    expect(http.defaults.withCredentials).toBe(true);
    expect(http.defaults.timeout).toBeGreaterThan(0);
  });

  it("cada request lleva X-Request-Id con formato UUID v4", async () => {
    const c1 = await requestConCaptura("get");
    const c2 = await requestConCaptura("get");
    expect(header(c1, "X-Request-Id")).toMatch(UUID_RE);
    expect(header(c2, "X-Request-Id")).toMatch(UUID_RE);
    expect(header(c1, "X-Request-Id")).not.toBe(header(c2, "X-Request-Id"));
  });

  it("en mutating adjunta el CSRF de la cookie", async () => {
    document.cookie = "XSRF-TOKEN=tok123; path=/";
    const c = await requestConCaptura("post");
    expect(header(c, "X-XSRF-TOKEN")).toBe("tok123");
  });

  it("sin cookie CSRF no adjunta el header", async () => {
    const c = await requestConCaptura("post");
    expect(header(c, "X-XSRF-TOKEN")).toBeNull();
  });
});

describe("client-base: ensureCsrfCookie", () => {
  it("no llama HTTP cuando la cookie ya existe", async () => {
    document.cookie = "XSRF-TOKEN=ya; path=/";
    const spy = vi.spyOn(http, "get") as unknown as ReturnType<typeof vi.fn>;
    await ensureCsrfCookie();
    expect(spy).not.toHaveBeenCalled();
  });

  it("pide GET /auth/csrf-init cuando falta la cookie", async () => {
    const spy = vi.spyOn(http, "get") as unknown as ReturnType<typeof vi.fn>;
    spy.mockResolvedValueOnce({ data: {} });
    await ensureCsrfCookie();
    expect(spy).toHaveBeenCalledWith("/auth/csrf-init");
  });

  it("no lanza si el init falla", async () => {
    const spy = vi.spyOn(http, "get") as unknown as ReturnType<typeof vi.fn>;
    spy.mockRejectedValueOnce(new Error("red caída"));
    await expect(ensureCsrfCookie()).resolves.toBeUndefined();
  });
});
