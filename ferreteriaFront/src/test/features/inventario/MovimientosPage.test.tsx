import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import MovimientosPage from "@/features/inventario/MovimientosPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiMovimientos } from "@/lib/api/reportes";

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

vi.mock("@/lib/api/reportes", () => ({
	apiMovimientos: vi.fn(async () => ({
		success: true,
		data: [
			{
				movimientoId: 7,
				productoId: 1,
				productoNombre: "Martillo",
				almacenId: 1,
				almacenNombre: "Central",
				tipo: "ENTRADA",
				cantidad: 5,
				costoUnitario: 100,
				motivoNombre: "Compra a proveedor",
				refTabla: null,
				refId: null,
				creadoEn: "2026-09-24T10:00:00",
			},
		],
		meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
	})),
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
	return render(<MovimientosPage />, { wrapper });
}

afterEach(() => {
	vi.clearAllMocks();
});

describe("MovimientosPage (smoke)", () => {
	it("renderiza título, filtros y la fila del movimiento", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Movimientos de inventario" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Producto \(id\)/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Almacén \(id\)/)).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /filtrar/i }),
		).toBeInTheDocument();
		expect(await screen.findByText("Martillo")).toBeInTheDocument();
		expect(screen.getByText("Entrada")).toBeInTheDocument();
	});

	it("aplicar filtro por producto muestra el badge de filtros activos", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		await user.type(screen.getByLabelText(/Producto \(id\)/), "1");
		await user.click(screen.getByRole("button", { name: /filtrar/i }));
		expect(await screen.findByText("Filtros activos")).toBeInTheDocument();
	});
});

const MOV_SALIDA = {
	movimientoId: 8,
	productoId: 2,
	productoNombre: "Clavo",
	almacenId: 1,
	almacenNombre: "Central",
	tipo: "SALIDA",
	cantidad: 3,
	costoUnitario: 5,
	motivoNombre: null,
	refTabla: "ventas",
	refId: 12,
	creadoEn: "2026-09-24T11:00:00",
};

describe("MovimientosPage (profundización)", () => {
	it("muestra salida, guion sin motivo y referencia a la venta", async () => {
		vi.mocked(apiMovimientos).mockResolvedValueOnce({
			success: true,
			data: [MOV_SALIDA],
			meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("Salida")).toBeInTheDocument();
		expect(screen.getByText("ventas#12")).toBeInTheDocument();
		expect(screen.getByText("Clavo")).toBeInTheDocument();
	});

	it("filtra por almacén y quita los filtros", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		await user.type(screen.getByLabelText(/Producto \(id\)/), "1");
		await user.type(screen.getByLabelText(/Almacén \(id\)/), "2");
		await user.click(screen.getByRole("button", { name: /filtrar/i }));
		await waitFor(() =>
			expect(vi.mocked(apiMovimientos)).toHaveBeenLastCalledWith(
				expect.objectContaining({ productoId: 1, almacenId: 2, page: 0 }),
			),
		);
		expect(await screen.findByText("Filtros activos")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Quitar filtros" }));
		await waitFor(() =>
			expect(vi.mocked(apiMovimientos)).toHaveBeenLastCalledWith(
				expect.objectContaining({ page: 0 }),
			),
		);
		expect(screen.queryByText("Filtros activos")).not.toBeInTheDocument();
	});

	it("cambiar el rango de fechas recarga la bitácora", async () => {
		renderPage();
		await screen.findByText("Martillo");
		const api = vi.mocked(apiMovimientos);
		const llamadasAntes = api.mock.calls.length;
		const rango = screen.getByTestId("rango-fechas");
		const [del] = within(rango).getAllByDisplayValue(/\d{4}-\d{2}-\d{2}/);
		const actual = (del as HTMLInputElement).value;
		const d = new Date(`${actual}T12:00:00`);
		d.setDate(d.getDate() - 2);
		const nuevo = d.toISOString().slice(0, 10);
		fireEvent.change(del, { target: { value: nuevo } });
		await waitFor(() =>
			expect(api.mock.calls.length).toBeGreaterThan(llamadasAntes),
		);
		const ultima = api.mock.calls[api.mock.calls.length - 1][0] as {
			inicio: string;
		};
		expect(ultima.inicio).toBe(nuevo);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientos).mockResolvedValue({
			success: true,
			data: [
				{
					movimientoId: 7,
					productoId: 1,
					productoNombre: "Martillo",
					almacenId: 1,
					almacenNombre: "Central",
					tipo: "ENTRADA",
					cantidad: 5,
					costoUnitario: 100,
					motivoNombre: "Compra a proveedor",
					refTabla: null,
					refId: null,
					creadoEn: "2026-09-24T10:00:00",
				},
			],
			meta: { page: 0, size: 20, totalElements: 40, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findByText("Martillo");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiMovimientos)).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta los movimientos visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^movimientos-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiMovimientos).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
