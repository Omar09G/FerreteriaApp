import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import HorasPicoPage from "@/features/reportes/HorasPicoPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiHorasPico } from "@/lib/api/reportes";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

const writeFile = vi.fn();
vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		aoa_to_sheet: vi.fn(() => ({})),
		book_append_sheet: vi.fn(),
	},
	writeFile: (...args: unknown[]) => writeFile(...args),
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
		labelFormatter,
	}: {
		formatter?: (value: unknown, name: unknown) => unknown;
		labelFormatter?: (label: unknown) => unknown;
	}) => {
		if (formatter) formatter(5, "numVentas");
		if (labelFormatter) labelFormatter(10);
		return null;
	},
	XAxis: ({ tickFormatter }: { tickFormatter?: (v: number) => unknown }) => {
		if (tickFormatter) tickFormatter(10);
		return null;
	},
	YAxis: () => null,
}));

vi.mock("@/lib/api/reportes", () => ({
	apiHorasPico: vi.fn(async () => [
		{
			hora: 10,
			numVentas: 5,
			totalAcumulado: 2000,
			ticketPromedio: 400,
			rankingHorario: 1,
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
	return render(<HorasPicoPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("HorasPicoPage (smoke)", () => {
	it("renderiza título, gráfica, detalle y la fila de la hora pico", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Ventas por hora" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Número de ventas por hora"),
		).toBeInTheDocument();
		expect(screen.getByText("Detalle")).toBeInTheDocument();
		expect(screen.getByText("10:00")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /excel/i })).toBeInTheDocument();
	});

	it("sin datos muestra el estado vacío", async () => {
		vi.mocked(apiHorasPico).mockResolvedValueOnce([]);
		renderPage();
		expect(
			await screen.findByText("Sin ventas en el periodo"),
		).toBeInTheDocument();
	});
});

describe("HorasPicoPage (profundización)", () => {
	it("formatea la hora con cero inicial y muestra varias filas", async () => {
		vi.mocked(apiHorasPico).mockResolvedValueOnce([
			{
				hora: 10,
				numVentas: 5,
				totalAcumulado: 2000,
				ticketPromedio: 400,
				rankingHorario: 1,
			},
			{
				hora: 9,
				numVentas: 2,
				totalAcumulado: 800,
				ticketPromedio: 400,
				rankingHorario: 2,
			},
		]);
		renderPage();
		expect(await screen.findByText("10:00")).toBeInTheDocument();
		expect(screen.getByText("09:00")).toBeInTheDocument();
	});

	it("muestra toast si el reporte falla con error genérico", async () => {
		vi.mocked(apiHorasPico).mockRejectedValueOnce(new Error("fallo de red"));
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
		await screen.findByText("Detalle");
		const api = vi.mocked(apiHorasPico);
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

	it("exporta el detalle por hora a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Detalle");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^horas-pico-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});
});
