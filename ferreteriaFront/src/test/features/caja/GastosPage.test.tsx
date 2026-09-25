import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import GastosPage from "@/features/caja/GastosPage";
import { ToastProvider } from "@/components/ui/Toast";
import {
	apiActualizarGasto,
	apiActualizarIngreso,
	apiCrearGasto,
	apiCrearIngreso,
	apiEliminarGasto,
	apiEliminarIngreso,
	apiGastos,
	apiIngresosOtros,
} from "@/lib/api/caja";
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
	apiGastos: vi.fn(async () => ({
		success: true,
		data: [
			{
				gastoId: 1,
				folio: "G-0001",
				tipoGastoId: 14,
				tipoGastoNombre: null,
				descripcion: "Flete de pedido",
				monto: 500,
				fechaGasto: "2026-09-01",
				formaPagoId: 1,
				formaPagoNombre: null,
				proveedorId: null,
				turnoCajaId: null,
				facturaUuid: null,
				usuarioId: 1,
				creadoEn: "2026-09-01T10:00:00",
			},
		],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	})),
	apiIngresosOtros: vi.fn(async () => ({
		success: true,
		data: [
			{
				ingresoOtroId: 1,
				concepto: "Venta de chatarra",
				monto: 300,
				fecha: "2026-09-01",
				formaPagoId: 1,
				formaPagoNombre: null,
				turnoCajaId: null,
				usuarioId: 1,
				creadoEn: "2026-09-01T11:00:00",
			},
		],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	})),
	apiCrearGasto: vi.fn(),
	apiActualizarGasto: vi.fn(),
	apiEliminarGasto: vi.fn(),
	apiCrearIngreso: vi.fn(),
	apiActualizarIngreso: vi.fn(),
	apiEliminarIngreso: vi.fn(),
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiProveedores: vi.fn(async () => [
		{ proveedorId: 1, razonSocial: "Proveedor Uno" },
	]),
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
	return render(<GastosPage />, { wrapper });
}

afterEach(() => {
	useAuthStore.setState({ autenticado: false, usuario: null });
	vi.clearAllMocks();
});

describe("GastosPage (smoke)", () => {
	it("renderiza título, tabs y la fila de gasto", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Gastos de caja" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Gastos", exact: true }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Ingresos", exact: true }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /registrar gasto/i }),
		).toBeInTheDocument();
		expect(await screen.findByText("Flete de pedido")).toBeInTheDocument();
	});

	it("cambiar al tab Ingresos muestra el título y la fila de ingreso", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Ingresos", exact: true }));
		expect(
			await screen.findByRole("heading", { name: "Ingresos de caja" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Venta de chatarra")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /registrar ingreso/i }),
		).toBeInTheDocument();
	});

	it("'Registrar gasto' abre el diálogo con el formulario", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: /registrar gasto/i }));
		const dialogo = await screen.findByRole("dialog");
		expect(
			within(dialogo).getByRole("heading", { name: "Registrar gasto" }),
		).toBeInTheDocument();
		expect(within(dialogo).getByLabelText(/tipo de gasto/i)).toBeInTheDocument();
		expect(within(dialogo).getByLabelText(/monto/i)).toBeInTheDocument();
	});
});

function comoAdmin() {
	useAuthStore.setState({
		autenticado: true,
		usuario: { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] },
	});
}

const GASTO_CONGELADO = {
	gastoId: 2,
	folio: "G-0002",
	tipoGastoId: 99,
	tipoGastoNombre: null,
	descripcion: "Renta local",
	monto: 2000,
	fechaGasto: "2026-09-02",
	formaPagoId: 99,
	formaPagoNombre: null,
	proveedorId: 5,
	turnoCajaId: 7,
	facturaUuid: null,
	usuarioId: 1,
	creadoEn: "2026-09-02T10:00:00",
};

describe("GastosPage (profundización)", () => {
	it("registra un gasto válido con proveedor y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearGasto).mockResolvedValueOnce({ gastoId: 9 } as never);
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: /registrar gasto/i }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar gasto",
		});
		await user.selectOptions(
			within(dialogo).getByLabelText(/tipo de gasto/i),
			"1",
		);
		await user.type(within(dialogo).getByLabelText(/^Monto/), "750");
		await user.type(
			within(dialogo).getByLabelText(/Descripción/),
			"Flete de pedido",
		);
		await user.selectOptions(
			within(dialogo).getByLabelText(/Proveedor/),
			"1",
		);
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar gasto/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearGasto)).toHaveBeenCalledWith(
				expect.objectContaining({
					tipoGastoId: 1,
					descripcion: "Flete de pedido",
					monto: 750,
					proveedorId: 1,
				}),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Gasto registrado"),
			}),
		);
	});

	it("valida tipo, monto y descripción del gasto", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: /registrar gasto/i }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar gasto",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar gasto/ }),
		);
		expect(
			await within(dialogo).findByText("Completa tipo, monto y descripción."),
		).toBeInTheDocument();
		expect(apiCrearGasto).not.toHaveBeenCalled();
	});

	it("muestra toast si crear el gasto falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearGasto).mockRejectedValueOnce(new Error("monto inválido"));
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: /registrar gasto/i }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar gasto",
		});
		await user.selectOptions(
			within(dialogo).getByLabelText(/tipo de gasto/i),
			"1",
		);
		await user.type(within(dialogo).getByLabelText(/^Monto/), "100");
		await user.type(
			within(dialogo).getByLabelText(/Descripción/),
			"Luz",
		);
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar gasto/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("monto inválido"),
				}),
			),
		);
	});

	it("como admin edita el gasto precargado y guarda cambios", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarGasto).mockResolvedValueOnce({ gastoId: 1 } as never);
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Editar" }));
		const dialogo = await screen.findByRole("dialog", { name: "Editar gasto" });
		expect(within(dialogo).getByLabelText(/^Monto/)).toHaveValue(500);
		await user.click(
			within(dialogo).getByRole("button", { name: /Guardar cambios/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiActualizarGasto)).toHaveBeenCalledWith(
				1,
				expect.objectContaining({ descripcion: "Flete de pedido", monto: 500 }),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Gasto actualizado"),
			}),
		);
	});

	it("muestra toast si actualizar el gasto falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarGasto).mockRejectedValueOnce(
			new Error("no autorizado"),
		);
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Editar" }));
		const dialogo = await screen.findByRole("dialog", { name: "Editar gasto" });
		await user.click(
			within(dialogo).getByRole("button", { name: /Guardar cambios/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("no autorizado") }),
			),
		);
	});

	it("como admin elimina el gasto con confirmación", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiEliminarGasto).mockResolvedValueOnce({ ok: true } as never);
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Eliminar" }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Eliminar gasto",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Eliminar" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiEliminarGasto)).toHaveBeenCalledWith(1),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Gasto eliminado"),
			}),
		);
	});

	it("muestra toast si eliminar el gasto falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiEliminarGasto).mockRejectedValueOnce(
			new Error("ligado a turno"),
		);
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Eliminar" }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Eliminar gasto",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Eliminar" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("ligado a turno"),
				}),
			),
		);
	});

	it("deshabilita editar y eliminar en gastos ligados a turno", async () => {
		comoAdmin();
		vi.mocked(apiGastos).mockResolvedValueOnce({
			success: true,
			data: [GASTO_CONGELADO],
			meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("Renta local")).toBeInTheDocument();
		expect(screen.getByText("Tipo 99")).toBeInTheDocument();
		expect(screen.getByText("Forma 99")).toBeInTheDocument();
		expect(screen.getByText("#5")).toBeInTheDocument();
		expect(
			screen.getByTitle("Ligado a un turno de caja: no modificable"),
		).toBeDisabled();
		expect(
			screen.getByTitle("Ligado a un turno de caja: no eliminable"),
		).toBeDisabled();
	});

	it("sin rol admin no muestra acciones de gasto", async () => {
		renderPage();
		await screen.findByText("Flete de pedido");
		expect(
			screen.queryByRole("button", { name: "Editar" }),
		).not.toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: "Eliminar" }),
		).not.toBeInTheDocument();
	});

	it("registra un ingreso válido y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearIngreso).mockResolvedValueOnce({ ingresoOtroId: 9 } as never);
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Ingresos", exact: true }));
		await screen.findByText("Venta de chatarra");
		await user.click(
			screen.getByRole("button", { name: /registrar ingreso/i }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar ingreso",
		});
		await user.type(
			within(dialogo).getByLabelText(/Concepto/),
			"Venta de chatarra",
		);
		await user.type(within(dialogo).getByLabelText(/^Monto/), "300");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar ingreso/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCrearIngreso)).toHaveBeenCalledWith(
				expect.objectContaining({ concepto: "Venta de chatarra", monto: 300 }),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Ingreso registrado"),
			}),
		);
	});

	it("valida concepto y monto del ingreso", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Ingresos", exact: true }));
		await screen.findByText("Venta de chatarra");
		await user.click(
			screen.getByRole("button", { name: /registrar ingreso/i }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar ingreso",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar ingreso/ }),
		);
		expect(
			await within(dialogo).findByText("Completa concepto y monto."),
		).toBeInTheDocument();
		expect(apiCrearIngreso).not.toHaveBeenCalled();
	});

	it("muestra toast si crear el ingreso falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearIngreso).mockRejectedValueOnce(new Error("fallo"));
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Ingresos", exact: true }));
		await screen.findByText("Venta de chatarra");
		await user.click(
			screen.getByRole("button", { name: /registrar ingreso/i }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar ingreso",
		});
		await user.type(
			within(dialogo).getByLabelText(/Concepto/),
			"Venta de chatarra",
		);
		await user.type(within(dialogo).getByLabelText(/^Monto/), "300");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar ingreso/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("fallo") }),
			),
		);
	});

	it("como admin edita y elimina el ingreso", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarIngreso).mockResolvedValueOnce({
			ingresoOtroId: 1,
		} as never);
		vi.mocked(apiEliminarIngreso).mockResolvedValueOnce({ ok: true } as never);
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Ingresos", exact: true }));
		await screen.findByText("Venta de chatarra");
		await user.click(screen.getByRole("button", { name: "Editar" }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Editar ingreso",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: /Guardar cambios/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiActualizarIngreso)).toHaveBeenCalledWith(
				1,
				expect.objectContaining({ concepto: "Venta de chatarra" }),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Ingreso actualizado"),
			}),
		);
		await user.click(screen.getByRole("button", { name: "Eliminar" }));
		const borrar = await screen.findByRole("dialog", {
			name: "Eliminar ingreso",
		});
		await user.click(within(borrar).getByRole("button", { name: "Eliminar" }));
		await waitFor(() =>
			expect(vi.mocked(apiEliminarIngreso)).toHaveBeenCalledWith(1),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Ingreso eliminado"),
			}),
		);
	});

	it("quita el rango con Todos y recarga sin fechas", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: "Todos" }));
		await waitFor(() => {
			const ultima = vi.mocked(apiGastos).mock.calls.at(-1) as unknown[];
			expect(ultima[2]).toBeUndefined();
		});
	});

	it("pagina los gastos cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiGastos).mockResolvedValue({
			success: true,
			data: [
				{
					gastoId: 1,
					folio: "G-0001",
					tipoGastoId: 14,
					tipoGastoNombre: null,
					descripcion: "Flete de pedido",
					monto: 500,
					fechaGasto: "2026-09-01",
					formaPagoId: 1,
					formaPagoNombre: null,
					proveedorId: null,
					turnoCajaId: null,
					facturaUuid: null,
					usuarioId: 1,
					creadoEn: "2026-09-01T10:00:00",
				},
			],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiGastos)).toHaveBeenCalledWith(
				0,
				15,
				expect.any(String),
				expect.any(String),
			),
		);
		expect(vi.mocked(apiGastos).mock.calls.at(-1)?.[0]).toBe(1);
	});

	it("exporta gastos e ingresos visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Flete de pedido");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^gastos-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
		await user.click(screen.getByRole("button", { name: "Ingresos", exact: true }));
		await screen.findByText("Venta de chatarra");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(2));
		expect(String(writeFile.mock.calls[1][1])).toMatch(
			/^ingresos-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si los gastos fallan al cargar", async () => {
		vi.mocked(apiGastos).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});

	it("muestra toast si los ingresos fallan al cargar", async () => {
		vi.mocked(apiIngresosOtros).mockRejectedValueOnce(new Error("caído"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("caído") }),
			),
		);
	});
});
