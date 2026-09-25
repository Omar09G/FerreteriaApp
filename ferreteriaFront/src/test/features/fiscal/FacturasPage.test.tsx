import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import Swal from "sweetalert2";

import { ToastProvider } from "@/components/ui/Toast";
import FacturasPage from "@/features/fiscal/FacturasPage";
import { apiCrearFactura, apiFacturaXml, apiFacturas } from "@/lib/api/fis";
import { useAuthStore } from "@/store/auth";

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

const FACTURA_RECIBIDA = {
	facturaId: 2,
	tipo: "RECIBIDA",
	serie: null,
	folio: "F-002",
	uuid: null,
	emisorRfc: "XAXX010101000",
	receptorRfc: "MELM8305281H0",
	subtotal: 200,
	iva: 32,
	total: 232,
	fechaTimbrado: "2026-09-02T12:00:00Z",
	estado: "CANCELADA",
	ventaId: null,
	usuarioId: 1,
	creadoEn: "2026-09-02T12:00:00Z",
};

describe("FacturasPage (profundización)", () => {
	it("filtra por tipo y ofrece Limpiar para quitar el filtro", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("F-001");
		await user.selectOptions(screen.getByLabelText("Tipo"), "EMITIDA");
		expect(
			await screen.findByRole("button", { name: "Limpiar" }),
		).toBeInTheDocument();
		expect(vi.mocked(apiFacturas)).toHaveBeenLastCalledWith(
			expect.objectContaining({ tipo: "EMITIDA", page: 0 }),
		);
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		expect(
			screen.queryByRole("button", { name: "Limpiar" }),
		).not.toBeInTheDocument();
		expect(vi.mocked(apiFacturas)).toHaveBeenLastCalledWith(
			expect.objectContaining({ tipo: undefined, page: 0 }),
		);
	});

	it("muestra badge RECIBIDA, guiones sin serie/UUID y estado CANCELADA", async () => {
		vi.mocked(apiFacturas).mockResolvedValueOnce({
			success: true,
			data: [FACTURA_RECIBIDA],
			meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("F-002")).toBeInTheDocument();
		expect(screen.getAllByText("RECIBIDA").length).toBeGreaterThanOrEqual(2);
		expect(screen.getByText("CANCELADA")).toBeInTheDocument();
		expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(1);
	});

	it("trunca el UUID largo en la tabla", async () => {
		renderPage();
		await screen.findByText("F-001");
		expect(screen.getByText("123e4567-e89b-12d3-a456-…")).toBeInTheDocument();
	});

	it("crea la factura con el formulario válido y recarga la lista", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearFactura).mockResolvedValueOnce({ ...FACTURA, facturaId: 9 } as never);
		renderPage();
		await screen.findByText("F-001");
		await user.click(screen.getByRole("button", { name: /Nueva factura/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva factura" });
		await user.type(within(dialogo).getByLabelText(/Folio/), "F-009");
		await user.type(within(dialogo).getByLabelText(/RFC emisor/), "xaxx010101000");
		await user.type(within(dialogo).getByLabelText(/RFC receptor/), "melm8305281h0");
		await user.type(within(dialogo).getByLabelText(/Subtotal/), "200");
		await user.type(within(dialogo).getByLabelText(/IVA/), "32");
		await user.click(
			within(dialogo).getByRole("button", { name: /Crear factura/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearFactura)).toHaveBeenCalledWith(
				expect.objectContaining({
					tipo: "EMITIDA",
					folio: "F-009",
					emisorRfc: "XAXX010101000",
					receptorRfc: "MELM8305281H0",
					subtotal: 200,
					iva: 32,
				}),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Factura creada"),
			}),
		);
	});

	it("muestra toast si crear la factura falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearFactura).mockRejectedValueOnce(new Error("folio duplicado"));
		renderPage();
		await screen.findByText("F-001");
		await user.click(screen.getByRole("button", { name: /Nueva factura/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva factura" });
		await user.type(within(dialogo).getByLabelText(/Folio/), "F-001");
		await user.type(within(dialogo).getByLabelText(/RFC emisor/), "XAXX010101000");
		await user.type(within(dialogo).getByLabelText(/RFC receptor/), "MELM8305281H0");
		await user.type(within(dialogo).getByLabelText(/Subtotal/), "100");
		await user.type(within(dialogo).getByLabelText(/IVA/), "16");
		await user.click(
			within(dialogo).getByRole("button", { name: /Crear factura/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("folio duplicado"),
				}),
			),
		);
	});

	it("cancela el diálogo de nueva factura con el botón Cancelar", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("F-001");
		await user.click(screen.getByRole("button", { name: /Nueva factura/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva factura" });
		await user.click(
			within(dialogo).getByRole("button", { name: /Cancelar/ }),
		);
		await waitFor(() =>
			expect(screen.queryByRole("dialog", { name: "Nueva factura" })).not.toBeInTheDocument(),
		);
		expect(apiCrearFactura).not.toHaveBeenCalled();
	});

	it("ver XML muestra estado vacío cuando no hay XML almacenado", async () => {
		const user = userEvent.setup();
		vi.mocked(apiFacturaXml).mockResolvedValueOnce({
			facturaId: 1,
			folio: "F-001",
			uuid: FACTURA.uuid,
			tipo: "EMITIDA",
			cfdiXml: null,
		} as never);
		renderPage();
		await screen.findByText("F-001");
		await user.click(screen.getByRole("button", { name: "Ver XML" }));
		expect(
			await screen.findByText("Sin XML almacenado"),
		).toBeInTheDocument();
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiFacturas).mockResolvedValue({
			success: true,
			data: [FACTURA],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findByText("F-001");
		const siguiente = screen.getByRole("button", { name: /siguiente/i });
		expect(siguiente).toBeEnabled();
		await user.click(siguiente);
		await waitFor(() =>
			expect(vi.mocked(apiFacturas)).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta las facturas visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("F-001");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^facturas-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiFacturas).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("sin conexión"),
				}),
			),
		);
	});
});
