import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import CuentasPagarPage from "@/features/compras/CuentasPagarPage";
import { ToastProvider } from "@/components/ui/Toast";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
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
