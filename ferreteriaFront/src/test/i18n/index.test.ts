import { renderHook, act } from "@testing-library/react";import { afterEach, describe, expect, it } from "vitest";

import { tFuera, useT } from "@/i18n";
import esDict from "@/i18n/es";
import enDict from "@/i18n/en";
import { useUiStore } from "@/store/ui";

type Dict = Record<string, unknown>;

function clavesHoja(d: Dict, prefijo = ""): string[] {
	return Object.entries(d).flatMap(([k, v]) =>
		v !== null && typeof v === "object"
			? clavesHoja(v as Dict, `${prefijo}${k}.`)
			: [`${prefijo}${k}`],
	);
}

afterEach(() => {
	act(() => {
		useUiStore.setState({ idioma: "es" });
	});
});

describe("diccionarios es/en", () => {
	it("mismo juego de namespaces de primer nivel", () => {
		expect(Object.keys(enDict).sort()).toEqual(Object.keys(esDict).sort());
	});

	it("misma cobertura de claves hoja en comun/auth/errores/paginacion/alerta", () => {
		for (const ns of ["comun", "auth", "errores", "paginacion", "alerta"]) {
			expect(clavesHoja((enDict as Dict)[ns] as Dict).sort()).toEqual(
				clavesHoja((esDict as Dict)[ns] as Dict).sort(),
			);
		}
	});

	it("spot-check es: valores clave", () => {
		const es = esDict as Dict;
		expect((es.comun as Dict).guardar).toBe("Guardar");
		expect((es.auth as Dict).titulo).toBe("Iniciar sesión");
		expect((es.alerta as Dict).success).toBe("Listo");
		expect((es.paginacion as Dict).aria).toBe("Paginación");
		expect((es.errores as Dict).servidor).toContain("servidor");
	});

	it("spot-check en: traduce (no copia) los mismos valores", () => {
		const en = enDict as Dict;
		expect((en.comun as Dict).guardar).toBe("Save");
		expect((en.auth as Dict).titulo).toBe("Sign in");
		expect((en.alerta as Dict).success).toBe("Done");
		expect((en.paginacion as Dict).aria).toBe("Pagination");
		expect((en.comun as Dict).guardar).not.toBe(
			((esDict as Dict).comun as Dict).guardar,
		);
	});
});

describe("tFuera (resolución + interpolación + fallback)", () => {
	it("resuelve claves anidadas en es por defecto", () => {
		useUiStore.setState({ idioma: "es" });
		expect(tFuera("comun.guardar")).toBe("Guardar");
		expect(tFuera("auth.titulo")).toBe("Iniciar sesión");
		expect(tFuera("auth.tema.light")).toBe("Tema claro");
	});

	it("con idioma en resuelve del diccionario inglés", () => {
		useUiStore.setState({ idioma: "en" });
		expect(tFuera("comun.guardar")).toBe("Save");
		expect(tFuera("auth.titulo")).toBe("Sign in");
	});

	it("interpola una variable (string y número)", () => {
		useUiStore.setState({ idioma: "es" });
		expect(tFuera("auth.idioma.actual", { idioma: "Español" })).toBe(
			"Idioma: Español",
		);
		expect(tFuera("paginacion.registros", { n: 5 })).toBe("5 registro(s)");
		useUiStore.setState({ idioma: "en" });
		expect(tFuera("auth.idioma.actual", { idioma: "English" })).toBe(
			"Language: English",
		);
	});

	it("interpola múltiples variables en la misma plantilla", () => {
		useUiStore.setState({ idioma: "es" });
		expect(
			tFuera("catalogo.promociones.campos.llevaPagaLabel", {
				lleva: 3,
				paga: 2,
			}),
		).toBe("3 × 2");
		expect(
			tFuera("catalogo.promociones.campos.usosDe", { actual: 1, max: 10 }),
		).toBe("1 / 10");
	});

	it("sin vars deja la plantilla intacta", () => {
		useUiStore.setState({ idioma: "es" });
		expect(tFuera("paginacion.registros")).toBe("{{n}} registro(s)");
	});

	it("clave inexistente retorna la clave tal cual", () => {
		useUiStore.setState({ idioma: "es" });
		expect(tFuera("no.existe.esta.clave")).toBe("no.existe.esta.clave");
		expect(tFuera("comun")).toBe("comun"); // nodo objeto, no string
		expect(tFuera("comun.guardar.extra")).toBe("comun.guardar.extra");
	});

	it("fallback a es cuando falta en el idioma activo", () => {
		useUiStore.setState({ idioma: "en" });
		const enComun = (enDict as Dict).comun as Dict;
		const respaldo = enComun.guardar;
		delete enComun.guardar;
		try {
			expect(tFuera("comun.guardar")).toBe("Guardar");
		} finally {
			enComun.guardar = respaldo;
		}
	});
});

describe("useT (hook reactivo al idioma)", () => {
	it("traduce según el idioma y reacciona al cambio", () => {
		useUiStore.setState({ idioma: "es" });
		const { result } = renderHook(() => useT());
		expect(result.current("comun.guardar")).toBe("Guardar");
		act(() => useUiStore.setState({ idioma: "en" }));
		expect(result.current("comun.guardar")).toBe("Save");
		expect(result.current("auth.tema.dark")).toBe("Dark theme");
	});

	it("soporta interpolación y fallback a la clave", () => {
		useUiStore.setState({ idioma: "es" });
		const { result } = renderHook(() => useT());
		expect(result.current("errores.inesperado", { status: 500 })).toBe(
			"Error inesperado (500).",
		);
		expect(result.current("clave.rara")).toBe("clave.rara");
	});
});
