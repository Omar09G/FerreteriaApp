import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import ComprasPage from "@/features/compras/ComprasPage";
import { ToastProvider } from "@/components/ui/Toast";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

const COMPRA = {
	compraId: 1,
	folio: "C-0001",
	facturaProveedor: "F-10235",
	proveedorId: 9,
	proveedor: "Aceros del Norte",
	almacenId: 1,
	almacen: "Central",
	fecha: "2026-09-24T10:00:00",
	formaPagoId: 4,
	formaPago: "Transferencia SPEI",
	subtotal: 1000,
	iva: 160,
	descuentoTotal: 0,
	total: 1160,
	estado: "RECIBIDA",
	usuarioId: 1,
	turnoCajaId: null,
	notas: null,
	detalles: [
		{
			compraDetalleId: 1,
			productoId: 1,
			producto: "Martillo",
			cantidad: 10,
			costoUnitario: 100,
			importeLinea: 1000,
		},
	],
};

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
	apiProductos: vi.fn(async () => ({
		success: true,
		data: [],
		meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
	})),
	apiProveedores: vi.fn(async () => [
		{ proveedorId: 9, razonSocial: "Aceros del Norte" },
	]),
}));

vi.mock("@/lib/api/caja", () => ({
	apiCajas: vi.fn(async () => []),
	apiTurnoActual: vi.fn(async () => {
		throw new Error("sin turno");
	}),
}));

vi.mock("@/lib/api/compras", () => ({
	apiCompras: vi.fn(async () => ({
		success: true,
		data: [COMPRA],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	})),
	apiCrearCompra: vi.fn(),
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
	return render(<ComprasPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("ComprasPage (smoke)", () => {
	it("renderiza título, filtro de proveedor y la fila de compra", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Compras" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /nueva compra/i }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Proveedor")).toBeInTheDocument();
		expect((await screen.findAllByText("C-0001")).length).toBeGreaterThanOrEqual(1);
		expect(
			screen.getAllByText("Aceros del Norte").length,
		).toBeGreaterThanOrEqual(1);
		expect(
			screen.getByRole("button", { name: "Ver detalle de C-0001" }),
		).toBeInTheDocument();
	});

	it("'Nueva compra' abre el diálogo de alta", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /nueva compra/i }));
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText("Nueva compra")).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/Almacén de entrada/),
		).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/Buscar producto/),
		).toBeInTheDocument();
	});

	it("ver detalle abre el diálogo con partidas y total", async () => {
		const user = userEvent.setup();
		renderPage();
		await user.click(
			await screen.findByRole("button", { name: "Ver detalle de C-0001" }),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(
			within(dialogo).getByText("Detalle de compra C-0001"),
		).toBeInTheDocument();
		expect(within(dialogo).getByText("Martillo")).toBeInTheDocument();
	});
});
