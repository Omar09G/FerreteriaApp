import { afterEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import CajaPage from "@/features/caja/CajaPage";
import {
	apiAbrirTurno,
	apiCajas,
	apiCerrarTurno,
	apiCortes,
	apiEsperadoTurno,
	apiMovimientosTurno,
	apiRegistrarMovimiento,
	apiTurnos,
} from "@/lib/api/caja";
import type { CorteCaja, TurnoCaja } from "@/lib/api/types";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("recharts", () => ({
	ResponsiveContainer: ({ children }: { children?: React.ReactNode }) => (
		<>{children}</>
	),
	BarChart: () => null,
	Bar: () => null,
	CartesianGrid: () => null,
	XAxis: () => null,
	YAxis: () => null,
	Tooltip: () => null,
	Legend: () => null,
}));

vi.mock("@/lib/api/caja", () => ({
	apiCajas: vi.fn(),
	apiAbrirTurno: vi.fn(),
	apiCerrarTurno: vi.fn(),
	apiCortes: vi.fn(),
	apiEsperadoTurno: vi.fn(),
	apiMovimientosTurno: vi.fn(),
	apiRegistrarMovimiento: vi.fn(),
	apiTurnos: vi.fn(),
}));

const CAJAS = [
	{
		cajaId: 1,
		nombre: "Caja Central",
		almacenId: 1,
		almacenNombre: "Matriz",
		activa: true,
	},
];

const TURNO_ABIERTO: TurnoCaja = {
	turnoCajaId: 10,
	cajaId: 1,
	cajaNombre: "Caja Central",
	usuarioId: 1,
	aperturaEn: "2026-09-24T09:00:00",
	montoApertura: 1000,
	cierreEn: null,
	montoEsperado: null,
	montoContado: null,
	diferencia: null,
	estado: "ABIERTO",
	observaciones: null,
};

const CORTE: CorteCaja = {
	corteId: 5,
	turnoCajaId: 9,
	cajaId: 1,
	cajaNombre: "Caja Central",
	almacenId: 1,
	almacenNombre: "Matriz",
	usuarioId: 1,
	usuarioCierreId: 1,
	fecha: "2026-09-23",
	aperturaEn: "2026-09-23T09:00:00",
	cierreEn: "2026-09-23T18:00:00",
	numVentas: 12,
	subtotal: 10000,
	iva: 1600,
	descuentos: 0,
	totalVendido: 11600,
	costoVentas: 7000,
	utilidadBruta: 4600,
	margenPct: 39.65,
	fondoApertura: 1000,
	entradasEfectivo: 11600,
	salidasEfectivo: 200,
	dineroEsperado: 12400,
	dineroContado: 12400,
	diferencia: 0,
	resultadoCaja: "CUADRADO",
	ingresosNoEfectivo: 0,
	egresosNoEfectivo: 0,
	perdidasInventario: 0,
	desgloseEntradas: "{}",
	desgloseSalidas: "{}",
	desgloseFormasPago: '{"Efectivo": 11600}',
	observaciones: null,
};

const META = { page: 0, size: 10, totalElements: 1, totalPages: 1 };
const META_VACIA = { page: 0, size: 10, totalElements: 0, totalPages: 0 };

function mockearRed() {
	vi.mocked(apiCajas).mockResolvedValue(CAJAS);
	vi.mocked(apiCortes).mockResolvedValue({
		success: true,
		data: [CORTE],
		meta: META,
	});
	vi.mocked(apiTurnos).mockResolvedValue({
		success: true,
		data: [TURNO_ABIERTO],
		meta: META,
	});
	vi.mocked(apiMovimientosTurno).mockResolvedValue([]);
	vi.mocked(apiEsperadoTurno).mockResolvedValue({
		montoApertura: 1000,
		entradasEfectivo: 11600,
		salidasEfectivo: 200,
		esperado: 12400,
	});
	vi.mocked(apiAbrirTurno).mockResolvedValue(TURNO_ABIERTO);
	vi.mocked(apiRegistrarMovimiento).mockResolvedValue({
		movimientoId: 1,
		turnoCajaId: 10,
		tipo: "SALIDA",
		concepto: "GASTO_OPERATIVO",
		monto: 100,
		formaPagoId: null,
		formaPagoNombre: null,
		refTabla: null,
		refId: null,
		creadoEn: "2026-09-24T10:00:00",
		refDescripcion: null,
	});
	vi.mocked(apiCerrarTurno).mockResolvedValue(CORTE);
}

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("CajaPage", () => {
	it("renderiza título, selector de caja, turnos vacíos y cortes", async () => {
		mockearRed();
		vi.mocked(apiTurnos).mockResolvedValue({
			success: true,
			data: [],
			meta: META_VACIA,
		});
		renderConProviders(<CajaPage />);

		expect(
			screen.getByRole("heading", { name: "Caja" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Caja")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Abrir turno/ }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("heading", { name: "Turnos" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("heading", { name: "Cortes de caja" }),
		).toBeInTheDocument();
		// Corte renderizado con sus métricas clave
		expect(await screen.findByText("Corte #5")).toBeInTheDocument();
		expect(screen.getByText("Total vendido")).toBeInTheDocument();
		expect(screen.getByText("Diferencia")).toBeInTheDocument();
	});

	it("al elegir caja muestra sus turnos y permite abrir el diálogo de apertura", async () => {
		mockearRed();
		const user = userEvent.setup();
		renderConProviders(<CajaPage />);

		await screen.findByText("Corte #5");
		await user.selectOptions(screen.getByLabelText("Caja"), "1");
		expect(await screen.findByText("Turno #10")).toBeInTheDocument();

		await user.click(screen.getByRole("button", { name: /Abrir turno/ }));
		expect(
			await screen.findByText("Abrir turno de caja"),
		).toBeInTheDocument();
	});

	it("abre la gráfica de cortes y cambia de tab sin crashear", async () => {
		mockearRed();
		const user = userEvent.setup();
		renderConProviders(<CajaPage />);

		await screen.findByText("Corte #5");
		await user.click(
			screen.getByRole("button", { name: /Ver gráfica de ventas/i }),
		);
		expect(
			await screen.findByText("Ventas por cierre de caja"),
		).toBeInTheDocument();
		// Tabla del tab Ventas
		expect(screen.getByText("Total periodo")).toBeInTheDocument();

		await user.click(
			screen.getByRole("button", { name: "Flujo efectivo" }),
		);
		expect(screen.getByText("Fondo")).toBeInTheDocument();

		await user.click(
			screen.getByRole("button", { name: "Formas de pago" }),
		);
		const dialog = screen.getByText("Ventas por cierre de caja").closest("section")!;
		expect(
			within(dialog as HTMLElement).getByText(/Efectivo vs digital/),
		).toBeInTheDocument();
	});
});
