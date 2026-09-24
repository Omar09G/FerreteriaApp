import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import ReportesIndexPage from "@/features/reportes/ReportesIndexPage";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("ReportesIndexPage", () => {
	it("muestra el índice con todas las entradas de reportes", () => {
		renderConProviders(<ReportesIndexPage />);

		expect(
			screen.getByRole("heading", { name: "Reportes" }),
		).toBeInTheDocument();
		for (const entrada of [
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
			expect(screen.getByText(entrada)).toBeInTheDocument();
		}
	});

	it("enlaza cada tarjeta a su ruta", () => {
		renderConProviders(<ReportesIndexPage />);

		expect(
			screen.getByRole("link", { name: /Ventas totales/ }),
		).toHaveAttribute("href", "/reportes/ventas-totales");
		expect(
			screen.getByRole("link", { name: /Cierre diario/ }),
		).toHaveAttribute("href", "/reportes/cierre-diario");
	});
});
