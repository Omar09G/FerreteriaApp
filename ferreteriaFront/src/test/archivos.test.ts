import { describe, expect, it } from "vitest";

import { esUrlImagenSegura } from "@/lib/api/archivos";

describe("esUrlImagenSegura", () => {
	it("acepta https, http (MinIO en dev) y data:image", () => {
		expect(
			esUrlImagenSegura("https://cdn.tienda.com/foto.jpg"),
		).toBe(true);
		expect(
			esUrlImagenSegura(
				"http://localhost:9000/ferreteria-fotos/e0e329fb-0f1d-468d-9112-27d9e79cc218.jpg",
			),
		).toBe(true);
		expect(esUrlImagenSegura("data:image/jpeg;base64,/9j/")).toBe(true);
	});

	it("rechaza esquemas activos o externos inseguros", () => {
		expect(esUrlImagenSegura("javascript:alert(1)")).toBe(false);
		expect(esUrlImagenSegura("file:///etc/passwd")).toBe(false);
		expect(esUrlImagenSegura("data:text/html,<h1>x</h1>")).toBe(false);
		expect(esUrlImagenSegura("ftp://servidor/foto.jpg")).toBe(false);
	});
});
