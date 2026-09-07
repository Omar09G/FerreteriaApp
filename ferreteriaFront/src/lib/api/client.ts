/**
 * Fachada de compatibilidad — el http base vive en client-base.ts.
 * Este archivo re-exporta para no romper imports existentes (`@/lib/api/client`).
 */
export { default } from "@/lib/api/client-base";
export { default as http, ensureCsrfCookie } from "@/lib/api/client-base";

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
