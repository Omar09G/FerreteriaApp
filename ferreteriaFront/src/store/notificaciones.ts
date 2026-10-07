import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { create } from "zustand";

import { useToast } from "@/components/ui/Toast";
import {
	apiMarcarTodasLeidas,
	apiNoLeidas,
	apiNotificaciones,
	urlStreamNotificaciones,
} from "@/lib/api/notificaciones";
import type { Notificacion } from "@/lib/api/types";
import { useAuthStore } from "@/store/auth";

/** Tope de recientes en memoria (el historial completo vive en la página). */
const MAX_RECIENTES = 60;

interface NotificacionesState {
	items: Notificacion[];
	noLeidas: number;
	conectado: boolean;
	hidratar: (items: Notificacion[], noLeidas: number) => void;
	recibir: (n: Notificacion) => boolean;
	marcarLeidaLocal: (id: number) => void;
	marcarTodasLocal: () => void;
	setConectado: (v: boolean) => void;
	reset: () => void;
}

export const useNotificacionesStore = create<NotificacionesState>()((set, get) => ({
	items: [],
	noLeidas: 0,
	conectado: false,
	hidratar: (items, noLeidas) => set({ items: items.slice(0, MAX_RECIENTES), noLeidas }),
	recibir: (n) => {
		if (get().items.some((i) => i.bandejaId === n.bandejaId)) return false;
		set((s) => ({
			items: [n, ...s.items].slice(0, MAX_RECIENTES),
			noLeidas: s.noLeidas + 1,
		}));
		return true;
	},
	marcarLeidaLocal: (id) =>
		set((s) => ({
			items: s.items.map((i) => (i.bandejaId === id ? { ...i, leidaEn: new Date().toISOString() } : i)),
			noLeidas: Math.max(0, s.noLeidas - (s.items.some((i) => i.bandejaId === id && !i.leidaEn) ? 1 : 0)),
		})),
	marcarTodasLocal: () =>
		set((s) => ({
			items: s.items.map((i) => (i.leidaEn ? i : { ...i, leidaEn: new Date().toISOString() })),
			noLeidas: 0,
		})),
	setConectado: (conectado) => set({ conectado }),
	reset: () => set({ items: [], noLeidas: 0, conectado: false }),
}));

/** Evento crudo del stream (claves del backend) → Notificacion del front. */
export function desdeEventoStream(e: {
	id: number;
	tipo: string;
	titulo: string;
	detalle: string | null;
	refTipo: string;
	refId: number;
	creadaEn: string;
}): Notificacion {
	return {
		bandejaId: e.id,
		tipo: e.tipo,
		titulo: e.titulo,
		detalle: e.detalle,
		refTipo: e.refTipo,
		refId: e.refId,
		leidaEn: null,
		creadaEn: e.creadaEn,
	};
}

/** Ruta a la que lleva cada aviso al hacer clic. */
export function rutaNotificacion(n: Pick<Notificacion, "refTipo">): string {
	switch (n.refTipo) {
		case "VENTA":
			return "/ventas/historial";
		case "COMPRA":
		case "CUENTAS":
			return n.refTipo === "CUENTAS" ? "/compras/cuentas-pagar" : "/compras/compras";
		case "TURNO":
		case "CORTE":
			return "/caja/cajas";
		case "NOMINA":
			return "/rrhh/nomina";
		case "INFORME":
			return "/dashboard";
		case "COBRANZA":
			return "/ventas/cobranza";
		case "RENTAS":
			return "/ventas/rentas";
		case "STOCK":
			return "/inventario/stock";
		case "CHAT":
			return "/chat";
		default:
			return "/notificaciones";
	}
}

/** Query keys a invalidar cuando llega un aviso (refresco de pantallas). */
export function clavesInvalidar(n: Pick<Notificacion, "refTipo" | "tipo">): string[][] {
	const porRef: Record<string, string[][]> = {
		VENTA: [["ventas"], ["dashboard"]],
		COMPRA: [["compras"], ["dashboard"]],
		TURNO: [["caja"], ["dashboard"]],
		CORTE: [["caja"], ["dashboard"]],
		NOMINA: [["nomina"], ["dashboard"]],
		INFORME: [["dashboard"]],
		CUENTAS: [["cuentas-pagar"], ["dashboard"]],
		COBRANZA: [["cuentas-cobrar"], ["dashboard"]],
		RENTAS: [["rentas"], ["dashboard"]],
		STOCK: [["stock"], ["dashboard"]],
		CHAT: [["chat"]],
	};
	return porRef[n.refTipo] ?? [["dashboard"]];
}

/**
 * Suscripción al stream SSE. Llamar una vez en zona autenticada (AppShell):
 * hidrata contador + recientes, recibe push con toast e invalida queries.
 * Limpia el EventSource al desmontar o cerrar sesión (sin fugas).
 */
export function useNotificacionesStream(activo: boolean) {
	const queryClient = useQueryClient();
	const { info } = useToast();

	useEffect(() => {
		if (!activo) {
			useNotificacionesStore.getState().reset();
			return;
		}
		let cerrado = false;
		let src: EventSource;
		try {
			src = new EventSource(urlStreamNotificaciones(), { withCredentials: true });
		} catch {
			return;
		}

		apiNoLeidas()
			.then((noLeidas) => {
				if (cerrado) return;
				return apiNotificaciones({ page: 0, size: 10 }).then((pag) => {
					if (cerrado) return;
					useNotificacionesStore.getState().hidratar(pag.data, noLeidas);
				});
			})
			.catch(() => {
				// Sin bandeja (backend anterior o sin sesión): el stream lo intentará igual.
			});

		src.onopen = () => {
			if (!cerrado) useNotificacionesStore.getState().setConectado(true);
		};
		src.onerror = () => {
			// EventSource reintenta solo con backoff; solo marcamos estado.
			if (!cerrado) useNotificacionesStore.getState().setConectado(false);
		};
		src.addEventListener("notificacion", (ev) => {
			try {
				const n = desdeEventoStream(JSON.parse((ev as MessageEvent).data));
				const nuevo = useNotificacionesStore.getState().recibir(n);
				if (!nuevo) return;
				for (const key of clavesInvalidar(n)) {
					void queryClient.invalidateQueries({ queryKey: key });
				}
				// En el chat el hilo ya muestra el mensaje: sin toast.
				const enChat = window.location.pathname.startsWith("/chat");
				if (!(n.tipo === "CHAT_MENSAJE" && enChat)) {
					info(n.titulo);
				}
			} catch {
				// Evento malformado: se ignora sin romper el stream.
			}
		});

		return () => {
			cerrado = true;
			src.close();
			useNotificacionesStore.getState().setConectado(false);
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [activo]);
}

/** Marca todo como leído (API + estado local optimista con rollback). */
export async function marcarTodoLeido(): Promise<void> {
	useNotificacionesStore.getState().marcarTodasLocal();
	try {
		await apiMarcarTodasLeidas();
	} catch {
		// Rollback: rehidrata el contador real.
		const noLeidas = await apiNoLeidas().catch(() => 0);
		useNotificacionesStore.setState({ noLeidas });
	}
}

export function useAutenticadoParaStream(): boolean {
	const autenticado = useAuthStore((s) => s.autenticado);
	const sesionLista = useAuthStore((s) => s.sesionLista);
	return autenticado && sesionLista;
}
