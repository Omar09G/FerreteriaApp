import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { ToastProvider } from "@/components/ui/Toast";
import { useAuthStore } from "@/store/auth";
import {
	useNotificacionesStore,
	useNotificacionesStream,
} from "@/store/notificaciones";

vi.mock("@/lib/api/notificaciones", async (importOriginal) => {
	const real =
		await importOriginal<typeof import("@/lib/api/notificaciones")>();
	return {
		...real,
		apiNoLeidas: vi.fn().mockResolvedValue(0),
		apiNotificaciones: vi.fn().mockResolvedValue({ data: [] }),
	};
});

type Handler = (ev: { data: string }) => void;

class FuenteFalsa {
	static instancias: FuenteFalsa[] = [];
	handlers: Record<string, Handler[]> = {};
	onopen: (() => void) | null = null;
	onerror: (() => void) | null = null;
	close = vi.fn();
	url: string;

	constructor(url: string) {
		this.url = url;
		FuenteFalsa.instancias.push(this);
	}

	addEventListener(tipo: string, h: Handler) {
		(this.handlers[tipo] ??= []).push(h);
	}

	emitir(tipo: string, datos: unknown) {
		for (const h of this.handlers[tipo] ?? []) h({ data: JSON.stringify(datos) });
	}
}

vi.stubGlobal("EventSource", FuenteFalsa as unknown as typeof EventSource);

function Sonda() {
	useNotificacionesStream(true);
	return null;
}

function renderSonda() {
	const qc = new QueryClient();
	return render(
		<ToastProvider>
			<QueryClientProvider client={qc}>
				<Sonda />
			</QueryClientProvider>
		</ToastProvider>,
	);
}

const UBICACION = window.location;

function simularNavegacion(): string[] {
	const redirecciones: string[] = [];
	Object.defineProperty(window, "location", {
		configurable: true,
		value: {
			get pathname() {
				return "/dashboard";
			},
			get href() {
				return "http://localhost:5173/dashboard";
			},
			set href(v: string) {
				redirecciones.push(v);
			},
		},
	});
	return redirecciones;
}

beforeEach(() => {
	vi.clearAllMocks();
	FuenteFalsa.instancias = [];
	localStorage.clear();
	sessionStorage.clear();
	useAuthStore.setState({
		autenticado: true,
		usuario: null,
		challenge: null,
		lastActivityAt: 0,
	});
	useNotificacionesStore.setState({ items: [], noLeidas: 2, conectado: false });
	Object.defineProperty(window, "location", {
		configurable: true,
		value: UBICACION,
	});
});

describe("useNotificacionesStream: SESION_DESPLAZADA", () => {
	it("cierra la sesión al instante, corta el stream y va al login con aviso", async () => {
		const redirecciones = simularNavegacion();
		renderSonda();
		await waitFor(() => expect(FuenteFalsa.instancias).toHaveLength(1));
		const src = FuenteFalsa.instancias[0];
		src.emitir("notificacion", {
			id: 999,
			tipo: "SESION_DESPLAZADA",
			titulo: "Sesión cerrada en este equipo",
			detalle: "Iniciaste sesión en otro equipo desde Guadalajara. Esta sesión se cerró.",
			refTipo: "SESION",
			refId: 42,
			creadaEn: new Date().toISOString(),
		});

		expect(src.close).toHaveBeenCalledOnce();
		expect(useAuthStore.getState().autenticado).toBe(false);
		expect(useNotificacionesStore.getState().items).toEqual([]);
		expect(useNotificacionesStore.getState().noLeidas).toBe(0);
		expect(redirecciones).toEqual(["/login?cerrada=1"]);
	});

	it("un aviso normal no cierra la sesión", async () => {
		const redirecciones = simularNavegacion();
		renderSonda();
		await waitFor(() => expect(FuenteFalsa.instancias).toHaveLength(1));
		FuenteFalsa.instancias[0].emitir("notificacion", {
			id: 7,
			tipo: "VENTA_TICKET",
			titulo: "Venta registrada",
			detalle: "Total $100.00",
			refTipo: "VENTA",
			refId: 5,
			creadaEn: new Date().toISOString(),
		});

		await waitFor(() =>
			expect(useNotificacionesStore.getState().items).toHaveLength(1),
		);
		expect(useAuthStore.getState().autenticado).toBe(true);
		expect(redirecciones).toEqual([]);
	});
});
