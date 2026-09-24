import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/lib/api/venta", () => ({
	apiRentas: vi.fn(),
	apiCrearRenta: vi.fn(),
	apiDevolucionRenta: vi.fn(),
	apiCancelarRenta: vi.fn(),
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

import {
	apiCancelarRenta,
	apiCrearRenta,
	apiDevolucionRenta,
	apiRentas,
} from "@/lib/api/venta";
import { apiAlmacenes, apiClientes, apiProductos } from "@/lib/api/catalogo";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import RentasPage from "@/features/ventas/RentasPage";
import {
	ALMACEN,
	CAJA,
	CLIENTE,
	RENTA,
	TURNO,
	pageOf,
	renderPagina,
} from "@/test/helpers/ventasPos";

const apiRentasMock = vi.mocked(apiRentas);
vi.mocked(apiCrearRenta);
vi.mocked(apiDevolucionRenta);
vi.mocked(apiCancelarRenta);
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
});
