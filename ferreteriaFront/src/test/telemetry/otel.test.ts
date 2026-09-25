/**
 * OTel con VITE_OTEL_ENABLED=false: el módulo debe importarse sin efectos
 * (modo noop), instalar el guard anti-startTime y exponer getTracer().
 *
 * La rama habilitada (exporters OTLP + providers + instrumentaciones +
 * Web Vitals + listeners de métricas) NO se cubre aquí: requiere
 * VITE_OTEL_ENABLED=true, un collector OTLP real y PerformanceEntries de
 * navegador; forzarla en jsdom implicaría mockear ~7 paquetes SDK y simular
 * exportaciones de red con intervalos vivos, fuera del alcance sin backend.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

let otel: typeof import("@/telemetry/otel");

beforeEach(async () => {
	vi.stubEnv("VITE_OTEL_ENABLED", "false");
	vi.resetModules();
	window.onerror = null;
	vi.spyOn(console, "info").mockImplementation(() => {});
	vi.spyOn(console, "warn").mockImplementation(() => {});
	otel = await import("@/telemetry/otel");
});

afterEach(() => {
	vi.unstubAllEnvs();
	vi.restoreAllMocks();
	window.onerror = null;
});

function eventoError(msg: string, stack: string): boolean {
	const err = new Error(msg);
	err.stack = stack;
	const ev = new ErrorEvent("error", {
		message: msg,
		error: err,
		cancelable: true,
	});
	return window.dispatchEvent(ev);
}

describe("otel (desactivado)", () => {
	it("informa el modo noop al importar", () => {
		expect(console.info).toHaveBeenCalledWith(
			'[OTel] Desactivado (VITE_OTEL_ENABLED !== "true")',
		);
	});

	it("expone un tracer no-op vía getTracer()", () => {
		const tracer = otel.getTracer();
		expect(tracer).toBeDefined();
		expect(typeof tracer.startSpan).toBe("function");
	});

	it("suprime el bug startTime/reportAllChanges en window error", () => {
		const cancelado = eventoError(
			"Cannot read properties of undefined (reading 'startTime')",
			"TypeError\n    at reportAllChanges (web-vitals.js:2:1)",
		);
		expect(cancelado).toBe(false);
		expect(console.warn).toHaveBeenCalledWith(
			"[OTel] suppressed startTime error",
			expect.stringContaining("startTime"),
		);
	});

	it("deja pasar errores ajenos al bug", () => {
		const cancelado = eventoError("otro fallo", "Error\n    at otro (app.js:1:1)");
		expect(cancelado).toBe(true);
		expect(console.warn).not.toHaveBeenCalledWith(
			"[OTel] suppressed startTime error",
			expect.anything(),
		);
	});

	it("suprime el rechazo startTime en unhandledrejection", () => {
		const err = new Error(
			"Cannot read properties of undefined (reading 'startTime')",
		);
		err.stack = `${err.stack}\n    at reportAllChanges (x:1:1)`;
		const ev = new Event("unhandledrejection", {
			cancelable: true,
		}) as Event & { reason?: unknown };
		ev.reason = err;
		expect(window.dispatchEvent(ev)).toBe(false);
		expect(console.warn).toHaveBeenCalledWith(
			"[OTel] suppressed startTime rejection",
			expect.stringContaining("startTime"),
		);
	});

	it("window.onerror suprime el bug y delega lo demás al handler previo", () => {
		const previo = vi.fn();
		// Reinstala el guard con un handler previo conocido.
		window.onerror = previo;
		return (async () => {
			vi.resetModules();
			const mod = await import("@/telemetry/otel");
			expect(mod.getTracer).toBeDefined();
			const suprimido = window.onerror?.(
				"startTime reportAllChanges",
				"",
				0,
				0,
				new Error("startTime reportAllChanges"),
			);
			expect(suprimido).toBe(true);
			expect(previo).not.toHaveBeenCalled();
			const ajeno = window.onerror?.("otro", "", 1, 1, new Error("otro"));
			expect(previo).toHaveBeenCalled();
			expect(ajeno).not.toBe(true);
		})();
	});
});
