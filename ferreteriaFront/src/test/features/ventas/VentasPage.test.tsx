import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/lib/api/venta", () => ({
	apiVentas: vi.fn(),
	apiCancelarVenta: vi.fn(),
}));
vi.mock("@/lib/api/catalogo", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiAlmacenes: vi.fn(),
}));

import { apiCancelarVenta, apiVentas } from "@/lib/api/venta";
import { apiAlmacenes } from "@/lib/api/catalogo";
import VentasPage from "@/features/ventas/VentasPage";
import {
	ALMACEN,
	VENTA,
	pageOf,
	renderPagina,
} from "@/test/helpers/ventasPos";

const apiVentasMock = vi.mocked(apiVentas);
const apiAlmacenesMock = vi.mocked(apiAlmacenes);
vi.mocked(apiCancelarVenta);

beforeEach(() => {
	vi.clearAllMocks();
	apiVentasMock.mockResolvedValue(pageOf([VENTA]));
	apiAlmacenesMock.mockResolvedValue([ALMACEN]);
});

describe("VentasPage", () => {
	it("renderiza título, filtros y tabla con una venta", async () => {
		renderPagina(<VentasPage />);
		expect(
			screen.getByRole("heading", { name: "Historial de ventas" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Almacén")).toBeInTheDocument();
		expect(await screen.findByText("Ventas (1)")).toBeInTheDocument();
		expect(await screen.findByText("V-0001")).toBeInTheDocument();
		expect(screen.getByText("Completada")).toBeInTheDocument();
	});

	it("abre el detalle al hacer clic en Ver detalle", async () => {
		const user = userEvent.setup();
		renderPagina(<VentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Ver detalle de V-0001" }),
		);
		expect(
			await screen.findByText("Detalle de venta V-0001"),
		).toBeInTheDocument();
		expect(screen.getByText("Artículos")).toBeInTheDocument();
		expect(screen.getByText("Martillo")).toBeInTheDocument();
	});

	it("abre el diálogo de cancelación con motivo", async () => {
		const user = userEvent.setup();
		renderPagina(<VentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Cancelar venta V-0001" }),
		);
		expect(
			await screen.findByRole("dialog", { name: "Cancelar venta" }),
		).toBeInTheDocument();
		expect(
			screen.getByLabelText(/Motivo de cancelación/),
		).toBeInTheDocument();
	});
});
