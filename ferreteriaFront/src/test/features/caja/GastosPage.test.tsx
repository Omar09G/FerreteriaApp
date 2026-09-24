import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import GastosPage from "@/features/caja/GastosPage";
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
import { apiProveedores } from "@/lib/api/catalogo";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("@/lib/api/caja", () => ({
	apiGastos: vi.fn(),
	apiCrearGasto: vi.fn(),
	apiActualizarGasto: vi.fn(),
	apiEliminarGasto: vi.fn(),
	apiIngresosOtros: vi.fn(),
	apiCrearIngreso: vi.fn(),
	apiActualizarIngreso: vi.fn(),
	apiEliminarIngreso: vi.fn(),
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiProveedores: vi.fn(),
}));

const META = { page: 0, size: 15, totalElements: 1, totalPages: 1 };

const GASTOS = [
	{
		gastoId: 1,
		folio: "G-001",
		tipoGastoId: 14,
		tipoGastoNombre: "Otros gastos",
		descripcion: "Flete de pedido",
		monto: 850,
		fechaGasto: "2026-09-20",
		formaPagoId: 1,
		formaPagoNombre: "Efectivo",
		proveedorId: null,
		turnoCajaId: null,
		facturaUuid: null,
		usuarioId: 1,
		creadoEn: "2026-09-20T10:00:00",
	},
];

const INGRESOS = [
	{
		ingresoOtroId: 7,
		concepto: "Venta de chatarra",
		monto: 1200,
		fecha: "2026-09-21",
		formaPagoId: 1,
		formaPagoNombre: "Efectivo",
		turnoCajaId: null,
		usuarioId: 1,
		creadoEn: "2026-09-21T11:00:00",
	},
];

function mockearRed() {
	vi.mocked(apiGastos).mockResolvedValue({
		success: true,
		data: GASTOS,
		meta: META,
	});
	vi.mocked(apiIngresosOtros).mockResolvedValue({
		success: true,
		data: INGRESOS,
		meta: META,
	});
	vi.mocked(apiProveedores).mockResolvedValue([]);
	vi.mocked(apiCrearGasto).mockResolvedValue(GASTOS[0]);
	vi.mocked(apiCrearIngreso).mockResolvedValue(INGRESOS[0]);
	vi.mocked(apiActualizarGasto).mockResolvedValue(GASTOS[0]);
	vi.mocked(apiActualizarIngreso).mockResolvedValue(INGRESOS[0]);
	vi.mocked(apiEliminarGasto).mockResolvedValue(undefined);
	vi.mocked(apiEliminarIngreso).mockResolvedValue(undefined);
}

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("GastosPage", () => {
	it("muestra gastos con su tabla y botón de registro", async () => {
		mockearRed();
		renderConProviders(<GastosPage />);

		expect(
			screen.getByRole("heading", { name: "Gastos de caja" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Registrar gasto/ }),
		).toBeInTheDocument();
		expect(await screen.findByText("Flete de pedido")).toBeInTheDocument();
		expect(screen.getByText("Descripción")).toBeInTheDocument();
		expect(screen.getByText("Monto")).toBeInTheDocument();
	});

	it("cambia al tab de ingresos y muestra su tabla", async () => {
		mockearRed();
		const user = userEvent.setup();
		renderConProviders(<GastosPage />);
		await screen.findByText("Flete de pedido");

		await user.click(screen.getByRole("button", { name: "Ingresos" }));
		expect(
			await screen.findByRole("heading", { name: "Ingresos de caja" }),
		).toBeInTheDocument();
		expect(screen.getByText("Venta de chatarra")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Registrar ingreso/ }),
		).toBeInTheDocument();
	});

	it("abre el diálogo de registro de gasto", async () => {
		mockearRed();
		const user = userEvent.setup();
		renderConProviders(<GastosPage />);
		await screen.findByText("Flete de pedido");

		await user.click(
			screen.getByRole("button", { name: /Registrar gasto/ }),
		);
		expect(
			await screen.findByRole("heading", { name: "Registrar gasto" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Tipo de gasto/)).toBeInTheDocument();
	});
});
