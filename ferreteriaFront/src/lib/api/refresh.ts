import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from "axios";

import type { ApiErrorBody, Envelope, TokenResponse } from "@/lib/api/types";

export interface RetryMeta {
	/** cuántos reintentos van consumidos en esta request */
	retries: number;
	/** ya se intentó refresh+retry una vez para esta request */
	refreshed: boolean;
	/** ya se intentó re-leer el CSRF token una vez para esta request */
	csrfRefreshed: boolean;
}

let refreshing: Promise<string> | null = null;

/**
 * Ejecuta el refresh del access token deduplicando llamadas concurrentes.
 * Usa directamente el `http` inyectado para evitar ciclo con endpoints.ts
 * (endpoints importa http desde client).
 */
export async function doRefresh(http: AxiosInstance): Promise<string> {
	if (!refreshing) {
		refreshing = (async () => {
			try {
				const { data } = await http.post<Envelope<TokenResponse>>(
					"/auth/refresh",
					{},
				);
				return data.data.accessToken;
			} finally {
				refreshing = null;
			}
		})();
	}
	return refreshing;
}

/** Alias del paso previo (`refreshAccess` en client.ts original). */
export const refreshAccess = doRefresh;

export function puedeRefrescar(
	error: AxiosError<ApiErrorBody>,
	original: InternalAxiosRequestConfig & { _retry?: RetryMeta },
	meta: RetryMeta,
): boolean {
	const es401 =
		error.response?.status === 401 ||
		error.response?.data?.codigo === "TOKEN_EXPIRADO" ||
		error.response?.data?.codigo === "CREDENCIALES_INVALIDAS";

	return (
		es401 &&
		!meta.refreshed &&
		!original.url?.includes("/auth/refresh") &&
		!original.url?.includes("/auth/login")
	);
}

/** Resetea el estado interno (útil para tests que quieran aislar el módulo). */
export function _resetRefreshingForTests(): void {
	refreshing = null;
}
