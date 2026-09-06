import type { AxiosError } from "axios";

import { tFuera } from "@/i18n";
import type { ApiErrorBody } from "@/lib/api/types";

export class ApiError extends Error {
	readonly codigo: string;
	readonly status: number;
	readonly details?: ApiErrorBody["details"];
	readonly requestId?: string;
	readonly instance?: string;

	constructor(body: ApiErrorBody) {
		const ui =
			body.errorMessage || body.codigo || tFuera("errores.desconocido");
		super(ui);
		this.name = "ApiError";
		this.codigo = body.codigo || "ERROR_INTERNO";
		this.status = body.errorCode ?? 0;
		this.details = body.details;
		this.requestId = body.requestId;
		this.instance = body.instance;
	}

	/** Mensaje amigable mostrado en toasts, incluye referencia de soporte si existe. */
	mensajeParaUsuario(): string {
		const base = this.message;
		if (this.requestId || this.instance) {
			const ref = this.requestId
				? ` (folio: ${this.requestId})`
				: this.instance;
			return `${base}${ref}`;
		}
		return base;
	}
}

export function esApiError(e: unknown): e is ApiError {
	return e instanceof ApiError;
}

/** Mensaje para toasts: usa el error del backend (ApiError) o un genérico del front. */
export function mensajeError(e: unknown): string {
	if (esApiError(e)) return e.mensajeParaUsuario();
	if (e instanceof Error && e.message) return e.message;
	return tFuera("errores.generico");
}

export function transformar(error: AxiosError<ApiErrorBody>): Error {
	if (error.response?.data && typeof error.response.data.codigo === "string") {
		return new ApiError(error.response.data);
	}
	if (!error.response) {
		return new Error(tFuera("errores.servidor"));
	}
	return new Error(
		tFuera("errores.inesperado", { status: error.response.status }),
	);
}
