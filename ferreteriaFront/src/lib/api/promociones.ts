import http from "./client";
import type {
	Envelope,
	PageEnvelope,
	Promocion,
	PromocionRequest,
} from "./types";

export interface PromocionesFiltros {
	nombre?: string;
	tipo?: string;
	estado?: string;
	desde?: string;
	hasta?: string;
	page: number;
	size: number;
	sort?: string;
}

export async function apiPromociones(
	f: PromocionesFiltros,
): Promise<PageEnvelope<Promocion>> {
	const params: Record<string, string | number> = {
		page: f.page,
		size: f.size,
	};
	if (f.nombre) params.nombre = f.nombre;
	if (f.tipo) params.tipo = f.tipo;
	if (f.estado) params.estado = f.estado;
	if (f.desde) params.desde = f.desde;
	if (f.hasta) params.hasta = f.hasta;
	if (f.sort) params.sort = f.sort;
	const { data } = await http.get<PageEnvelope<Promocion>>("/promociones", {
		params,
	});
	return data;
}

export async function apiPromocion(id: number): Promise<Promocion> {
	const { data } = await http.get<Envelope<Promocion>>(`/promociones/${id}`);
	return data.data;
}

export async function apiCrearPromocion(
	body: PromocionRequest,
): Promise<Promocion> {
	const { data } = await http.post<Envelope<Promocion>>("/promociones", body);
	return data.data;
}

export async function apiActualizarPromocion(
	id: number,
	body: PromocionRequest,
): Promise<Promocion> {
	const { data } = await http.put<Envelope<Promocion>>(
		`/promociones/${id}`,
		body,
	);
	return data.data;
}

export async function apiEliminarPromocion(id: number): Promise<void> {
	await http.delete(`/promociones/${id}`);
}
