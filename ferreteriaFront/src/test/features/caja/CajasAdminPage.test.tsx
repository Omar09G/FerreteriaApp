import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import CajasAdminPage from "@/features/caja/CajasAdminPage";
import {
	apiActualizarCaja,
	apiActualizarEstadoCaja,
	apiCajas,
	apiCrearCaja,
} from "@/lib/api/caja";
import { apiAlmacenes } from "@/lib/api/catalogo";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("@/lib/api/caja", () => ({
	apiCajas: vi.fn(),
	apiCrearCaja: vi.fn(),
	apiActualizarCaja: vi.fn(),
	apiActualizarEstadoCaja: vi.fn(),
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiAlmacenes: vi.fn(),
}));

const CAJAS = [
	{
		cajaId: 1,
		nombre: "Caja Central",
		almacenId: 1,
		almacenNombre: "Matriz",
		activa: true,
	},
	{
		cajaId: 2,
		nombre: "Caja Sucursal",
		almacenId: 2,
		almacenNombre: "Sucursal Norte",
		activa: false,
	},
];

function mockearRed() {
	vi.mocked(apiCajas).mockResolvedValue(CAJAS);
	vi.mocked(apiAlmacenes).mockResolvedValue([
		{ almacenId: 1, nombre: "Matriz" },
		{ almacenId: 2, nombre: "Sucursal Norte" },
	]);
	vi.mocked(apiCrearCaja).mockResolvedValue(CAJAS[0]);
	vi.mocked(apiActualizarCaja).mockResolvedValue(CAJAS[0]);
	vi.mocked(apiActualizarEstadoCaja).mockResolvedValue(CAJAS[0]);
}

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("CajasAdminPage", () => {
	it("como ADMINISTRADOR muestra tabla de cajas y botón de alta", async () => {
		mockearRed();
		renderConProviders(<CajasAdminPage />);

		expect(
			screen.getByRole("heading", { name: "Administrar cajas" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nueva caja/ }),
		).toBeInTheDocument();
		expect(await screen.findByText("Caja Central")).toBeInTheDocument();
		expect(screen.getByText("Caja Sucursal")).toBeInTheDocument();
		expect(screen.getByText("Matriz")).toBeInTheDocument();
	});

	it("abre el diálogo de nueva caja con su formulario", async () => {
		mockearRed();
		const user = userEvent.setup();
		renderConProviders(<CajasAdminPage />);
		await screen.findByText("Caja Central");

		await user.click(screen.getByRole("button", { name: /Nueva caja/ }));
		expect(
			await screen.findByRole("heading", { name: "Nueva caja" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Nombre de la caja/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Almacén/)).toBeInTheDocument();
	});

	it("abre el diálogo de edición al pulsar Editar", async () => {
		mockearRed();
		const user = userEvent.setup();
		renderConProviders(<CajasAdminPage />);
		await screen.findByText("Caja Central");

		await user.click(screen.getAllByRole("button", { name: "Editar" })[0]);
		expect(
			await screen.findByRole("heading", { name: "Editar caja" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Nombre de la caja/)).toHaveValue(
			"Caja Central",
		);
	});

	it("sin rol ADMINISTRADOR muestra aviso de permisos", async () => {
		mockearRed();
		renderConProviders(<CajasAdminPage />, { roles: ["VENDEDOR"] });
		expect(
			await screen.findByText("No tienes permisos para administrar cajas."),
		).toBeInTheDocument();
	});
});
