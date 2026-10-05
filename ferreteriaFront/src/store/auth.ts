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
	 *
	 * NUNCA se confía en un valor persistido: cada arranque parte en false
	 * y solo /auth/me (cookie vigente) o el OTP verificado lo activan.
	 */
	autenticado: boolean;
	usuario: MeResponse | null;
	/**
	 * True cuando el bootstrap ya revalidó la sesión contra el backend
	 * (/auth/me). Mientras sea false, los guards muestran un splash y NO
	 * deciden (evita mostrar el sistema con un flag persistido obsoleto).
	 * No se persiste.
	 */
	sesionLista: boolean;
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
	setSesionLista: () => void;
	pingActivity: () => void;
}

export const useAuthStore = create<AuthState>()(
	persist(
		(set) => ({
			autenticado: false,
			usuario: null,
			sesionLista: false,
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
			// Iniciar la fase 1 (desafío OTP) invalida cualquier marca de
			// sesión previa: sin código verificado nadie está autenticado.
			setChallenge: (challenge) => {
				try {
					if (challenge) sessionStorage.setItem(CHALLENGE_KEY, JSON.stringify(challenge));
					else sessionStorage.removeItem(CHALLENGE_KEY);
				} catch {
					// sessionStorage no disponible: solo memoria.
				}
				set(
					challenge
						? { challenge, autenticado: false, usuario: null }
						: { challenge },
				);
			},
			setSesionLista: () => set({ sesionLista: true }),
			pingActivity: () => set({ lastActivityAt: Date.now() }),
		}),
		{
			name: "ferreteria-auth",
			storage: createJSONStorage(() => localStorage),
			// Solo persistimos `usuario` (perfil cacheado para pintar el nombre
			// tras revalidar). `autenticado` JAMÁS se persiste ni se rehidrata:
			// cada arranque parte en false hasta que /auth/me (cookie vigente)
			// o el OTP verificado lo activen. Así recargar u otra pestaña no
			// puede reutilizar un flag obsoleto para entrar al sistema.
			// Los tokens NO se persisten: viven en cookies HttpOnly.
			partialize: (state) => ({
				usuario: state.usuario,
			}),
			merge: (persisted, current) => ({
				...current,
				...(persisted as Partial<AuthState>),
				autenticado: false,
				sesionLista: false,
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
