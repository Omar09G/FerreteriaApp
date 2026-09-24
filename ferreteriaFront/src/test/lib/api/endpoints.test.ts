import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  apiCambiarPassword,
  apiEliminar,
  apiLogin,
  apiLogout,
  apiMe,
  apiRefresh,
} from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import type { MeResponse, TokenResponse } from "@/lib/api/types";

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
  delete: ReturnType<typeof vi.fn>;
};

const mock = http as unknown as HttpMock;

const usuario = { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] } as unknown as MeResponse;
const token: TokenResponse = {
  accessToken: "jwt",
  expiresInSeconds: 900,
  usuario,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("endpoints: auth", () => {
  it("apiLogin hace POST /auth/login y devuelve data", async () => {
    const payload = { username: "admin", password: "Secreta1!" };
    mock.post.mockResolvedValueOnce({ data: { success: true, data: token } });
    await expect(apiLogin(payload)).resolves.toEqual(token);
    expect(mock.post).toHaveBeenCalledWith("/auth/login", payload);
  });

  it("apiRefresh hace POST /auth/refresh con body vacío (cookie HttpOnly)", async () => {
    mock.post.mockResolvedValueOnce({ data: { success: true, data: token } });
    await expect(apiRefresh()).resolves.toEqual(token);
    expect(mock.post).toHaveBeenCalledWith("/auth/refresh", {});
  });

  it("apiLogout hace POST /auth/logout con body vacío", async () => {
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: { revocado: true } },
    });
    await expect(apiLogout()).resolves.toEqual({ revocado: true });
    expect(mock.post).toHaveBeenCalledWith("/auth/logout", {});
  });

  it("apiCambiarPassword hace POST con el body", async () => {
    const body = { passwordActual: "Vieja1!", nuevaPassword: "Nueva1!" };
    mock.post.mockResolvedValueOnce({
      data: { success: true, data: { cambiada: true } },
    });
    await expect(apiCambiarPassword(body)).resolves.toEqual({ cambiada: true });
    expect(mock.post).toHaveBeenCalledWith("/auth/change-password", body);
  });

  it("apiMe pide GET /auth/me", async () => {
    mock.get.mockResolvedValueOnce({ data: { success: true, data: usuario } });
    await expect(apiMe()).resolves.toEqual(usuario);
    expect(mock.get).toHaveBeenCalledWith("/auth/me");
  });

  it("propaga ApiError con credenciales inválidas en login", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 401,
      codigo: "CREDENCIALES_INVALIDAS",
      errorMessage: "credenciales inválidas",
    });
    mock.post.mockRejectedValueOnce(err);
    await expect(
      apiLogin({ username: "x", password: "y" }),
    ).rejects.toBe(err);
  });

  it("propaga error de sesión expirada en apiMe", async () => {
    const err = new ApiError({
      success: false,
      data: null,
      errorCode: 401,
      codigo: "TOKEN_EXPIRADO",
      errorMessage: "sesión expirada",
    });
    mock.get.mockRejectedValueOnce(err);
    await expect(apiMe()).rejects.toBe(err);
  });
});

describe("apiEliminar", () => {
  it("hace DELETE al path y devuelve data", async () => {
    mock.delete.mockResolvedValueOnce({
      data: { success: true, data: { ok: true } },
    });
    await expect(apiEliminar("/usuarios/3")).resolves.toEqual({ ok: true });
    expect(mock.delete).toHaveBeenCalledWith("/usuarios/3");
  });

  it("propaga errores", async () => {
    const err = new Error("red caída");
    mock.delete.mockRejectedValueOnce(err);
    await expect(apiEliminar("/x")).rejects.toBe(err);
  });
});
