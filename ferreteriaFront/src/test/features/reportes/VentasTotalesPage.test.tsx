import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import VentasTotalesPage from "@/features/reportes/VentasTotalesPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiVentasTotales } from "@/lib/api/reportes";
import { ApiError } from "@/lib/api/errors";

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
	AreaChart: ({ children }: { children: React.ReactNode }) => <>{children}</>,
	Area: () => null,
	CartesianGrid: () => null,
	Tooltip: ({
		formatter,
		labelFormatter,
	}: {
		formatter?: (value: unknown, name: unknown) => unknown;
		labelFormatter?: (label: unknown) => unknown;
	}) => {
		if (formatter) formatter(1160, "Total vendido");
		if (labelFormatter) labelFormatter("2026-09-01");
		return null;
	},
	XAxis: ({ tickFormatter }: { tickFormatter?: (v: string) => unknown }) => {
		if (tickFormatter) tickFormatter("2026-09-01");
		return null;
	},
	YAxis: ({ tickFormatter }: { tickFormatter?: (v: number) => unknown }) => {
		if (tickFormatter) tickFormatter(1160);
		return null;
	},
}));

vi.mock("@/lib/api/reportes", () => ({
	apiVentasTotales: vi.fn(async () => [
		{
			fecha: "2026-09-01",
			numVentas: 5,
			subtotal: 1000,
			iva: 160,
			descuentos: 0,
			totalVendido: 1160,
			ticketPromedio: 232,
			costoVentas: 700,
			utilidadBruta: 460,
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
	return render(<VentasTotalesPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("VentasTotalesPage (smoke)", () => {
	it("renderiza título, tarjeta de gráfica y detalle diario con la fila", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Ventas totales" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Total vendido y utilidad bruta por día"),
		).toBeInTheDocument();
		expect(screen.getByText("Detalle diario")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /excel/i })).toBeInTheDocument();
		expect(screen.getByText("Ver reportes →")).toBeInTheDocument();
	});

	it("sin datos muestra el estado vacío", async () => {
		vi.mocked(apiVentasTotales).mockResolvedValueOnce([]);
		renderPage();
		expect(
			await screen.findByText("Sin ventas en el periodo"),
		).toBeInTheDocument();
	});
});

describe("VentasTotalesPage (profundización)", () => {
	it("muestra toast si el reporte falla con error genérico", async () => {
		vi.mocked(apiVentasTotales).mockRejectedValueOnce(
			new Error("fallo de red"),
		);
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("fallo de red"),
				}),
			),
		);
	});

	it("muestra el mensaje del backend si falla con ApiError", async () => {
		vi.mocked(apiVentasTotales).mockRejectedValueOnce(
			new ApiError({
				success: false,
				data: null,
				errorCode: 500,
				codigo: "ERROR_INTERNO",
				errorMessage: "Error interno del servidor",
			}),
		);
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("Error interno del servidor"),
				}),
			),
		);
	});

	it("cambiar la fecha inicial recarga el reporte con el nuevo rango", async () => {
		renderPage();
		await screen.findByText("Detalle diario");
		const api = vi.mocked(apiVentasTotales);
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

	it("exporta el detalle diario a Excel con el nombre del reporte", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Detalle diario");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^ventas-totales-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("enlaza a la sección de reportes", async () => {
		renderPage();
		await screen.findByText("Detalle diario");
		expect(
			screen.getByRole("link", { name: /ver reportes/i }),
		).toHaveAttribute("href", "/reportes");
	});
});
