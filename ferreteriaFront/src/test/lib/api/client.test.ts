import { describe, expect, it } from "vitest";

import httpDefault, {
  ApiError,
  CSRF_COOKIE,
  CSRF_HEADER,
  doRefresh,
  ensureCsrfCookie,
  esApiError,
  http as httpNamed,
  mensajeError,
  MUTATING_METHODS,
  puedeRefrescar,
  readCookie,
  refreshAccess,
  transformar,
} from "@/lib/api/client";
import httpBase, {
  ensureCsrfCookie as ensureBase,
} from "@/lib/api/client-base";

describe("client (fachada de compatibilidad)", () => {
  it("re-exporta la misma instancia axios de client-base", () => {
    expect(httpDefault).toBe(httpBase);
    expect(httpNamed).toBe(httpBase);
  });

  it("re-exporta ensureCsrfCookie de client-base", () => {
    expect(ensureCsrfCookie).toBe(ensureBase);
    expect(typeof ensureCsrfCookie).toBe("function");
  });

  it("re-exporta utilidades de errores", () => {
    expect(typeof ApiError).toBe("function");
    expect(typeof esApiError).toBe("function");
    expect(typeof mensajeError).toBe("function");
    expect(typeof transformar).toBe("function");
  });

  it("re-exporta constantes y helpers CSRF", () => {
    expect(CSRF_COOKIE).toBe("XSRF-TOKEN");
    expect(CSRF_HEADER).toBe("X-XSRF-TOKEN");
    expect(MUTATING_METHODS.has("post")).toBe(true);
    expect(typeof readCookie).toBe("function");
  });

  it("re-exporta helpers de refresh", () => {
    expect(typeof doRefresh).toBe("function");
    expect(typeof refreshAccess).toBe("function");
    expect(typeof puedeRefrescar).toBe("function");
  });
});
