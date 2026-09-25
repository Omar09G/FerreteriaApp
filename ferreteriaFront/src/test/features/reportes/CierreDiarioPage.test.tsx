import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import CierreDiarioPage from "@/features/reportes/CierreDiarioPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiCierreDiario } from "@/lib/api/reportes";
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

vi.mock("@/lib/api/reportes", () => ({
	apiCierreDiario: vi.fn(async () => [
		{
			fecha: "2026-09-01",
			numCortes: 2,
			tickets: 10,
			totalVendido: 15000,
			utilidadBruta: 4000,
			margenPctPromedio: 26.6,
			perdidas: 0,
			entradasEfectivo: 12000,
			salidasEfectivo: 500,
			efectivoDepositado: 11500,
			diferenciaTotal: 0,
			ingresosDigitales: 3000,
			todoCuadrado: true,
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
	return render(<CierreDiarioPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("CierreDiarioPage (smoke)", () => {
	it("renderiza título, tabla de cortes y badge de cuadratura", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Cierre diario" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Cortes del periodo")).toBeInTheDocument();
		expect(await screen.findByText("Cuadrado")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /excel/i })).toBeInTheDocument();
	});

	it("sin datos muestra el estado vacío", async () => {
		vi.mocked(apiCierreDiario).mockResolvedValueOnce([]);
		renderPage();
		expect(
			await screen.findByText("Sin cortes en el periodo"),
		).toBeInTheDocument();
	});
});

describe("CierreDiarioPage (profundización)", () => {
	it("muestra Diferencia cuando un día no cuadra", async () => {
		vi.mocked(apiCierreDiario).mockResolvedValueOnce([
			{
				fecha: "2026-09-02",
				numCortes: 1,
				tickets: 4,
				totalVendido: 5000,
				utilidadBruta: 1000,
				margenPctPromedio: 20,
				perdidas: 0,
				entradasEfectivo: 4000,
				salidasEfectivo: 0,
				efectivoDepositado: 3900,
				diferenciaTotal: 100,
				ingresosDigitales: 1000,
				todoCuadrado: false,
			},
		]);
		renderPage();
		expect(await screen.findByText("Diferencia")).toBeInTheDocument();
	});

	it("muestra toast si el reporte falla con error genérico", async () => {
		vi.mocked(apiCierreDiario).mockRejectedValueOnce(
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
		vi.mocked(apiCierreDiario).mockRejectedValueOnce(
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
		await screen.findByText("Cortes del periodo");
		const api = vi.mocked(apiCierreDiario);
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

	it("exporta los cortes a Excel con el nombre del reporte", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Cortes del periodo");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^cierre-diario-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});
});
