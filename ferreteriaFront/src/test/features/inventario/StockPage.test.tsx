import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import StockPage from "@/features/inventario/StockPage";
import { ToastProvider } from "@/components/ui/Toast";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiAlmacenes: vi.fn(async () => [
		{
			almacenId: 1,
			nombre: "Central",
			direccion: null,
			telefono: null,
			esPuntoVenta: true,
			activo: true,
		},
	]),
}));

vi.mock("@/lib/api/reportes", () => ({
	apiStock: vi.fn(async () => ({
		success: true,
		data: [
			{
				productoId: 1,
				productoNombre: "Martillo",
				productoCodigo: "MAR-001",
				almacenId: 1,
				almacenNombre: "Central",
				stock: 10,
				stockMinimo: 5,
				reservado: 0,
			},
		],
		meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
	})),
}));

vi.mock("@/lib/api/inventario", () => ({
	apiCrearMovimiento: vi.fn(),
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
	return render(<StockPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("StockPage (smoke)", () => {
	it("renderiza título, filtros y la fila de stock", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Existencias" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Almacén")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /solo bajo stock/i }),
		).toBeInTheDocument();
		expect(await screen.findByText("Martillo")).toBeInTheDocument();
		expect(screen.getByText("MAR-001")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Ajustar stock de Martillo" }),
		).toBeInTheDocument();
	});

	it("el filtro 'Solo bajo stock' cambia de estado al hacer click", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		const boton = screen.getByRole("button", { name: /solo bajo stock/i });
		await user.click(boton);
		expect(
			await screen.findByRole("button", {
				name: "Mostrando solo bajo stock",
			}),
		).toBeInTheDocument();
	});

	it("abrir 'Ajustar' muestra el diálogo de ajuste de existencia", async () => {
		const user = userEvent.setup();
		renderPage();
		await user.click(
			await screen.findByRole("button", {
				name: "Ajustar stock de Martillo",
			}),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(
			within(dialogo).getByText("Ajustar existencia"),
		).toBeInTheDocument();
		expect(
			within(dialogo).getByRole("button", { name: /registrar entrada/i }),
		).toBeInTheDocument();
	});
});
