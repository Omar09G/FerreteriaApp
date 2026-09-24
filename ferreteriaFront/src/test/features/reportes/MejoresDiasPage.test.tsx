import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import MejoresDiasPage from "@/features/reportes/MejoresDiasPage";
import { apiMejoresDias } from "@/lib/api/reportes";
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
	BarChart: () => null,
	Bar: () => null,
	CartesianGrid: () => null,
	XAxis: () => null,
	YAxis: () => null,
	Tooltip: () => null,
}));

vi.mock("@/lib/api/reportes", () => ({
	apiMejoresDias: vi.fn(),
}));

const DATOS = [
	{
		diaNum: 6,
		diaSemana: "Sábado",
		diasConVenta: 4,
		numVentas: 30,
		totalAcumulado: 25000,
		promedioPorDia: 6250,
		ranking: 1,
	},
];

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("MejoresDiasPage", () => {
	it("renderiza título y tarjeta de acumulado por día", async () => {
		vi.mocked(apiMejoresDias).mockResolvedValue(DATOS);
		renderConProviders(<MejoresDiasPage />);

		expect(
			screen.getByRole("heading", { name: "Mejores días de venta" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Total acumulado por día de la semana"),
		).toBeInTheDocument();
		expect(screen.getByText("Ver reportes →")).toBeInTheDocument();
	});

	it("muestra estado vacío sin ventas en el periodo", async () => {
		vi.mocked(apiMejoresDias).mockResolvedValue([]);
		renderConProviders(<MejoresDiasPage />);

		expect(
			await screen.findByText("Sin ventas en el periodo"),
		).toBeInTheDocument();
	});
});
