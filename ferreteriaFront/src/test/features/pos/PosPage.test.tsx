import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/lib/api/catalogo", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiProductos: vi.fn(),
	apiAlmacenes: vi.fn(),
	apiClientes: vi.fn(),
	apiGetCliente: vi.fn(),
}));
vi.mock("@/lib/api/caja", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiCajas: vi.fn(),
	apiTurnoActual: vi.fn(),
}));
vi.mock("@/lib/api/venta", () => ({
	apiCheckout: vi.fn(),
	apiVentas: vi.fn(),
}));
vi.mock("@/lib/api/promociones", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiEvaluarPromociones: vi.fn(),
}));
vi.mock("@/lib/api/ticketConfig", () => ({
	apiGetTicketConfig: vi.fn(),
}));

import {
	apiAlmacenes,
	apiClientes,
	apiGetCliente,
	apiProductos,
} from "@/lib/api/catalogo";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import { apiCheckout, apiVentas } from "@/lib/api/venta";
import { apiEvaluarPromociones } from "@/lib/api/promociones";
import { apiGetTicketConfig } from "@/lib/api/ticketConfig";
import PosPage from "@/features/pos/PosPage";
import {
	ALMACEN,
	CAJA,
	CLIENTE,
	PRODUCTO,
	TURNO,
	pageOf,
	renderPagina,
} from "@/test/helpers/ventasPos";

const apiProductosMock = vi.mocked(apiProductos);
const apiAlmacenesMock = vi.mocked(apiAlmacenes);
const apiClientesMock = vi.mocked(apiClientes);
vi.mocked(apiGetCliente);
const apiCajasMock = vi.mocked(apiCajas);
const apiTurnoMock = vi.mocked(apiTurnoActual);
vi.mocked(apiCheckout);
const apiVentasMock = vi.mocked(apiVentas);
const apiPromosMock = vi.mocked(apiEvaluarPromociones);
const apiTicketMock = vi.mocked(apiGetTicketConfig);

beforeEach(() => {
	vi.clearAllMocks();
	apiAlmacenesMock.mockResolvedValue([ALMACEN]);
	apiCajasMock.mockResolvedValue([CAJA]);
	apiClientesMock.mockResolvedValue(pageOf([CLIENTE]));
	apiProductosMock.mockResolvedValue(pageOf([]));
	apiTurnoMock.mockResolvedValue(TURNO);
	apiPromosMock.mockResolvedValue([]);
	apiTicketMock.mockResolvedValue(null as never);
	apiVentasMock.mockResolvedValue(pageOf([]));
});

describe("PosPage", () => {
	it("renderiza punto de venta, ticket vacío, promoción y cobro", async () => {
		renderPagina(<PosPage />);
		expect(
			screen.getByRole("heading", { name: "Punto de venta" }),
		).toBeInTheDocument();
		expect(
			screen.getByLabelText(/Almacén \/ punto de venta/),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Caja donde operas/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Buscar producto/)).toBeInTheDocument();
		expect(await screen.findByText("Ticket (0)")).toBeInTheDocument();
		expect(
			screen.getByText("Agrega productos con el buscador."),
		).toBeInTheDocument();
		expect(
			screen.getByText(/Agrega productos para validar/),
		).toBeInTheDocument();
		const turno = await screen.findByRole("status");
		expect(within(turno).getByText("#5")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Cobrar/ }),
		).toBeDisabled();
	});

	it("abre las ventas del día y muestra el resumen vacío", async () => {
		const user = userEvent.setup();
		renderPagina(<PosPage />);
		await user.click(
			screen.getByRole("button", { name: "Ver ventas del día" }),
		);
		expect(await screen.findByText(/Ventas de hoy/)).toBeInTheDocument();
		expect(
			await screen.findByText("Aún no hay ventas hoy."),
		).toBeInTheDocument();
		expect(screen.getByText("Tickets")).toBeInTheDocument();
		expect(apiVentasMock).toHaveBeenCalled();
	});

	it("busca un producto por nombre y muestra el resultado", async () => {
		const user = userEvent.setup();
		apiProductosMock.mockResolvedValue(pageOf([PRODUCTO]));
		renderPagina(<PosPage />);
		const input = screen.getByLabelText(/Buscar producto/);
		await user.type(input, "Martillo{enter}");
		expect(
			await screen.findByRole("listbox", { name: "Resultados de búsqueda" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Martillo")).toBeInTheDocument();
		expect(apiProductosMock).toHaveBeenCalled();
	});
});
