import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/lib/api/venta", () => ({
	apiVentas: vi.fn(),
	apiCrearDevolucion: vi.fn(),
	apiDevolucionesDeVenta: vi.fn(),
}));

import {
	apiCrearDevolucion,
	apiDevolucionesDeVenta,
	apiVentas,
} from "@/lib/api/venta";
import DevolucionesPage from "@/features/ventas/DevolucionesPage";
import { VENTA, pageOf, renderPagina } from "@/test/helpers/ventasPos";

const apiVentasMock = vi.mocked(apiVentas);
const apiDevolucionesMock = vi.mocked(apiDevolucionesDeVenta);
vi.mocked(apiCrearDevolucion);

beforeEach(() => {
	vi.clearAllMocks();
	apiVentasMock.mockResolvedValue(pageOf([VENTA]));
	apiDevolucionesMock.mockResolvedValue([]);
});

describe("DevolucionesPage", () => {
	it("renderiza título y tabla de ventas", async () => {
		renderPagina(<DevolucionesPage />);
		expect(
			screen.getByRole("heading", { name: "Devoluciones" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Ventas (1)")).toBeInTheDocument();
		expect(await screen.findByText("V-0001")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Devoluciones de V-0001" }),
		).toBeInTheDocument();
	});

	it("abre el historial de devoluciones de la venta", async () => {
		const user = userEvent.setup();
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		expect(await screen.findByText("Venta V-0001")).toBeInTheDocument();
		expect(screen.getByText("Historial de devoluciones")).toBeInTheDocument();
		expect(
			screen.getByText("Sin devoluciones registradas."),
		).toBeInTheDocument();
	});

	it("abre el formulario de devolución y permite marcar partidas", async () => {
		const user = userEvent.setup();
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		await user.click(
			await screen.findByRole("button", { name: /Registrar devolución/ }),
		);
		expect(
			await screen.findByText("Nueva devolución · V-0001"),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Motivo/)).toBeInTheDocument();
		expect(screen.getByText("Partidas a devolver")).toBeInTheDocument();
		const checkbox = screen.getByRole("checkbox", {
			name: "Devolver Martillo",
		});
		await user.click(checkbox);
		expect(checkbox).toBeChecked();
	});
});
