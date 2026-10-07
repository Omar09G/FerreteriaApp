import { beforeEach, describe, expect, it } from "vitest";

import type { Notificacion } from "@/lib/api/types";
import {
	clavesInvalidar,
	desdeEventoStream,
	rutaNotificacion,
	useNotificacionesStore,
} from "@/store/notificaciones";

function aviso(id: number, leida = false): Notificacion {
	return {
		bandejaId: id,
		tipo: "VENTA_TICKET",
		titulo: `Venta V-${id}`,
		detalle: "Total $100.00",
		refTipo: "VENTA",
		refId: id,
		leidaEn: leida ? new Date().toISOString() : null,
		creadaEn: new Date().toISOString(),
	};
}

beforeEach(() => {
	useNotificacionesStore.getState().reset();
});

describe("store notificaciones", () => {
	it("hidratar fija recientes y contador", () => {
		useNotificacionesStore.getState().hidratar([aviso(1), aviso(2)], 5);
		const s = useNotificacionesStore.getState();
		expect(s.items).toHaveLength(2);
		expect(s.noLeidas).toBe(5);
	});

	it("recibir suma una vez y deduplica por id", () => {
		const st = useNotificacionesStore.getState();
		expect(st.recibir(aviso(1))).toBe(true);
		expect(st.recibir(aviso(1))).toBe(false);
		const s = useNotificacionesStore.getState();
		expect(s.items).toHaveLength(1);
		expect(s.noLeidas).toBe(1);
	});

	it("marcarLeidaLocal descuenta solo si estaba sin leer", () => {
		const st = useNotificacionesStore.getState();
		st.hidratar([aviso(1), aviso(2, true)], 1);
		st.marcarLeidaLocal(1);
		expect(useNotificacionesStore.getState().noLeidas).toBe(0);
		st.marcarLeidaLocal(2);
		expect(useNotificacionesStore.getState().noLeidas).toBe(0);
	});

	it("marcarTodasLocal deja el contador en cero", () => {
		const st = useNotificacionesStore.getState();
		st.hidratar([aviso(1), aviso(2)], 2);
		st.marcarTodasLocal();
		const s = useNotificacionesStore.getState();
		expect(s.noLeidas).toBe(0);
		expect(s.items.every((i) => i.leidaEn !== null)).toBe(true);
	});
});

describe("mapeo del stream", () => {
	it("desdeEventoStream traduce id → bandejaId", () => {
		const n = desdeEventoStream({
			id: 9,
			tipo: "CORTE_CAJA",
			titulo: "Corte: cuadró",
			detalle: null,
			refTipo: "CORTE",
			refId: 3,
			creadaEn: new Date().toISOString(),
		});
		expect(n.bandejaId).toBe(9);
		expect(n.leidaEn).toBeNull();
	});

	it("rutaNotificacion lleva a la pantalla origen", () => {
		expect(rutaNotificacion({ refTipo: "VENTA" })).toBe("/ventas/historial");
		expect(rutaNotificacion({ refTipo: "CORTE" })).toBe("/caja/cajas");
		expect(rutaNotificacion({ refTipo: "NOMINA" })).toBe("/rrhh/nomina");
		expect(rutaNotificacion({ refTipo: "CHAT" })).toBe("/chat");
		expect(rutaNotificacion({ refTipo: "OTRO" })).toBe("/notificaciones");
	});

	it("clavesInvalidar refresca la pantalla relacionada", () => {
		expect(clavesInvalidar({ refTipo: "VENTA", tipo: "VENTA_TICKET" })).toContainEqual([
			"ventas",
		]);
		expect(clavesInvalidar({ refTipo: "STOCK", tipo: "STOCK_BAJO" })).toContainEqual([
			"stock",
		]);
	});
});
