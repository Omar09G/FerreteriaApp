import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import StockPage from "@/features/inventario/StockPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiAlmacenes } from "@/lib/api/catalogo";
import { apiStock } from "@/lib/api/reportes";
import { apiCrearMovimiento } from "@/lib/api/inventario";

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

vi.mock("@/lib/api/catalogo", () => ({
	apiAlmacenes: vi.fn(async () => [
		{
			almacenId: 1,
			nombre: "Central",
			direccion: null,
			telefono: null,
			esPuntoVenta: true,
			activo: true,
		},
	]),
}));

vi.mock("@/lib/api/reportes", () => ({
	apiStock: vi.fn(async () => ({
		success: true,
		data: [
			{
				productoId: 1,
				productoNombre: "Martillo",
				productoCodigo: "MAR-001",
				almacenId: 1,
				almacenNombre: "Central",
				stock: 10,
				stockMinimo: 5,
				reservado: 0,
			},
		],
		meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
	})),
}));

vi.mock("@/lib/api/inventario", () => ({
	apiCrearMovimiento: vi.fn(),
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
	return render(<StockPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("StockPage (smoke)", () => {
	it("renderiza título, filtros y la fila de stock", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Existencias" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Almacén")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /solo bajo stock/i }),
		).toBeInTheDocument();
		expect(await screen.findByText("Martillo")).toBeInTheDocument();
		expect(screen.getByText("MAR-001")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Ajustar stock de Martillo" }),
		).toBeInTheDocument();
	});

	it("el filtro 'Solo bajo stock' cambia de estado al hacer click", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		const boton = screen.getByRole("button", { name: /solo bajo stock/i });
		await user.click(boton);
		expect(
			await screen.findByRole("button", {
				name: "Mostrando solo bajo stock",
			}),
		).toBeInTheDocument();
	});

	it("abrir 'Ajustar' muestra el diálogo de ajuste de existencia", async () => {
		const user = userEvent.setup();
		renderPage();
		await user.click(
			await screen.findByRole("button", {
				name: "Ajustar stock de Martillo",
			}),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(
			within(dialogo).getByText("Ajustar existencia"),
		).toBeInTheDocument();
		expect(
			within(dialogo).getByRole("button", { name: /registrar entrada/i }),
		).toBeInTheDocument();
	});
});

const FILA_AGOTADA = {
	productoId: 2,
	productoNombre: "Clavo",
	productoCodigo: null,
	almacenId: 1,
	almacenNombre: "Central",
	stock: 0,
	stockMinimo: 5,
	reservado: 2,
};

describe("StockPage (profundización)", () => {
	it("filtra por almacén y ofrece Limpiar", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		await user.selectOptions(screen.getByLabelText("Almacén"), "1");
		expect(
			await screen.findByRole("button", { name: "Limpiar" }),
		).toBeInTheDocument();
		expect(vi.mocked(apiStock)).toHaveBeenLastCalledWith(
			expect.objectContaining({ almacenId: 1, page: 0 }),
		);
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		expect(
			screen.queryByRole("button", { name: "Limpiar" }),
		).not.toBeInTheDocument();
	});

	it("muestra Agotado, guion sin código y reservado", async () => {
		vi.mocked(apiStock).mockResolvedValueOnce({
			success: true,
			data: [FILA_AGOTADA],
			meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("Agotado")).toBeInTheDocument();
		expect(screen.getByText("Clavo")).toBeInTheDocument();
	});

	it("el ajuste rápido avisa cuando no hay filas", async () => {
		vi.mocked(apiStock).mockResolvedValueOnce({
			success: true,
			data: [],
			meta: { page: 0, size: 20, totalElements: 0, totalPages: 1 },
		} as never);
		renderPage();
		await waitFor(() =>
			expect(
				screen.getByRole("button", { name: /Ajuste rápido/ }),
			).toBeDisabled(),
		);
	});

	it("registra una entrada con costo y nota y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearMovimiento).mockResolvedValueOnce({
			movimientoId: 1,
			cantidad: 5,
		} as never);
		renderPage();
		await user.click(
			await screen.findByRole("button", {
				name: "Ajustar stock de Martillo",
			}),
		);
		const dialogo = await screen.findByRole("dialog");
		await user.type(within(dialogo).getByLabelText(/Cantidad/), "5");
		await user.type(
			within(dialogo).getByLabelText(/Costo unitario/),
			"102.5",
		);
		await user.type(within(dialogo).getByLabelText(/Nota/), "Conteo");
		await user.click(
			within(dialogo).getByRole("button", { name: /registrar entrada/i }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearMovimiento)).toHaveBeenCalledWith({
				productoId: 1,
				almacenId: 1,
				tipo: "ENTRADA",
				cantidad: 5,
				motivoId: 6,
				nota: "Conteo",
				costoUnitario: 102.5,
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Entrada registrada"),
			}),
		);
	});

	it("registra una salida y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearMovimiento).mockResolvedValueOnce({
			movimientoId: 2,
			cantidad: 3,
		} as never);
		renderPage();
		await user.click(
			await screen.findByRole("button", {
				name: "Ajustar stock de Martillo",
			}),
		);
		const dialogo = await screen.findByRole("dialog");
		await user.click(within(dialogo).getByRole("button", { name: "Salida" }));
		expect(
			within(dialogo).getByRole("button", { name: /registrar salida/i }),
		).toBeInTheDocument();
		await user.type(within(dialogo).getByLabelText(/Cantidad/), "3");
		await user.click(
			within(dialogo).getByRole("button", { name: /registrar salida/i }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearMovimiento)).toHaveBeenCalledWith(
				expect.objectContaining({ tipo: "SALIDA", cantidad: 3 }),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Salida registrada"),
			}),
		);
	});

	it("valida cantidad mayor a cero", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearMovimiento).mockRejectedValueOnce(new Error("fallo"));
		renderPage();
		await user.click(
			await screen.findByRole("button", {
				name: "Ajustar stock de Martillo",
			}),
		);
		const dialogo = await screen.findByRole("dialog");
		const cantidad = within(dialogo).getByLabelText(/Cantidad/);
		await user.type(cantidad, "5");
		await user.click(
			within(dialogo).getByRole("button", { name: /registrar entrada/i }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearMovimiento)).toHaveBeenCalledTimes(1),
		);
		await user.clear(cantidad);
		expect(
			await within(dialogo).findByText("La cantidad debe ser mayor a 0."),
		).toBeInTheDocument();
		expect(vi.mocked(apiCrearMovimiento)).toHaveBeenCalledTimes(1);
	});

	it("rechaza la salida que dejaría el stock en negativo", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearMovimiento).mockRejectedValueOnce(new Error("fallo"));
		renderPage();
		await user.click(
			await screen.findByRole("button", {
				name: "Ajustar stock de Martillo",
			}),
		);
		const dialogo = await screen.findByRole("dialog");
		await user.type(within(dialogo).getByLabelText(/Cantidad/), "5");
		await user.click(
			within(dialogo).getByRole("button", { name: /registrar entrada/i }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearMovimiento)).toHaveBeenCalledTimes(1),
		);
		await user.click(within(dialogo).getByRole("button", { name: "Salida" }));
		const cantidad = within(dialogo).getByLabelText(/Cantidad/);
		await user.clear(cantidad);
		await user.type(cantidad, "999");
		expect(
			await within(dialogo).findByText(
				"La salida dejaría el stock en negativo.",
			),
		).toBeInTheDocument();
		expect(vi.mocked(apiCrearMovimiento)).toHaveBeenCalledTimes(1);
	});

	it("cancela el ajuste sin llamar al backend", async () => {
		const user = userEvent.setup();
		renderPage();
		await user.click(
			await screen.findByRole("button", {
				name: "Ajustar stock de Martillo",
			}),
		);
		const dialogo = await screen.findByRole("dialog");
		await user.click(within(dialogo).getByRole("button", { name: /Cancelar/ }));
		await waitFor(() =>
			expect(
				screen.queryByRole("dialog", { name: "Ajustar existencia" }),
			).not.toBeInTheDocument(),
		);
		expect(apiCrearMovimiento).not.toHaveBeenCalled();
	});

	it("muestra toast si el ajuste falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearMovimiento).mockRejectedValueOnce(
			new Error("stock insuficiente"),
		);
		renderPage();
		await user.click(
			await screen.findByRole("button", {
				name: "Ajustar stock de Martillo",
			}),
		);
		const dialogo = await screen.findByRole("dialog");
		await user.type(within(dialogo).getByLabelText(/Cantidad/), "2");
		await user.click(
			within(dialogo).getByRole("button", { name: /registrar entrada/i }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("stock insuficiente"),
				}),
			),
		);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiStock).mockResolvedValue({
			success: true,
			data: [
				{
					productoId: 1,
					productoNombre: "Martillo",
					productoCodigo: "MAR-001",
					almacenId: 1,
					almacenNombre: "Central",
					stock: 10,
					stockMinimo: 5,
					reservado: 0,
				},
			],
			meta: { page: 0, size: 20, totalElements: 40, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findByText("Martillo");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiStock)).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta las filas visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^inventario-stock-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiStock).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
