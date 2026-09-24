import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import MejoresVendedoresPage from "@/features/reportes/MejoresVendedoresPage";
import { apiMejoresVendedores } from "@/lib/api/reportes";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("@/lib/api/reportes", () => ({
	apiMejoresVendedores: vi.fn(),
}));

const DATOS = [
	{
		mes: "2026-09-01",
		usuarioId: 2,
		vendedor: "María López",
		numVentas: 40,
		totalVendido: 60000,
		ticketPromedio: 1500,
		utilidadGenerada: 18000,
		rankingMes: 1,
		rankingHistorico: 1,
	},
];

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("MejoresVendedoresPage", () => {
	it("renderiza ranking con vendedor y métricas", async () => {
		vi.mocked(apiMejoresVendedores).mockResolvedValue(DATOS);
		renderConProviders(<MejoresVendedoresPage />);

		expect(
			screen.getByRole("heading", { name: "Mejores vendedores" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Ranking del periodo"),
		).toBeInTheDocument();
		expect(screen.getByText("María López")).toBeInTheDocument();
		expect(screen.getByText("Total vendido")).toBeInTheDocument();
		expect(screen.getByText("Utilidad")).toBeInTheDocument();
	});

	it("muestra estado vacío sin ventas en el periodo", async () => {
		vi.mocked(apiMejoresVendedores).mockResolvedValue([]);
		renderConProviders(<MejoresVendedoresPage />);

		expect(
			await screen.findByText("Sin ventas en el periodo"),
		).toBeInTheDocument();
	});
});
