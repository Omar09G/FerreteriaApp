import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import CuentasPagarPage from "@/features/compras/CuentasPagarPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiTurnoActual } from "@/lib/api/caja";
import {
	apiAbonarCuentaPagar,
	apiCuentasPagar,
	apiEnviarCuentasPagarInforme,
	apiEstadoCuentasPagarInforme,
	apiFacturasPendientes,
	apiFacturasVencidas,
} from "@/lib/api/compras";
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

vi.mock("@/lib/api/caja", () => ({
	apiCajas: vi.fn(async () => [
		{
			cajaId: 1,
			nombre: "Caja 1",
			almacenId: 1,
			almacenNombre: "Central",
			activa: true,
		},
	]),
	apiTurnoActual: vi.fn(async () => {
		throw new Error("sin turno");
	}),
}));

vi.mock("@/lib/api/compras", () => ({
	apiCuentasPagar: vi.fn(async () => [
		{
			cuentaPagarId: 1,
			compraFolio: "C-0001",
			proveedor: "Aceros del Norte",
			montoTotal: 1160,
			montoPagado: 0,
			saldo: 1160,
			fechaVencimiento: "2026-10-24",
			diasVencido: 0,
			estado: "PENDIENTE",
		},
		{
			cuentaPagarId: 2,
			compraFolio: "C-0002",
			proveedor: "Tornillos SA",
			montoTotal: 500,
			montoPagado: 0,
			saldo: 500,
			fechaVencimiento: "2026-08-01",
			diasVencido: 54,
			estado: "PENDIENTE",
		},
	]),
	apiFacturasPendientes: vi.fn(async () => [
		{
			cuentaPagarId: 1,
			compraFolio: "C-0001",
			facturaProveedor: "F-1",
			proveedorId: 9,
			proveedor: "Aceros del Norte",
			fechaCompra: "2026-09-24",
			montoTotal: 1160,
			montoPagado: 0,
			saldo: 1160,
			estadoPago: "PENDIENTE",
			fechaVencimiento: "2026-10-24",
			diasParaVencer: 30,
			alerta: "Por vencer",
		},
	]),
	apiFacturasVencidas: vi.fn(async () => [
		{
			cuentaPagarId: 2,
			compraFolio: "C-0002",
			facturaProveedor: "F-2",
			proveedorId: 10,
			proveedor: "Tornillos SA",
			fechaCompra: "2026-07-01",
			montoTotal: 500,
			montoPagado: 0,
			saldo: 500,
			estadoPago: "PENDIENTE",
			fechaVencimiento: "2026-08-01",
			contactoTelefono: null,
			diasVencido: 54,
			antiguedad: "54 días",
		},
	]),
	apiAbonarCuentaPagar: vi.fn(),
	apiEnviarCuentasPagarInforme: vi.fn(),
	apiEstadoCuentasPagarInforme: vi.fn(),
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
	return render(<CuentasPagarPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("CuentasPagarPage (smoke)", () => {
	it("renderiza título, tarjetas de resumen y la tabla de cuentas", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Cuentas por pagar" }),
		).toBeInTheDocument();
		expect(screen.getByText("Próximas a vencer")).toBeInTheDocument();
		expect(screen.getByText("Vencidas — prioridad de pago")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Todas" }),
		).toBeInTheDocument();
		expect(await screen.findByText("C-0001")).toBeInTheDocument();
		expect(screen.getByText("C-0002")).toBeInTheDocument();
		expect(
			screen.getAllByRole("button", { name: /abonar/i }).length,
		).toBeGreaterThan(0);
	});

	it("cambiar al tab 'Vencidas' filtra la tabla", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getByRole("button", { name: "Vencidas" }));
		expect(screen.queryByText("C-0001")).not.toBeInTheDocument();
		expect(screen.getByText("C-0002")).toBeInTheDocument();
	});

	it("'Abonar' abre el diálogo de abono con el saldo", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getAllByRole("button", { name: /abonar/i })[0]);
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText(/Abonar C-/)).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/Monto del abono/),
		).toBeInTheDocument();
		expect(within(dialogo).getByLabelText(/^Caja/)).toBeInTheDocument();
	});
});

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

describe("CuentasPagarPage (profundización)", () => {
	it("muestra badges de vencida, pagada y cancelada y cierra las cerradas", async () => {
		vi.mocked(apiCuentasPagar).mockResolvedValueOnce([
			{
				cuentaPagarId: 1,
				compraFolio: "C-0001",
				proveedor: "Aceros del Norte",
				montoTotal: 1160,
				montoPagado: 0,
				saldo: 1160,
				fechaVencimiento: "2026-10-24",
				diasVencido: 0,
				estado: "PARCIAL",
			},
			{
				cuentaPagarId: 3,
				compraFolio: "C-0003",
				proveedor: "Aceros del Norte",
				montoTotal: 1160,
				montoPagado: 1160,
				saldo: 0,
				fechaVencimiento: "2026-09-01",
				diasVencido: 0,
				estado: "LIQUIDADA",
			},
			{
				cuentaPagarId: 4,
				compraFolio: "C-0004",
				proveedor: "Aceros del Norte",
				montoTotal: 800,
				montoPagado: 0,
				saldo: 800,
				fechaVencimiento: "2026-09-01",
				diasVencido: 0,
				estado: "CANCELADA",
			},
		] as never);
		renderPage();
		expect(await screen.findByText("C-0003")).toBeInTheDocument();
		expect(screen.getByText("Pagada")).toBeInTheDocument();
		expect(screen.getByText("Cancelada")).toBeInTheDocument();
		expect(screen.getByText("Parcial")).toBeInTheDocument();
		expect(screen.getAllByText("Cerrada")).toHaveLength(2);
	});

	it("cambiar al tab 'Pendientes' filtra la tabla", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getByRole("button", { name: "Pendientes" }));
		expect(screen.getByText("C-0001")).toBeInTheDocument();
		expect(screen.queryByText("C-0002")).not.toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Todas" }));
		expect(screen.getByText("C-0002")).toBeInTheDocument();
	});

	it("muestra vacíos de próximas y vencidas cuando no hay facturas", async () => {
		vi.mocked(apiFacturasPendientes).mockResolvedValueOnce([]);
		vi.mocked(apiFacturasVencidas).mockResolvedValueOnce([]);
		renderPage();
		expect(await screen.findByText("Sin facturas por vencer.")).toBeInTheDocument();
		expect(screen.getByText("Sin facturas vencidas.")).toBeInTheDocument();
	});

	it("registra el abono con caja y turno y liquida la cuenta", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnoActual).mockResolvedValue(TURNO as never);
		vi.mocked(apiAbonarCuentaPagar).mockResolvedValueOnce({
			cuentaPagarId: 1,
			compraFolio: "C-0001",
			estado: "LIQUIDADA",
			saldo: 0,
		} as never);
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getAllByRole("button", { name: /abonar/i })[0]);
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(within(dialogo).getByLabelText(/^Caja/), "1");
		expect(
			await within(dialogo).findByText(/Turno abierto/),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar abono/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiAbonarCuentaPagar)).toHaveBeenCalledWith(
				1,
				{ monto: 1160, formaPagoId: 1, cajaId: 1, referencia: undefined },
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("liquidada") }),
		);
	});

	it("registra un abono parcial y muestra el saldo restante", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnoActual).mockResolvedValue(TURNO as never);
		vi.mocked(apiAbonarCuentaPagar).mockResolvedValueOnce({
			cuentaPagarId: 1,
			compraFolio: "C-0001",
			estado: "PARCIAL",
			saldo: 660,
		} as never);
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getAllByRole("button", { name: /abonar/i })[0]);
		const dialogo = await screen.findByRole("dialog");
		const monto = within(dialogo).getByLabelText(/Monto del abono/);
		await user.clear(monto);
		await user.type(monto, "500");
		await user.selectOptions(within(dialogo).getByLabelText(/^Caja/), "1");
		await within(dialogo).findByText(/Turno abierto/);
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar abono/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiAbonarCuentaPagar)).toHaveBeenCalledWith(
				1,
				expect.objectContaining({ monto: 500 }),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Abono en C-0001") }),
		);
	});

	it("pide referencia cuando la forma de pago la requiere", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnoActual).mockResolvedValue(TURNO as never);
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getAllByRole("button", { name: /abonar/i })[0]);
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(
			within(dialogo).getByLabelText("Forma de pago"),
			"4",
		);
		expect(
			await within(dialogo).findByLabelText(/Referencia/),
		).toBeInTheDocument();
		await user.selectOptions(
			within(dialogo).getByLabelText(/^Caja/),
			"1",
		);
		await within(dialogo).findByText(/Turno abierto/);
		// Sin referencia el abono es inválido.
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar abono/ }),
		);
		expect(
			await within(dialogo).findByText(/Revisa el monto/),
		).toBeInTheDocument();
		expect(apiAbonarCuentaPagar).not.toHaveBeenCalled();
	});

	it("valida monto mayor a cero y hasta el saldo", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnoActual).mockResolvedValue(TURNO as never);
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getAllByRole("button", { name: /abonar/i })[0]);
		const dialogo = await screen.findByRole("dialog");
		const monto = within(dialogo).getByLabelText(/Monto del abono/);
		await user.clear(monto);
		await user.type(monto, "9999");
		await user.selectOptions(within(dialogo).getByLabelText(/^Caja/), "1");
		await within(dialogo).findByText(/Turno abierto/);
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar abono/ }),
		);
		expect(
			await within(dialogo).findByText(/Revisa el monto/),
		).toBeInTheDocument();
		expect(apiAbonarCuentaPagar).not.toHaveBeenCalled();
	});

	it("avisa cuando la caja no tiene turno abierto", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnoActual).mockRejectedValue(new Error("sin turno"));
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getAllByRole("button", { name: /abonar/i })[0]);
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(within(dialogo).getByLabelText(/^Caja/), "1");
		expect(
			await within(dialogo).findByText(/no tiene un turno abierto/),
		).toBeInTheDocument();
	});

	it("muestra toast si el abono falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnoActual).mockResolvedValue(TURNO as never);
		vi.mocked(apiAbonarCuentaPagar).mockRejectedValueOnce(
			new Error("caja cerrada"),
		);
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getAllByRole("button", { name: /abonar/i })[0]);
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(within(dialogo).getByLabelText(/^Caja/), "1");
		await within(dialogo).findByText(/Turno abierto/);
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar abono/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("caja cerrada") }),
			),
		);
	});

	it("exporta las cuentas visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("C-0001");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^cuentas-pagar-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si las cuentas fallan al cargar", async () => {
		vi.mocked(apiCuentasPagar).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});

	it("muestra toast si las pendientes fallan al cargar", async () => {
		vi.mocked(apiFacturasPendientes).mockRejectedValueOnce(new Error("caído"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("caído") }),
			),
		);
	});

	it("muestra toast si las vencidas fallan al cargar", async () => {
		vi.mocked(apiFacturasVencidas).mockRejectedValueOnce(new Error("caído"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("caído") }),
			),
		);
	});
});

describe("CuentasPagarPage (recordatorio manual)", () => {
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
		renderPage();
		await screen.findByText("C-0001");
		expect(
			screen.queryByRole("button", { name: /recordatorio/i }),
		).not.toBeInTheDocument();
	});

	it("envía directo cuando hoy aún no se envió", async () => {
		const user = userEvent.setup();
		comoAdmin();
		vi.mocked(apiEstadoCuentasPagarInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			yaEnviado: false,
			estado: null,
			enviadoEn: null,
		});
		vi.mocked(apiEnviarCuentasPagarInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			destinatarios: 2,
			emailsEnviados: 2,
			whatsappsEnviados: 0,
			vencidas: 1,
			pendientes: 1,
			totalVencido: 500,
			totalPendiente: 1160,
		});
		renderPage();
		await screen.findByText("C-0001");
		await user.click(
			screen.getByRole("button", { name: /recordatorio/i }),
		);
		await waitFor(() => {
			expect(apiEstadoCuentasPagarInforme).toHaveBeenCalledOnce();
			expect(apiEnviarCuentasPagarInforme).toHaveBeenCalledOnce();
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
		vi.mocked(apiEstadoCuentasPagarInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			yaEnviado: true,
			estado: "ENVIADA",
			enviadoEn: "2026-10-05T09:00:00",
		});
		vi.mocked(apiEnviarCuentasPagarInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			destinatarios: 1,
			emailsEnviados: 1,
			whatsappsEnviados: 0,
			vencidas: 0,
			pendientes: 2,
			totalVencido: 0,
			totalPendiente: 800,
		});
		renderPage();
		await screen.findByText("C-0001");
		await user.click(
			screen.getByRole("button", { name: /recordatorio/i }),
		);
		expect(
			await screen.findByText("Recordatorio ya enviado"),
		).toBeInTheDocument();
		expect(apiEnviarCuentasPagarInforme).not.toHaveBeenCalled();
		await user.click(screen.getByRole("button", { name: /reenviar/i }));
		await waitFor(() => {
			expect(apiEnviarCuentasPagarInforme).toHaveBeenCalledOnce();
		});
	});
});
