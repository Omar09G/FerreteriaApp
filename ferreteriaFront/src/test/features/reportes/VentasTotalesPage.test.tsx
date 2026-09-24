import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import VentasTotalesPage from "@/features/reportes/VentasTotalesPage";
import { apiVentasTotales } from "@/lib/api/reportes";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("recharts", () => ({
	ResponsiveContainer: ({ children }: { children?: React.ReactNode }) => (
		<>{children}</>
	),
	AreaChart: () => null,
	Area: () => null,
	CartesianGrid: () => null,
	XAxis: () => null,
	YAxis: () => null,
	Tooltip: () => null,
}));

vi.mock("@/lib/api/reportes", () => ({
	apiVentasTotales: vi.fn(),
}));

const DATOS = [
	{
		fecha: "2026-09-20",
		numVentas: 5,
		subtotal: 4000,
		iva: 640,
		descuentos: 100,
		totalVendido: 4540,
		ticketPromedio: 908,
		costoVentas: 2500,
		utilidadBruta: 2040,
	},
];

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("VentasTotalesPage", () => {
	it("renderiza título, tabla diaria y enlace a reportes", async () => {
		vi.mocked(apiVentasTotales).mockResolvedValue(DATOS);
		renderConProviders(<VentasTotalesPage />);

		expect(
			screen.getByRole("heading", { name: "Ventas totales" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Detalle diario")).toBeInTheDocument();
		expect(screen.getByText("Total")).toBeInTheDocument();
		expect(screen.getByText("Utilidad")).toBeInTheDocument();
		expect(screen.getByText("Ver reportes →")).toBeInTheDocument();
	});

	it("muestra estado vacío sin ventas en el periodo", async () => {
		vi.mocked(apiVentasTotales).mockResolvedValue([]);
		renderConProviders(<VentasTotalesPage />);

		expect(
			await screen.findByText("Sin ventas en el periodo"),
		).toBeInTheDocument();
	});
});
