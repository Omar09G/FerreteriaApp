import { beforeEach, describe, expect, it } from "vitest";
import { useUiStore } from "@/store/ui";
import {
	aLocalDate,
	formatoFecha,
	formatoFechaHora,
	formatoMoneda,
	formatoNumero,
	formatoPorcentaje,
	getFechaActualLocal,
	getFechaActualLocalWithTime,
	getFechaHoraActualLocal,
	getHoraActualLocal,
	hoyLocal,
} from "@/lib/format";

const MXN = new Intl.NumberFormat("es-MX", {
	style: "currency",
	currency: "MXN",
	minimumFractionDigits: 2,
	maximumFractionDigits: 2,
});

beforeEach(() => {
	useUiStore.getState().setIdioma("es");
});

describe("formatoMoneda", () => {
	it("devuelve — para null/undefined/cadena vacía", () => {
		expect(formatoMoneda(null)).toBe("—");
		expect(formatoMoneda(undefined)).toBe("—");
		expect(formatoMoneda("")).toBe("—");
	});

	it("devuelve — para valores no numéricos", () => {
		expect(formatoMoneda("abc")).toBe("—");
	});

	it("formatea números como MXN es-MX", () => {
		expect(formatoMoneda(1234.5)).toBe(MXN.format(1234.5));
		expect(formatoMoneda(1234.5)).toContain("$");
		expect(formatoMoneda(0)).toBe(MXN.format(0));
		expect(formatoMoneda(-50)).toBe(MXN.format(-50));
	});

	it("acepta strings numéricos", () => {
		expect(formatoMoneda("99.9")).toBe(MXN.format(99.9));
	});
});

describe("formatoNumero", () => {
	it("devuelve — para null/undefined/vacío/NaN", () => {
		expect(formatoNumero(null)).toBe("—");
		expect(formatoNumero(undefined)).toBe("—");
		expect(formatoNumero("")).toBe("—");
		expect(formatoNumero("xyz")).toBe("—");
	});

	it("formatea según idioma es", () => {
		useUiStore.getState().setIdioma("es");
		const esperado = new Intl.NumberFormat("es-MX", {
			maximumFractionDigits: 2,
		}).format(1234.567);
		expect(formatoNumero(1234.567)).toBe(esperado);
	});

	it("formatea según idioma en", () => {
		useUiStore.getState().setIdioma("en");
		const esperado = new Intl.NumberFormat("en-US", {
			maximumFractionDigits: 2,
		}).format(1234.567);
		expect(formatoNumero(1234.567)).toBe(esperado);
		expect(formatoNumero("42")).toBe(
			new Intl.NumberFormat("en-US", { maximumFractionDigits: 2 }).format(42),
		);
	});
});

describe("formatoFecha", () => {
	it("devuelve — para null/undefined/vacío", () => {
		expect(formatoFecha(null)).toBe("—");
		expect(formatoFecha(undefined)).toBe("—");
		expect(formatoFecha("")).toBe("—");
	});

	it("formatea yyyy-MM-dd según idioma", () => {
		useUiStore.getState().setIdioma("es");
		const esperado = new Intl.DateTimeFormat("es-MX", {
			year: "numeric",
			month: "2-digit",
			day: "2-digit",
		}).format(new Date(2026, 2, 5));
		expect(formatoFecha("2026-03-05")).toBe(esperado);
	});

	it("acepta datetime y usa solo la parte de fecha", () => {
		expect(formatoFecha("2026-03-05T18:30:00Z")).toBe(
			formatoFecha("2026-03-05"),
		);
	});

	it("devuelve el input si no es parseable", () => {
		expect(formatoFecha("no-fecha")).toBe("no-fecha");
		expect(formatoFecha("abcd-ef-gh")).toBe("abcd-ef-gh");
	});
});

describe("formatoFechaHora", () => {
	it("devuelve — para null/undefined/vacío", () => {
		expect(formatoFechaHora(null)).toBe("—");
		expect(formatoFechaHora(undefined)).toBe("—");
		expect(formatoFechaHora("")).toBe("—");
	});

	it("formatea un Instant UTC a fecha/hora local", () => {
		useUiStore.getState().setIdioma("es");
		const esperado = new Intl.DateTimeFormat("es-MX", {
			day: "2-digit",
			month: "2-digit",
			year: "numeric",
			hour: "2-digit",
			minute: "2-digit",
		}).format(new Date("2026-01-15T12:00:00Z"));
		expect(formatoFechaHora("2026-01-15T12:00:00Z")).toBe(esperado);
	});

	it("devuelve el input si es inválido", () => {
		expect(formatoFechaHora("esto-no-es-fecha")).toBe("esto-no-es-fecha");
	});
});

describe("aLocalDate / hoyLocal", () => {
	it("convierte Date a yyyy-MM-dd con padding", () => {
		expect(aLocalDate(new Date(2026, 0, 5))).toBe("2026-01-05");
		expect(aLocalDate(new Date(2026, 11, 31))).toBe("2026-12-31");
	});

	it("hoyLocal coincide con aLocalDate de hoy", () => {
		expect(hoyLocal()).toBe(aLocalDate(new Date()));
		expect(hoyLocal()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
	});
});

describe("formatoPorcentaje", () => {
	it("devuelve — para null/undefined/vacío/NaN", () => {
		expect(formatoPorcentaje(null)).toBe("—");
		expect(formatoPorcentaje(undefined)).toBe("—");
		expect(formatoPorcentaje("")).toBe("—");
		expect(formatoPorcentaje("abc")).toBe("—");
	});

	it("muestra un decimal con %", () => {
		expect(formatoPorcentaje(0)).toBe("0.0%");
		expect(formatoPorcentaje(12.345)).toBe("12.3%");
		expect(formatoPorcentaje("50")).toBe("50.0%");
		expect(formatoPorcentaje(-5.55)).toBe("-5.5%");
	});
});

describe("helpers de fecha/hora actual", () => {
	it("getFechaHoraActualLocal y getFechaActualLocalWithTime usan yyyy-MM-ddTHH:mm:ss", () => {
		const re = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/;
		expect(getFechaHoraActualLocal()).toMatch(re);
		expect(getFechaActualLocalWithTime()).toMatch(re);
	});

	it("getHoraActualLocal usa HH:mm", () => {
		expect(getHoraActualLocal()).toMatch(/^\d{2}:\d{2}$/);
	});

	it("getFechaActualLocal coincide con hoyLocal", () => {
		expect(getFechaActualLocal()).toBe(hoyLocal());
	});
});
