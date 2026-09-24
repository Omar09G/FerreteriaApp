import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/lib/api/venta", () => ({
	apiCuentasCobrar: vi.fn(),
	apiPagoCliente: vi.fn(),
}));
vi.mock("@/lib/api/catalogo", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiClientes: vi.fn(),
}));

import { apiCuentasCobrar, apiPagoCliente } from "@/lib/api/venta";
import { apiClientes } from "@/lib/api/catalogo";
import CobranzaPage from "@/features/ventas/CobranzaPage";
import {
	CLIENTE,
	CUENTA,
	pageOf,
	renderPagina,
} from "@/test/helpers/ventasPos";

const apiCuentasMock = vi.mocked(apiCuentasCobrar);
vi.mocked(apiPagoCliente);
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
});
