import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import FacturasPage from "@/features/fiscal/FacturasPage";
import { apiCrearFactura, apiFacturaXml, apiFacturas } from "@/lib/api/fis";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/fis", () => ({
	apiCrearFactura: vi.fn(),
	apiFacturaXml: vi.fn(),
	apiFacturas: vi.fn(),
}));

const FACTURA = {
	facturaId: 1,
	tipo: "EMITIDA",
	serie: "A",
	folio: "F-001",
	uuid: "123e4567-e89b-12d3-a456-426614174000",
	emisorRfc: "XAXX010101000",
	receptorRfc: "MELM8305281H0",
	subtotal: 100,
	iva: 16,
	total: 116,
	fechaTimbrado: "2026-09-01T12:00:00Z",
	estado: "TIMBRADA",
	ventaId: null,
	usuarioId: 1,
	creadoEn: "2026-09-01T12:00:00Z",
};

function renderPage() {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>
					<FacturasPage />
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
	vi.mocked(apiFacturas).mockResolvedValue({
		success: true,
		data: [FACTURA],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	} as never);
	vi.mocked(apiFacturaXml).mockResolvedValue({
		facturaId: 1,
		folio: "F-001",
		uuid: FACTURA.uuid,
		tipo: "EMITIDA",
		cfdiXml: "<cfdi:Comprobante>F-001</cfdi:Comprobante>",
	} as never);
});

describe("FacturasPage (smoke)", () => {
	it("renderiza título, filtro y tabla", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Facturas CFDI" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nueva factura/ }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Tipo/)).toBeInTheDocument();
		expect(await screen.findByText("F-001")).toBeInTheDocument();
		expect(screen.getAllByText("EMITIDA").length).toBeGreaterThanOrEqual(1);
		expect(screen.getByText("TIMBRADA")).toBeInTheDocument();
	});

	it("abre el diálogo de nueva factura", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("F-001");
		await user.click(screen.getByRole("button", { name: /Nueva factura/ }));
		expect(
			await screen.findByRole("heading", { name: "Nueva factura" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Folio/)).toBeInTheDocument();
		expect(screen.getByLabelText(/RFC emisor/)).toBeInTheDocument();
		expect(apiCrearFactura).toBeDefined();
	});

	it("ver XML abre el diálogo con el contenido", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("F-001");
		await user.click(screen.getByRole("button", { name: "Ver XML" }));
		expect(await screen.findByText("XML · F-001")).toBeInTheDocument();
		expect(
			await screen.findByText("<cfdi:Comprobante>F-001</cfdi:Comprobante>"),
		).toBeInTheDocument();
		expect(apiFacturaXml).toHaveBeenCalledWith(1);
	});
});
