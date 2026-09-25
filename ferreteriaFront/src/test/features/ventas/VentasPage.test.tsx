import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Swal from "sweetalert2";

vi.mock("@/lib/api/venta", () => ({
	apiVentas: vi.fn(),
	apiCancelarVenta: vi.fn(),
}));
vi.mock("@/lib/api/catalogo", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiAlmacenes: vi.fn(),
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
const apiCancelarVentaMock = vi.mocked(apiCancelarVenta);

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

	it("filtra por almacén y muestra Limpiar para quitar el filtro", async () => {
		const user = userEvent.setup();
		renderPagina(<VentasPage />);
		await screen.findByText("V-0001");
		await user.selectOptions(screen.getByLabelText("Almacén"), "1");
		expect(
			await screen.findByRole("button", { name: "Limpiar" }),
		).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		expect(
			screen.queryByRole("button", { name: "Limpiar" }),
		).not.toBeInTheDocument();
	});

	it("muestra el botón Excel para exportar lo visible", async () => {
		renderPagina(<VentasPage />);
		await screen.findByText("V-0001");
		expect(
			screen.getByRole("button", { name: /excel/i }),
		).toBeInTheDocument();
	});

	it("valida el motivo y confirma la cancelación", async () => {
		const user = userEvent.setup();
		apiCancelarVentaMock.mockResolvedValue({ ...VENTA, estado: "CANCELADA" });
		renderPagina(<VentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Cancelar venta V-0001" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Cancelar venta",
		});
		expect(
			within(dialogo).getByRole("button", { name: /Confirmar cancelación/ }),
		).toBeDisabled();
		await user.type(
			within(dialogo).getByLabelText(/Motivo de cancelación/),
			"abc",
		);
		expect(
			within(dialogo).getByText("Mínimo 5 caracteres."),
		).toBeInTheDocument();
		expect(
			within(dialogo).getByRole("button", { name: /Confirmar cancelación/ }),
		).toBeDisabled();
		const motivo = within(dialogo).getByLabelText(/Motivo de cancelación/);
		await user.clear(motivo);
		await user.type(motivo, "error de captura");
		await user.click(
			within(dialogo).getByRole("button", { name: /Confirmar cancelación/ }),
		);
		expect(apiCancelarVentaMock).toHaveBeenCalledWith(1, {
			motivo: "error de captura",
		});
	});
});

const VENTA_DEVUELTA = {
	...VENTA,
	ventaId: 2,
	folio: "V-0002",
	clienteNombre: null,
	formaPagoId: 99,
	formaPagoNombre: "Desconocida",
	descuentoTotal: 10,
	estado: "DEVUELTA_PARCIAL",
	notas: "Cliente devolvió una pieza",
	detalles: [
		{
			ventaDetalleId: 2,
			productoId: 10,
			productoNombre: "Martillo",
			cantidad: 2,
			precioUnitario: 50,
			costoUnitario: 30,
			descuentoLinea: 5,
			totalLinea: 95,
			promocionId: 7,
		},
	],
	pagos: [
		{
			pagoClienteId: 2,
			formaPagoId: 99,
			referencia: "REF-9",
			monto: 106,
			fecha: "2026-01-10",
		},
	],
};

describe("VentasPage (profundización)", () => {
	it("muestra todos los estados de venta en la tabla", async () => {
		apiVentasMock.mockResolvedValueOnce(
			pageOf([
				VENTA,
				{ ...VENTA, ventaId: 3, folio: "V-0003", estado: "CANCELADA" },
				{ ...VENTA_DEVUELTA },
				{
					...VENTA,
					ventaId: 4,
					folio: "V-0004",
					estado: "DEVUELTA_TOTAL",
				},
			]),
		);
		renderPagina(<VentasPage />);
		expect(await screen.findByText("V-0004")).toBeInTheDocument();
		expect(screen.getByText("Completada")).toBeInTheDocument();
		expect(screen.getByText("Cancelada")).toBeInTheDocument();
		expect(screen.getByText("Devuelta parcial")).toBeInTheDocument();
		expect(screen.getByText("Devuelta total")).toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: "Cancelar venta V-0003" }),
		).not.toBeInTheDocument();
	});

	it("muestra promo, descuento, pagos con referencia, notas y consumidor final", async () => {
		const user = userEvent.setup();
		apiVentasMock.mockResolvedValueOnce(pageOf([{ ...VENTA_DEVUELTA }]));
		renderPagina(<VentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Ver detalle de V-0002" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Detalle de venta V-0002",
		});
		expect(within(dialogo).getByText("promo #7")).toBeInTheDocument();
		expect(within(dialogo).getByText("Consumidor final")).toBeInTheDocument();
		expect(within(dialogo).getByText("Desconocida")).toBeInTheDocument();
		expect(within(dialogo).getByText("Forma 99")).toBeInTheDocument();
		expect(within(dialogo).getByText("· ref: REF-9")).toBeInTheDocument();
		expect(
			within(dialogo).getByText("Cliente devolvió una pieza"),
		).toBeInTheDocument();
		expect(within(dialogo).getByText("Devuelta parcial")).toBeInTheDocument();
	});

	it("muestra toast si cancelar la venta falla", async () => {
		const user = userEvent.setup();
		apiCancelarVentaMock.mockRejectedValueOnce(new Error("ya cancelada"));
		renderPagina(<VentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Cancelar venta V-0001" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Cancelar venta",
		});
		await user.type(
			within(dialogo).getByLabelText(/Motivo de cancelación/),
			"error de captura",
		);
		await user.click(
			within(dialogo).getByRole("button", { name: /Confirmar cancelación/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("ya cancelada"),
				}),
			),
		);
	});

	it("cambiar el rango de fechas recarga el historial", async () => {
		renderPagina(<VentasPage />);
		await screen.findByText("V-0001");
		const llamadasAntes = apiVentasMock.mock.calls.length;
		const rango = screen.getByTestId("rango-fechas");
		const [del] = within(rango).getAllByDisplayValue(/\d{4}-\d{2}-\d{2}/);
		const actual = (del as HTMLInputElement).value;
		const d = new Date(`${actual}T12:00:00`);
		d.setDate(d.getDate() - 2);
		const nuevo = d.toISOString().slice(0, 10);
		fireEvent.change(del, { target: { value: nuevo } });
		await waitFor(() =>
			expect(apiVentasMock.mock.calls.length).toBeGreaterThan(llamadasAntes),
		);
		const ultima = apiVentasMock.mock.calls[apiVentasMock.mock.calls.length - 1][0] as {
			desde: string;
		};
		expect(ultima.desde).toBe(nuevo);
	});

	it("pagina el historial cuando hay más de una página", async () => {
		const user = userEvent.setup();
		apiVentasMock.mockResolvedValue({
			success: true,
			data: [VENTA],
			meta: { page: 0, size: 20, totalElements: 40, totalPages: 2 },
		});
		renderPagina(<VentasPage />);
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
		renderPagina(<VentasPage />);
		await screen.findByText("V-0001");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^ventas-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si el historial falla al cargar", async () => {
		apiVentasMock.mockRejectedValueOnce(new Error("sin conexión"));
		renderPagina(<VentasPage />);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
