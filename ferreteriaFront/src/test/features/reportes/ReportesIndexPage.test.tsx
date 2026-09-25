import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

import ReportesIndexPage from "@/features/reportes/ReportesIndexPage";

function renderPage() {
	return render(
		<MemoryRouter>
			<ReportesIndexPage />
		</MemoryRouter>,
	);
}

describe("ReportesIndexPage (smoke)", () => {
	it("renderiza título y todas las entradas de reportes", () => {
		renderPage();
		expect(screen.getByRole("heading", { name: "Reportes" })).toBeInTheDocument();
		for (const nombre of [
			"Ventas totales",
			"Horas pico",
			"Mejores días",
			"Top productos",
			"Mejores clientes",
			"Mejores vendedores",
			"Cierre diario",
			"Mejores Categorías",
			"Productos sin Movimiento",
			"Panel de control",
		]) {
			expect(screen.getByText(nombre)).toBeInTheDocument();
		}
	});

	it("cada entrada enlaza a su ruta", () => {
		renderPage();
		expect(screen.getByRole("link", { name: /ventas totales/i })).toHaveAttribute(
			"href",
			"/reportes/ventas-totales",
		);
		expect(screen.getByRole("link", { name: /cierre diario/i })).toHaveAttribute(
			"href",
			"/reportes/cierre-diario",
		);
		expect(
			screen.getByRole("link", { name: /panel de control/i }),
		).toHaveAttribute("href", "/dashboard");
	});

	it("todas las entradas enlazan a su ruta correspondiente", () => {
		renderPage();
		const rutas: Array<[RegExp, string]> = [
			[/horas pico/i, "/reportes/horas-pico"],
			[/mejores días/i, "/reportes/mejores-dias"],
			[/top productos/i, "/reportes/top-productos"],
			[/mejores clientes/i, "/reportes/mejores-clientes"],
			[/mejores vendedores/i, "/reportes/mejores-vendedores"],
			[/mejores categorías/i, "/reportes/mejores-categorias"],
			[/productos sin movimiento/i, "/reportes/productos-sin-movimiento"],
		];
		for (const [nombre, href] of rutas) {
			expect(screen.getByRole("link", { name: nombre })).toHaveAttribute(
				"href",
				href,
			);
		}
		expect(screen.getAllByRole("link")).toHaveLength(10);
	});
});
