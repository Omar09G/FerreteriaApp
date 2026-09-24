import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import ConteosPage from "@/features/inventario/ConteosPage";
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
	apiProductos: vi.fn(async () => ({
		success: true,
		data: [],
		meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
	})),
}));

vi.mock("@/lib/api/inventario", () => ({
	apiConteos: vi.fn(async () => ({
		success: true,
		data: [
			{
				conteoId: 1,
				almacenId: 1,
				almacenNombre: "Central",
				fecha: "2026-09-24T10:00:00",
				estado: "EN_PROCESO",
				usuarioId: 1,
				usuarioNombre: "Admin",
				observaciones: "Conteo de prueba",
				totalPartidas: 1,
				diferenciaTotal: 2,
				detalles: [
					{
						productoId: 1,
						productoCodigo: "MAR-001",
						productoNombre: "Martillo",
						cantidadSistema: 10,
						cantidadFisica: 12,
						diferencia: 2,
					},
				],
			},
		],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	})),
	apiCrearConteo: vi.fn(),
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
	return render(<ConteosPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("ConteosPage (smoke)", () => {
	it("renderiza título, filtros y la fila del conteo", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Conteos físicos" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /nuevo conteo/i }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Estado")).toBeInTheDocument();
		expect((await screen.findAllByText("Central")).length).toBeGreaterThanOrEqual(1);
		expect(screen.getByText("EN_PROCESO", { selector: "span" })).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Ver detalle del conteo 1" }),
		).toBeInTheDocument();
	});

	it("'Nuevo conteo' abre el diálogo de alta", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findAllByText("Central");
		await user.click(screen.getByRole("button", { name: /nuevo conteo/i }));
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText("Nuevo conteo")).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/Buscar producto/),
		).toBeInTheDocument();
	});

	it("ver detalle abre el diálogo con las partidas", async () => {
		const user = userEvent.setup();
		renderPage();
		await user.click(
			await screen.findByRole("button", { name: "Ver detalle del conteo 1" }),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(
			within(dialogo).getByText(/Conteo #1/),
		).toBeInTheDocument();
		expect(within(dialogo).getByText("Martillo")).toBeInTheDocument();
	});
});
