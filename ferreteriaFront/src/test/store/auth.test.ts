import { renderHook, act } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
	tieneRol,
	useAutenticado,
	useAuthStore,
	useTieneRol,
} from "@/store/auth";
import type { MeResponse, TokenResponse } from "@/lib/api/types";

const ME: MeResponse = {
	usuarioId: 7,
	username: "cajera1",
	roles: ["VENDEDOR", "CAJERO"],
};

const TOKEN: TokenResponse = {
	accessToken: "tok-abc",
	expiresInSeconds: 900,
	usuario: ME,
};

function estadoInicial() {
  localStorage.clear();
  sessionStorage.clear();
  useAuthStore.setState({
    autenticado: false,
    usuario: null,
    challenge: null,
    lastActivityAt: 0,
  });
  // Limpia el rastro que persist pudo escribir en el setState anterior.
  localStorage.clear();
  sessionStorage.clear();
}

beforeEach(() => {
	vi.useFakeTimers();
	vi.setSystemTime(new Date("2026-05-01T12:00:00"));
	estadoInicial();
	vi.restoreAllMocks();
});

describe("auth store: acciones", () => {
	it("setSession activa sesión con usuario y marca actividad", () => {
		useAuthStore.getState().setSession(TOKEN);
		const s = useAuthStore.getState();
		expect(s.autenticado).toBe(true);
		expect(s.usuario).toEqual(ME);
		expect(s.lastActivityAt).toBe(Date.now());
	});

	it("setSession con accessToken vacío no autentica", () => {
		useAuthStore
			.getState()
			.setSession({ ...TOKEN, accessToken: "" });
		expect(useAuthStore.getState().autenticado).toBe(false);
	});

	it("setTokens solo mueve el marcador (tokens viven en cookie HttpOnly)", () => {
		useAuthStore.getState().setTokens("nuevo-access", null);
		expect(useAuthStore.getState().autenticado).toBe(true);
		expect(useAuthStore.getState().usuario).toBeNull();
		useAuthStore.getState().setTokens(null, null);
		expect(useAuthStore.getState().autenticado).toBe(false);
	});

	it("setMe confirma sesión contra /auth/me", () => {
		useAuthStore.getState().setMe(ME);
		const s = useAuthStore.getState();
		expect(s.autenticado).toBe(true);
		expect(s.usuario).toEqual(ME);
	});

	it("clearSession limpia marcador y usuario", () => {
		useAuthStore.getState().setSession(TOKEN);
		useAuthStore.getState().clearSession();
		const s = useAuthStore.getState();
		expect(s.autenticado).toBe(false);
		expect(s.usuario).toBeNull();
	});

	it("pingActivity refresca la marca sin tocar sesión ni usuario", () => {
		useAuthStore.getState().setSession(TOKEN);
		vi.setSystemTime(Date.now() + 5_000);
		useAuthStore.getState().pingActivity();
		const s = useAuthStore.getState();
		expect(s.lastActivityAt).toBe(Date.now());
		expect(s.autenticado).toBe(true);
		expect(s.usuario).toEqual(ME);
	});

  it("persist solo guarda autenticado+usuario (nunca tokens)", () => {
    useAuthStore.getState().setSession(TOKEN);
    const raw = localStorage.getItem("ferreteria-auth");
    expect(raw).not.toBeNull();
    const guardado = JSON.parse(raw as string);
    expect(guardado.state.autenticado).toBe(true);
    expect(guardado.state.usuario).toEqual(ME);
    expect(JSON.stringify(guardado.state)).not.toContain("accessToken");
    expect(JSON.stringify(guardado.state)).not.toContain("refreshToken");
    expect(JSON.stringify(guardado.state)).not.toContain("lastActivityAt");
  });

  it("setChallenge guarda el desafío y setSession lo limpia", () => {
    const challenge = { challengeId: "ch-1", canales: ["email"] };
    useAuthStore.getState().setChallenge(challenge as never);
    expect(useAuthStore.getState().challenge?.challengeId).toBe("ch-1");
    expect(sessionStorage.getItem("ferreteria-otp-challenge")).toContain("ch-1");
    useAuthStore.getState().setSession(TOKEN);
    expect(useAuthStore.getState().challenge).toBeNull();
    expect(sessionStorage.getItem("ferreteria-otp-challenge")).toBeNull();
  });
});

describe("auth store: selectores y roles", () => {
	it("useAutenticado refleja setSession/clearSession", () => {
		const { result } = renderHook(() => useAutenticado());
		expect(result.current).toBe(false);
		act(() => useAuthStore.getState().setSession(TOKEN));
		expect(result.current).toBe(true);
		act(() => useAuthStore.getState().clearSession());
		expect(result.current).toBe(false);
	});

	it("tieneRol: matriz de casos", () => {
		expect(tieneRol(null)).toBe(false);
		expect(tieneRol(undefined)).toBe(false);
		expect(tieneRol([])).toBe(false);
		// Sin requerimiento basta con tener algún rol.
		expect(tieneRol(["VENDEDOR"])).toBe(true);
		expect(tieneRol(["VENDEDOR"], [])).toBe(true);
		expect(tieneRol(["VENDEDOR"], ["ADMINISTRADOR"])).toBe(false);
		expect(tieneRol(["VENDEDOR"], ["VENDEDOR"])).toBe(true);
		expect(tieneRol(["VENDEDOR", "CAJERO"], ["ADMINISTRADOR", "CAJERO"])).toBe(
			true,
		);
	});

	it("useTieneRol lee los roles del usuario en sesión", () => {
		const { result, rerender } = renderHook(() =>
			useTieneRol(["ADMINISTRADOR"]),
		);
		expect(result.current).toBe(false);
		act(() => useAuthStore.getState().setMe(ME));
		rerender();
		expect(result.current).toBe(false);
		const { result: vendedor } = renderHook(() => useTieneRol(["VENDEDOR"]));
		expect(vendedor.current).toBe(true);
	});
});
