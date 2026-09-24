import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import TrasladosPage from "@/features/inventario/TrasladosPage";
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
		{
			almacenId: 2,
			nombre: "Sucursal",
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
}));

vi.mock("@/lib/api/inventario", () => ({
	apiTraslados: vi.fn(async () => ({
		success: true,
		data: [
			{
				trasladoId: 3,
				folio: "TR-0003",
				almacenOrigen: 1,
				almacenOrigenNombre: "Central",
				almacenDestino: 2,
				almacenDestinoNombre: "Sucursal",
				estado: "APLICADO",
				usuarioId: 1,
				creadoEn: "2026-09-24T10:00:00",
				detalles: [
					{ productoId: 1, productoNombre: "Martillo", cantidad: 4 },
				],
			},
		],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	})),
	apiCrearTraslado: vi.fn(),
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
	return render(<TrasladosPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("TrasladosPage (smoke)", () => {
	it("renderiza título, filtro de estado y la fila del traslado", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Traslados" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /nuevo traslado/i }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Estado")).toBeInTheDocument();
		expect(await screen.findByText("TR-0003")).toBeInTheDocument();
		expect(screen.getByText("Central")).toBeInTheDocument();
		expect(screen.getByText("Sucursal")).toBeInTheDocument();
	});

	it("'Nuevo traslado' abre el diálogo de alta", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("TR-0003");
		await user.click(
			screen.getByRole("button", { name: /nuevo traslado/i }),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText("Nuevo traslado")).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/Almacén origen/),
		).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/Almacén destino/),
		).toBeInTheDocument();
	});

	it("ver detalle abre el diálogo con los productos trasladados", async () => {
		const user = userEvent.setup();
		renderPage();
		await user.click(
			await screen.findByTitle("Ver traslado TR-0003"),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText("Martillo")).toBeInTheDocument();
	});
});
