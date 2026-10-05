import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

import type { MeResponse, OtpChallenge, TokenResponse } from "@/lib/api/types";

const CHALLENGE_KEY = "ferreteria-otp-challenge";

function leerChallenge(): OtpChallenge | null {
	try {
		const raw = sessionStorage.getItem(CHALLENGE_KEY);
		return raw ? (JSON.parse(raw) as OtpChallenge) : null;
	} catch {
		return null;
	}
}

interface AuthState {
	/**
	 * Marcador de "estoy autenticado". El access y refresh tokens viven en
	 * cookies HttpOnly (browser-only) y JS NO puede leerlos: aquí solo
	 * guardamos lo que necesitamos para el UX (perfil + estado de actividad).
	 */
	autenticado: boolean;
	usuario: MeResponse | null;
	/**
	 * Desafío OTP pendiente (primera fase del login). No es secreto sensible
	 * (el código viaja por email/WhatsApp) pero tampoco se persiste en
	 * localStorage: vive en memoria + sessionStorage para sobrevivir recargas.
	 */
	challenge: OtpChallenge | null;
	/** Marca de la última interacción del usuario (epoch ms). */
	lastActivityAt: number;
	setSession: (token: TokenResponse) => void;
	setTokens: (accessToken: string | null, refreshToken: string | null) => void;
	setMe: (me: MeResponse) => void;
	clearSession: () => void;
	setChallenge: (challenge: OtpChallenge | null) => void;
	pingActivity: () => void;
}

export const useAuthStore = create<AuthState>()(
	persist(
		(set) => ({
			autenticado: false,
			usuario: null,
			challenge: typeof sessionStorage === "undefined" ? null : leerChallenge(),
			lastActivityAt: Date.now(),
			setSession: (token) => {
				try {
					sessionStorage.removeItem(CHALLENGE_KEY);
				} catch {
					// sessionStorage no disponible: solo memoria.
				}
				set({
					autenticado: Boolean(token.accessToken),
					usuario: token.usuario,
					challenge: null,
					lastActivityAt: Date.now(),
				});
			},
			// Los tokens no se persisten: viajan en cookies HttpOnly. Este método
			// solo actualiza el marcador de autenticación para compatibilidad con
			// flujos que lo invocan tras refresh.
			setTokens: (accessToken) =>
				set({ autenticado: Boolean(accessToken), lastActivityAt: Date.now() }),
			// FRONT-SEC-002: setMe confirma contra el backend (/auth/me) que la
			// cookie `at` sigue vigente. Si 401, el llamador hace clearSession.
			setMe: (me) =>
				set({ autenticado: true, usuario: me, lastActivityAt: Date.now() }),
			clearSession: () =>
				set({ autenticado: false, usuario: null, lastActivityAt: Date.now() }),
			setChallenge: (challenge) => {
				try {
					if (challenge) sessionStorage.setItem(CHALLENGE_KEY, JSON.stringify(challenge));
					else sessionStorage.removeItem(CHALLENGE_KEY);
				} catch {
					// sessionStorage no disponible: solo memoria.
				}
				set({ challenge });
			},
			pingActivity: () => set({ lastActivityAt: Date.now() }),
		}),
		{
			name: "ferreteria-auth",
			storage: createJSONStorage(() => localStorage),
			// Solo persistimos `usuario` (perfil cacheado) y `autenticado` como
			// pista de UX. El access y refresh tokens NO se persisten: viven en
			// cookies HttpOnly y se revalidan contra el backend en cada mount.
			// Al recargar, si la cookie `at` sigue vigente, /auth/me responde 200
			// y `setSession` reactiva el flag; si expiró, /auth/me responde 401
			// y `clearSession` lo limpia.
			partialize: (state) => ({
				autenticado: state.autenticado,
				usuario: state.usuario,
			}),
		},
	),
);

/** True si el store marca sesión activa. La fuente de verdad real es la
 * cookie HttpOnly: usar también un endpoint /auth/me para verificar. */
export function useAutenticado(): boolean {
	return useAuthStore((s) => s.autenticado);
}

export function tieneRol(
	roles: string[] | null | undefined,
	requerido?: string[],
): boolean {
	if (!roles || roles.length === 0) return false;
	if (!requerido || requerido.length === 0) return true;
	return roles.some((r) => requerido.includes(r));
}

export function useTieneRol(requerido?: string[]): boolean {
	return tieneRol(
		useAuthStore((s) => s.usuario?.roles),
		requerido,
	);
}
