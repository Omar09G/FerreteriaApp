import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Swal from "sweetalert2";

vi.mock("@/lib/api/venta", () => ({
	apiRentas: vi.fn(),
	apiCrearRenta: vi.fn(),
	apiDevolucionRenta: vi.fn(),
	apiCancelarRenta: vi.fn(),
	apiEnviarRentasInforme: vi.fn(),
	apiEstadoRentasInforme: vi.fn(),
}));
vi.mock("@/lib/api/catalogo", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiAlmacenes: vi.fn(),
	apiClientes: vi.fn(),
	apiProductos: vi.fn(),
}));
vi.mock("@/lib/api/caja", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiCajas: vi.fn(),
	apiTurnoActual: vi.fn(),
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
	apiCancelarRenta,
	apiCrearRenta,
	apiDevolucionRenta,
	apiEnviarRentasInforme,
	apiEstadoRentasInforme,
	apiRentas,
} from "@/lib/api/venta";
import { apiAlmacenes, apiClientes, apiProductos } from "@/lib/api/catalogo";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import { useAuthStore } from "@/store/auth";
import RentasPage from "@/features/ventas/RentasPage";
import {
	ALMACEN,
	CAJA,
	CLIENTE,
	PRODUCTO,
	RENTA,
	TURNO,
	pageOf,
	renderPagina,
} from "@/test/helpers/ventasPos";

const apiRentasMock = vi.mocked(apiRentas);
vi.mocked(apiCrearRenta);
const apiDevolucionRentaMock = vi.mocked(apiDevolucionRenta);
const apiCancelarRentaMock = vi.mocked(apiCancelarRenta);
const apiAlmacenesMock = vi.mocked(apiAlmacenes);
const apiClientesMock = vi.mocked(apiClientes);
vi.mocked(apiProductos);
const apiCajasMock = vi.mocked(apiCajas);
vi.mocked(apiTurnoActual);

beforeEach(() => {
	vi.clearAllMocks();
	apiRentasMock.mockResolvedValue(pageOf([RENTA]));
	apiAlmacenesMock.mockResolvedValue([ALMACEN]);
	apiClientesMock.mockResolvedValue(pageOf([CLIENTE]));
	apiCajasMock.mockResolvedValue([CAJA]);
	vi.mocked(apiTurnoActual).mockResolvedValue(TURNO);
	apiDevolucionRentaMock.mockResolvedValue({} as never);
	apiCancelarRentaMock.mockResolvedValue({} as never);
});

describe("RentasPage", () => {
	it("renderiza título, filtro y tabla con una renta", async () => {
		renderPagina(<RentasPage />);
		expect(screen.getByRole("heading", { name: "Rentas" })).toBeInTheDocument();
		expect(screen.getByLabelText("Estado")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nueva renta/ }),
		).toBeInTheDocument();
		expect(await screen.findByText("Rentas (1)")).toBeInTheDocument();
		expect(await screen.findByText("R-001")).toBeInTheDocument();
		expect(screen.getAllByText("ABIERTA").length).toBeGreaterThanOrEqual(1);
	});

	it("abre el formulario de nueva renta", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await user.click(screen.getByRole("button", { name: /Nueva renta/ }));
		expect(
			await screen.findByRole("dialog", { name: "Nueva renta" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Devolución esperada/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Buscar producto/)).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Registrar renta/ }),
		).toBeInTheDocument();
	});

	it("abre el detalle de la renta al hacer clic en Ver detalles", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Ver detalles" }),
		);
		expect(await screen.findByText("Traslado 7")).toBeInTheDocument();
		expect(screen.getByText("Costo/Día")).toBeInTheDocument();
		expect(screen.getByText("Martillo")).toBeInTheDocument();
	});

	it("muestra el botón Excel para exportar lo visible", async () => {
		renderPagina(<RentasPage />);
		await screen.findByText("R-001");
		expect(
			screen.getByRole("button", { name: /excel/i }),
		).toBeInTheDocument();
	});

	it("abre el diálogo de devolución y la registra con días cobrados", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Registrar devolución" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar devolución",
		});
		expect(
			within(dialogo).getByLabelText("Días cobrados de Martillo"),
		).toHaveValue(1);
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar devolución/ }),
		);
		expect(apiDevolucionRentaMock).toHaveBeenCalledWith(7, {
			detalles: [{ productoId: 10, diasCobrados: 1 }],
		});
	});

	it("pide confirmación y cancela la renta", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Cancelar renta" }),
		);
		expect(
			await screen.findByRole("dialog", { name: "Confirmar cancelación" }),
		).toBeInTheDocument();
		await user.click(
			screen.getByRole("button", { name: "Sí, cancelar renta" }),
		);
		expect(apiCancelarRentaMock).toHaveBeenCalledWith(7);
	});
});

describe("RentasPage (profundización)", () => {
	it("filtra por estado y muestra Limpiar", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await screen.findByText("R-001");
		await user.selectOptions(screen.getByLabelText("Estado"), "ABIERTA");
		expect(
			await screen.findByRole("button", { name: "Limpiar" }),
		).toBeInTheDocument();
		expect(apiRentasMock).toHaveBeenLastCalledWith(
			expect.objectContaining({ estado: "ABIERTA", page: 0 }),
		);
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		expect(
			screen.queryByRole("button", { name: "Limpiar" }),
		).not.toBeInTheDocument();
	});

	it("registra una renta válida con partida y muestra éxito", async () => {
		const user = userEvent.setup();
		const apiCrearMock = vi.mocked(apiCrearRenta);
		apiCrearMock.mockResolvedValue({ ...RENTA, rentaId: 9 } as never);
		vi.mocked(apiProductos).mockResolvedValue(pageOf([PRODUCTO]) as never);
		renderPagina(<RentasPage />);
		await user.click(screen.getByRole("button", { name: /Nueva renta/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva renta" });
		await user.selectOptions(within(dialogo).getByLabelText(/Cliente/), "1");
		await user.selectOptions(within(dialogo).getByLabelText(/Almacén/), "1");
		await user.selectOptions(within(dialogo).getByLabelText(/Caja/), "1");
		await within(dialogo).findByText(/Turno abierto/);
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo{enter}",
		);
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		const costo = await within(dialogo).findByLabelText(
			"Costo por día de Martillo",
		);
		await user.clear(costo);
		await user.type(costo, "150");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar renta/ }),
		);
		await waitFor(() =>
			expect(apiCrearMock).toHaveBeenCalledWith(
				expect.objectContaining({
					clienteId: 1,
					almacenId: 1,
					cajaId: 1,
					detalles: [{ productoId: 10, cantidad: 1, costoDia: 150 }],
				}),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Renta registrada"),
			}),
		);
	});

	it("valida cliente, almacén, caja y partida antes de registrar", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await user.click(screen.getByRole("button", { name: /Nueva renta/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva renta" });
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar renta/ }),
		);
		expect(
			await within(dialogo).findByText(/caja con turno abierto/),
		).toBeInTheDocument();
		expect(apiCrearRenta).not.toHaveBeenCalled();
	});

	it("muestra toast si crear la renta falla", async () => {
		const user = userEvent.setup();
		const apiCrearMock = vi.mocked(apiCrearRenta);
		apiCrearMock.mockRejectedValueOnce(new Error("sin stock"));
		vi.mocked(apiProductos).mockResolvedValue(pageOf([PRODUCTO]) as never);
		renderPagina(<RentasPage />);
		await user.click(screen.getByRole("button", { name: /Nueva renta/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva renta" });
		await user.selectOptions(within(dialogo).getByLabelText(/Cliente/), "1");
		await user.selectOptions(within(dialogo).getByLabelText(/Almacén/), "1");
		await user.selectOptions(within(dialogo).getByLabelText(/Caja/), "1");
		await within(dialogo).findByText(/Turno abierto/);
		await user.type(
			within(dialogo).getByLabelText(/Buscar producto/),
			"Martillo{enter}",
		);
		await user.click(
			await within(dialogo).findByRole("button", { name: /Martillo/ }),
		);
		await within(dialogo).findByLabelText("Costo por día de Martillo");
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar renta/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin stock") }),
			),
		);
	});

	it("valida días no negativos en la devolución", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Registrar devolución" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar devolución",
		});
		const dias = within(dialogo).getByLabelText("Días cobrados de Martillo");
		fireEvent.change(dias, { target: { value: "-2" } });
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar devolución/ }),
		);
		expect(
			await within(dialogo).findByText(/no pueden ser negativos/),
		).toBeInTheDocument();
		expect(apiDevolucionRentaMock).not.toHaveBeenCalled();
	});

	it("muestra toast de éxito al registrar la devolución", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Registrar devolución" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar devolución",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar devolución/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("Devolución registrada"),
				}),
			),
		);
	});

	it("muestra toast si la devolución falla", async () => {
		const user = userEvent.setup();
		apiDevolucionRentaMock.mockRejectedValueOnce(new Error("fuera de plazo"));
		renderPagina(<RentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Registrar devolución" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Registrar devolución",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: /Registrar devolución/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("fuera de plazo"),
				}),
			),
		);
	});

	it("muestra toast de éxito al cancelar y de error si falla", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Cancelar renta" }),
		);
		await user.click(
			await screen.findByRole("button", { name: "Sí, cancelar renta" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("Renta cancelada"),
				}),
			),
		);
	});

	it("muestra toast si cancelar la renta falla", async () => {
		const user = userEvent.setup();
		apiCancelarRentaMock.mockRejectedValueOnce(new Error("ya devuelta"));
		renderPagina(<RentasPage />);
		await user.click(
			await screen.findByRole("button", { name: "Cancelar renta" }),
		);
		await user.click(
			await screen.findByRole("button", { name: "Sí, cancelar renta" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("ya devuelta") }),
			),
		);
	});

	it("quita el rango con Todos y recarga sin fechas", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await screen.findByText("R-001");
		await user.click(screen.getByRole("button", { name: "Todos" }));
		await waitFor(() => {
			const ultima = apiRentasMock.mock.calls.at(-1)?.[0] as Record<
				string,
				unknown
			>;
			expect(ultima.desde).toBeUndefined();
		});
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		apiRentasMock.mockResolvedValue({
			success: true,
			data: [RENTA],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPagina(<RentasPage />);
		await screen.findByText("R-001");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(apiRentasMock).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta las rentas visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPagina(<RentasPage />);
		await screen.findByText("R-001");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^rentas-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		apiRentasMock.mockRejectedValueOnce(new Error("sin conexión"));
		renderPagina(<RentasPage />);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});

describe("RentasPage (recordatorio manual)", () => {
	function comoAdmin() {
		useAuthStore.setState({
			autenticado: true,
			usuario: { roles: ["GERENTE"] } as never,
		});
	}

	function comoSinRol() {
		useAuthStore.setState({ autenticado: false, usuario: null });
	}

	it("oculta el botón sin rol GERENTE/ADMINISTRADOR", async () => {
		comoSinRol();
		renderPagina(<RentasPage />);
		await screen.findByRole("heading", { name: "Rentas" });
		expect(
			screen.queryByRole("button", { name: /recordatorio/i }),
		).not.toBeInTheDocument();
	});

	it("envía directo cuando hoy aún no se envió", async () => {
		const user = userEvent.setup();
		comoAdmin();
		vi.mocked(apiEstadoRentasInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			yaEnviado: false,
			estado: null,
			enviadoEn: null,
		});
		vi.mocked(apiEnviarRentasInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			destinatarios: 2,
			emailsEnviados: 2,
			whatsappsEnviados: 2,
			vencidas: 1,
			proximas: 1,
		});
		renderPagina(<RentasPage />);
		await screen.findByRole("heading", { name: "Rentas" });
		await user.click(screen.getByRole("button", { name: /recordatorio/i }));
		await waitFor(() => {
			expect(apiEstadoRentasInforme).toHaveBeenCalledOnce();
			expect(apiEnviarRentasInforme).toHaveBeenCalledOnce();
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
		vi.mocked(apiEstadoRentasInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			yaEnviado: true,
			estado: "ENVIADA",
			enviadoEn: "2026-10-05T09:10:00",
		});
		vi.mocked(apiEnviarRentasInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			destinatarios: 1,
			emailsEnviados: 1,
			whatsappsEnviados: 0,
			vencidas: 1,
			proximas: 0,
		});
		renderPagina(<RentasPage />);
		await screen.findByRole("heading", { name: "Rentas" });
		await user.click(screen.getByRole("button", { name: /recordatorio/i }));
		expect(await screen.findByText("Recordatorio ya enviado")).toBeInTheDocument();
		expect(apiEnviarRentasInforme).not.toHaveBeenCalled();
		await user.click(screen.getByRole("button", { name: /reenviar/i }));
		await waitFor(() => {
			expect(apiEnviarRentasInforme).toHaveBeenCalledOnce();
		});
	});
});
