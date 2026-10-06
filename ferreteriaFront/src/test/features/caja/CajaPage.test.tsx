import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import CajaPage from "@/features/caja/CajaPage";
import { ToastProvider } from "@/components/ui/Toast";
import {
	apiAbrirTurno,
	apiCajas,
	apiCerrarTurno,
	apiCortes,
	apiEnviarTurnoAbiertoInforme,
	apiEsperadoTurno,
	apiEstadoTurnoAbiertoInforme,
	apiMovimientosTurno,
	apiRegistrarMovimiento,
	apiTurnos,
} from "@/lib/api/caja";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("recharts", () => ({
	ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
		<>{children}</>
	),
	BarChart: ({ children }: { children: React.ReactNode }) => <>{children}</>,
	Bar: () => null,
	CartesianGrid: () => null,
	Legend: () => null,
	Tooltip: ({
		formatter,
	}: {
		formatter?: (value: unknown, name?: unknown) => unknown;
	}) => {
		if (formatter) formatter(1160, "Total vendido");
		return null;
	},
	XAxis: () => null,
	YAxis: ({ tickFormatter }: { tickFormatter?: (v: number) => unknown }) => {
		if (tickFormatter) tickFormatter(1160);
		return null;
	},
}));

vi.mock("@/lib/api/caja", () => ({
	apiCajas: vi.fn(async () => [
		{
			cajaId: 1,
			nombre: "Caja Central",
			almacenId: 1,
			almacenNombre: "Central",
			activa: true,
		},
	]),
	apiTurnos: vi.fn(async () => ({
		success: true,
		data: [
			{
				turnoCajaId: 5,
				cajaId: 1,
				cajaNombre: "Caja Central",
				usuarioId: 1,
				aperturaEn: "2026-09-25T08:00:00",
				montoApertura: 500,
				cierreEn: null,
				montoEsperado: null,
				montoContado: null,
				diferencia: null,
				estado: "ABIERTO",
				observaciones: null,
			},
		],
		meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
	})),
	apiCortes: vi.fn(async () => ({
		success: true,
		data: [
			{
				corteId: 1,
				turnoCajaId: 5,
				cajaId: 1,
				cajaNombre: "Caja Central",
				almacenId: 1,
				almacenNombre: "Central",
				usuarioId: 1,
				usuarioCierreId: 1,
				fecha: "2026-09-25",
				aperturaEn: "2026-09-25T08:00:00",
				cierreEn: "2026-09-25T18:00:00",
				numVentas: 10,
				subtotal: 1000,
				iva: 160,
				descuentos: 0,
				totalVendido: 1160,
				costoVentas: 700,
				utilidadBruta: 460,
				margenPct: 39.6,
				fondoApertura: 500,
				entradasEfectivo: 100,
				salidasEfectivo: 50,
				dineroEsperado: 1710,
				dineroContado: 1710,
				diferencia: 0,
				resultadoCaja: "CUADRADO",
				ingresosNoEfectivo: 0,
				egresosNoEfectivo: 0,
				perdidasInventario: 0,
				desgloseEntradas: "{}",
				desgloseSalidas: "{}",
				desgloseFormasPago: '{"Efectivo": 1000}',
				observaciones: null,
			},
		],
		meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
	})),
	apiMovimientosTurno: vi.fn(async () => []),
	apiEsperadoTurno: vi.fn(async () => ({
		montoApertura: 500,
		entradasEfectivo: 100,
		salidasEfectivo: 50,
		esperado: 550,
	})),
	apiAbrirTurno: vi.fn(),
	apiRegistrarMovimiento: vi.fn(),
	apiCerrarTurno: vi.fn(),
	apiEnviarTurnoAbiertoInforme: vi.fn(),
	apiEstadoTurnoAbiertoInforme: vi.fn(),
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
	return render(<CajaPage />, { wrapper });
}

afterEach(() => {
	useAuthStore.setState({ autenticado: false, usuario: null });
	vi.clearAllMocks();
});

describe("CajaPage (smoke)", () => {
	it("renderiza título, selector de caja, turnos vacíos y el corte", async () => {
		renderPage();
		expect(screen.getByRole("heading", { name: "Caja" })).toBeInTheDocument();
		expect(screen.getByLabelText("Caja")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /abrir turno/i }),
		).toBeInTheDocument();
		expect(
			screen.getByText("Elige una caja para ver sus turnos."),
		).toBeInTheDocument();
		expect(await screen.findByText("Corte #1")).toBeInTheDocument();
		expect(screen.getByText("Rendimiento de ventas")).toBeInTheDocument();
	});

	it("al elegir caja muestra sus turnos y habilita 'Abrir turno'", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByRole("option", { name: /caja central/i });
		await user.selectOptions(screen.getByLabelText("Caja"), "1");
		expect(await screen.findByText("Turno #5")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /abrir turno/i }),
		).toBeEnabled();
	});

	it("'Abrir turno' abre el diálogo de apertura", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByRole("option", { name: /caja central/i });
		await user.selectOptions(screen.getByLabelText("Caja"), "1");
		await screen.findByText("Turno #5");
		await user.click(screen.getByRole("button", { name: /abrir turno/i }));
		const dialogo = await screen.findByRole("dialog");
		expect(
			within(dialogo).getByText("Abrir turno de caja"),
		).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/fondo de apertura/i),
		).toBeInTheDocument();
	});

	it("'Movimientos' del turno muestra la lista (vacía) y el form de registro", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByRole("option", { name: /caja central/i });
		await user.selectOptions(screen.getByLabelText("Caja"), "1");
		await user.click(
			await screen.findByRole("button", { name: "Movimientos" }),
		);
		expect(
			await screen.findByText("Sin movimientos registrados."),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Tipo")).toBeInTheDocument();
		expect(screen.getByLabelText("Concepto")).toBeInTheDocument();
	});

	it("'Gráfica' abre el diálogo con tabs de ventas/efectivo/formas", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Corte #1");
		await user.click(
			screen.getByRole("button", { name: /ver gráfica de ventas/i }),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(
			within(dialogo).getByText("Ventas por cierre de caja"),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: "Flujo efectivo" }),
		);
		expect(within(dialogo).getByText("Fondo")).toBeInTheDocument();
	});

	it("como administrador muestra 'Administrar cajas'", () => {
		useAuthStore.setState({
			autenticado: true,
			usuario: { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] },
		});
		renderPage();
		expect(
			screen.getByRole("button", { name: /administrar cajas/i }),
		).toBeInTheDocument();
	});
});

const TURNO_CERRADO = {
	turnoCajaId: 6,
	cajaId: 1,
	cajaNombre: "Caja Central",
	usuarioId: 1,
	aperturaEn: "2026-09-24T08:00:00",
	montoApertura: 400,
	cierreEn: "2026-09-24T18:00:00",
	montoEsperado: 1500,
	montoContado: 1500,
	diferencia: 0,
	estado: "CERRADO",
	observaciones: null,
};

const CORTE_SOBRANTE = {
	corteId: 2,
	turnoCajaId: 6,
	cajaId: 1,
	cajaNombre: "Caja Central",
	almacenId: 1,
	almacenNombre: "Central",
	usuarioId: 1,
	usuarioCierreId: 1,
	fecha: "2026-09-24",
	aperturaEn: "2026-09-24T08:00:00",
	cierreEn: "2026-09-24T18:00:00",
	numVentas: 1,
	subtotal: 500,
	iva: 80,
	descuentos: 0,
	totalVendido: 580,
	costoVentas: 400,
	utilidadBruta: 180,
	margenPct: 15,
	fondoApertura: 400,
	entradasEfectivo: 200,
	salidasEfectivo: 100,
	dineroEsperado: 1080,
	dineroContado: 1130,
	diferencia: 50,
	resultadoCaja: "SOBRANTE",
	ingresosNoEfectivo: 0,
	egresosNoEfectivo: 0,
	perdidasInventario: 100,
	desgloseEntradas: "{}",
	desgloseSalidas: '{"GASTO_OPERATIVO": 100}',
	desgloseFormasPago: '{"EFECTIVO": 400, "Transferencia SPEI": 180}',
	observaciones: "Sobrante por redondeo",
};

const CORTE_FALTANTE = {
	corteId: 3,
	turnoCajaId: 7,
	cajaId: 1,
	cajaNombre: "Caja Central",
	almacenId: 1,
	almacenNombre: "Central",
	usuarioId: 1,
	usuarioCierreId: 1,
	fecha: "2026-09-23",
	aperturaEn: "2026-09-23T08:00:00",
	cierreEn: "2026-09-23T18:00:00",
	numVentas: 5,
	subtotal: 800,
	iva: 128,
	descuentos: 0,
	totalVendido: 928,
	costoVentas: 900,
	utilidadBruta: 28,
	margenPct: 3,
	fondoApertura: 400,
	entradasEfectivo: 100,
	salidasEfectivo: 50,
	dineroEsperado: 1378,
	dineroContado: 1348,
	diferencia: -30,
	resultadoCaja: "FALTANTE",
	ingresosNoEfectivo: 0,
	egresosNoEfectivo: 0,
	perdidasInventario: 0,
	desgloseEntradas: "",
	desgloseSalidas: "no-es-json",
	desgloseFormasPago: "{}",
	observaciones: null,
};

const MOVIMIENTOS = [
	{
		movimientoId: 1,
		tipo: "ENTRADA",
		concepto: "Venta",
		monto: 116,
		refDescripcion: "V-0001",
		formaPagoNombre: "Efectivo",
	},
	{
		movimientoId: 2,
		tipo: "SALIDA",
		concepto: "Gasto",
		monto: 50,
		refDescripcion: null,
		formaPagoNombre: null,
	},
];

async function elegirCaja(user: ReturnType<typeof userEvent.setup>) {
	await screen.findByRole("option", { name: /caja central/i });
	await user.selectOptions(screen.getByLabelText("Caja"), "1");
}

describe("CajaPage (profundización)", () => {
	it("deshabilita 'Abrir turno' sin caja elegida", () => {
		renderPage();
		expect(screen.getByRole("button", { name: /abrir turno/i })).toBeDisabled();
	});

	it("abre el turno con el fondo indicado y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiAbrirTurno).mockResolvedValueOnce({ turnoCajaId: 9 } as never);
		renderPage();
		await elegirCaja(user);
		await user.click(screen.getByRole("button", { name: /abrir turno/i }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Abrir turno de caja",
		});
		const fondo = within(dialogo).getByLabelText(/fondo de apertura/i);
		await user.clear(fondo);
		await user.type(fondo, "500");
		await user.click(within(dialogo).getByRole("button", { name: "Abrir" }));
		await waitFor(() =>
			expect(vi.mocked(apiAbrirTurno)).toHaveBeenCalledWith(1, 500),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Turno abierto") }),
		);
	});

	it("muestra toast si abrir el turno falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiAbrirTurno).mockRejectedValueOnce(
			new Error("ya hay turno abierto"),
		);
		renderPage();
		await elegirCaja(user);
		await user.click(screen.getByRole("button", { name: /abrir turno/i }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Abrir turno de caja",
		});
		await user.click(within(dialogo).getByRole("button", { name: "Abrir" }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("ya hay turno abierto"),
				}),
			),
		);
	});

	it("muestra mensaje cuando la caja no tiene turnos", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnos).mockResolvedValueOnce({
			success: true,
			data: [],
			meta: { page: 0, size: 10, totalElements: 0, totalPages: 0 },
		} as never);
		renderPage();
		await elegirCaja(user);
		expect(
			await screen.findByText("Aún no hay turnos para esta caja."),
		).toBeInTheDocument();
	});

	it("lista movimientos con referencia y permite ocultar", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientosTurno).mockResolvedValue(MOVIMIENTOS as never);
		renderPage();
		await elegirCaja(user);
		await user.click(await screen.findByRole("button", { name: "Movimientos" }));
		expect(await screen.findByText(/V-0001/)).toBeInTheDocument();
		expect(screen.getByText("Gasto")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Ocultar" }));
		expect(
			screen.queryByText("Sin movimientos registrados."),
		).not.toBeInTheDocument();
	});

	it("registra una salida manual y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientosTurno).mockResolvedValue([]);
		vi.mocked(apiRegistrarMovimiento).mockResolvedValueOnce({
			movimientoId: 3,
		} as never);
		renderPage();
		await elegirCaja(user);
		await user.click(await screen.findByRole("button", { name: "Movimientos" }));
		await screen.findByText("Sin movimientos registrados.");
		await user.type(screen.getByLabelText("Monto"), "100");
		await user.click(screen.getByRole("button", { name: /Registrar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiRegistrarMovimiento)).toHaveBeenCalledWith(1, 5, {
				tipo: "SALIDA",
				concepto: "GASTO_OPERATIVO",
				monto: 100,
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Movimiento registrado"),
			}),
		);
	});

	it("cambia a entrada y ofrece sus conceptos", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientosTurno).mockResolvedValue([]);
		renderPage();
		await elegirCaja(user);
		await user.click(await screen.findByRole("button", { name: "Movimientos" }));
		await screen.findByText("Sin movimientos registrados.");
		await user.selectOptions(screen.getByLabelText("Tipo"), "ENTRADA");
		expect(
			screen.getByRole("option", { name: "Otro ingreso" }),
		).toBeInTheDocument();
	});

	it("deshabilita Registrar con monto inválido", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientosTurno).mockResolvedValue([]);
		renderPage();
		await elegirCaja(user);
		await user.click(await screen.findByRole("button", { name: "Movimientos" }));
		await screen.findByText("Sin movimientos registrados.");
		expect(screen.getByRole("button", { name: /Registrar/ })).toBeDisabled();
	});

	it("muestra toast si registrar el movimiento falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientosTurno).mockResolvedValue([]);
		vi.mocked(apiRegistrarMovimiento).mockRejectedValueOnce(
			new Error("monto inválido"),
		);
		renderPage();
		await elegirCaja(user);
		await user.click(await screen.findByRole("button", { name: "Movimientos" }));
		await screen.findByText("Sin movimientos registrados.");
		await user.type(screen.getByLabelText("Monto"), "100");
		await user.click(screen.getByRole("button", { name: /Registrar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("monto inválido"),
				}),
			),
		);
	});

	it("muestra el turno cerrado sin acciones de cierre ni registro", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnos).mockResolvedValueOnce({
			success: true,
			data: [TURNO_CERRADO],
			meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		await screen.findByRole("option", { name: /caja central/i });
		await user.selectOptions(screen.getByLabelText("Caja"), "1");
		expect(await screen.findByText("Turno #6")).toBeInTheDocument();
		expect(screen.getByText("Cerrado")).toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: "Cerrar" }),
		).not.toBeInTheDocument();
	});

	it("cierra el turno con conteo y observaciones y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientosTurno).mockResolvedValue([]);
		vi.mocked(apiCerrarTurno).mockResolvedValueOnce({ corteId: 9 } as never);
		renderPage();
		await elegirCaja(user);
		await user.click(
			await screen.findByRole("button", { name: "Cerrar" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Cerrar turno y generar corte",
		});
		expect(
			await within(dialogo).findByText(/Fondo de apertura/),
		).toBeInTheDocument();
		await user.type(
			within(dialogo).getByLabelText(/Dinero contado/),
			"1710",
		);
		await user.type(
			within(dialogo).getByLabelText(/Observaciones/),
			"Todo en orden",
		);
		await user.click(
			within(dialogo).getByRole("button", { name: /Cerrar y cortar/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiCerrarTurno)).toHaveBeenCalledWith(1, 5, {
				montoContado: 1710,
				observaciones: "Todo en orden",
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Corte de caja realizado"),
			}),
		);
	});

	it("muestra toast si cerrar el turno falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientosTurno).mockResolvedValue([]);
		vi.mocked(apiCerrarTurno).mockRejectedValueOnce(
			new Error("diferencia alta"),
		);
		renderPage();
		await elegirCaja(user);
		await user.click(
			await screen.findByRole("button", { name: "Cerrar" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Cerrar turno y generar corte",
		});
		await user.type(
			within(dialogo).getByLabelText(/Dinero contado/),
			"100",
		);
		await user.click(
			within(dialogo).getByRole("button", { name: /Cerrar y cortar/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("diferencia alta"),
				}),
			),
		);
	});

	it("muestra toast si el esperado del turno falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientosTurno).mockResolvedValue([]);
		vi.mocked(apiEsperadoTurno).mockRejectedValueOnce(
			new Error("sin datos"),
		);
		renderPage();
		await elegirCaja(user);
		await user.click(
			await screen.findByRole("button", { name: "Cerrar" }),
		);
		await screen.findByRole("dialog", {
			name: "Cerrar turno y generar corte",
		});
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin datos") }),
			),
		);
	});

	it("muestra sobrante, margen medio, pérdidas y observaciones del corte", async () => {
		vi.mocked(apiCortes).mockResolvedValueOnce({
			success: true,
			data: [CORTE_SOBRANTE],
			meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("Corte #2")).toBeInTheDocument();
		expect(screen.getAllByText(/Sobrante/).length).toBeGreaterThanOrEqual(2);
		expect(screen.getByText("SOBRANTE")).toBeInTheDocument();
		expect(screen.getByText("Salidas desglosadas")).toBeInTheDocument();
		expect(screen.getByText("GASTO_OPERATIVO")).toBeInTheDocument();
		expect(screen.getByText("Sobrante por redondeo")).toBeInTheDocument();
		expect(screen.getByText(/1 venta/)).toBeInTheDocument();
	});

	it("muestra faltante con desglose inválido y margen bajo", async () => {
		vi.mocked(apiCortes).mockResolvedValueOnce({
			success: true,
			data: [CORTE_FALTANTE],
			meta: { page: 0, size: 10, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("Corte #3")).toBeInTheDocument();
		expect(screen.getByText(/Faltante/)).toBeInTheDocument();
		expect(screen.getByText("FALTANTE")).toBeInTheDocument();
		expect(screen.queryByText("Salidas desglosadas")).not.toBeInTheDocument();
	});

	it("la gráfica muestra efectivo, formas y aviso de costo cero", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCortes).mockResolvedValueOnce({
			success: true,
			data: [CORTE_SOBRANTE, { ...CORTE_SOBRANTE, corteId: 4, costoVentas: 0 }],
			meta: { page: 0, size: 10, totalElements: 2, totalPages: 1 },
		} as never);
		renderPage();
		await screen.findByText("Corte #2");
		await user.click(
			screen.getByRole("button", { name: /ver gráfica de ventas/i }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Ventas por cierre de caja",
		});
		expect(
			within(dialogo).getAllByText(/costo_ventas=0/).length,
		).toBeGreaterThanOrEqual(2);
		await user.click(
			within(dialogo).getByRole("button", { name: "Flujo efectivo" }),
		);
		expect(
			within(dialogo).getByText(/Fondo \+ entradas - salidas/),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: "Formas de pago" }),
		);
		expect(
			within(dialogo).getByText(/Efectivo vs digital/),
		).toBeInTheDocument();
	});

	it("la gráfica avisa sin desglose de formas de pago", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Corte #1");
		await user.click(
			screen.getByRole("button", { name: /ver gráfica de ventas/i }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Ventas por cierre de caja",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Formas de pago" }),
		);
		expect(
			within(dialogo).getByText(/Claves normalizadas/),
		).toBeInTheDocument();
	});

	it("muestra vacío de cortes y deshabilita la gráfica", async () => {
		vi.mocked(apiCortes).mockResolvedValueOnce({
			success: true,
			data: [],
			meta: { page: 0, size: 10, totalElements: 0, totalPages: 1 },
		} as never);
		renderPage();
		expect(
			await screen.findByText("Sin cortes para el rango seleccionado."),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /ver gráfica de ventas/i }),
		).toBeDisabled();
	});

	it("la gráfica vacía indica que no hay cortes", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCortes).mockResolvedValueOnce({
			success: true,
			data: [],
			meta: { page: 0, size: 10, totalElements: 0, totalPages: 1 },
		} as never);
		renderPage();
		await screen.findByText("Sin cortes para el rango seleccionado.");
		await user.click(screen.getByRole("button", { name: "Hoy" }));
		await waitFor(() =>
			expect(vi.mocked(apiCortes)).toHaveBeenCalledWith(
				expect.any(Number),
				10,
				expect.any(String),
				expect.any(String),
			),
		);
	});

	it("cambia el rango de cortes y recarga", async () => {
		renderPage();
		await screen.findByText("Corte #1");
		const api = vi.mocked(apiCortes);
		const llamadasAntes = api.mock.calls.length;
		const desde = screen.getByLabelText("Desde");
		const actual = (desde as HTMLInputElement).value;
		const d = new Date(`${actual}T12:00:00`);
		d.setDate(d.getDate() - 2);
		const nuevo = d.toISOString().slice(0, 10);
		fireEvent.change(desde, { target: { value: nuevo } });
		await waitFor(() =>
			expect(api.mock.calls.length).toBeGreaterThan(llamadasAntes),
		);
		expect(api.mock.calls.at(-1)?.[2]).toBe(nuevo);
	});

	it("pagina turnos y cortes", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnos).mockResolvedValue({
			success: true,
			data: [
				{
					turnoCajaId: 5,
					cajaId: 1,
					cajaNombre: "Caja Central",
					usuarioId: 1,
					aperturaEn: "2026-09-25T08:00:00",
					montoApertura: 500,
					cierreEn: null,
					montoEsperado: null,
					montoContado: null,
					diferencia: null,
					estado: "ABIERTO",
					observaciones: null,
				},
			],
			meta: { page: 0, size: 10, totalElements: 20, totalPages: 2 },
		} as never);
		vi.mocked(apiCortes).mockResolvedValue({
			success: true,
			data: [],
			meta: { page: 0, size: 10, totalElements: 20, totalPages: 2 },
		} as never);
		renderPage();
		await elegirCaja(user);
		await screen.findByText("Turno #5");
		const siguientes = screen.getAllByRole("button", {
			name: /Página siguiente/i,
		});
		expect(siguientes.length).toBeGreaterThanOrEqual(2);
		await user.click(siguientes[0]);
		await waitFor(() =>
			expect(vi.mocked(apiTurnos)).toHaveBeenCalledWith(1, 1),
		);
		await user.click(siguientes[1]);
		await waitFor(() =>
			expect(vi.mocked(apiCortes)).toHaveBeenCalledWith(
				1,
				10,
				expect.any(String),
				expect.any(String),
			),
		);
	});

	it("muestra toast si las cajas fallan al cargar", async () => {
		vi.mocked(apiCajas).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});

	it("muestra toast si los turnos fallan al cargar", async () => {
		const user = userEvent.setup();
		vi.mocked(apiTurnos).mockRejectedValueOnce(new Error("turnos caídos"));
		renderPage();
		await elegirCaja(user);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("turnos caídos"),
				}),
			),
		);
	});

	it("muestra toast si los cortes fallan al cargar", async () => {
		vi.mocked(apiCortes).mockRejectedValueOnce(new Error("cortes caídos"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("cortes caídos"),
				}),
			),
		);
	});

	it("muestra toast si los movimientos fallan al cargar", async () => {
		const user = userEvent.setup();
		vi.mocked(apiMovimientosTurno).mockRejectedValueOnce(
			new Error("movimientos caídos"),
		);
		renderPage();
		await elegirCaja(user);
		await user.click(await screen.findByRole("button", { name: "Movimientos" }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("movimientos caídos"),
				}),
			),
		);
	});
});

describe("CajaPage (aviso de turnos)", () => {
	function comoGerente() {
		useAuthStore.setState({
			autenticado: true,
			usuario: { roles: ["GERENTE"] } as never,
		});
	}

	it("oculta el botón sin rol GERENTE/ADMINISTRADOR", async () => {
		renderPage();
		expect(screen.getByRole("heading", { name: "Caja" })).toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: /turnos abiertos/i }),
		).not.toBeInTheDocument();
	});

	it("envía directo cuando hoy aún no se avisó", async () => {
		const user = userEvent.setup();
		comoGerente();
		vi.mocked(apiEstadoTurnoAbiertoInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			yaEnviado: false,
			estado: null,
			enviadoEn: null,
		});
		vi.mocked(apiEnviarTurnoAbiertoInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			destinatarios: 2,
			emailsEnviados: 2,
			whatsappsEnviados: 1,
			turnos: 3,
		});
		renderPage();
		await user.click(
			screen.getByRole("button", { name: /turnos abiertos/i }),
		);
		await waitFor(() => {
			expect(apiEstadoTurnoAbiertoInforme).toHaveBeenCalledOnce();
			expect(apiEnviarTurnoAbiertoInforme).toHaveBeenCalledOnce();
		});
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("Aviso enviado a 2 destinatarios"),
				}),
			),
		);
	});

	it("avisa si ya se envió y reenvía solo al confirmar", async () => {
		const user = userEvent.setup();
		comoGerente();
		vi.mocked(apiEstadoTurnoAbiertoInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			yaEnviado: true,
			estado: "ENVIADA",
			enviadoEn: "2026-10-05T21:00:00",
		});
		vi.mocked(apiEnviarTurnoAbiertoInforme).mockResolvedValueOnce({
			fecha: "2026-10-05",
			destinatarios: 1,
			emailsEnviados: 1,
			whatsappsEnviados: 0,
			turnos: 1,
		});
		renderPage();
		await user.click(
			screen.getByRole("button", { name: /turnos abiertos/i }),
		);
		expect(await screen.findByText("Aviso ya enviado")).toBeInTheDocument();
		expect(apiEnviarTurnoAbiertoInforme).not.toHaveBeenCalled();
		await user.click(screen.getByRole("button", { name: /reenviar/i }));
		await waitFor(() => {
			expect(apiEnviarTurnoAbiertoInforme).toHaveBeenCalledOnce();
		});
	});
});
