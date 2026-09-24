import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import ConfiguracionPage from "@/features/administracion/ConfiguracionPage";
import {
	apiGetTicketConfig,
	apiPutTicketConfig,
} from "@/lib/api/ticketConfig";
import { printTicketById } from "@/lib/print/ticket";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
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
