import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import TrasladosPage from "@/features/inventario/TrasladosPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiAlmacenes, apiProductos } from "@/lib/api/catalogo";
import { apiCrearTraslado, apiTraslados } from "@/lib/api/inventario";

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

const PRODUCTO_TRASLADO = {
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
	stockActual: 50,
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

describe("TrasladosPage (profundización)", () => {
	it("filtra por estado y recarga la lista", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("TR-0003");
		await user.selectOptions(screen.getByLabelText("Estado"), "CANCELADO");
		await waitFor(() =>
			expect(vi.mocked(apiTraslados)).toHaveBeenLastCalledWith(
				expect.objectContaining({ estado: "CANCELADO", page: 0 }),
			),
		);
	});

	it("muestra el badge de cancelado", async () => {
		vi.mocked(apiTraslados).mockResolvedValueOnce({
			success: true,
			data: [
				{
					trasladoId: 4,
					folio: "TR-0004",
					almacenOrigen: 1,
					almacenOrigenNombre: "Central",
					almacenDestino: 2,
					almacenDestinoNombre: "Sucursal",
					estado: "CANCELADO",
					usuarioId: 1,
					creadoEn: "2026-09-24T10:00:00",
					detalles: [],
				},
			],
			meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("CANCELADO")).toBeInTheDocument();
	});

	it("registra un traslado válido y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(
			paginaProductos([PRODUCTO_TRASLADO]) as never,
		);
		vi.mocked(apiCrearTraslado).mockResolvedValueOnce({
			trasladoId: 9,
		} as never);
		renderPage();
		await screen.findByText("TR-0003");
		await user.click(screen.getByRole("button", { name: /nuevo traslado/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén origen/),
			"1",
		);
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén destino/),
			"2",
		);
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo{enter}",
		);
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		const cantidad = await within(dialogo).findByLabelText(
			"Cantidad de Martillo",
		);
		await user.clear(cantidad);
		await user.type(cantidad, "4");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar traslado/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearTraslado)).toHaveBeenCalledWith({
				almacenOrigen: 1,
				almacenDestino: 2,
				detalles: [{ productoId: 1, cantidad: 4 }],
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Traslado registrado"),
			}),
		);
	});

	it("rechaza origen y destino iguales", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("TR-0003");
		await user.click(screen.getByRole("button", { name: /nuevo traslado/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén origen/),
			"1",
		);
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén destino/),
			"1",
		);
		expect(
			await within(dialogo).findByText(
				"El almacén de destino debe ser distinto al de origen.",
			),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar traslado/ }),
		);
		expect(
			await within(dialogo).findByText(/origen y destino distintos/),
		).toBeInTheDocument();
		expect(apiCrearTraslado).not.toHaveBeenCalled();
	});

	it("valida almacenes y partida antes de registrar", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("TR-0003");
		await user.click(screen.getByRole("button", { name: /nuevo traslado/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar traslado/ }),
		);
		expect(
			await within(dialogo).findByText(/origen y destino distintos/),
		).toBeInTheDocument();
		expect(apiCrearTraslado).not.toHaveBeenCalled();
	});

	it("muestra Sin coincidencias y permite quitar la partida", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValueOnce(paginaProductos([]) as never);
		renderPage();
		await screen.findByText("TR-0003");
		await user.click(screen.getByRole("button", { name: /nuevo traslado/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Inexistente{enter}",
		);
		expect(
			await within(dialogo).findByText("Sin coincidencias."),
		).toBeInTheDocument();
		vi.mocked(apiProductos).mockResolvedValue(
			paginaProductos([PRODUCTO_TRASLADO]) as never,
		);
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo{enter}",
		);
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		await within(dialogo).findByLabelText("Cantidad de Martillo");
		await user.click(within(dialogo).getByRole("button", { name: "Quitar" }));
		expect(
			within(dialogo).queryByLabelText("Cantidad de Martillo"),
		).not.toBeInTheDocument();
	});

	it("muestra toast si registrar el traslado falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(
			paginaProductos([PRODUCTO_TRASLADO]) as never,
		);
		vi.mocked(apiCrearTraslado).mockRejectedValueOnce(
			new Error("stock insuficiente"),
		);
		renderPage();
		await screen.findByText("TR-0003");
		await user.click(screen.getByRole("button", { name: /nuevo traslado/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén origen/),
			"1",
		);
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén destino/),
			"2",
		);
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo{enter}",
		);
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		await within(dialogo).findByLabelText("Cantidad de Martillo");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar traslado/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("stock insuficiente"),
				}),
			),
		);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTraslados).mockResolvedValue({
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
					detalles: [],
				},
			],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findByText("TR-0003");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiTraslados)).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta los traslados visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("TR-0003");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^traslados-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiTraslados).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
