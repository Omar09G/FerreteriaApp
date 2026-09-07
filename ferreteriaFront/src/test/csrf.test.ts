import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import { describe, expect, it } from "vitest";

import type { ApiErrorBody } from "@/lib/api/types";
import { isCsrfFailure } from "@/lib/api/csrf";

function axiosError(opts: {
	status?: number;
	data?: ApiErrorBody | null;
	method?: string;
}): AxiosError<ApiErrorBody> {
	const config = {
		method: (opts.method ?? "post").toLowerCase(),
		headers: {},
	} as unknown as InternalAxiosRequestConfig;
	const response: AxiosResponse<ApiErrorBody> | undefined =
		opts.status === undefined
			? undefined
			: ({
					status: opts.status,
					data: opts.data as ApiErrorBody,
					config,
					headers: {},
				} as unknown as AxiosResponse<ApiErrorBody>);
	const err = new Error("axios") as AxiosError<ApiErrorBody>;
	(err as unknown as { config: InternalAxiosRequestConfig }).config = config;
	(err as unknown as { response: AxiosResponse<ApiErrorBody> | undefined }).response =
		response;
	(err as unknown as { isAxiosError: boolean }).isAxiosError = true;
	return err;
}

describe("isCsrfFailure (FRONT-SEC-001)", () => {
	it("403 con codigo CSRF_TOKEN_INVALID en POST → sí es CSRF (se reintenta)", () => {
		const err = axiosError({
			status: 403,
			data: {
				success: false,
				data: null,
				errorCode: 403,
				codigo: "CSRF_TOKEN_INVALID",
				errorMessage: "token inválido",
			},
			method: "post",
		});
		expect(isCsrfFailure(err, "post")).toBe(true);
	});

	it("403 con codigo CSRF_INVALIDO en PUT → sí es CSRF", () => {
		const err = axiosError({
			status: 403,
			data: {
				success: false,
				data: null,
				errorCode: 403,
				codigo: "CSRF_INVALIDO",
				errorMessage: "csrf inválido",
			},
			method: "put",
		});
		expect(isCsrfFailure(err, "put")).toBe(true);
	});

	it("403 sin body en POST → sí es CSRF (rechazo bare de Spring Security)", () => {
		const err = axiosError({ status: 403, data: null, method: "post" });
		expect(isCsrfFailure(err, "post")).toBe(true);
	});

	it("403 con codigo ACCESO_DENEGADO en POST → NO es CSRF (no retry → evita loop)", () => {
		const err = axiosError({
			status: 403,
			data: {
				success: false,
				data: null,
				errorCode: 403,
				codigo: "ACCESO_DENEGADO",
				errorMessage: "sin permisos",
			},
			method: "post",
		});
		expect(isCsrfFailure(err, "post")).toBe(false);
	});

	it("403 con codigo RECURSO_NO_ENCONTRADO → NO es CSRF", () => {
		const err = axiosError({
			status: 403,
			data: {
				success: false,
				data: null,
				errorCode: 403,
				codigo: "RECURSO_NO_ENCONTRADO",
				errorMessage: "x",
			},
			method: "delete",
		});
		expect(isCsrfFailure(err, "delete")).toBe(false);
	});

	it("500 → NO es CSRF (no aplica la clasificación)", () => {
		const err = axiosError({
			status: 500,
			data: {
				success: false,
				data: null,
				errorCode: 500,
				codigo: "ERROR_INTERNO",
				errorMessage: "boom",
			},
			method: "post",
		});
		expect(isCsrfFailure(err, "post")).toBe(false);
	});

	it("401 → NO es CSRF (el refresh se maneja en otra rama)", () => {
		const err = axiosError({
			status: 401,
			data: {
				success: false,
				data: null,
				errorCode: 401,
				codigo: "TOKEN_EXPIRADO",
				errorMessage: "x",
			},
			method: "post",
		});
		expect(isCsrfFailure(err, "post")).toBe(false);
	});

	it("403 sin body en GET → NO es CSRF (CSRF solo aplica a mutating)", () => {
		const err = axiosError({ status: 403, data: null, method: "get" });
		expect(isCsrfFailure(err, "get")).toBe(false);
	});

	it("sin response (red caída) → NO es CSRF", () => {
		const err = axiosError({ method: "post" });
		expect(isCsrfFailure(err, "post")).toBe(false);
	});
});