import { afterEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";

import ProductosSinMovimientoPage from "@/features/reportes/ProductosSinMovimientoPage";
import { apiProductosSinMovimiento } from "@/lib/api/reportes";
import {
	renderConProviders,
	resetAuth,
} from "@/test/helpers/renderProveedores";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), close: vi.fn(), showLoading: vi.fn() },
}));

vi.mock("@/lib/api/reportes", () => ({
	apiProductosSinMovimiento: vi.fn(),
}));

const DATOS = [
	{
		productoId: 9,
		codigo: "TOR-009",
		producto: "Tornillo 1/4 x 2 (caja 100)",
		categoria: "Tornillería",
		stock: 15,
		costoActual: 120,
		dineroDetenidoEnEstante: 1800,
		ultimaVenta: "2026-06-01",
		diasSinVender: 115,
		prioridadPromocion: "ALTA",
		imagenUrl: null,
	},
];

afterEach(() => {
	resetAuth();
	vi.clearAllMocks();
});

describe("ProductosSinMovimientoPage", () => {
	it("renderiza tabla con producto detenido y sus métricas", async () => {
		vi.mocked(apiProductosSinMovimiento).mockResolvedValue(DATOS);
		renderConProviders(<ProductosSinMovimientoPage />);

		expect(
			screen.getByRole("heading", { name: "Productos sin movimiento" }),
		).toBeInTheDocument();
		expect(
			await screen.findByText("Tornillo 1/4 x 2 (caja 100)"),
		).toBeInTheDocument();
		expect(screen.getByText("TOR-009")).toBeInTheDocument();
		expect(screen.getByText("Dinero en estante")).toBeInTheDocument();
		expect(screen.getByText("Días sin vender")).toBeInTheDocument();
		expect(screen.getByText("ALTA")).toBeInTheDocument();
	});

	it("muestra estado vacío cuando todo tiene movimiento", async () => {
		vi.mocked(apiProductosSinMovimiento).mockResolvedValue([]);
		renderConProviders(<ProductosSinMovimientoPage />);

		expect(
			await screen.findByText("No hay productos sin movimiento"),
		).toBeInTheDocument();
	});
});
