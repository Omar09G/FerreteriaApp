import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Swal from "sweetalert2";

vi.mock("@/lib/api/venta", () => ({
	apiCotizaciones: vi.fn(),
	apiCrearCotizacion: vi.fn(),
	apiConvertirCotizacion: vi.fn(),
}));
vi.mock("@/lib/api/catalogo", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiAlmacenes: vi.fn(),
	apiClientes: vi.fn(),
	apiProductos: vi.fn(),
}));
vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));
vi.mock("@/lib/api/caja", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiCajas: vi.fn(),
	apiTurnoActual: vi.fn(),
}));
vi.mock("@/lib/api/archivos", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiSubirImagen: vi.fn(),
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

import {
	apiConvertirCotizacion,
	apiCotizaciones,
	apiCrearCotizacion,
} from "@/lib/api/venta";
import { apiSubirImagen } from "@/lib/api/archivos";
import { apiAlmacenes, apiClientes, apiProductos } from "@/lib/api/catalogo";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import CotizacionesPage from "@/features/ventas/CotizacionesPage";
import {
	ALMACEN,
	CLIENTE,
	COTIZACION,
	PRODUCTO,
	pageOf,
	renderPagina,
} from "@/test/helpers/ventasPos";

const apiCotizacionesMock = vi.mocked(apiCotizaciones);
vi.mocked(apiCrearCotizacion);
vi.mocked(apiConvertirCotizacion);
const apiAlmacenesMock = vi.mocked(apiAlmacenes);
const apiClientesMock = vi.mocked(apiClientes);
const apiProductosMock = vi.mocked(apiProductos);
const apiCajasMock = vi.mocked(apiCajas);
vi.mocked(apiTurnoActual);

beforeEach(() => {
	vi.clearAllMocks();
	Object.defineProperty(URL, "createObjectURL", {
		value: vi.fn(() => "blob:mock"),
		writable: true,
		configurable: true,
	});
	Object.defineProperty(URL, "revokeObjectURL", {
		value: vi.fn(),
		writable: true,
		configurable: true,
	});
	apiCotizacionesMock.mockResolvedValue(pageOf([COTIZACION]));
	apiAlmacenesMock.mockResolvedValue([ALMACEN]);
	apiClientesMock.mockResolvedValue(pageOf([CLIENTE]));
	apiCajasMock.mockResolvedValue([]);
});

describe("CotizacionesPage", () => {
	it("renderiza título, filtro y tabla con una cotización", async () => {
		renderPagina(<CotizacionesPage />);
		expect(
			screen.getByRole("heading", { name: "Cotizaciones" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Estado")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nueva cotización/ }),
		).toBeInTheDocument();
		expect(await screen.findByText("Cotizaciones (1)")).toBeInTheDocument();
		expect(await screen.findByText("COT-001")).toBeInTheDocument();
		expect(screen.getByText("VIGENTE")).toBeInTheDocument();
	});

	it("abre el formulario de nueva cotización", async () => {
		const user = userEvent.setup();
		renderPagina(<CotizacionesPage />);
		await user.click(
			screen.getByRole("button", { name: /Nueva cotización/ }),
		);
		expect(
			await screen.findByRole("dialog", { name: "Nueva cotización" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Buscar producto/)).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Registrar cotización/ }),
		).toBeInTheDocument();
	});

	it("abre el detalle de partidas al hacer clic en Ver detalles", async () => {
		const user = userEvent.setup();
		renderPagina(<CotizacionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Ver detalles" }),
		);
		expect(
			await screen.findByText("Detalles de cotización"),
		).toBeInTheDocument();
		expect(screen.getByText("Martillo")).toBeInTheDocument();
		expect(screen.getByText("Cant.")).toBeInTheDocument();
	});

	it("muestra la foto de evidencia en el detalle cuando existe", async () => {
		const user = userEvent.setup();
		apiCotizacionesMock.mockResolvedValueOnce(
			pageOf([
				{ ...COTIZACION, evidenciaUrl: "https://cdn.tienda.com/llave.jpg" },
			]),
		);
		renderPagina(<CotizacionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Ver detalles" }),
		);
		expect(
			await screen.findByText("Detalles de cotización"),
		).toBeInTheDocument();
		expect(
			screen.getByRole("img", { name: "Foto de evidencia del cliente" }),
		).toHaveAttribute("src", "https://cdn.tienda.com/llave.jpg");
	});

	it("filtra por estado y muestra Limpiar para quitar el filtro", async () => {
		const user = userEvent.setup();
		renderPagina(<CotizacionesPage />);
		await screen.findByText("COT-001");
		await user.selectOptions(screen.getByLabelText("Estado"), "VIGENTE");
		expect(
			await screen.findByRole("button", { name: "Limpiar" }),
		).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		expect(
			screen.queryByRole("button", { name: "Limpiar" }),
		).not.toBeInTheDocument();
	});

	it("muestra el botón Excel para exportar lo visible", async () => {
		renderPagina(<CotizacionesPage />);
		await screen.findByText("COT-001");
		expect(
			screen.getByRole("button", { name: /excel/i }),
		).toBeInTheDocument();
	});

	it("busca un producto y lo agrega como partida", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		renderPagina(<CotizacionesPage />);
		await user.click(
			screen.getByRole("button", { name: /Nueva cotización/ }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva cotización",
		});
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo",
		);
		await user.click(within(dialogo).getByRole("button", { name: /Buscar/ }));
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		expect(
			await within(dialogo).findByText("Total estimado"),
		).toBeInTheDocument();
	});

	it("abre el diálogo de convertir a venta", async () => {
		const user = userEvent.setup();
		renderPagina(<CotizacionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Convertir a venta" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Convertir a venta",
		});
		expect(within(dialogo).getByLabelText(/Almacén/)).toBeInTheDocument();
		expect(within(dialogo).getByLabelText(/Caja/)).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/Forma de pago/),
		).toBeInTheDocument();
		expect(
			within(dialogo).getByRole("button", { name: /Convertir a venta/ }),
		).toBeInTheDocument();
	});
});

const TURNO = {
	turnoCajaId: 5,
	cajaId: 1,
	cajaNombre: "Caja 1",
	usuarioId: 1,
	aperturaEn: "2026-01-10T08:00:00",
	montoApertura: 500,
	cierreEn: null,
	montoEsperado: null,
	montoContado: null,
	diferencia: null,
	estado: "ABIERTO",
	observaciones: null,
};

describe("CotizacionesPage (profundización)", () => {
	it("muestra los estados convertida, expirada y cancelada", async () => {
		apiCotizacionesMock.mockResolvedValue(
			pageOf([
				COTIZACION,
				{ ...COTIZACION, cotizacionId: 2, folio: "COT-002", estado: "CONVERTIDA" },
				{ ...COTIZACION, cotizacionId: 3, folio: "COT-003", estado: "EXPIRADA" },
				{ ...COTIZACION, cotizacionId: 4, folio: "COT-004", estado: "CANCELADA" },
			]),
		);
		renderPagina(<CotizacionesPage />);
		expect(await screen.findByText("COT-004")).toBeInTheDocument();
		expect(screen.getByText("VIGENTE")).toBeInTheDocument();
		expect(screen.getByText("CONVERTIDA")).toBeInTheDocument();
		expect(screen.getByText("EXPIRADA")).toBeInTheDocument();
		expect(screen.getByText("CANCELADA")).toBeInTheDocument();
	});

	it("crea la cotización con cliente, vigencia y partida editada", async () => {
		const user = userEvent.setup();
		const apiCrearMock = vi.mocked(apiCrearCotizacion);
		apiCrearMock.mockResolvedValue({ ...COTIZACION, cotizacionId: 9 } as never);
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		renderPagina(<CotizacionesPage />);
		await user.click(screen.getByRole("button", { name: /Nueva cotización/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva cotización",
		});
		await user.selectOptions(
			within(dialogo).getByLabelText(/Cliente/),
			"1",
		);
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo",
		);
		await user.click(within(dialogo).getByRole("button", { name: /Buscar/ }));
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		const cantidad = await within(dialogo).findByLabelText(
			"Cantidad de Martillo",
		);
		await user.clear(cantidad);
		await user.type(cantidad, "3");
		const precio = within(dialogo).getByLabelText("Precio de Martillo");
		await user.clear(precio);
		await user.type(precio, "60");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar cotización/ }),
		);
		await waitFor(() =>
			expect(apiCrearMock).toHaveBeenCalledWith({
				clienteId: 1,
				vigenciaHasta: undefined,
				detalles: [{ productoId: 10, cantidad: 3, precioUnitario: 60 }],
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Cotización creada"),
			}),
		);
	});

	it("muestra el campo de foto de evidencia en el formulario", async () => {
		const user = userEvent.setup();
		renderPagina(<CotizacionesPage />);
		await user.click(screen.getByRole("button", { name: /Nueva cotización/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva cotización",
		});
		expect(
			within(dialogo).getByText("Foto de evidencia (opcional)"),
		).toBeInTheDocument();
	});

	it("sube la foto y la liga a la cotización creada", async () => {
		const user = userEvent.setup();
		const apiCrearMock = vi.mocked(apiCrearCotizacion);
		apiCrearMock.mockResolvedValue({ ...COTIZACION, cotizacionId: 9 } as never);
		vi.mocked(apiSubirImagen).mockResolvedValue("https://cdn.tienda.com/llave.jpg");
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		renderPagina(<CotizacionesPage />);
		await user.click(screen.getByRole("button", { name: /Nueva cotización/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva cotización",
		});
		const inputFile = dialogo.querySelector(
			'input[type="file"]',
		) as HTMLInputElement;
		expect(inputFile).not.toBeNull();
		await user.upload(
			inputFile,
			new File(["x"], "llave.jpg", { type: "image/jpeg" }),
		);
		await waitFor(() =>
			expect(apiSubirImagen).toHaveBeenCalledTimes(1),
		);
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo",
		);
		await user.click(within(dialogo).getByRole("button", { name: /Buscar/ }));
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar cotización/ }),
		);
		await waitFor(() =>
			expect(apiCrearMock).toHaveBeenCalledWith(
				expect.objectContaining({
					evidenciaUrl: "https://cdn.tienda.com/llave.jpg",
				}),
			),
		);
	});

	it("quita partidas agregadas antes de registrar", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		renderPagina(<CotizacionesPage />);
		await user.click(screen.getByRole("button", { name: /Nueva cotización/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva cotización",
		});
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo",
		);
		await user.click(within(dialogo).getByRole("button", { name: /Buscar/ }));
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		await within(dialogo).findByText("Total estimado");
		await user.click(within(dialogo).getByRole("button", { name: "Quitar" }));
		expect(
			within(dialogo).queryByText("Total estimado"),
		).not.toBeInTheDocument();
	});

	it("valida al menos una partida antes de registrar", async () => {
		const user = userEvent.setup();
		renderPagina(<CotizacionesPage />);
		await user.click(screen.getByRole("button", { name: /Nueva cotización/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva cotización",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar cotización/ }),
		);
		expect(
			await within(dialogo).findByText(/partida con cantidad y precio válidos/),
		).toBeInTheDocument();
	});

	it("muestra toast si crear la cotización falla", async () => {
		const user = userEvent.setup();
		const apiCrearMock = vi.mocked(apiCrearCotizacion);
		apiCrearMock.mockRejectedValueOnce(new Error("sin stock"));
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		renderPagina(<CotizacionesPage />);
		await user.click(screen.getByRole("button", { name: /Nueva cotización/ }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva cotización",
		});
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo",
		);
		await user.click(within(dialogo).getByRole("button", { name: /Buscar/ }));
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		await within(dialogo).findByText("Total estimado");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar cotización/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin stock") }),
			),
		);
	});

	it("convierte la cotización con almacén y caja con turno", async () => {
		const user = userEvent.setup();
		const apiConvertirMock = vi.mocked(apiConvertirCotizacion);
		apiConvertirMock.mockResolvedValue({ ...COTIZACION } as never);
		vi.mocked(apiCajas).mockResolvedValue([
			{
				cajaId: 1,
				nombre: "Caja 1",
				almacenId: 1,
				almacenNombre: "Matriz",
				activa: true,
			},
		] as never);
		vi.mocked(apiTurnoActual).mockResolvedValue(TURNO as never);
		renderPagina(<CotizacionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Convertir a venta" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Convertir a venta",
		});
		await user.selectOptions(within(dialogo).getByLabelText(/Almacén/), "1");
		await user.selectOptions(within(dialogo).getByLabelText(/Caja/), "1");
		expect(
			await within(dialogo).findByText(/Turno abierto/),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: /Convertir a venta/ }),
		);
		await waitFor(() =>
			expect(apiConvertirMock).toHaveBeenCalledWith(1, 1, 1, 1),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("convertida a venta"),
			}),
		);
	});

	it("valida almacén y caja con turno al convertir", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCajas).mockResolvedValue([
			{
				cajaId: 1,
				nombre: "Caja 1",
				almacenId: 1,
				almacenNombre: "Matriz",
				activa: true,
			},
		] as never);
		vi.mocked(apiTurnoActual).mockRejectedValue(new Error("sin turno"));
		renderPagina(<CotizacionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Convertir a venta" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Convertir a venta",
		});
		await user.selectOptions(within(dialogo).getByLabelText(/Almacén/), "1");
		await user.selectOptions(within(dialogo).getByLabelText(/Caja/), "1");
		expect(
			await within(dialogo).findByText(/no tiene un turno abierto/),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: /Convertir a venta/ }),
		);
		expect(
			await within(dialogo).findByText(/caja con turno abierto/),
		).toBeInTheDocument();
		expect(apiConvertirCotizacion).not.toHaveBeenCalled();
	});

	it("muestra toast si convertir falla", async () => {
		const user = userEvent.setup();
		const apiConvertirMock = vi.mocked(apiConvertirCotizacion);
		apiConvertirMock.mockRejectedValueOnce(new Error("caja cerrada"));
		vi.mocked(apiCajas).mockResolvedValue([
			{
				cajaId: 1,
				nombre: "Caja 1",
				almacenId: 1,
				almacenNombre: "Matriz",
				activa: true,
			},
		] as never);
		vi.mocked(apiTurnoActual).mockResolvedValue(TURNO as never);
		renderPagina(<CotizacionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Convertir a venta" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Convertir a venta",
		});
		await user.selectOptions(within(dialogo).getByLabelText(/Almacén/), "1");
		await user.selectOptions(within(dialogo).getByLabelText(/Caja/), "1");
		await within(dialogo).findByText(/Turno abierto/);
		await user.click(
			within(dialogo).getByRole("button", { name: /Convertir a venta/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("caja cerrada"),
				}),
			),
		);
	});

	it("exporta las cotizaciones visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPagina(<CotizacionesPage />);
		await screen.findByText("COT-001");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^cotizaciones-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		apiCotizacionesMock.mockResolvedValue({
			success: true,
			data: [COTIZACION],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPagina(<CotizacionesPage />);
		await screen.findByText("COT-001");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(apiCotizacionesMock).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		apiCotizacionesMock.mockRejectedValueOnce(new Error("sin conexión"));
		renderPagina(<CotizacionesPage />);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
