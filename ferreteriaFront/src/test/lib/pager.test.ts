import { describe, expect, it } from "vitest";
import { clampSize, pagerParams } from "@/lib/pager";

describe("pagerParams", () => {
	it("devuelve page y size sin sort", () => {
		expect(pagerParams({ page: 0, size: 20 })).toEqual({
			page: 0,
			size: 20,
		});
	});

	it("incluye sort cuando viene informado", () => {
		expect(pagerParams({ page: 2, size: 10, sort: "nombre,asc" })).toEqual({
			page: 2,
			size: 10,
			sort: "nombre,asc",
		});
	});

	it("omite sort vacío", () => {
		expect(pagerParams({ page: 1, size: 10, sort: "" })).toEqual({
			page: 1,
			size: 10,
		});
	});
});

describe("clampSize", () => {
	it("limita por abajo a 1", () => {
		expect(clampSize(0)).toBe(1);
		expect(clampSize(-50)).toBe(1);
	});

	it("deja pasar el rango válido", () => {
		expect(clampSize(1)).toBe(1);
		expect(clampSize(50)).toBe(50);
		expect(clampSize(100)).toBe(100);
	});

	it("limita por arriba a 100", () => {
		expect(clampSize(101)).toBe(100);
		expect(clampSize(1000)).toBe(100);
	});
});
