import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import ProductosSinMovimientoPage from "@/features/reportes/ProductosSinMovimientoPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiProductosSinMovimiento } from "@/lib/api/reportes";

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
	apiProductosSinMovimiento: vi.fn(async () => [
		{
			productoId: 1,
			codigo: "TOR-001",
			producto: "Tornillo 1/4",
			categoria: "Fijación",
			stock: 100,
			costoActual: 5,
			dineroDetenidoEnEstante: 500,
			ultimaVenta: "2026-01-01",
			diasSinVender: 200,
			prioridadPromocion: "ALTA",
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
	return render(<ProductosSinMovimientoPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("ProductosSinMovimientoPage (smoke)", () => {
	it("renderiza título, tabla y la fila del producto", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Productos sin movimiento" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Tornillo 1/4"),
		).toBeInTheDocument();
		expect(screen.getByText("TOR-001")).toBeInTheDocument();
		expect(screen.getByText("ALTA")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /excel/i })).toBeInTheDocument();
	});

	it("sin datos muestra el estado vacío", async () => {
		vi.mocked(apiProductosSinMovimiento).mockResolvedValueOnce([]);
		renderPage();
		expect(
			await screen.findByText("No hay productos sin movimiento"),
		).toBeInTheDocument();
	});
});

describe("ProductosSinMovimientoPage (profundización)", () => {
	it("muestra toast si el reporte falla con error genérico", async () => {
		vi.mocked(apiProductosSinMovimiento).mockRejectedValueOnce(
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

	it("exporta los productos a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Tornillo 1/4");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^sin-movimiento-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra los badges de stock, dinero detenido y días sin vender", async () => {
		renderPage();
		await screen.findByText("Tornillo 1/4");
		expect(screen.getByText("Fijación")).toBeInTheDocument();
		expect(screen.getByText("2026-01-01")).toBeInTheDocument();
		expect(
			screen.getByRole("link", { name: /ver reportes/i }),
		).toHaveAttribute("href", "/reportes");
	});
});
