import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Swal from "sweetalert2";

vi.mock("@/lib/api/venta", () => ({
	apiVentas: vi.fn(),
	apiCrearDevolucion: vi.fn(),
	apiDevolucionesDeVenta: vi.fn(),
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
	apiCrearDevolucion,
	apiDevolucionesDeVenta,
	apiVentas,
} from "@/lib/api/venta";
import DevolucionesPage from "@/features/ventas/DevolucionesPage";
import { VENTA, pageOf, renderPagina } from "@/test/helpers/ventasPos";

const apiVentasMock = vi.mocked(apiVentas);
const apiDevolucionesMock = vi.mocked(apiDevolucionesDeVenta);
const apiCrearDevolucionMock = vi.mocked(apiCrearDevolucion);

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

	it("muestra el botón Excel para exportar lo visible", async () => {
		renderPagina(<DevolucionesPage />);
		await screen.findByText("V-0001");
		expect(
			screen.getByRole("button", { name: /excel/i }),
		).toBeInTheDocument();
	});

	it("deshabilita la acción en ventas canceladas", async () => {
		apiVentasMock.mockResolvedValue(
			pageOf([{ ...VENTA, ventaId: 2, folio: "V-0002", estado: "CANCELADA" }]),
		);
		renderPagina(<DevolucionesPage />);
		expect(await screen.findByText("V-0002")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Devoluciones de V-0002" }),
		).toBeDisabled();
	});

	it("registra la devolución con motivo y partidas marcadas", async () => {
		const user = userEvent.setup();
		apiCrearDevolucionMock.mockResolvedValue({} as never);
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		await user.click(
			await screen.findByRole("button", { name: /Registrar devolución/ }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva devolución · V-0001",
		});
		await user.type(
			within(dialogo).getByLabelText(/Motivo/),
			"Producto dañado",
		);
		await user.click(
			within(dialogo).getByRole("checkbox", { name: "Devolver Martillo" }),
		);
		await user.click(
			within(dialogo).getByRole("button", { name: "Registrar devolución" }),
		);
		expect(apiCrearDevolucionMock).toHaveBeenCalledWith(
			expect.objectContaining({ ventaId: 1, motivo: "Producto dañado" }),
		);
	});
});

const DEVOLUCION_PREVIA = {
	devolucionId: 5,
	folio: "DEV-005",
	ventaId: 1,
	motivo: "Dañado",
	formaDevolucionId: 99,
	formaDevolucionNombre: null,
	fecha: "2026-01-11T10:00:00",
	total: 100,
	detalles: [
		{
			ventaDetalleId: 1,
			productoId: 10,
			productoNombre: "Martillo",
			cantidad: 1,
			precioUnitario: 50,
		},
	],
};

describe("DevolucionesPage (profundización)", () => {
	it("lista las devoluciones previas con forma desconocida", async () => {
		const user = userEvent.setup();
		apiDevolucionesMock.mockResolvedValue([DEVOLUCION_PREVIA] as never);
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		expect(await screen.findByText("DEV-005")).toBeInTheDocument();
		expect(screen.getByText(/Dañado/)).toBeInTheDocument();
		expect(screen.getByText(/Forma 99/)).toBeInTheDocument();
	});

	it("muestra error cuando el historial falla", async () => {
		const user = userEvent.setup();
		apiDevolucionesMock.mockRejectedValueOnce(new Error("historial caído"));
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		expect(
			await screen.findByText("Error: historial caído"),
		).toBeInTheDocument();
	});

	it("marca la línea como devuelta cuando ya se devolvió todo", async () => {
		const user = userEvent.setup();
		apiDevolucionesMock.mockResolvedValue([
			{
				...DEVOLUCION_PREVIA,
				detalles: [
					{
						ventaDetalleId: 1,
						productoId: 10,
						productoNombre: "Martillo",
						cantidad: 2,
						precioUnitario: 50,
					},
				],
			},
		] as never);
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		await user.click(
			await screen.findByRole("button", { name: /Registrar devolución/ }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva devolución · V-0001",
		});
		const check = within(dialogo).getByRole("checkbox", {
			name: "Devolver Martillo",
		});
		expect(check).toBeDisabled();
		expect(within(dialogo).getByText(/· Devuelto/)).toBeInTheDocument();
	});

	it("muestra el remanente disponible por línea", async () => {
		const user = userEvent.setup();
		apiDevolucionesMock.mockResolvedValue([DEVOLUCION_PREVIA] as never);
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		await user.click(
			await screen.findByRole("button", { name: /Registrar devolución/ }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva devolución · V-0001",
		});
		expect(
			within(dialogo).getByText("· Restan 1 de 2"),
		).toBeInTheDocument();
	});

	it("registra con forma de devolución y cantidad editada", async () => {
		const user = userEvent.setup();
		apiCrearDevolucionMock.mockResolvedValue({} as never);
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		await user.click(
			await screen.findByRole("button", { name: /Registrar devolución/ }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva devolución · V-0001",
		});
		await user.type(
			within(dialogo).getByLabelText(/Motivo/),
			"Producto dañado",
		);
		await user.selectOptions(
			within(dialogo).getByLabelText(/Forma de devolución/),
			"4",
		);
		await user.click(
			within(dialogo).getByRole("checkbox", { name: "Devolver Martillo" }),
		);
		const cantidad = within(dialogo).getByLabelText("Cantidad de Martillo");
		await user.clear(cantidad);
		await user.type(cantidad, "2");
		expect(within(dialogo).getByText("Total a devolver")).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: "Registrar devolución" }),
		);
		expect(apiCrearDevolucionMock).toHaveBeenCalledWith({
			ventaId: 1,
			motivo: "Producto dañado",
			formaDevolucionId: 4,
			detalles: [
				{
					productoId: 10,
					ventaDetalleId: 1,
					cantidad: 2,
					precioUnitario: 50,
				},
			],
		});
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Devolución registrada"),
			}),
		);
	});

	it("valida motivo y partidas antes de registrar", async () => {
		const user = userEvent.setup();
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		await user.click(
			await screen.findByRole("button", { name: /Registrar devolución/ }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva devolución · V-0001",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Registrar devolución" }),
		);
		expect(
			await within(dialogo).findByText(/motivo y al menos una partida/),
		).toBeInTheDocument();
	});

	it("cancela el formulario y vuelve al historial", async () => {
		const user = userEvent.setup();
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		await user.click(
			await screen.findByRole("button", { name: /Registrar devolución/ }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva devolución · V-0001",
		});
		await user.click(within(dialogo).getByRole("button", { name: /Cancelar/ }));
		await waitFor(() =>
			expect(
				screen.queryByRole("dialog", { name: "Nueva devolución · V-0001" }),
			).not.toBeInTheDocument(),
		);
		expect(apiCrearDevolucionMock).not.toHaveBeenCalled();
	});

	it("muestra toast si registrar falla", async () => {
		const user = userEvent.setup();
		apiCrearDevolucionMock.mockRejectedValueOnce(new Error("ya devuelta"));
		renderPagina(<DevolucionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Devoluciones de V-0001" }),
		);
		await user.click(
			await screen.findByRole("button", { name: /Registrar devolución/ }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Nueva devolución · V-0001",
		});
		await user.type(
			within(dialogo).getByLabelText(/Motivo/),
			"Producto dañado",
		);
		await user.click(
			within(dialogo).getByRole("checkbox", { name: "Devolver Martillo" }),
		);
		await user.click(
			within(dialogo).getByRole("button", { name: "Registrar devolución" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("ya devuelta"),
				}),
			),
		);
	});

	it("muestra Cliente general cuando la venta no tiene cliente", async () => {
		apiVentasMock.mockResolvedValue(
			pageOf([{ ...VENTA, clienteNombre: null }]),
		);
		renderPagina(<DevolucionesPage />);
		expect(await screen.findByText("Cliente general")).toBeInTheDocument();
	});

	it("habilita la acción en ventas con devolución parcial", async () => {
		apiVentasMock.mockResolvedValue(
			pageOf([{ ...VENTA, ventaId: 2, folio: "V-0002", estado: "DEVUELTA_PARCIAL" }]),
		);
		renderPagina(<DevolucionesPage />);
		expect(await screen.findByText("V-0002")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Devoluciones de V-0002" }),
		).toBeEnabled();
	});

	it("quita el rango con Todos y recarga sin fechas", async () => {
		const user = userEvent.setup();
		renderPagina(<DevolucionesPage />);
		await screen.findByText("V-0001");
		await user.click(screen.getByRole("button", { name: "Todos" }));
		await waitFor(() => {
			const ultima = apiVentasMock.mock.calls.at(-1)?.[0] as Record<
				string,
				unknown
			>;
			expect(ultima.desde).toBeUndefined();
		});
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		apiVentasMock.mockResolvedValue({
			success: true,
			data: [VENTA],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		});
		renderPagina(<DevolucionesPage />);
		await screen.findByText("V-0001");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(apiVentasMock).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta las ventas visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPagina(<DevolucionesPage />);
		await screen.findByText("V-0001");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^devoluciones-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		apiVentasMock.mockRejectedValueOnce(new Error("sin conexión"));
		renderPagina(<DevolucionesPage />);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
