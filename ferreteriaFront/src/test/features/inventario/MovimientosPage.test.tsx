import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import MovimientosPage from "@/features/inventario/MovimientosPage";
import { ToastProvider } from "@/components/ui/Toast";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/reportes", () => ({
	apiMovimientos: vi.fn(async () => ({
		success: true,
		data: [
			{
				movimientoId: 7,
				productoId: 1,
				productoNombre: "Martillo",
				almacenId: 1,
				almacenNombre: "Central",
				tipo: "ENTRADA",
				cantidad: 5,
				costoUnitario: 100,
				motivoNombre: "Compra a proveedor",
				refTabla: null,
				refId: null,
				creadoEn: "2026-09-24T10:00:00",
			},
		],
		meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
	})),
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
	return render(<MovimientosPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("MovimientosPage (smoke)", () => {
	it("renderiza título, filtros y la fila del movimiento", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Movimientos de inventario" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Producto \(id\)/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Almacén \(id\)/)).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /filtrar/i }),
		).toBeInTheDocument();
		expect(await screen.findByText("Martillo")).toBeInTheDocument();
		expect(screen.getByText("Entrada")).toBeInTheDocument();
	});

	it("aplicar filtro por producto muestra el badge de filtros activos", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		await user.type(screen.getByLabelText(/Producto \(id\)/), "1");
		await user.click(screen.getByRole("button", { name: /filtrar/i }));
		expect(await screen.findByText("Filtros activos")).toBeInTheDocument();
	});
});
