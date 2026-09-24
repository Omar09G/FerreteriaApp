import { describe, expect, it } from "vitest";
import {
	PRESETS_RANGO,
	construirPreset,
	rangoFechas,
} from "@/lib/rango";
import { aLocalDate, hoyLocal } from "@/lib/format";

function haceDias(n: number): string {
	const d = new Date();
	d.setDate(d.getDate() - n);
	return aLocalDate(d);
}

describe("rangoFechas", () => {
	it("por defecto es hoy/hoy", () => {
		const hoy = hoyLocal();
		expect(rangoFechas()).toEqual({ inicio: hoy, fin: hoy });
	});

	it("fin cae a inicio cuando no se indica", () => {
		expect(rangoFechas("2026-01-10")).toEqual({
			inicio: "2026-01-10",
			fin: "2026-01-10",
		});
	});

	it("respeta inicio y fin explícitos", () => {
		expect(rangoFechas("2026-01-01", "2026-01-31")).toEqual({
			inicio: "2026-01-01",
			fin: "2026-01-31",
		});
	});

	it("trata null como ausente", () => {
		const hoy = hoyLocal();
		expect(rangoFechas(null, null)).toEqual({ inicio: hoy, fin: hoy });
		const manana = haceDias(-1);
		expect(rangoFechas(null, manana)).toEqual({
			inicio: hoy,
			fin: manana,
		});
	});

	it("lanza si fin < inicio", () => {
		expect(() => rangoFechas("2026-02-01", "2026-01-01")).toThrow(
			"La fecha de inicio no puede ser posterior a la de fin.",
		);
	});
});

describe("construirPreset", () => {
	it("hoy y ids desconocidos devuelven hoy/hoy", () => {
		const hoy = hoyLocal();
		expect(construirPreset("hoy")).toEqual({ inicio: hoy, fin: hoy });
		expect(construirPreset("inexistente")).toEqual({
			inicio: hoy,
			fin: hoy,
		});
	});

	it("ayer devuelve el día anterior en inicio y fin", () => {
		const ayer = haceDias(1);
		expect(construirPreset("ayer")).toEqual({ inicio: ayer, fin: ayer });
	});

	it("ultimos-7 cubre hoy menos 6 días", () => {
		expect(construirPreset("ultimos-7")).toEqual({
			inicio: haceDias(6),
			fin: hoyLocal(),
		});
	});

	it("ultimos-15 cubre hoy menos 14 días", () => {
		expect(construirPreset("ultimos-15")).toEqual({
			inicio: haceDias(14),
			fin: hoyLocal(),
		});
	});

	it("mes va del día 01 al día de hoy", () => {
		const hoy = new Date();
		const esperado = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, "0")}-01`;
		expect(construirPreset("mes")).toEqual({
			inicio: esperado,
			fin: hoyLocal(),
		});
	});
});

describe("PRESETS_RANGO", () => {
	it("contiene los 5 presets en orden", () => {
		expect(PRESETS_RANGO.map((p) => p.id)).toEqual([
			"hoy",
			"ayer",
			"ultimos-7",
			"ultimos-15",
			"mes",
		]);
	});
});
