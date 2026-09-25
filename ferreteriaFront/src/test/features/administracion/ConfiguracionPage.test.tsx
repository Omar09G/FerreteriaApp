import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import Swal from "sweetalert2";

import { ToastProvider } from "@/components/ui/Toast";
import ConfiguracionPage from "@/features/administracion/ConfiguracionPage";
import {
	apiGetTicketConfig,
	apiPutTicketConfig,
} from "@/lib/api/ticketConfig";
import { printTicketById } from "@/lib/print/ticket";
import {
	disconnect,
	ensureConnected,
	isSerialSupported,
	openPort,
	printViaSerial,
	requestSerialPort,
	setSilentEnabled,
} from "@/lib/print/serial";
import { buildEscPosTicket } from "@/lib/print/escpos";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/print/serial", () => ({
	disconnect: vi.fn(),
	ensureConnected: vi.fn(async () => null),
	getSilentEnabled: vi.fn(() => false),
	isSerialSupported: vi.fn(() => false),
	openPort: vi.fn(),
	printViaSerial: vi.fn(),
	requestSerialPort: vi.fn(),
	setSilentEnabled: vi.fn(),
}));

vi.mock("@/lib/print/escpos", () => ({
	buildEscPosTicket: vi.fn(() => new Uint8Array([27, 64])),
}));

vi.mock("@/lib/api/ticketConfig", () => ({
	apiGetTicketConfig: vi.fn(),
	apiPutTicketConfig: vi.fn(),
}));

vi.mock("@/lib/print/ticket", () => ({
	printTicketById: vi.fn(),
}));

const CONFIG = {
	ticketConfigId: 1,
	almacenId: null,
	logotipoUrl: null,
	mostrarLogotipo: false,
	nombreNegocio: "El Tornillo Feliz",
	direccion: "Av. Principal 123",
	cp: "40006",
	rfc: "XAXX010101000",
	telefono: "555000111",
	email: "tienda@example.com",
	sitioWeb: "tienda.example.com",
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
	pieSecundario: "T: 555000111",
	anchoPapelMm: 80,
	fontSizePt: 9,
	actualizadoEn: null,
	actualizadoPor: null,
};

function renderPage() {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>
					<ConfiguracionPage />
				</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>,
	);
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	useAuthStore.setState({
		autenticado: true,
		usuario: { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] },
		lastActivityAt: Date.now(),
	});
	localStorage.clear();
	vi.mocked(apiGetTicketConfig).mockResolvedValue({ ...CONFIG } as never);
	vi.mocked(apiPutTicketConfig).mockImplementation(async (body) => ({
		...CONFIG,
		...body,
	}) as never);
});

describe("ConfiguracionPage (smoke)", () => {
	it("renderiza título, formulario y vista previa en vivo", async () => {
		renderPage();
		expect(
			await screen.findByDisplayValue("El Tornillo Feliz"),
		).toBeInTheDocument();
		expect(
			screen.getByRole("heading", { name: "Configuración" }),
		).toBeInTheDocument();
		expect(screen.getByText("Encabezado - Logotipo / Empresa")).toBeInTheDocument();
		expect(screen.getByText("Cuerpo del ticket")).toBeInTheDocument();
		expect(screen.getByText("Vista previa 80mm")).toBeInTheDocument();
		expect(screen.getByText(/Total \(con impuestos\)/)).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Guardar/ }),
		).toBeInTheDocument();
	});

	it("guardar envía la configuración al backend", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		await user.click(screen.getByRole("button", { name: /Guardar/ }));
		await vi.waitFor(() => {
			expect(apiPutTicketConfig).toHaveBeenCalledOnce();
		});
	});

	it("probar impresión usa el helper sin abrir el diálogo del navegador", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		await user.click(
			screen.getByRole("button", { name: "Probar impresión" }),
		);
		expect(printTicketById).toHaveBeenCalled();
	});
});

describe("ConfiguracionPage (profundización)", () => {
	it("muestra spinner mientras carga la configuración", () => {
		vi.mocked(apiGetTicketConfig).mockReturnValueOnce(
			new Promise(() => {}) as never,
		);
		renderPage();
		expect(screen.getByText("Cargando configuración…")).toBeInTheDocument();
	});

	it("muestra el error si la carga falla", async () => {
		vi.mocked(apiGetTicketConfig).mockRejectedValueOnce(
			new Error("sin conexión"),
		);
		renderPage();
		expect(await screen.findByText(/sin conexión/)).toBeInTheDocument();
	});

	it("edita campos, normaliza el RFC a mayúsculas y guarda los cambios", async () => {
		const user = userEvent.setup();
		renderPage();
		const nombre = await screen.findByDisplayValue("El Tornillo Feliz");
		await user.clear(nombre);
		await user.type(nombre, "La Tuerca Alegre");
		const rfc = screen.getByLabelText(/CIF \/ RFC empresa/);
		await user.clear(rfc);
		await user.type(rfc, "xaxx010101000");
		await user.click(screen.getByLabelText("Mostrar logotipo"));
		await user.selectOptions(screen.getByLabelText("Ancho papel"), "58");
		await user.selectOptions(screen.getByLabelText("Tamaño fuente"), "12");
		await user.click(screen.getByRole("button", { name: /Guardar/ }));
		await waitFor(() => {
			expect(apiPutTicketConfig).toHaveBeenCalledOnce();
		});
		const body = vi.mocked(apiPutTicketConfig).mock.calls[0][0] as Record<
			string,
			unknown
		>;
		expect(body.nombreNegocio).toBe("La Tuerca Alegre");
		expect(body.rfc).toBe("XAXX010101000");
		expect(body.mostrarLogotipo).toBe(true);
		expect(body.anchoPapelMm).toBe(58);
		expect(body.fontSizePt).toBe(12);
		expect(document.querySelector("#ticket-preview")).toHaveStyle({
			width: "208px",
		});
	});

	it("alterna los toggles del cuerpo del ticket", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		const toggle = screen.getByLabelText("Datos del cliente");
		expect(toggle).toBeChecked();
		await user.click(toggle);
		expect(toggle).not.toBeChecked();
		await user.click(screen.getByRole("button", { name: /Guardar/ }));
		await waitFor(() => {
			expect(apiPutTicketConfig).toHaveBeenCalledOnce();
		});
		const body = vi.mocked(apiPutTicketConfig).mock.calls[0][0] as Record<
			string,
			unknown
		>;
		expect(body.mostrarDatosCliente).toBe(false);
	});

	it("muestra toast de error si guardar falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiPutTicketConfig).mockRejectedValueOnce(
			new Error("nombre requerido"),
		);
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		await user.click(screen.getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("nombre requerido"),
				}),
			),
		);
	});

	it("muestra quién actualizó y cuándo cuando hay actualizadoEn", async () => {
		vi.mocked(apiGetTicketConfig).mockResolvedValueOnce({
			ticketConfigId: 1,
			almacenId: null,
			logotipoUrl: null,
			mostrarLogotipo: false,
			nombreNegocio: "El Tornillo Feliz",
			direccion: null,
			cp: null,
			rfc: null,
			telefono: null,
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
			mensajePie: null,
			pieSecundario: null,
			anchoPapelMm: 80,
			fontSizePt: 9,
			actualizadoEn: "2026-09-20T10:00:00",
			actualizadoPor: 1,
		} as never);
		renderPage();
		expect(await screen.findByText(/Actualizado:/)).toBeInTheDocument();
		expect(screen.getByText(/por admin/)).toBeInTheDocument();
	});

	it("conecta y desconecta la impresora USB cuando Web Serial está soportado", async () => {
		const user = userEvent.setup();
		vi.mocked(isSerialSupported).mockReturnValue(true);
		vi.mocked(requestSerialPort).mockResolvedValueOnce({ id: "p1" } as never);
		vi.mocked(openPort).mockResolvedValueOnce(undefined);
		vi.mocked(disconnect).mockResolvedValueOnce(undefined);
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		await user.click(
			screen.getByRole("button", { name: /Conectar impresora USB/ }),
		);
		expect(await screen.findByText(/Conectada \(background listo\)/)).toBeInTheDocument();
		expect(openPort).toHaveBeenCalledWith({ id: "p1" });
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Impresora USB conectada"),
			}),
		);
		await user.click(screen.getByRole("button", { name: /Desconectar/ }));
		expect(
			await screen.findByText(/se usará diálogo del navegador/),
		).toBeInTheDocument();
		expect(disconnect).toHaveBeenCalledOnce();
	});

	it("muestra error si conectar la impresora falla", async () => {
		const user = userEvent.setup();
		vi.mocked(isSerialSupported).mockReturnValue(true);
		vi.mocked(requestSerialPort).mockRejectedValueOnce(
			new Error("sin permiso"),
		);
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		await user.click(
			screen.getByRole("button", { name: /Conectar impresora USB/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin permiso") }),
			),
		);
	});

	it("restaura la conexión silenciosa al cargar si ya había permiso", async () => {
		vi.mocked(isSerialSupported).mockReturnValue(true);
		vi.mocked(ensureConnected).mockResolvedValueOnce({ id: "p1" } as never);
		renderPage();
		expect(
			await screen.findByText(/Conectada \(background listo\)/),
		).toBeInTheDocument();
	});

	it("alterna la impresión silenciosa automática", async () => {
		const user = userEvent.setup();
		vi.mocked(isSerialSupported).mockReturnValue(true);
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		const toggle = screen.getByLabelText(
			"Impresión silenciosa automática tras venta",
		);
		await user.click(toggle);
		expect(setSilentEnabled).toHaveBeenCalledWith(true);
	});

	it("envía el ticket de prueba por USB en background", async () => {
		const user = userEvent.setup();
		vi.mocked(isSerialSupported).mockReturnValue(true);
		vi.mocked(requestSerialPort).mockResolvedValueOnce({ id: "p1" } as never);
		vi.mocked(openPort).mockResolvedValueOnce(undefined);
		vi.mocked(printViaSerial).mockResolvedValueOnce(undefined);
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		await user.click(
			screen.getByRole("button", { name: /Conectar impresora USB/ }),
		);
		await screen.findByText(/Conectada \(background listo\)/);
		await user.click(
			screen.getByRole("button", { name: /Probar impresión background/ }),
		);
		await waitFor(() => expect(buildEscPosTicket).toHaveBeenCalled());
		expect(printViaSerial).toHaveBeenCalledOnce();
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Ticket de prueba enviado por USB"),
			}),
		);
	});

	it("muestra error si la impresión background falla", async () => {
		const user = userEvent.setup();
		vi.mocked(isSerialSupported).mockReturnValue(true);
		vi.mocked(requestSerialPort).mockResolvedValueOnce({ id: "p1" } as never);
		vi.mocked(openPort).mockResolvedValueOnce(undefined);
		vi.mocked(printViaSerial).mockRejectedValueOnce(
			new Error("impresora ocupada"),
		);
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		await user.click(
			screen.getByRole("button", { name: /Conectar impresora USB/ }),
		);
		await screen.findByText(/Conectada \(background listo\)/);
		await user.click(
			screen.getByRole("button", { name: /Probar impresión background/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("impresora ocupada"),
				}),
			),
		);
	});

	it("probar con diálogo usa el helper de impresión", async () => {
		const user = userEvent.setup();
		vi.mocked(isSerialSupported).mockReturnValue(true);
		renderPage();
		await screen.findByDisplayValue("El Tornillo Feliz");
		await user.click(
			screen.getByRole("button", { name: "Probar con diálogo" }),
		);
		expect(printTicketById).toHaveBeenCalled();
	});
});
