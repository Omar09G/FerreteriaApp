import axios, {
	type AxiosError,
	type AxiosInstance,
	type InternalAxiosRequestConfig,
} from "axios";

import { tFuera } from "@/i18n";
import { useAuthStore } from "@/store/auth";
import { env } from "@/config/env";
import type { ApiErrorBody } from "@/lib/api/types";
import { ApiError, transformar } from "@/lib/api/errors";
import {
	CSRF_COOKIE,
	CSRF_HEADER,
	MUTATING_METHODS,
	isCsrfFailure,
	readCookie,
	ensureCsrfCookie as ensureCsrfCookieInternal,
} from "@/lib/api/csrf";
import { doRefresh, puedeRefrescar, type RetryMeta } from "@/lib/api/refresh";

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
	withCredentials: true,
	timeout: env.apiTimeoutMs,
});

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
	config.headers.set("X-Request-Id", nuevoRequestId());
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

		const esCsrf = isCsrfFailure(error, original.method);

		if (esCsrf && !meta.csrfRefreshed) {
			meta.csrfRefreshed = true;
			original._retry = meta;
			const csrf = readCookie(CSRF_COOKIE);
			if (csrf) {
				original.headers.set(CSRF_HEADER, csrf);
				return http(original);
			}
		}

		if (isRetryable(error) && meta.retries < env.apiMaxRetries) {
			meta.retries += 1;
			original._retry = meta;
			const backoff = env.apiRetryBackoffMs * Math.pow(2, meta.retries - 1);
			await sleep(backoff);
			return http(original);
		}

		if (puedeRefrescar(error, original, meta)) {
			meta.refreshed = true;
			original._retry = meta;
			try {
				await doRefresh(http);
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

export async function ensureCsrfCookie(): Promise<void> {
	return ensureCsrfCookieInternal(http);
}
