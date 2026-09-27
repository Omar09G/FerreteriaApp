import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import ConteosPage from "@/features/inventario/ConteosPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiProductos } from "@/lib/api/catalogo";
import { apiConteos, apiCrearConteo } from "@/lib/api/inventario";

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

const PRODUCTO_CONTEO = {
	productoId: 1,
	codigo: "MAR-001",
	tipo: "PRODUCTO",
	nombre: "Martillo",
	descripcion: null,
	categoriaId: 1,
	categoriaNombre: "Herramientas",
	marcaId: null,
	marcaNombre: null,
	unidadMedidaId: 1,
	unidadMedidaClave: "PZA",
	costoActual: 100,
	precioMenudeo: 150,
	precioMayoreo: null,
	aplicaIva: true,
	stockActual: 10,
	imagenUrl: null,
	codigosBarras: [],
};

function paginaProductos(items: unknown[]) {
	return {
		success: true,
		data: items,
		meta: { page: 0, size: 20, totalElements: items.length, totalPages: 1 },
	};
}

describe("ConteosPage (profundización)", () => {
	it("filtra por almacén, estado y rango combinados", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findAllByText("Central");
		await user.selectOptions(screen.getByLabelText("Almacén"), "1");
		await waitFor(() =>
			expect(vi.mocked(apiConteos)).toHaveBeenLastCalledWith(
				expect.objectContaining({ almacenId: 1, page: 0 }),
			),
		);
		await user.selectOptions(screen.getByLabelText("Estado"), "APLICADO");
		await waitFor(() =>
			expect(vi.mocked(apiConteos)).toHaveBeenLastCalledWith(
				expect.objectContaining({ estado: "APLICADO", page: 0 }),
			),
		);
		await user.click(screen.getByRole("button", { name: "Todos" }));
		await waitFor(() => {
			const ultima = vi.mocked(apiConteos).mock.calls.at(-1)?.[0] as Record<
				string,
				unknown
			>;
			expect(ultima.fechaInicio).toBeUndefined();
		});
	});

	it("muestra badges de aplicado y cancelado con diferencias", async () => {
		vi.mocked(apiConteos).mockResolvedValueOnce({
			success: true,
			data: [
				{
					conteoId: 2,
					almacenId: 1,
					almacenNombre: null,
					fecha: "2026-09-24T10:00:00",
					estado: "APLICADO",
					usuarioId: 2,
					usuarioNombre: null,
					observaciones: null,
					totalPartidas: 1,
					diferenciaTotal: -3,
					detalles: [
						{
							productoId: 2,
							productoCodigo: null,
							productoNombre: null,
							cantidadSistema: 10,
							cantidadFisica: 7,
							diferencia: -3,
						},
					],
				},
				{
					conteoId: 3,
					almacenId: 1,
					almacenNombre: "Central",
					fecha: "2026-09-24T10:00:00",
					estado: "CANCELADO",
					usuarioId: 1,
					usuarioNombre: "Admin",
					observaciones: "Duplicado",
					totalPartidas: 0,
					diferenciaTotal: 0,
					detalles: [],
				},
			],
			meta: { page: 0, size: 15, totalElements: 2, totalPages: 1 },
		} as never);
		renderPage();
		const tabla = await screen.findByRole("table");
		expect(await within(tabla).findByText("APLICADO")).toBeInTheDocument();
		expect(within(tabla).getByText("CANCELADO")).toBeInTheDocument();
		expect(within(tabla).getByText("#2")).toBeInTheDocument();
	});

	it("muestra el detalle con diferencia negativa y código ausente", async () => {
		const user = userEvent.setup();
		vi.mocked(apiConteos).mockResolvedValueOnce({
			success: true,
			data: [
				{
					conteoId: 2,
					almacenId: 1,
					almacenNombre: null,
					fecha: "2026-09-24T10:00:00",
					estado: "APLICADO",
					usuarioId: 2,
					usuarioNombre: null,
					observaciones: null,
					totalPartidas: 1,
					diferenciaTotal: -3,
					detalles: [
						{
							productoId: 2,
							productoCodigo: null,
							productoNombre: null,
							cantidadSistema: 10,
							cantidadFisica: 7,
							diferencia: -3,
						},
					],
				},
			],
			meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		await user.click(
			await screen.findByRole("button", { name: "Ver detalle del conteo 2" }),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(
			within(dialogo).getByRole("heading", { name: /Conteo #2/ }),
		).toBeInTheDocument();
		expect(within(dialogo).getByText("Contó")).toBeInTheDocument();
	});

	it("registra un conteo válido con partida y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(
			paginaProductos([PRODUCTO_CONTEO]) as never,
		);
		vi.mocked(apiCrearConteo).mockResolvedValueOnce({ conteoId: 9 } as never);
		renderPage();
		await screen.findAllByText("Central");
		await user.click(screen.getByRole("button", { name: /nuevo conteo/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(within(dialogo).getByLabelText(/Almacén/), "1");
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo{enter}",
		);
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		const cantidad = await within(dialogo).findByLabelText(
			"Cantidad física de Martillo",
		);
		await user.clear(cantidad);
		await user.type(cantidad, "12");
		await user.type(
			within(dialogo).getByLabelText(/Observaciones/),
			"Conteo mensual",
		);
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar conteo/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearConteo)).toHaveBeenCalledWith({
				almacenId: 1,
				observaciones: "Conteo mensual",
				detalles: [{ productoId: 1, cantidadFisica: 12 }],
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Conteo físico registrado"),
			}),
		);
	});

	it("valida almacén y partida antes de registrar", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findAllByText("Central");
		await user.click(screen.getByRole("button", { name: /nuevo conteo/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar conteo/ }),
		);
		expect(
			await within(dialogo).findByText(/almacén y agrega al menos una partida/),
		).toBeInTheDocument();
		expect(apiCrearConteo).not.toHaveBeenCalled();
	});

	it("muestra Sin coincidencias y permite quitar la partida", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValueOnce(paginaProductos([]) as never);
		renderPage();
		await screen.findAllByText("Central");
		await user.click(screen.getByRole("button", { name: /nuevo conteo/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Inexistente{enter}",
		);
		expect(
			await within(dialogo).findByText("Sin coincidencias."),
		).toBeInTheDocument();
		vi.mocked(apiProductos).mockResolvedValue(
			paginaProductos([PRODUCTO_CONTEO]) as never,
		);
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo{enter}",
		);
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		await within(dialogo).findByLabelText("Cantidad física de Martillo");
		await user.click(within(dialogo).getByRole("button", { name: "Quitar" }));
		expect(
			within(dialogo).queryByLabelText("Cantidad física de Martillo"),
		).not.toBeInTheDocument();
	});

	it("muestra toast si registrar el conteo falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(
			paginaProductos([PRODUCTO_CONTEO]) as never,
		);
		vi.mocked(apiCrearConteo).mockRejectedValueOnce(
			new Error("conteo duplicado"),
		);
		renderPage();
		await screen.findAllByText("Central");
		await user.click(screen.getByRole("button", { name: /nuevo conteo/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(within(dialogo).getByLabelText(/Almacén/), "1");
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo{enter}",
		);
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		await within(dialogo).findByLabelText("Cantidad física de Martillo");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar conteo/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("conteo duplicado"),
				}),
			),
		);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiConteos).mockResolvedValue({
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
					detalles: [],
				},
			],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findAllByText("Central");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiConteos)).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta los conteos visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findAllByText("Central");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^conteos-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiConteos).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
