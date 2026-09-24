import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import HorasPicoPage from "@/features/reportes/HorasPicoPage";
import { apiHorasPico } from "@/lib/api/reportes";
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
	apiHorasPico: vi.fn(),
}));

const DATOS = [
	{ hora: 9, numVentas: 4, totalAcumulado: 3200, ticketPromedio: 800, rankingHorario: 1 },
	{ hora: 12, numVentas: 7, totalAcumulado: 6100, ticketPromedio: 871.43, rankingHorario: 2 },
];

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("HorasPicoPage", () => {
	it("renderiza título, gráfica y tabla por hora", async () => {
		vi.mocked(apiHorasPico).mockResolvedValue(DATOS);
		renderConProviders(<HorasPicoPage />);

		expect(
			screen.getByRole("heading", { name: "Ventas por hora" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Número de ventas por hora"),
		).toBeInTheDocument();
		expect(screen.getByText("09:00")).toBeInTheDocument();
		expect(screen.getByText("12:00")).toBeInTheDocument();
		expect(screen.getByText("Ticket promedio")).toBeInTheDocument();
	});

	it("muestra estado vacío sin ventas en el periodo", async () => {
		vi.mocked(apiHorasPico).mockResolvedValue([]);
		renderConProviders(<HorasPicoPage />);

		expect(
			await screen.findByText("Sin ventas en el periodo"),
		).toBeInTheDocument();
	});
});
