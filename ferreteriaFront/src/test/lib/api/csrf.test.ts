import { afterEach, describe, expect, it, vi } from "vitest";

// NOTA: isCsrfFailure ya está cubierto en src/test/csrf.test.ts.
// Aquí solo se cubren los exports restantes del módulo csrf.
import {
  ensureCsrfCookie,
  getCsrfToken,
  readCookie,
} from "@/lib/api/csrf";

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

describe("readCookie", () => {
  it("devuelve el valor de la cookie pedida", () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    document.cookie = "otra=1; path=/";
    expect(readCookie("XSRF-TOKEN")).toBe("abc123");
  });

  it("devuelve null cuando no existe", () => {
    expect(readCookie("XSRF-TOKEN")).toBeNull();
  });

  it("decodifica valores con encoding", () => {
    document.cookie = `XSRF-TOKEN=${encodeURIComponent("a b+c")}; path=/`;
    expect(readCookie("XSRF-TOKEN")).toBe("a b+c");
  });
});

describe("getCsrfToken", () => {
  it("lee la cookie XSRF-TOKEN", () => {
    expect(getCsrfToken()).toBeNull();
    document.cookie = "XSRF-TOKEN=tok; path=/";
    expect(getCsrfToken()).toBe("tok");
  });
});

describe("ensureCsrfCookie", () => {
  it("no llama HTTP cuando la cookie ya existe", async () => {
    document.cookie = "XSRF-TOKEN=ya; path=/";
    const http = { get: vi.fn() } as unknown as Parameters<
      typeof ensureCsrfCookie
    >[0];
    await ensureCsrfCookie(http);
    expect(http.get).not.toHaveBeenCalled();
  });

  it("pide GET /auth/csrf-init cuando falta la cookie", async () => {
    const http = {
      get: vi.fn().mockResolvedValueOnce({ data: {} }),
    } as unknown as Parameters<typeof ensureCsrfCookie>[0];
    await ensureCsrfCookie(http);
    expect(http.get).toHaveBeenCalledWith("/auth/csrf-init");
  });

  it("no lanza si el init falla (best-effort)", async () => {
    const http = {
      get: vi.fn().mockRejectedValueOnce(new Error("red caída")),
    } as unknown as Parameters<typeof ensureCsrfCookie>[0];
    await expect(ensureCsrfCookie(http)).resolves.toBeUndefined();
  });
});
