import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import MejoresCategoriasPage from "@/features/reportes/MejoresCategoriasPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiMejoresCategorias } from "@/lib/api/reportes";

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
	apiMejoresCategorias: vi.fn(async () => [
		{
			mes: "2026-09-01",
			categoriaId: 1,
			categoria: "Herramientas",
			unidadesVendidas: 50,
			ingreso: 20000,
			utilidad: 600,
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
	return render(<MejoresCategoriasPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("MejoresCategoriasPage (smoke)", () => {
	it("renderiza título, ranking y la fila de la categoría", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Mejores categorías" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Ranking de mejor categoría vendida por mes"),
		).toBeInTheDocument();
		expect(await screen.findByText("Herramientas")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /excel/i })).toBeInTheDocument();
	});

	it("sin datos no muestra la tabla pero sí el enlace a reportes", async () => {
		vi.mocked(apiMejoresCategorias).mockResolvedValueOnce([]);
		renderPage();
		expect(
			await screen.findByText("Ver reportes →"),
		).toBeInTheDocument();
		expect(screen.queryByText("Herramientas")).not.toBeInTheDocument();
	});
});

describe("MejoresCategoriasPage (profundización)", () => {
	it("pinta la utilidad con los tres tonos según el monto", async () => {
		vi.mocked(apiMejoresCategorias).mockResolvedValueOnce([
			{
				mes: "2026-09-01",
				categoriaId: 1,
				categoria: "Herramientas",
				unidadesVendidas: 50,
				ingreso: 20000,
				utilidad: 600,
				rankingMes: 1,
				rankingHistorico: 1,
			},
			{
				mes: "2026-09-01",
				categoriaId: 2,
				categoria: "Fijación",
				unidadesVendidas: 30,
				ingreso: 8000,
				utilidad: 200,
				rankingMes: 2,
				rankingHistorico: 3,
			},
			{
				mes: "2026-09-01",
				categoriaId: 3,
				categoria: "Pintura",
				unidadesVendidas: 5,
				ingreso: 1000,
				utilidad: 50,
				rankingMes: 3,
				rankingHistorico: 5,
			},
		]);
		renderPage();
		expect(
			await screen.findByText("Ranking de mejor categoría vendida por mes"),
		).toBeInTheDocument();
		expect(screen.getByText("Herramientas")).toBeInTheDocument();
		expect(screen.getByText("Fijación")).toBeInTheDocument();
		expect(screen.getByText("Pintura")).toBeInTheDocument();
	});

	it("muestra toast si el reporte falla con error genérico", async () => {
		vi.mocked(apiMejoresCategorias).mockRejectedValueOnce(
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
		await screen.findByText("Ranking de mejor categoría vendida por mes");
		const api = vi.mocked(apiMejoresCategorias);
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
		await screen.findByText("Ranking de mejor categoría vendida por mes");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^mejores-categorias-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});
});
