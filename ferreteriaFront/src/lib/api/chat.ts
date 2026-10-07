import http from "./client";
import type {
	ChatConversacion,
	ChatMensaje,
	CrearDirectaRequest,
	CrearGrupoRequest,
	Envelope,
	EnviarMensajeRequest,
	PageEnvelope,
} from "./types";

export async function apiConversaciones(): Promise<ChatConversacion[]> {
	const { data } = await http.get<Envelope<ChatConversacion[]>>(
		"/chat/conversaciones",
	);
	return data.data;
}

export async function apiCrearDirecta(
	body: CrearDirectaRequest,
): Promise<ChatConversacion> {
	const { data } = await http.post<Envelope<ChatConversacion>>(
		"/chat/directas",
		body,
	);
	return data.data;
}

export async function apiCrearGrupo(
	body: CrearGrupoRequest,
): Promise<ChatConversacion> {
	const { data } = await http.post<Envelope<ChatConversacion>>(
		"/chat/grupos",
		body,
	);
	return data.data;
}

export async function apiHistorial(
	conversacionId: number,
	p: { page: number; size: number },
): Promise<PageEnvelope<ChatMensaje>> {
	const { data } = await http.get<PageEnvelope<ChatMensaje>>(
		`/chat/${conversacionId}/mensajes`,
		{ params: { page: p.page, size: p.size } },
	);
	return data;
}

export async function apiEnviarMensaje(
	conversacionId: number,
	body: EnviarMensajeRequest,
): Promise<ChatMensaje> {
	const { data } = await http.post<Envelope<ChatMensaje>>(
		`/chat/${conversacionId}/mensajes`,
		body,
	);
	return data.data;
}

export async function apiMarcarChatLeida(
	conversacionId: number,
): Promise<void> {
	await http.patch(`/chat/${conversacionId}/leida`);
}
