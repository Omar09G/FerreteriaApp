/**
 * Variables de entorno expuestas al runtime. Centralizar la lectura evita
 * que cada archivo importe `import.meta.env` y facilita el tipado.
 */

function num(name: string, def: number): number {
	const raw = (import.meta.env as Record<string, string | undefined>)[name];
	if (raw == null || raw === "") return def;
	const n = Number(raw);
	return Number.isFinite(n) && n >= 0 ? n : def;
}

function bool(name: string, def: boolean): boolean {
	const raw = (import.meta.env as Record<string, string | undefined>)[name];
	if (raw == null) return def;
	return raw === "true" || raw === "1";
}

export const env = {
	/** URL del backend (proxy en dev). */
	apiProxy:
		(import.meta.env.VITE_API_PROXY as string | undefined) ||
		"http://localhost:8080",
	/**
	 * Solo dev: salta el proxy de Vite y va directo al backend. Requiere modo
	 * `development` además de la flag, para que en producción se use el path
	 * relativo (`/api/v1`) y la cookie HttpOnly siga siendo same-origin.
	 */
	devSinProxy: import.meta.env.DEV && bool("VITE_DEV_SIN_PROXY", false),
	/** URL absoluta del backend (prioridad sobre VITE_API_PROXY). */
	apiUrl: import.meta.env.VITE_API_URL as string | undefined,
	/** Timeout de inactividad (ms). 0 = desactivado. */
	sessionTimeoutMs: num("VITE_SESSION_TIMEOUT_MS", 15 * 60 * 1000),
	/** Reintentos automáticos ante fallo de red / 5xx. */
	apiMaxRetries: num("VITE_API_MAX_RETRIES", 3),
	/** Backoff inicial entre reintentos (ms), se duplica cada intento. */
	apiRetryBackoffMs: num("VITE_API_RETRY_BACKOFF_MS", 1000),
	/** Timeout por request individual (ms). Sin esto, una conexión colgada
	 * bloquea el ciclo de reintentos indefinidamente. */
	apiTimeoutMs: num("VITE_API_TIMEOUT_MS", 30_000),
};
