import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import TopProductosPage from "@/features/reportes/TopProductosPage";
import { apiTopProductos } from "@/lib/api/reportes";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("@/lib/api/reportes", () => ({
	apiTopProductos: vi.fn(),
}));

const DATOS = [
	{
		mes: "2026-09-01",
		productoId: 1,
		codigo: "MAR-001",
		producto: "Martillo de uña 16oz",
		categoria: "Herramientas",
		unidadesVendidas: 25,
		ingresoTotal: 7500,
		costoTotal: 5000,
		utilidad: 2500,
		rankingMes: 1,
		rankingUnidades: 1,
	},
];

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("TopProductosPage", () => {
	it("renderiza ranking con producto, posición y montos", async () => {
		vi.mocked(apiTopProductos).mockResolvedValue(DATOS);
		renderConProviders(<TopProductosPage />);

		expect(
			screen.getByRole("heading", { name: "Productos más vendidos" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Ranking del periodo"),
		).toBeInTheDocument();
		expect(screen.getByText("Martillo de uña 16oz")).toBeInTheDocument();
		expect(screen.getByText("MAR-001")).toBeInTheDocument();
		expect(screen.getByText("Utilidad")).toBeInTheDocument();
	});

	it("muestra estado vacío sin ventas en el periodo", async () => {
		vi.mocked(apiTopProductos).mockResolvedValue([]);
		renderConProviders(<TopProductosPage />);

		expect(
			await screen.findByText("Sin ventas en el periodo"),
		).toBeInTheDocument();
	});
});
