import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import MejoresDiasPage from "@/features/reportes/MejoresDiasPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiMejoresDias } from "@/lib/api/reportes";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("recharts", () => ({
	ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
		<>{children}</>
	),
	BarChart: ({ children }: { children: React.ReactNode }) => <>{children}</>,
	Bar: () => null,
	CartesianGrid: () => null,
	Tooltip: ({
		formatter,
	}: {
		formatter?: (value: unknown, name: unknown) => unknown;
	}) => {
		if (formatter) formatter(15000, "Total acumulado");
		return null;
	},
	XAxis: () => null,
	YAxis: ({ tickFormatter }: { tickFormatter?: (v: number) => unknown }) => {
		if (tickFormatter) tickFormatter(15000);
		return null;
	},
}));

vi.mock("@/lib/api/reportes", () => ({
	apiMejoresDias: vi.fn(async () => [
		{
			diaNum: 6,
			diaSemana: "Sábado",
			diasConVenta: 4,
			numVentas: 20,
			totalAcumulado: 15000,
			promedioPorDia: 3750,
			ranking: 1,
		},
	]),
}));

function renderPage() {
	const qc = new QueryClient({
		defaultOptions: { queries: { retry: false } },
	});
	const wrapper = ({ children }: { children: ReactNode }) => (
		<MemoryRouter>
			<QueryClientProvider client={qc}>
				<ToastProvider>{children}</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>
	);
	return render(<MejoresDiasPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("MejoresDiasPage (smoke)", () => {
	it("renderiza título y tarjeta de acumulado por día", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Mejores días de venta" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Total acumulado por día de la semana"),
		).toBeInTheDocument();
		expect(screen.getByText("Ver reportes →")).toBeInTheDocument();
	});

	it("sin datos muestra el estado vacío", async () => {
		vi.mocked(apiMejoresDias).mockResolvedValueOnce([]);
		renderPage();
		expect(
			await screen.findByText("Sin ventas en el periodo"),
		).toBeInTheDocument();
	});
});

describe("MejoresDiasPage (profundización)", () => {
	it("muestra toast si el reporte falla con error genérico", async () => {
		vi.mocked(apiMejoresDias).mockRejectedValueOnce(new Error("fallo de red"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("fallo de red"),
				}),
			),
		);
	});

	it("cambiar la fecha inicial recarga el reporte con el nuevo rango", async () => {
		renderPage();
		await screen.findByText("Total acumulado por día de la semana");
		const api = vi.mocked(apiMejoresDias);
		const llamadasAntes = api.mock.calls.length;
		const rango = screen.getByTestId("rango-fechas");
		const [del] = within(rango).getAllByDisplayValue(/\d{4}-\d{2}-\d{2}/);
		const actual = (del as HTMLInputElement).value;
		const d = new Date(`${actual}T12:00:00`);
		d.setDate(d.getDate() - 2);
		const nuevo = d.toISOString().slice(0, 10);
		fireEvent.change(del, { target: { value: nuevo } });
		await waitFor(() =>
			expect(api.mock.calls.length).toBeGreaterThan(llamadasAntes),
		);
		const ultima = api.mock.calls[api.mock.calls.length - 1];
		expect(ultima[0]).toBe(nuevo);
	});

	it("enlaza a la sección de reportes", async () => {
		renderPage();
		await screen.findByText("Total acumulado por día de la semana");
		expect(
			screen.getByRole("link", { name: /ver reportes/i }),
		).toHaveAttribute("href", "/reportes");
	});
});
