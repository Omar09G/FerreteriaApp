import { beforeEach, describe, expect, it } from "vitest";

import { useUiStore } from "@/store/ui";

beforeEach(() => {
	localStorage.clear();
	useUiStore.setState({ tema: "system", idioma: "es" });
	// Limpia lo que persist escribió durante el setState.
	localStorage.clear();
});

describe("ui store", () => {
	it("valores por defecto: tema system, idioma es", () => {
		const s = useUiStore.getState();
		expect(s.tema).toBe("system");
		expect(s.idioma).toBe("es");
	});

	it("setTema cambia entre light/dark/system", () => {
		useUiStore.getState().setTema("dark");
		expect(useUiStore.getState().tema).toBe("dark");
		useUiStore.getState().setTema("light");
		expect(useUiStore.getState().tema).toBe("light");
		useUiStore.getState().setTema("system");
		expect(useUiStore.getState().tema).toBe("system");
	});

	it("setIdioma alterna es/en", () => {
		useUiStore.getState().setIdioma("en");
		expect(useUiStore.getState().idioma).toBe("en");
		useUiStore.getState().setIdioma("es");
		expect(useUiStore.getState().idioma).toBe("es");
	});

	it("persiste preferencias en localStorage", () => {
		useUiStore.getState().setTema("dark");
		useUiStore.getState().setIdioma("en");
		const raw = localStorage.getItem("ferreteria-ui");
		expect(raw).not.toBeNull();
		const guardado = JSON.parse(raw as string);
		expect(guardado.state.tema).toBe("dark");
		expect(guardado.state.idioma).toBe("en");
	});

	it("rehidrata desde localStorage al recargar el store", async () => {
		localStorage.setItem(
			"ferreteria-ui",
			JSON.stringify({ state: { tema: "light", idioma: "en" }, version: 0 }),
		);
		// Re-ejecuta la hidratación contra el storage ya sembrado.
		await useUiStore.persist.rehydrate();
		expect(useUiStore.getState().tema).toBe("light");
		expect(useUiStore.getState().idioma).toBe("en");
	});
});
