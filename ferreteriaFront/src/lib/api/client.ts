import axios, {
	type AxiosError,
	type AxiosInstance,
	type InternalAxiosRequestConfig,
} from "axios";

import { tFuera } from "@/i18n";
import { useAuthStore } from "@/store/auth";
import { env } from "@/config/env";
import type { ApiErrorBody } from "@/lib/api/types";
import {
	ApiError,
	transformar,
} from "@/lib/api/errors";
import {
	CSRF_COOKIE,
	CSRF_HEADER,
	MUTATING_METHODS,
	readCookie,
	ensureCsrfCookie as ensureCsrfCookieInternal,
} from "@/lib/api/csrf";
import {
	doRefresh,
	puedeRefrescar,
	type RetryMeta,
} from "@/lib/api/refresh";

// ── Re-exports de fachada (compatibilidad con imports existentes) ──────────
export { ApiError, esApiError, mensajeError, transformar } from "@/lib/api/errors";
export {
	CSRF_COOKIE,
	CSRF_HEADER,
	MUTATING_METHODS,
	readCookie,
} from "@/lib/api/csrf";
export {
	doRefresh,
	refreshAccess,
	puedeRefrescar,
	type RetryMeta,
} from "@/lib/api/refresh";

function nuevoRequestId(): string {
	if (
		typeof crypto !== "undefined" &&
		typeof crypto.randomUUID === "function"
	) {
		return crypto.randomUUID();
	}
	return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

const apiUrl = env.apiUrl;
const proxyHost = env.apiProxy;
const BASE_URL =
	apiUrl || (env.devSinProxy ? `${proxyHost}/api/v1` : "/api/v1");

const http: AxiosInstance = axios.create({
	baseURL: BASE_URL,
	// both access and refresh tokens live in HttpOnly cookies; the browser
	// attaches them automatically on same-origin requests.
	withCredentials: true,
	// Timeout por intento individual: sin esto, una conexión colgada
	// bloquearía el ciclo de reintentos indefinidamente.
	timeout: env.apiTimeoutMs,
});

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
	config.headers.set("X-Request-Id", nuevoRequestId());
	// CSRF double-submit: para métodos que mutan estado, copiamos el valor de
	// la cookie XSRF-TOKEN (no HttpOnly, JS-readable) en el header
	// X-XSRF-TOKEN. Spring lo valida contra la cookie.
	const method = (config.method ?? "get").toLowerCase();
	if (MUTATING_METHODS.has(method)) {
		const csrf = readCookie(CSRF_COOKIE);
		if (csrf) {
			config.headers.set(CSRF_HEADER, csrf);
		}
	}
	return config;
});

function sleep(ms: number): Promise<void> {
	return new Promise((res) => window.setTimeout(res, ms));
}

/**
 * ¿Tiene sentido reintentar este error?
 * - Sin respuesta: red caída / timeout / CORS → sí.
 * - 5xx: backend sobrecargado / caído → sí.
 * - 4xx: input inválido / sin permisos → no, reintentar solo retrasa el
 *   feedback sin cambiar el resultado (excepto el caso 401 manejado abajo).
 */
function isRetryable(error: AxiosError): boolean {
	if (!error.response) return true;
	return error.response.status >= 500;
}

http.interceptors.response.use(
	(res) => res,
	async (error: AxiosError<ApiErrorBody>) => {
		const original = error.config as
			| (InternalAxiosRequestConfig & { _retry?: RetryMeta })
			| undefined;
		if (!original) throw transformar(error);

		const meta: RetryMeta = original._retry ?? {
			retries: 0,
			refreshed: false,
			csrfRefreshed: false,
		};

		// ── 0) CSRF mismatch: re-leer el token una vez y reintentar ──
		// El XSRF-TOKEN puede haber rotado entre requests; si la cookie aún
		// tiene valor, copiar el nuevo al header suele bastar. Si no, el caller
		// debe llamar /csrf-init antes del próximo mutating call.
		const esCsrf =
			(error.response?.status === 403 &&
				error.response?.data?.codigo === "CSRF_TOKEN_INVALID") ||
			// Spring suele devolver 403 sin cuerpo para CSRF; cualquier 403 en
			// un mutating method sin mensaje específico lo tratamos como CSRF.
			(error.response?.status === 403 &&
				MUTATING_METHODS.has((original.method ?? "get").toLowerCase()));

		if (esCsrf && !meta.csrfRefreshed) {
			meta.csrfRefreshed = true;
			original._retry = meta;
			const csrf = readCookie(CSRF_COOKIE);
			if (csrf) {
				original.headers.set(CSRF_HEADER, csrf);
				return http(original);
			}
		}

		// ── 1) Reintento con backoff exponencial (red / 5xx) ──
		if (isRetryable(error) && meta.retries < env.apiMaxRetries) {
			meta.retries += 1;
			original._retry = meta;
			const backoff = env.apiRetryBackoffMs * Math.pow(2, meta.retries - 1);
			await sleep(backoff);
			return http(original);
		}

		// ── 2) 401 / token expirado: refresh + un reintento ──
		if (puedeRefrescar(error, original, meta)) {
			meta.refreshed = true;
			original._retry = meta;
			try {
				await doRefresh(http);
				// El browser ya rotó la cookie `at` automáticamente. Re-leemos
				// CSRF por si también rotó (defensivo).
				const csrf = readCookie(CSRF_COOKIE);
				if (csrf) original.headers.set(CSRF_HEADER, csrf);
				return http(original);
			} catch {
				useAuthStore.getState().clearSession();
				return Promise.reject(
					new ApiError({
						success: false,
						data: null,
						errorCode: 401,
						codigo: "TOKEN_EXPIRADO",
						errorMessage: tFuera("errores.sesionExpirada"),
					}),
				);
			}
		}
		throw transformar(error);
	},
);

export default http;

/**
 * Garantiza que la cookie XSRF-TOKEN exista. Llamar al montar la app antes
 * del primer mutating request (incluido /auth/login). Si el token aún no
 * está, hace un GET a /csrf-init que el backend aprovecha para emitir la
 * cookie.
 * Fachada que delega a csrf.ts con el http local inyectado.
 */
export async function ensureCsrfCookie(): Promise<void> {
	return ensureCsrfCookieInternal(http);
}
