import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import userEvent from "@testing-library/user-event";
import Swal from "sweetalert2";

import { ToastProvider } from "@/components/ui/Toast";

vi.mock("@/lib/api/catalogo", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiProductos: vi.fn(),
	apiAlmacenes: vi.fn(),
	apiClientes: vi.fn(),
	apiGetCliente: vi.fn(),
}));
vi.mock("@/lib/api/caja", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiCajas: vi.fn(),
	apiTurnoActual: vi.fn(),
}));
vi.mock("@/lib/api/venta", () => ({
	apiCheckout: vi.fn(),
	apiVentas: vi.fn(),
}));
vi.mock("@/lib/api/promociones", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiEvaluarPromociones: vi.fn(),
}));
vi.mock("@/lib/api/ticketConfig", () => ({
	apiGetTicketConfig: vi.fn(),
}));
vi.mock("@/lib/print/serial", () => ({
	getSilentEnabled: vi.fn(() => false),
	printViaSerial: vi.fn(),
}));
vi.mock("@/lib/print/escpos", () => ({
	buildEscPosTicket: vi.fn(() => new Uint8Array([27, 64])),
}));
vi.mock("@/lib/print/ticket", () => ({
	printTicketById: vi.fn(),
}));
vi.mock("@/lib/camara", () => ({
	camaraDisponible: vi.fn(() => false),
}));
vi.mock("@/components/ScannerCamara", () => ({
	ScannerCamara: ({
		abierto,
		onDetectado,
		onCerrar,
	}: {
		abierto: boolean;
		onDetectado: (codigo: string) => void;
		onCerrar: () => void;
	}) =>
		abierto ? (
			<div role="dialog" aria-label="Escáner">
				<button type="button" onClick={() => onDetectado("12345678")}>
					Simular detección
				</button>
				<button type="button" onClick={onCerrar}>
					Cerrar escáner
				</button>
			</div>
		) : null,
}));
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

import {
	apiAlmacenes,
	apiClientes,
	apiGetCliente,
	apiProductos,
} from "@/lib/api/catalogo";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import { apiCheckout, apiVentas } from "@/lib/api/venta";
import { apiEvaluarPromociones } from "@/lib/api/promociones";
import { apiGetTicketConfig } from "@/lib/api/ticketConfig";
import { getSilentEnabled, printViaSerial } from "@/lib/print/serial";
import { buildEscPosTicket } from "@/lib/print/escpos";
import { printTicketById } from "@/lib/print/ticket";
import { camaraDisponible } from "@/lib/camara";
import PosPage from "@/features/pos/PosPage";
import {
	ALMACEN,
	CAJA,
	CLIENTE,
	PRODUCTO,
	TURNO,
	VENTA,
	pageOf,
	renderPagina,
} from "@/test/helpers/ventasPos";

const apiProductosMock = vi.mocked(apiProductos);
const apiAlmacenesMock = vi.mocked(apiAlmacenes);
const apiClientesMock = vi.mocked(apiClientes);
vi.mocked(apiGetCliente);
const apiCajasMock = vi.mocked(apiCajas);
const apiTurnoMock = vi.mocked(apiTurnoActual);
const apiCheckoutMock = vi.mocked(apiCheckout);
const apiVentasMock = vi.mocked(apiVentas);
const apiPromosMock = vi.mocked(apiEvaluarPromociones);
const apiTicketMock = vi.mocked(apiGetTicketConfig);

beforeEach(() => {
	vi.clearAllMocks();
	apiAlmacenesMock.mockResolvedValue([ALMACEN]);
	apiCajasMock.mockResolvedValue([CAJA]);
	apiClientesMock.mockResolvedValue(pageOf([CLIENTE]));
	apiProductosMock.mockResolvedValue(pageOf([]));
	apiTurnoMock.mockResolvedValue(TURNO);
	apiPromosMock.mockResolvedValue([]);
	apiTicketMock.mockResolvedValue(null as never);
	apiVentasMock.mockResolvedValue(pageOf([]));
});

describe("PosPage", () => {
	it("renderiza punto de venta, ticket vacío, promoción y cobro", async () => {
		renderPagina(<PosPage />);
		expect(
			screen.getByRole("heading", { name: "Punto de venta" }),
		).toBeInTheDocument();
		expect(
			screen.getByLabelText(/Almacén \/ punto de venta/),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Caja donde operas/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Buscar producto/)).toBeInTheDocument();
		expect(await screen.findByText("Ticket (0)")).toBeInTheDocument();
		expect(
			screen.getByText("Agrega productos con el buscador."),
		).toBeInTheDocument();
		expect(
			screen.getByText(/Agrega productos para validar/),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Cobrar/ }),
		).toBeDisabled();
	});

	it("abre las ventas del día y muestra el resumen vacío", async () => {
		const user = userEvent.setup();
		renderPagina(<PosPage />);
		await user.click(
			screen.getByRole("button", { name: "Ver ventas del día" }),
		);
		expect(await screen.findByText(/Ventas de hoy/)).toBeInTheDocument();
		expect(
			await screen.findByText("Aún no hay ventas hoy."),
		).toBeInTheDocument();
		expect(screen.getByText("Tickets")).toBeInTheDocument();
		expect(apiVentasMock).toHaveBeenCalled();
	});

	it("busca un producto por nombre y muestra el resultado", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		renderPagina(<PosPage />);
		const input = screen.getByLabelText(/Buscar producto/);
		await user.type(input, "Martillo{enter}");
		expect(
			await screen.findByRole("listbox", { name: "Resultados de búsqueda" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Martillo")).toBeInTheDocument();
		expect(apiProductosMock).toHaveBeenCalled();
	});

	it("muestra el turno abierto al seleccionar almacén y caja", async () => {
		const user = userEvent.setup();
		renderPagina(<PosPage />);
		await screen.findByRole("option", { name: "Matriz" });
		await user.selectOptions(
			screen.getByLabelText(/Almacén \/ punto de venta/),
			"1",
		);
		await screen.findByRole("option", { name: "Caja 1 · Matriz" });
		await user.selectOptions(screen.getByLabelText(/Caja donde operas/), "1");
		expect(await screen.findByText(/abierto desde/)).toBeInTheDocument();
		expect(screen.getByText("#5")).toBeInTheDocument();
	});

	it("agrega al ticket, cobra y confirma la venta", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		apiCheckoutMock.mockResolvedValue(VENTA);
		renderPagina(<PosPage />);
		await screen.findByRole("option", { name: "Matriz" });
		await user.selectOptions(
			screen.getByLabelText(/Almacén \/ punto de venta/),
			"1",
		);
		await screen.findByRole("option", { name: "Caja 1 · Matriz" });
		await user.selectOptions(screen.getByLabelText(/Caja donde operas/), "1");
		await screen.findByText(/abierto desde/);
		await user.type(screen.getByLabelText(/Buscar producto/), "Martillo{enter}");
		const lista = await screen.findByRole("listbox", {
			name: "Resultados de búsqueda",
		});
		await user.click(within(lista).getByRole("button", { name: /Martillo/ }));
		expect(await screen.findByText("Ticket (1)")).toBeInTheDocument();
		await user.type(screen.getByLabelText("Recibido"), "100");
		const cobrar = screen.getByRole("button", { name: /Cobrar/ });
		expect(cobrar).toBeEnabled();
		await user.click(cobrar);
		const confirmar = await screen.findByRole("dialog", {
			name: "Confirmar venta",
		});
		await user.click(
			within(confirmar).getByRole("button", { name: /Confirmar y cobrar/ }),
		);
		expect(apiCheckoutMock).toHaveBeenCalled();
		expect(
			await screen.findByRole("dialog", { name: "Venta registrada" }),
		).toBeInTheDocument();
		expect(await screen.findByText("V-0001")).toBeInTheDocument();
	});

	it("muestra las ventas del día con datos en tabla", async () => {
		const user = userEvent.setup();
		apiVentasMock.mockResolvedValue(pageOf([VENTA]));
		renderPagina(<PosPage />);
		await user.click(
			screen.getByRole("button", { name: "Ver ventas del día" }),
		);
		expect(await screen.findByText(/Ventas de hoy/)).toBeInTheDocument();
		expect(await screen.findByText("V-0001")).toBeInTheDocument();
		expect(screen.getByText("Tickets")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /excel/i }),
		).toBeInTheDocument();
	});

	it("cancela la venta en curso desde el diálogo", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		renderPagina(<PosPage />);
		await user.type(screen.getByLabelText(/Buscar producto/), "Martillo{enter}");
		const lista = await screen.findByRole("listbox", {
			name: "Resultados de búsqueda",
		});
		await user.click(within(lista).getByRole("button", { name: /Martillo/ }));
		expect(await screen.findByText("Ticket (1)")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Cancelar venta" }));
		expect(
			await screen.findByRole("dialog", {
				name: "¿Cancelar la venta en curso?",
			}),
		).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Sí, cancelar" }));
		expect(await screen.findByText("Ticket (0)")).toBeInTheDocument();
	});
});

const PRODUCTO_SIN_STOCK = {
	...PRODUCTO,
	productoId: 11,
	nombre: "Clavo",
	stockActual: 0,
};

const PRODUCTO_BARRAS = {
	...PRODUCTO,
	productoId: 12,
	nombre: "Cinta",
	codigo: "CIN-001",
	codigosBarras: ["12345678"],
};

const PRODUCTO_SIN_IVA = {
	...PRODUCTO,
	productoId: 13,
	nombre: "Libro",
	aplicaIva: false,
};

const PROMO = {
	promocionId: 3,
	nombre: "Promo 10",
	tipo: "DESCUENTO",
	estado: "ACTIVA",
	aplica: true,
	motivo: "Aplica por monto mínimo",
	beneficioEstimado: 10,
	valorPct: 10,
	valorMonto: null,
	compraMinTotal: 100,
	compraMinCantidad: 1,
	maxUsosTotal: 10,
	maxUsosCliente: null,
	usosActual: 0,
	vigenciaDesde: "2026-01-01",
	vigenciaHasta: null,
	diasSemana: [1, 2, 3],
	horaDesde: "08:00",
	horaHasta: "20:00",
	soloMayoristas: false,
	productos: [10],
	categorias: [],
};

const PROMO_NO_APLICA = { ...PROMO, promocionId: 4, aplica: false, beneficioEstimado: 0, motivo: "No alcanza el mínimo" };

const TICKET_CONFIG = {
	ticketConfigId: 1,
	almacenId: null,
	logotipoUrl: null,
	mostrarLogotipo: false,
	nombreNegocio: "El Tornillo Feliz",
	direccion: "Av. Principal 123",
	cp: "40006",
	rfc: "XAXX010101000",
	telefono: "555000111",
	email: null,
	sitioWeb: null,
	tituloDocumento: "Factura simplificada",
	mostrarDatosCliente: true,
	mostrarNumeroFactura: true,
	mostrarCaja: true,
	mostrarFechaHora: true,
	mostrarVendedor: true,
	mostrarDesgloseIva: true,
	mostrarDescuento: true,
	mostrarCambio: true,
	mensajePie: "Gracias por su compra",
	pieSecundario: null,
	anchoPapelMm: 80,
	fontSizePt: 9,
	actualizadoEn: null,
	actualizadoPor: null,
};

const VENTA_DESCUENTO = {
	...VENTA,
	descuentoTotal: 10,
	total: 106,
	detalles: [{ ...VENTA.detalles[0], promocionId: 7 }],
};

function renderSinLimpiar(ui: React.ReactNode) {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>{ui}</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>,
	);
}

async function prepararTicket(user: ReturnType<typeof userEvent.setup>) {
	await screen.findByRole("option", { name: "Matriz" });
	await user.selectOptions(
		screen.getByLabelText(/Almacén \/ punto de venta/),
		"1",
	);
	await screen.findByRole("option", { name: "Caja 1 · Matriz" });
	await user.selectOptions(screen.getByLabelText(/Caja donde operas/), "1");
	await screen.findByText(/abierto desde/);
}

async function agregarMartillo(user: ReturnType<typeof userEvent.setup>) {
	apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
	await user.type(screen.getByLabelText(/Buscar producto/), "Martillo{enter}");
	const lista = await screen.findByRole("listbox", {
		name: "Resultados de búsqueda",
	});
	await user.click(within(lista).getByRole("button", { name: /Martillo/ }));
	expect(await screen.findByText("Ticket (1)")).toBeInTheDocument();
}

describe("PosPage (profundización)", () => {
	it("rechaza el producto sin existencia con aviso", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO_SIN_STOCK]));
		renderPagina(<PosPage />);
		await user.type(screen.getByLabelText(/Buscar producto/), "Clavo{enter}");
		const lista = await screen.findByRole("listbox", {
			name: "Resultados de búsqueda",
		});
		await user.click(within(lista).getByRole("button", { name: /Clavo/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("Sin existencia"),
				}),
			),
		);
		expect(screen.getByText("Ticket (0)")).toBeInTheDocument();
	});

	it("rechaza agregar más allá de la existencia", async () => {
		const user = userEvent.setup();
		const pocoStock = { ...PRODUCTO, stockActual: 1 };
		apiProductosMock.mockResolvedValue(pageOf([pocoStock]));
		renderPagina(<PosPage />);
		await prepararTicket(user);
		await user.type(screen.getByLabelText(/Buscar producto/), "Martillo{enter}");
		const lista = await screen.findByRole("listbox", {
			name: "Resultados de búsqueda",
		});
		await user.click(within(lista).getByRole("button", { name: /Martillo/ }));
		expect(await screen.findByText("Ticket (1)")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Más" }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("Existencia insuficiente"),
				}),
			),
		);
	});

	it("añade directo al buscar el código interno exacto", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		renderPagina(<PosPage />);
		await user.type(screen.getByLabelText(/Buscar producto/), "MAR-001{enter}");
		expect(await screen.findByText("Ticket (1)")).toBeInTheDocument();
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Escaneado:") }),
		);
	});

	it("detecta código de barras y añade el producto escaneado", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO_BARRAS]));
		renderPagina(<PosPage />);
		await user.type(screen.getByLabelText(/Buscar producto/), "12345678");
		expect(await screen.findByText("Ticket (1)")).toBeInTheDocument();
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Escaneado:") }),
		);
		expect(apiProductosMock).toHaveBeenCalledWith(
			expect.objectContaining({ q: "12345678" }),
		);
	});

	it("muestra sin coincidencias cuando la búsqueda no trae nada", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([]));
		renderPagina(<PosPage />);
		await user.type(
			screen.getByLabelText(/Buscar producto/),
			"Inexistente{enter}",
		);
		expect(
			await screen.findByText(/Sin coincidencias para/),
		).toBeInTheDocument();
	});

	it("muestra error cuando la búsqueda falla", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockRejectedValueOnce(new Error("búsqueda caída"));
		renderPagina(<PosPage />);
		await user.type(screen.getByLabelText(/Buscar producto/), "Martillo{enter}");
		expect(await screen.findByText(/búsqueda caída/)).toBeInTheDocument();
	});

	it("opera la línea: menos, cantidad directa, quitar y limpiar", async () => {
		const user = userEvent.setup();
		renderPagina(<PosPage />);
		await agregarMartillo(user);
		await user.click(screen.getByRole("button", { name: "Más" }));
		expect(await screen.findByText("Ticket (1)")).toBeInTheDocument();
		const cantidad = screen.getByLabelText("Cantidad de Martillo");
		fireEvent.change(cantidad, { target: { value: "3" } });
		await user.click(screen.getByRole("button", { name: "Menos" }));
		await user.click(screen.getByRole("button", { name: "Quitar" }));
		expect(await screen.findByText("Ticket (0)")).toBeInTheDocument();
		await user.type(screen.getByLabelText(/Buscar producto/), "x");
		expect(screen.getByRole("button", { name: "Limpiar ticket" })).toBeEnabled();
		await user.click(screen.getByRole("button", { name: "Limpiar ticket" }));
		expect(screen.getByLabelText(/Buscar producto/)).toHaveValue("");
	});

	it("cobra con cliente, forma no efectivo y notas", async () => {
		const user = userEvent.setup();
		apiCheckoutMock.mockResolvedValue(VENTA);
		renderPagina(<PosPage />);
		await prepararTicket(user);
		await agregarMartillo(user);
		await user.selectOptions(screen.getByLabelText(/Cliente/), "1");
		await user.selectOptions(screen.getByLabelText(/Forma de pago/), "2");
		expect(screen.getByLabelText(/Referencia/)).toBeInTheDocument();
		expect(screen.queryByLabelText("Recibido")).not.toBeInTheDocument();
		await user.type(screen.getByLabelText(/Referencia/), "AUT-123");
		await user.type(screen.getByLabelText(/Notas/), "Venta mostrador");
		await user.click(screen.getByRole("button", { name: /Cobrar/ }));
		const confirmar = await screen.findByRole("dialog", {
			name: "Confirmar venta",
		});
		expect(within(confirmar).getByText("Juan Pérez")).toBeInTheDocument();
		expect(within(confirmar).getByText("AUT-123")).toBeInTheDocument();
		expect(within(confirmar).getByText("Venta mostrador")).toBeInTheDocument();
		await user.click(
			within(confirmar).getByRole("button", { name: /Confirmar y cobrar/ }),
		);
		await waitFor(() =>
			expect(apiCheckoutMock).toHaveBeenCalledWith(
				expect.objectContaining({
					clienteId: 1,
					formaPagoId: 2,
					notas: "Venta mostrador",
				}),
			),
		);
		const pagos = apiCheckoutMock.mock.calls[0][0].pagos;
		expect(pagos[0]).toMatchObject({ formaPagoId: 2, referencia: "AUT-123" });
	});

	it("muestra Sin IVA y el desglose en la confirmación", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO_SIN_IVA]));
		apiCheckoutMock.mockResolvedValue(VENTA);
		renderPagina(<PosPage />);
		await prepararTicket(user);
		await user.type(screen.getByLabelText(/Buscar producto/), "Libro{enter}");
		const lista = await screen.findByRole("listbox", {
			name: "Resultados de búsqueda",
		});
		await user.click(within(lista).getByRole("button", { name: /Libro/ }));
		expect(await screen.findByText("Ticket (1)")).toBeInTheDocument();
		await user.type(screen.getByLabelText("Recibido"), "100");
		await user.click(screen.getByRole("button", { name: /Cobrar/ }));
		const confirmar = await screen.findByRole("dialog", {
			name: "Confirmar venta",
		});
		expect(within(confirmar).getByText("Sin IVA")).toBeInTheDocument();
		expect(within(confirmar).getByText("IVA (16%)")).toBeInTheDocument();
	});

	it("aplica la promoción y la envía en el checkout", async () => {
		const user = userEvent.setup();
		apiPromosMock.mockResolvedValue([PROMO] as never);
		apiCheckoutMock.mockResolvedValue(VENTA);
		renderPagina(<PosPage />);
		await prepararTicket(user);
		await agregarMartillo(user);
		expect(
			await screen.findByText(/Promoción aplicable: Promo 10/),
		).toBeInTheDocument();
		expect(screen.getAllByText("Aplica").length).toBeGreaterThanOrEqual(1);
		expect(
			screen.getByRole("button", { name: /Cobrar .*ahorro/ }),
		).toBeInTheDocument();
		await user.type(screen.getByLabelText("Recibido"), "100");
		await user.click(screen.getByRole("button", { name: /Cobrar/ }));
		const confirmar = await screen.findByRole("dialog", {
			name: "Confirmar venta",
		});
		expect(
			within(confirmar).getByText("Promoción aplicada"),
		).toBeInTheDocument();
		await user.click(
			within(confirmar).getByRole("button", { name: /Confirmar y cobrar/ }),
		);
		await waitFor(() =>
			expect(apiCheckoutMock).toHaveBeenCalledWith(
				expect.objectContaining({ promocionId: 3 }),
			),
		);
		const monto = apiCheckoutMock.mock.calls[0][0].pagos[0].monto;
		expect(monto).toBe(100);
	});

	it("muestra cuando ninguna promoción aplica", async () => {
		const user = userEvent.setup();
		apiPromosMock.mockResolvedValue([PROMO_NO_APLICA] as never);
		renderPagina(<PosPage />);
		await agregarMartillo(user);
		expect(
			await screen.findByText(/Ninguna promoción aplica/),
		).toBeInTheDocument();
		expect(screen.getByText("Sin promo")).toBeInTheDocument();
	});

	it("muestra error cuando evaluar promociones falla", async () => {
		const user = userEvent.setup();
		apiPromosMock.mockRejectedValue(new Error("promos caídas"));
		renderPagina(<PosPage />);
		await agregarMartillo(user);
		expect(await screen.findByText(/promos caídas/)).toBeInTheDocument();
	});

	it("muestra toast si el cobro falla", async () => {
		const user = userEvent.setup();
		apiCheckoutMock.mockRejectedValueOnce(new Error("caja cerrada"));
		renderPagina(<PosPage />);
		await prepararTicket(user);
		await agregarMartillo(user);
		await user.type(screen.getByLabelText("Recibido"), "100");
		await user.click(screen.getByRole("button", { name: /Cobrar/ }));
		const confirmar = await screen.findByRole("dialog", {
			name: "Confirmar venta",
		});
		await user.click(
			within(confirmar).getByRole("button", { name: /Confirmar y cobrar/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("caja cerrada"),
				}),
			),
		);
	});

	it("muestra descuento y promo en el ticket de venta registrada", async () => {
		const user = userEvent.setup();
		apiCheckoutMock.mockResolvedValue(VENTA_DESCUENTO);
		renderPagina(<PosPage />);
		await prepararTicket(user);
		await agregarMartillo(user);
		await user.type(screen.getByLabelText("Recibido"), "200");
		await user.click(screen.getByRole("button", { name: /Cobrar/ }));
		const confirmar = await screen.findByRole("dialog", {
			name: "Confirmar venta",
		});
		await user.click(
			within(confirmar).getByRole("button", { name: /Confirmar y cobrar/ }),
		);
		const resultado = await screen.findByRole("dialog", {
			name: "Venta registrada",
		});
		expect(within(resultado).getByText("Descuento")).toBeInTheDocument();
		expect(
			within(resultado).getByText(/Promoción aplicada en ticket: #7/),
		).toBeInTheDocument();
		await user.click(within(resultado).getByRole("button", { name: "Imprimir ticket" }));
		expect(printTicketById).toHaveBeenCalledWith("ticket-print-venta");
		expect(
			within(resultado).getByRole("link", { name: "Ver cobranza" }),
		).toHaveAttribute("href", "/ventas/cobranza");
		const cierres = within(resultado).getAllByRole("button", { name: "Cerrar" });
		await user.click(cierres[cierres.length - 1]);
		await waitFor(() =>
			expect(
				screen.queryByRole("dialog", { name: "Venta registrada" }),
			).not.toBeInTheDocument(),
		);
	});

	it("imprime en background cuando la silenciosa está activa", async () => {
		const user = userEvent.setup();
		vi.mocked(getSilentEnabled).mockReturnValueOnce(true);
		apiTicketMock.mockResolvedValue(TICKET_CONFIG as never);
		apiCheckoutMock.mockResolvedValue({ ...VENTA, clienteId: 1, cliente: null });
		vi.mocked(apiGetCliente).mockResolvedValue(CLIENTE as never);
		renderPagina(<PosPage />);
		await prepararTicket(user);
		await agregarMartillo(user);
		await user.type(screen.getByLabelText("Recibido"), "200");
		await user.click(screen.getByRole("button", { name: /Cobrar/ }));
		const confirmar = await screen.findByRole("dialog", {
			name: "Confirmar venta",
		});
		await user.click(
			within(confirmar).getByRole("button", { name: /Confirmar y cobrar/ }),
		);
		await waitFor(() => expect(buildEscPosTicket).toHaveBeenCalled());
		expect(printViaSerial).toHaveBeenCalledOnce();
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Ticket enviado a impresora USB"),
			}),
		);
		expect(apiGetCliente).toHaveBeenCalledWith(1);
	});

	it("no bloquea la venta si la impresión background falla", async () => {
		const user = userEvent.setup();
		vi.mocked(getSilentEnabled).mockReturnValueOnce(true);
		const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
		apiTicketMock.mockResolvedValue(TICKET_CONFIG as never);
		vi.mocked(printViaSerial).mockRejectedValueOnce(new Error("usb ocupada"));
		apiCheckoutMock.mockResolvedValue(VENTA);
		try {
			renderPagina(<PosPage />);
			await prepararTicket(user);
			await agregarMartillo(user);
			await user.type(screen.getByLabelText("Recibido"), "200");
			await user.click(screen.getByRole("button", { name: /Cobrar/ }));
			const confirmar = await screen.findByRole("dialog", {
				name: "Confirmar venta",
			});
			await user.click(
				within(confirmar).getByRole("button", { name: /Confirmar y cobrar/ }),
			);
			expect(
				await screen.findByRole("dialog", { name: "Venta registrada" }),
			).toBeInTheDocument();
		} finally {
			warn.mockRestore();
		}
	});

	it("bloquea el cobro sin turno y enlaza a abrir turno", async () => {
		const user = userEvent.setup();
		apiTurnoMock.mockRejectedValue(new Error("sin turno"));
		renderPagina(<PosPage />);
		await screen.findByRole("option", { name: "Matriz" });
		await user.selectOptions(
			screen.getByLabelText(/Almacén \/ punto de venta/),
			"1",
		);
		await screen.findByRole("option", { name: "Caja 1 · Matriz" });
		await user.selectOptions(screen.getByLabelText(/Caja donde operas/), "1");
		expect(
			await screen.findByText("Esta caja no tiene un turno abierto."),
		).toBeInTheDocument();
		expect(
			screen.getByRole("link", { name: "Abrir turno" }),
		).toHaveAttribute("href", "/caja/cajas");
		await agregarMartillo(user);
		expect(screen.getByRole("button", { name: /Cobrar/ })).toBeDisabled();
	});

	it("avisa cuando la cámara no está disponible", async () => {
		const user = userEvent.setup();
		renderPagina(<PosPage />);
		await user.click(screen.getByRole("button", { name: "Escanear con cámara" }));
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("conexión segura"),
			}),
		);
	});

	it("escanea con cámara y añade el producto detectado", async () => {
		const user = userEvent.setup();
		vi.mocked(camaraDisponible).mockReturnValueOnce(true);
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO_BARRAS]));
		renderPagina(<PosPage />);
		await user.click(screen.getByRole("button", { name: "Escanear con cámara" }));
		const scanner = await screen.findByRole("dialog", { name: "Escáner" });
		await user.click(
			within(scanner).getByRole("button", { name: "Simular detección" }),
		);
		expect(await screen.findByText("Ticket (1)")).toBeInTheDocument();
	});

	it("restaura almacén y caja guardados en preferencias", async () => {
		localStorage.setItem(
			"ferreteria-pos",
			JSON.stringify({ cajaId: 1, almacenId: 1 }),
		);
		renderSinLimpiar(<PosPage />);
		expect(await screen.findByText(/abierto desde/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Almacén \/ punto de venta/)).toHaveValue("1");
		expect(screen.getByLabelText(/Caja donde operas/)).toHaveValue("1");
		localStorage.clear();
	});

	it("ignora preferencias corruptas y arranca vacío", () => {
		localStorage.setItem("ferreteria-pos", "no-json{{{");
		renderSinLimpiar(<PosPage />);
		expect(screen.getByLabelText(/Almacén \/ punto de venta/)).toHaveValue("");
		localStorage.clear();
	});

	it("deshabilita limpiar y cancelar con el ticket vacío", () => {
		renderPagina(<PosPage />);
		expect(
			screen.getByRole("button", { name: "Limpiar ticket" }),
		).toBeDisabled();
		expect(screen.getByRole("button", { name: "Cancelar venta" })).toBeDisabled();
	});

	it("deshabilita cobrar con cantidad en cero o recibido insuficiente", async () => {
		const user = userEvent.setup();
		renderPagina(<PosPage />);
		await prepararTicket(user);
		await agregarMartillo(user);
		const cantidad = screen.getByLabelText("Cantidad de Martillo");
		fireEvent.change(cantidad, { target: { value: "0" } });
		expect(screen.getByRole("button", { name: /Cobrar/ })).toBeDisabled();
		fireEvent.change(cantidad, { target: { value: "2" } });
		await user.type(screen.getByLabelText("Recibido"), "10");
		expect(screen.getByRole("button", { name: /Cobrar/ })).toBeDisabled();
		expect(screen.getByText("Cambio")).toBeInTheDocument();
	});

	it("muestra error cuando las ventas del día fallan", async () => {
		const user = userEvent.setup();
		apiVentasMock.mockRejectedValueOnce(new Error("reporte caído"));
		renderPagina(<PosPage />);
		await user.click(
			screen.getByRole("button", { name: "Ver ventas del día" }),
		);
		expect(await screen.findByText(/reporte caído/)).toBeInTheDocument();
	});

	it("muestra canceladas, consumidor final y exporta el día a Excel", async () => {
		const user = userEvent.setup();
		apiVentasMock.mockResolvedValue(
			pageOf([
				{ ...VENTA, clienteNombre: null, descuentoTotal: 10 },
				{ ...VENTA, ventaId: 2, folio: "V-0002", estado: "CANCELADA" },
			]),
		);
		renderPagina(<PosPage />);
		await user.click(
			screen.getByRole("button", { name: "Ver ventas del día" }),
		);
		expect(await screen.findByText(/Ventas de hoy/)).toBeInTheDocument();
		expect(screen.getByText("Cancelada")).toBeInTheDocument();
		expect(
			screen.getAllByText("Consumidor final").length,
		).toBeGreaterThanOrEqual(2);
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^pos-venta-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});
});
