// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/** Fila de la bandeja de notificaciones en tiempo real (SSE). */
export interface Notificacion {
	bandejaId: number;
	tipo: string;
	titulo: string;
	detalle: string | null;
	refTipo: string;
	refId: number;
	leidaEn: string | null;
	creadaEn: string;
}

export interface NoLeidasResponse {
	noLeidas: number;
}

/** Chat interno 1 a 1 y por grupos. */
export interface ChatParticipante {
	usuarioId: number;
	username: string;
}

export interface ChatUltimoMensaje {
	cuerpo: string;
	autorNombre: string;
	creadaEn: string;
}

export interface ChatConversacion {
	conversacionId: number;
	tipo: "DIRECTA" | "GRUPO";
	titulo: string;
	participantes: ChatParticipante[];
	ultimoMensaje: ChatUltimoMensaje | null;
	noLeidos: number;
}

export interface ChatMensaje {
	mensajeId: number;
	autorId: number;
	autorNombre: string;
	cuerpo: string;
	creadaEn: string;
}

export interface CrearDirectaRequest {
	otroUsuarioId: number;
}

export interface CrearGrupoRequest {
	titulo: string;
	miembroIds: number[];
}

export interface EnviarMensajeRequest {
	cuerpo: string;
}
