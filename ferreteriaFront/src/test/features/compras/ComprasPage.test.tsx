import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import ComprasPage from "@/features/compras/ComprasPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import { apiProductos } from "@/lib/api/catalogo";
import { apiCompras, apiCrearCompra } from "@/lib/api/compras";

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

const PRODUCTO_COMPRA = {
	productoId: 10,
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

const TURNO = {
	turnoCajaId: 5,
	cajaId: 1,
	cajaNombre: "Caja 1",
	usuarioId: 1,
	aperturaEn: "2026-09-24T08:00:00",
	montoApertura: 500,
	cierreEn: null,
	montoEsperado: null,
	montoContado: null,
	diferencia: null,
	estado: "ABIERTO",
	observaciones: null,
};

const CAJA = {
	cajaId: 1,
	nombre: "Caja 1",
	almacenId: 1,
	almacenNombre: "Central",
	activa: true,
};

function paginaProductos(items: unknown[]) {
	return {
		success: true,
		data: items,
		meta: { page: 0, size: 20, totalElements: items.length, totalPages: 1 },
	};
}

describe("ComprasPage (profundización)", () => {
	it("filtra por proveedor y ofrece Limpiar", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findAllByText("C-0001");
		await user.selectOptions(screen.getByLabelText("Proveedor"), "9");
		expect(
			await screen.findByRole("button", { name: "Limpiar" }),
		).toBeInTheDocument();
		expect(vi.mocked(apiCompras)).toHaveBeenLastCalledWith(
			expect.objectContaining({ proveedorId: 9, page: 0 }),
		);
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		expect(
			screen.queryByRole("button", { name: "Limpiar" }),
		).not.toBeInTheDocument();
	});

	it("filtra por rango de fechas y lo quita con Todos", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findAllByText("C-0001");
		const api = vi.mocked(apiCompras);
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
		expect(api.mock.calls.at(-1)?.[0]).toMatchObject({ desde: nuevo });
		await user.click(screen.getByRole("button", { name: "Todos" }));
		await waitFor(() => {
			const ultima = api.mock.calls.at(-1)?.[0] as Record<string, unknown>;
			expect(ultima.desde).toBeUndefined();
		});
	});

	it("muestra turno, notas y guion sin factura en el detalle", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCompras).mockResolvedValue({
			success: true,
			data: [
				{
					...COMPRA,
					facturaProveedor: null,
					estado: "PENDIENTE",
					turnoCajaId: 5,
					notas: "Entrega urgente",
				},
			],
			meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		await user.click(
			await screen.findByRole("button", { name: "Ver detalle de C-0001" }),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText("PENDIENTE")).toBeInTheDocument();
		expect(within(dialogo).getByText("#5")).toBeInTheDocument();
		expect(within(dialogo).getByText("Entrega urgente")).toBeInTheDocument();
	});

	it("busca producto, lo agrega como partida y calcula el total", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(paginaProductos([PRODUCTO_COMPRA]) as never);
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /nueva compra/i }));
		const dialogo = await screen.findByRole("dialog");
		const buscar = within(dialogo).getByLabelText(/Buscar producto/);
		await user.type(buscar, "Martillo{enter}");
		await within(dialogo).findByRole("button", { name: /Martillo/ });
		expect(vi.mocked(apiProductos)).toHaveBeenCalledWith(
			expect.objectContaining({ q: "Martillo" }),
		);
		await user.click(within(dialogo).getByRole("button", { name: /Martillo/ }));
		expect(
			await within(dialogo).findByLabelText("Cantidad de Martillo"),
		).toBeInTheDocument();
		expect(within(dialogo).getByText("Total estimado")).toBeInTheDocument();
		// Agregar el mismo producto no duplica la partida.
		await user.type(within(dialogo).getByLabelText(/Buscar producto/), "Martillo{enter}");
		await within(dialogo).findByRole("button", { name: /Martillo/ });
		await user.click(within(dialogo).getByRole("button", { name: /Martillo/ }));
		expect(
			within(dialogo).getAllByLabelText("Cantidad de Martillo"),
		).toHaveLength(1);
	});

	it("muestra Sin coincidencias cuando la búsqueda no trae nada", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(paginaProductos([]) as never);
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /nueva compra/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Inexistente{enter}",
		);
		expect(
			await within(dialogo).findByText("Sin coincidencias."),
		).toBeInTheDocument();
	});

	it("quita partidas agregadas con el botón Quitar", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(paginaProductos([PRODUCTO_COMPRA]) as never);
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /nueva compra/i }));
		const dialogo = await screen.findByRole("dialog");
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

	it("registra la compra a crédito con partida y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(paginaProductos([PRODUCTO_COMPRA]) as never);
		vi.mocked(apiCrearCompra).mockResolvedValueOnce({ ...COMPRA, compraId: 9 } as never);
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /nueva compra/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(within(dialogo).getByLabelText(/^Proveedor/), "9");
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén de entrada/),
			"1",
		);
		await user.selectOptions(
			within(dialogo).getByLabelText("Forma de pago"),
			"6",
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
			within(dialogo).getByRole("button", { name: /Registrar compra/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearCompra)).toHaveBeenCalledWith({
				proveedorId: 9,
				almacenId: 1,
				formaPagoId: 6,
				cajaId: 0,
				facturaProveedor: undefined,
				notas: undefined,
				detalles: [{ productoId: 10, cantidad: 1, costoUnitario: 100 }],
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Compra registrada"),
			}),
		);
	});

	it("valida proveedor, almacén y partida antes de registrar", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /nueva compra/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar compra/ }),
		);
		expect(
			await within(dialogo).findByText(
				"Completa proveedor, almacén y al menos una partida.",
			),
		).toBeInTheDocument();
		expect(apiCrearCompra).not.toHaveBeenCalled();
	});

	it("muestra toast si registrar la compra falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(paginaProductos([PRODUCTO_COMPRA]) as never);
		vi.mocked(apiCrearCompra).mockRejectedValueOnce(new Error("sin stock"));
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /nueva compra/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(within(dialogo).getByLabelText(/^Proveedor/), "9");
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén de entrada/),
			"1",
		);
		await user.selectOptions(
			within(dialogo).getByLabelText("Forma de pago"),
			"6",
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
			within(dialogo).getByRole("button", { name: /Registrar compra/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin stock") }),
			),
		);
	});

	it("avisa cuando la caja no tiene turno abierto", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCajas).mockResolvedValue([CAJA] as never);
		vi.mocked(apiTurnoActual).mockRejectedValue(new Error("sin turno"));
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /nueva compra/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén de entrada/),
			"1",
		);
		await user.selectOptions(within(dialogo).getByLabelText(/^Caja/), "1");
		expect(
			await within(dialogo).findByText(/no tiene un turno abierto/),
		).toBeInTheDocument();
	});

	it("confirma turno abierto al seleccionar caja con turno", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCajas).mockResolvedValue([CAJA] as never);
		vi.mocked(apiTurnoActual).mockResolvedValue(TURNO as never);
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /nueva compra/i }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(
			within(dialogo).getByLabelText(/Almacén de entrada/),
			"1",
		);
		await user.selectOptions(within(dialogo).getByLabelText(/^Caja/), "1");
		expect(
			await within(dialogo).findByText(/Turno abierto/),
		).toBeInTheDocument();
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCompras).mockResolvedValue({
			success: true,
			data: [COMPRA],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiCompras)).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta las compras visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findAllByText("C-0001");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^compras-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiCompras).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
