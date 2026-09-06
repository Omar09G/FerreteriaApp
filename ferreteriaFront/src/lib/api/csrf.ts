import type { AxiosInstance } from "axios";

export const CSRF_COOKIE = "XSRF-TOKEN";
export const CSRF_HEADER = "X-XSRF-TOKEN";
export const MUTATING_METHODS = new Set(["post", "put", "patch", "delete"]);

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
