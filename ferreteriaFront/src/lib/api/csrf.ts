import type { AxiosError, AxiosInstance } from "axios";

import type { ApiErrorBody } from "@/lib/api/types";

export const CSRF_COOKIE = "XSRF-TOKEN";
export const CSRF_HEADER = "X-XSRF-TOKEN";
export const MUTATING_METHODS = new Set(["post", "put", "patch", "delete"]);

/**
 * Códigos de error que el backend puede emitir para un fallo CSRF. Spring no
 * tiene un `ErrorCode` propio de CSRF (lo rechaza antes del controller), así
 * que aceptamos tanto el que ya usaba el front (`CSRF_TOKEN_INVALID`) como
 * el nombre idiomático (`CSRF_INVALIDO`) por si el backend lo estandariza.
 */
const CSRF_ERROR_CODES: ReadonlySet<string> = new Set([
	"CSRF_TOKEN_INVALID",
	"CSRF_INVALIDO",
]);

/**
 * FRONT-SEC-001: clasificación narrow de "este 403 es CSRF, re-leer la cookie".
 *
 * Reglas — todo otro 403 (permisos, ACL, business) NO se reintenta:
 *   1. La respuesta trae `codigo` en el envelope y ese código está en
 *      {@link CSRF_ERROR_CODES} (señal explícita).
 *   2. Spring Security, cuando rechaza por CSRF, devuelve 403 con cuerpo
 *      vacío (no pasa por el `@RestControllerAdvice`, así que no hay
 *      envelope). En ese caso + método mutating → probable CSRF.
 *
 * Excluimos explícitamente respuestas con `codigo` conocido no-CSRF (ej.
 * `ACCESO_DENEGADO`, `RECURSO_NO_ENCONTRADO`) para no entrar en loops sobre
 * denegaciones reales.
 */
export function isCsrfFailure(
	error: AxiosError<ApiErrorBody>,
	method: string | undefined,
): boolean {
	if (error.response?.status !== 403) return false;
	const body = error.response.data;
	const codigo = body && typeof body.codigo === "string" ? body.codigo : null;

	if (codigo !== null) {
		// Hay envelope: si el codigo es CSRF → sí; cualquier otro → no.
		return CSRF_ERROR_CODES.has(codigo);
	}

	// Sin envelope: solo en mutating. CSRF en GET no tiene sentido (Spring
	// lo permite en métodos seguros), pero defendemos contra el caso raro
	// de un endpoint custom.
	const m = (method ?? "get").toLowerCase();
	return MUTATING_METHODS.has(m);
}

/** Lee el valor de una cookie por nombre. Devuelve null si no existe. */
export function readCookie(name: string): string | null {
	if (typeof document === "undefined") return null;
	const prefix = `${encodeURIComponent(name)}=`;
	const parts = document.cookie ? document.cookie.split(";") : [];
	for (const raw of parts) {
		const c = raw.trim();
		if (c.startsWith(prefix)) {
			return decodeURIComponent(c.substring(prefix.length));
		}
	}
	return null;
}

export function getCsrfToken(): string | null {
	return readCookie(CSRF_COOKIE);
}

/**
 * Garantiza que la cookie XSRF-TOKEN exista. Llamar al montar la app antes
 * del primer mutating request (incluido /auth/login). Si el token aún no
 * está, hace un GET a /csrf-init que el backend aprovecha para emitir la
 * cookie.
 */
export async function ensureCsrfCookie(http: AxiosInstance): Promise<void> {
	if (readCookie(CSRF_COOKIE)) return;
	try {
		await http.get("/auth/csrf-init");
	} catch {
		// best-effort: si falla, el siguiente mutating request obtendrá 403 y
		// el caller verá el error. Pero no bloqueamos el arranque por esto.
	}
}
