import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import MejoresVendedoresPage from "@/features/reportes/MejoresVendedoresPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiMejoresVendedores } from "@/lib/api/reportes";

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
	apiMejoresVendedores: vi.fn(async () => [
		{
			mes: "2026-09",
			usuarioId: 1,
			vendedor: "Ana López",
			numVentas: 10,
			totalVendido: 20000,
			ticketPromedio: 2000,
			utilidadGenerada: 5000,
			rankingMes: 1,
			rankingHistorico: 1,
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
	return render(<MejoresVendedoresPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("MejoresVendedoresPage (smoke)", () => {
	it("renderiza título, ranking y la fila del vendedor", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Mejores vendedores" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Ranking del periodo")).toBeInTheDocument();
		expect(await screen.findByText("Ana López")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /excel/i })).toBeInTheDocument();
	});

	it("sin datos muestra el estado vacío", async () => {
		vi.mocked(apiMejoresVendedores).mockResolvedValueOnce([]);
		renderPage();
		expect(
			await screen.findByText("Sin ventas en el periodo"),
		).toBeInTheDocument();
	});
});

describe("MejoresVendedoresPage (profundización)", () => {
	it("muestra toast si el reporte falla con error genérico", async () => {
		vi.mocked(apiMejoresVendedores).mockRejectedValueOnce(
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

	it("cambiar la fecha inicial recarga el reporte con el nuevo rango", async () => {
		renderPage();
		await screen.findByText("Ranking del periodo");
		const api = vi.mocked(apiMejoresVendedores);
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

	it("exporta el ranking a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Ranking del periodo");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^mejores-vendedores-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});
});
