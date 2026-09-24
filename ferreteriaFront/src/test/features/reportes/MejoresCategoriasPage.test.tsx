import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import MejoresCategoriasPage from "@/features/reportes/MejoresCategoriasPage";
import { apiMejoresCategorias } from "@/lib/api/reportes";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("@/lib/api/reportes", () => ({
	apiMejoresCategorias: vi.fn(),
}));

const DATOS = [
	{
		mes: "2026-09-01",
		categoriaId: 5,
		categoria: "Herramientas",
		unidadesVendidas: 120,
		ingreso: 36000,
		utilidad: 9000,
		rankingMes: 1,
		rankingHistorico: 1,
	},
];

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("MejoresCategoriasPage", () => {
	it("renderiza ranking de categorías con badges de utilidad", async () => {
		vi.mocked(apiMejoresCategorias).mockResolvedValue(DATOS);
		renderConProviders(<MejoresCategoriasPage />);

		expect(
			screen.getByRole("heading", { name: "Mejores categorías" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Ranking de mejor categoría vendida por mes"),
		).toBeInTheDocument();
		expect(screen.getByText("Herramientas")).toBeInTheDocument();
		expect(screen.getByText("Ingreso total")).toBeInTheDocument();
		expect(screen.getByText("Utilidad total")).toBeInTheDocument();
	});

	it("no muestra tabla cuando no hay datos", async () => {
		vi.mocked(apiMejoresCategorias).mockResolvedValue([]);
		renderConProviders(<MejoresCategoriasPage />);

		expect(
			await screen.findByText("Mejores categorías"),
		).toBeInTheDocument();
		expect(
			screen.queryByText("Ranking de mejor categoría vendida por mes"),
		).not.toBeInTheDocument();
	});
});
