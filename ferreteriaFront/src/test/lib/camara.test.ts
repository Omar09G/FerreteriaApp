import { afterEach, describe, expect, it, vi } from "vitest";
import { camaraDisponible } from "@/lib/camara";

afterEach(() => {
	vi.unstubAllGlobals();
});

describe("camaraDisponible", () => {
	it("false cuando no hay mediaDevices (http de LAN)", () => {
		vi.stubGlobal("navigator", {});
		expect(camaraDisponible()).toBe(false);
	});

	it("false cuando mediaDevices no expone getUserMedia", () => {
		vi.stubGlobal("navigator", { mediaDevices: {} });
		expect(camaraDisponible()).toBe(false);
	});

	it("false cuando getUserMedia es null", () => {
		vi.stubGlobal("navigator", {
			mediaDevices: { getUserMedia: null },
		});
		expect(camaraDisponible()).toBe(false);
	});

	it("true cuando getUserMedia existe (contexto seguro)", () => {
		vi.stubGlobal("navigator", {
			mediaDevices: { getUserMedia: vi.fn() },
		});
		expect(camaraDisponible()).toBe(true);
	});
});
