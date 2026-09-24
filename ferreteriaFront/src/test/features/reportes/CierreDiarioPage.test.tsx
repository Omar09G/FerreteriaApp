import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import CierreDiarioPage from "@/features/reportes/CierreDiarioPage";
import { apiCierreDiario } from "@/lib/api/reportes";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("@/lib/api/reportes", () => ({
	apiCierreDiario: vi.fn(),
}));

const DATOS = [
	{
		fecha: "2026-09-23",
		numCortes: 2,
		tickets: 18,
		totalVendido: 22000,
		utilidadBruta: 8000,
		margenPctPromedio: 36.36,
		perdidas: 0,
		entradasEfectivo: 20000,
		salidasEfectivo: 500,
		efectivoDepositado: 19500,
		diferenciaTotal: 0,
		ingresosDigitales: 2000,
		todoCuadrado: true,
	},
];

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("CierreDiarioPage", () => {
	it("renderiza tabla de cortes con cuadratura", async () => {
		vi.mocked(apiCierreDiario).mockResolvedValue(DATOS);
		renderConProviders(<CierreDiarioPage />);

		expect(
			screen.getByRole("heading", { name: "Cierre diario" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Cortes del periodo"),
		).toBeInTheDocument();
		expect(screen.getByText("Cuadratura")).toBeInTheDocument();
		expect(screen.getByText("Cuadrado")).toBeInTheDocument();
		expect(screen.getByText("Total vendido")).toBeInTheDocument();
	});

	it("muestra estado vacío sin cortes en el periodo", async () => {
		vi.mocked(apiCierreDiario).mockResolvedValue([]);
		renderConProviders(<CierreDiarioPage />);

		expect(
			await screen.findByText("Sin cortes en el periodo"),
		).toBeInTheDocument();
	});
});
