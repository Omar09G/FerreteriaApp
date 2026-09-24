import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import MejoresClientesPage from "@/features/reportes/MejoresClientesPage";
import { apiMejoresClientes } from "@/lib/api/reportes";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("@/lib/api/reportes", () => ({
	apiMejoresClientes: vi.fn(),
}));

const DATOS = [
	{
		mes: "2026-09-01",
		clienteId: 3,
		cliente: "Constructora del Norte",
		numCompras: 8,
		totalComprado: 45000,
		ticketPromedio: 5625,
		rankingMes: 1,
		rankingHistorico: 2,
	},
];

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("MejoresClientesPage", () => {
	it("renderiza ranking con cliente y totales", async () => {
		vi.mocked(apiMejoresClientes).mockResolvedValue(DATOS);
		renderConProviders(<MejoresClientesPage />);

		expect(
			screen.getByRole("heading", { name: "Mejores clientes" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Ranking del periodo"),
		).toBeInTheDocument();
		expect(screen.getByText("Constructora del Norte")).toBeInTheDocument();
		expect(screen.getByText("Total comprado")).toBeInTheDocument();
		expect(screen.getByText("Ticket promedio")).toBeInTheDocument();
	});

	it("muestra estado vacío sin clientes en el periodo", async () => {
		vi.mocked(apiMejoresClientes).mockResolvedValue([]);
		renderConProviders(<MejoresClientesPage />);

		expect(
			await screen.findByText("Sin clientes en el periodo"),
		).toBeInTheDocument();
	});
});
