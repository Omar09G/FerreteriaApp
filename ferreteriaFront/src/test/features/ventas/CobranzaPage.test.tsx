import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Swal from "sweetalert2";

vi.mock("@/lib/api/venta", () => ({
	apiCuentasCobrar: vi.fn(),
	apiPagoCliente: vi.fn(),
	apiEnviarCobranzaInforme: vi.fn(),
	apiEstadoCobranzaInforme: vi.fn(),
}));
vi.mock("@/lib/api/catalogo", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiClientes: vi.fn(),
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
	apiCuentasCobrar,
	apiEnviarCobranzaInforme,
	apiEstadoCobranzaInforme,
	apiPagoCliente,
} from "@/lib/api/venta";
import { apiClientes } from "@/lib/api/catalogo";
import { useAuthStore } from "@/store/auth";
import CobranzaPage from "@/features/ventas/CobranzaPage";
import {
	CLIENTE,
	CUENTA,
	pageOf,
	renderPagina,
} from "@/test/helpers/ventasPos";

const apiCuentasMock = vi.mocked(apiCuentasCobrar);
const apiPagoClienteMock = vi.mocked(apiPagoCliente);
const apiClientesMock = vi.mocked(apiClientes);

beforeEach(() => {
	vi.clearAllMocks();
	apiCuentasMock.mockResolvedValue(pageOf([CUENTA]));
	apiClientesMock.mockResolvedValue(pageOf([CLIENTE]));
});

describe("CobranzaPage", () => {
	it("renderiza título, filtros y tabla con una cuenta", async () => {
		renderPagina(<CobranzaPage />);
		expect(
			screen.getByRole("heading", { name: "Cobranza" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Estado")).toBeInTheDocument();
		expect(screen.getByLabelText("Cliente")).toBeInTheDocument();
		expect(await screen.findByText("Cuentas por cobrar (1)")).toBeInTheDocument();
		expect(await screen.findByText("V-0001")).toBeInTheDocument();
		expect(screen.getAllByText("Juan Pérez").length).toBeGreaterThanOrEqual(1);
	});

	it("abre el estado de cuenta al hacer clic en el historial", async () => {
		const user = userEvent.setup();
		renderPagina(<CobranzaPage />);
		await user.click(
			await screen.findByRole("button", { name: "Estado de cuenta" }),
		);
		expect(
			await screen.findByRole("dialog", { name: "Estado de cuenta · Juan Pérez" }),
		).toBeInTheDocument();
		const dialogo = screen.getByRole("dialog", {
			name: "Estado de cuenta · Juan Pérez",
		});
		expect(
			within(dialogo).getByText("Sin abonos registrados."),
		).toBeInTheDocument();
		expect(
			within(dialogo).getByRole("button", { name: /Registrar abono/ }),
		).toBeInTheDocument();
	});

	it("abre el formulario de abono con el saldo precargado", async () => {
		const user = userEvent.setup();
		renderPagina(<CobranzaPage />);
		await user.click(
			await screen.findByRole("button", { name: "Registrar abono" }),
		);
		expect(
			await screen.findByRole("dialog", { name: "Registrar abono" }),
		).toBeInTheDocument();
		const dialogo = screen.getByRole("dialog", { name: "Registrar abono" });
		expect(within(dialogo).getByLabelText(/Monto del abono/)).toHaveValue(116);
		expect(within(dialogo).getByLabelText(/Forma de pago/)).toBeInTheDocument();
	});

	it("pasa del estado de cuenta al formulario de abono", async () => {
		const user = userEvent.setup();
		renderPagina(<CobranzaPage />);
		await user.click(
			await screen.findByRole("button", { name: "Estado de cuenta" }),
		);
		const historial = await screen.findByRole("dialog", {
			name: "Estado de cuenta · Juan Pérez",
		});
		await user.click(
			within(historial).getByRole("button", { name: /Registrar abono/ }),
		);
		const abono = await screen.findByRole("dialog", {
			name: "Registrar abono",
		});
		expect(within(abono).getByLabelText(/Monto del abono/)).toHaveValue(116);
	});

	it("registra el abono contra la cuenta", async () => {
		const user = userEvent.setup();
		apiPagoClienteMock.mockResolvedValue({} as never);
		renderPagina(<CobranzaPage />);
		await user.click(
			await screen.findByRole("button", { name: "Registrar abono" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar abono",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Registrar abono" }),
		);
		expect(apiPagoClienteMock).toHaveBeenCalledWith(
			expect.objectContaining({ cuentaCobrarId: 1, monto: 116 }),
		);
	});

	it("filtra por estado y muestra Limpiar para quitar el filtro", async () => {
		const user = userEvent.setup();
		renderPagina(<CobranzaPage />);
		await screen.findByText("V-0001");
		const estado = screen.getByLabelText("Estado");
		const valores = within(estado)
			.getAllByRole("option")
			.map((o) => (o as HTMLOptionElement).value)
			.filter((v) => v !== "");
		expect(valores.length).toBeGreaterThan(0);
		await user.selectOptions(estado, valores[0]);
		expect(
			await screen.findByRole("button", { name: "Limpiar" }),
		).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		expect(
			screen.queryByRole("button", { name: "Limpiar" }),
		).not.toBeInTheDocument();
	});
});

const CUENTA_VENCIDA = {
	cuentaCobrarId: 2,
	ventaId: 2,
	ventaFolio: "V-0002",
	clienteId: 1,
	clienteNombre: "Juan Pérez",
	montoTotal: 232,
	montoPagado: 100,
	saldo: 132,
	fechaVencimiento: "2020-01-10",
	estado: "VIGENTE",
	creadoEn: "2020-01-05T12:00:00",
	pagos: [
		{
			pagoClienteId: 9,
			formaPagoId: 99,
			referencia: "REF-1",
			monto: 100,
			fecha: "2020-01-06",
		},
	],
};

const CUENTA_PARCIAL = {
	cuentaCobrarId: 4,
	ventaId: 4,
	ventaFolio: "V-0004",
	clienteId: 1,
	clienteNombre: "Juan Pérez",
	montoTotal: 232,
	montoPagado: 100,
	saldo: 132,
	fechaVencimiento: "2099-06-10",
	estado: "PARCIAL",
	creadoEn: "2026-01-05T12:00:00",
	pagos: [],
};

const CUENTA_LIQUIDADA = {
	cuentaCobrarId: 3,
	ventaId: 3,
	ventaFolio: "V-0003",
	clienteId: 1,
	clienteNombre: "Juan Pérez",
	montoTotal: 116,
	montoPagado: 116,
	saldo: 0,
	fechaVencimiento: "2026-02-10",
	estado: "LIQUIDADA",
	creadoEn: "2026-01-10T12:00:00",
	pagos: [],
};

describe("CobranzaPage (profundización)", () => {
	it("muestra parcial, vencida y badges de deuda en la tabla", async () => {
		apiCuentasMock.mockResolvedValue(pageOf([CUENTA, CUENTA_VENCIDA, CUENTA_PARCIAL]));
		renderPagina(<CobranzaPage />);
		expect(await screen.findByText("V-0004")).toBeInTheDocument();
		const tabla = screen.getByRole("table");
		expect(within(tabla).getByText("Parcial")).toBeInTheDocument();
		expect(within(tabla).getByText(/Vencida \d+d/)).toBeInTheDocument();
		expect(within(tabla).getByText("Vigente vencida")).toBeInTheDocument();
	});

	it("muestra la cuenta liquidada sin badge de peligro ni botón de abono", async () => {
		apiCuentasMock.mockResolvedValue(pageOf([CUENTA_LIQUIDADA]));
		renderPagina(<CobranzaPage />);
		expect(await screen.findByText("V-0003")).toBeInTheDocument();
		expect(screen.getByText("LIQUIDADA")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Registrar abono" }),
		).toBeDisabled();
	});

	it("lista los abonos previos con forma desconocida en el estado de cuenta", async () => {
		const user = userEvent.setup();
		apiCuentasMock.mockResolvedValue(pageOf([CUENTA_VENCIDA]));
		renderPagina(<CobranzaPage />);
		await user.click(
			await screen.findByRole("button", { name: "Estado de cuenta" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Estado de cuenta · Juan Pérez",
		});
		expect(within(dialogo).getByText("Forma 99")).toBeInTheDocument();
		expect(
			within(dialogo).getByRole("button", { name: /Registrar abono/ }),
		).toBeInTheDocument();
	});

	it("filtra por cliente y recarga la lista", async () => {
		const user = userEvent.setup();
		renderPagina(<CobranzaPage />);
		await screen.findByText("V-0001");
		await user.selectOptions(screen.getByLabelText("Cliente"), "1");
		await waitFor(() =>
			expect(apiCuentasMock).toHaveBeenLastCalledWith(
				expect.objectContaining({ clienteId: 1, page: 0 }),
			),
		);
		expect(
			await screen.findByRole("button", { name: "Limpiar" }),
		).toBeInTheDocument();
	});

	it("quita el rango con Todos y recarga sin fechas", async () => {
		const user = userEvent.setup();
		renderPagina(<CobranzaPage />);
		await screen.findByText("V-0001");
		expect(screen.getByTestId("rango-fechas")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Todos" }));
		await waitFor(() => {
			const ultima = apiCuentasMock.mock.calls.at(-1)?.[0] as Record<
				string,
				unknown
			>;
			expect(ultima.desde).toBeUndefined();
			expect(ultima.hasta).toBeUndefined();
		});
	});

	it("registra el abono con referencia cuando la forma la requiere", async () => {
		const user = userEvent.setup();
		apiPagoClienteMock.mockResolvedValue({} as never);
		renderPagina(<CobranzaPage />);
		await user.click(
			await screen.findByRole("button", { name: "Registrar abono" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar abono",
		});
		await user.selectOptions(
			within(dialogo).getByLabelText(/Forma de pago/),
			"4",
		);
		expect(
			await within(dialogo).findByLabelText(/Referencia del pago/),
		).toBeInTheDocument();
		await user.type(
			within(dialogo).getByLabelText(/Referencia del pago/),
			"SPEI-123",
		);
		await user.click(
			within(dialogo).getByRole("button", { name: "Registrar abono" }),
		);
		await waitFor(() =>
			expect(apiPagoClienteMock).toHaveBeenCalledWith({
				cuentaCobrarId: 1,
				formaPagoId: 4,
				monto: 116,
				referencia: "SPEI-123",
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Abono aplicado"),
			}),
		);
	});

	it("valida que el monto no supere el saldo", async () => {
		const user = userEvent.setup();
		renderPagina(<CobranzaPage />);
		await user.click(
			await screen.findByRole("button", { name: "Registrar abono" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar abono",
		});
		const monto = within(dialogo).getByLabelText(/Monto del abono/);
		await user.clear(monto);
		await user.type(monto, "9999");
		await user.click(
			within(dialogo).getByRole("button", { name: "Registrar abono" }),
		);
		expect(
			await within(dialogo).findByText(
				"El monto debe ser mayor a 0 y no superar el saldo.",
			),
		).toBeInTheDocument();
		expect(apiPagoClienteMock).not.toHaveBeenCalled();
	});

	it("muestra toast si el abono falla", async () => {
		const user = userEvent.setup();
		apiPagoClienteMock.mockRejectedValueOnce(new Error("monto inválido"));
		renderPagina(<CobranzaPage />);
		await user.click(
			await screen.findByRole("button", { name: "Registrar abono" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar abono",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Registrar abono" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("monto inválido"),
				}),
			),
		);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		apiCuentasMock.mockResolvedValue({
			success: true,
			data: [CUENTA],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPagina(<CobranzaPage />);
		await screen.findByText("V-0001");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(apiCuentasMock).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta las cuentas visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPagina(<CobranzaPage />);
		await screen.findByText("V-0001");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^cobranza-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		apiCuentasMock.mockRejectedValueOnce(new Error("sin conexión"));
		renderPagina(<CobranzaPage />);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});

describe("CobranzaPage (recordatorio manual)", () => {
	function comoAdmin() {
		useAuthStore.setState({
			autenticado: true,
			usuario: { roles: ["ADMINISTRADOR"] } as never,
		});
	}

	function comoSinRol() {
		useAuthStore.setState({ autenticado: false, usuario: null });
	}

	it("oculta el botón sin rol GERENTE/ADMINISTRADOR", async () => {
		comoSinRol();
		renderPagina(<CobranzaPage />);
		await screen.findByText("V-0001");
		expect(
			screen.queryByRole("button", { name: /recordatorio/i }),
		).not.toBeInTheDocument();
	});

	it("envía directo cuando hoy aún no se envió", async () => {
		const user = userEvent.setup();
		comoAdmin();
		vi.mocked(apiEstadoCobranzaInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			yaEnviado: false,
			estado: null,
			enviadoEn: null,
		});
		vi.mocked(apiEnviarCobranzaInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			destinatarios: 2,
			emailsEnviados: 2,
			whatsappsEnviados: 1,
			vencidas: 1,
			pendientes: 1,
			totalVencido: 500,
			totalPendiente: 300,
		});
		renderPagina(<CobranzaPage />);
		await screen.findByText("V-0001");
		await user.click(screen.getByRole("button", { name: /recordatorio/i }));
		await waitFor(() => {
			expect(apiEstadoCobranzaInforme).toHaveBeenCalledOnce();
			expect(apiEnviarCobranzaInforme).toHaveBeenCalledOnce();
		});
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("Recordatorio enviado a 2 destinatarios"),
				}),
			),
		);
	});

	it("avisa si ya se envió y reenvía solo al confirmar", async () => {
		const user = userEvent.setup();
		comoAdmin();
		vi.mocked(apiEstadoCobranzaInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			yaEnviado: true,
			estado: "ENVIADA",
			enviadoEn: "2026-10-05T09:05:00",
		});
		vi.mocked(apiEnviarCobranzaInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			destinatarios: 1,
			emailsEnviados: 1,
			whatsappsEnviados: 0,
			vencidas: 0,
			pendientes: 2,
			totalVencido: 0,
			totalPendiente: 800,
		});
		renderPagina(<CobranzaPage />);
		await screen.findByText("V-0001");
		await user.click(screen.getByRole("button", { name: /recordatorio/i }));
		expect(await screen.findByText("Recordatorio ya enviado")).toBeInTheDocument();
		expect(apiEnviarCobranzaInforme).not.toHaveBeenCalled();
		await user.click(screen.getByRole("button", { name: /reenviar/i }));
		await waitFor(() => {
			expect(apiEnviarCobranzaInforme).toHaveBeenCalledOnce();
		});
	});
});
