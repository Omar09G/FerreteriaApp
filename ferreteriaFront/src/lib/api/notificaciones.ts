import { env } from "@/config/env";

import http from "./client";
import type {
	Envelope,
	NoLeidasResponse,
	Notificacion,
	PageEnvelope,
} from "./types";

export async function apiNotificaciones(p: {
	page: number;
	size: number;
}): Promise<PageEnvelope<Notificacion>> {
	const { data } = await http.get<PageEnvelope<Notificacion>>("/notificaciones", {
		params: { page: p.page, size: p.size },
	});
	return data;
}

export async function apiNoLeidas(): Promise<number> {
	const { data } = await http.get<Envelope<NoLeidasResponse>>(
		"/notificaciones/no-leidas",
	);
	return data.data.noLeidas;
}

export async function apiMarcarLeida(id: number): Promise<Notificacion> {
	const { data } = await http.patch<Envelope<Notificacion>>(
		`/notificaciones/${id}/leida`,
	);
	return data.data;
}

export async function apiMarcarTodasLeidas(): Promise<void> {
	await http.patch("/notificaciones/leidas");
}

/**
 * URL del stream SSE. Espeja la resolución de client-base: path relativo
 * (mismo origen, las cookies HttpOnly viajan solas) salvo dev sin proxy.
 */
export function urlStreamNotificaciones(): string {
	const base =
		env.apiUrl || (env.devSinProxy ? `${env.apiProxy}/api/v1` : "/api/v1");
	return `${base}/notificaciones/stream`;
}
