import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/lib/api/venta", () => ({
	apiCotizaciones: vi.fn(),
	apiCrearCotizacion: vi.fn(),
	apiConvertirCotizacion: vi.fn(),
}));
vi.mock("@/lib/api/catalogo", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiClientes: vi.fn(),
	apiProductos: vi.fn(),
}));
vi.mock("@/lib/api/caja", async (importOriginal) => ({
	...((await importOriginal()) as Record<string, unknown>),
	apiCajas: vi.fn(),
	apiTurnoActual: vi.fn(),
}));

import {
	apiConvertirCotizacion,
	apiCotizaciones,
	apiCrearCotizacion,
} from "@/lib/api/venta";
import { apiClientes, apiProductos } from "@/lib/api/catalogo";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import CotizacionesPage from "@/features/ventas/CotizacionesPage";
import {
	CLIENTE,
	COTIZACION,
	pageOf,
	renderPagina,
} from "@/test/helpers/ventasPos";

const apiCotizacionesMock = vi.mocked(apiCotizaciones);
vi.mocked(apiCrearCotizacion);
vi.mocked(apiConvertirCotizacion);
const apiClientesMock = vi.mocked(apiClientes);
vi.mocked(apiProductos);
const apiCajasMock = vi.mocked(apiCajas);
vi.mocked(apiTurnoActual);

beforeEach(() => {
	vi.clearAllMocks();
	apiCotizacionesMock.mockResolvedValue(pageOf([COTIZACION]));
	apiClientesMock.mockResolvedValue(pageOf([CLIENTE]));
	apiCajasMock.mockResolvedValue([]);
});

describe("CotizacionesPage", () => {
	it("renderiza título, filtro y tabla con una cotización", async () => {
		renderPagina(<CotizacionesPage />);
		expect(
			screen.getByRole("heading", { name: "Cotizaciones" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText("Estado")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nueva cotización/ }),
		).toBeInTheDocument();
		expect(await screen.findByText("Cotizaciones (1)")).toBeInTheDocument();
		expect(await screen.findByText("COT-001")).toBeInTheDocument();
		expect(screen.getByText("VIGENTE")).toBeInTheDocument();
	});

	it("abre el formulario de nueva cotización", async () => {
		const user = userEvent.setup();
		renderPagina(<CotizacionesPage />);
		await user.click(
			screen.getByRole("button", { name: /Nueva cotización/ }),
		);
		expect(
			await screen.findByRole("dialog", { name: "Nueva cotización" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Buscar producto/)).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Registrar cotización/ }),
		).toBeInTheDocument();
	});

	it("abre el detalle de partidas al hacer clic en Ver detalles", async () => {
		const user = userEvent.setup();
		renderPagina(<CotizacionesPage />);
		await user.click(
			await screen.findByRole("button", { name: "Ver detalles" }),
		);
		expect(
			await screen.findByText("Detalles de cotización"),
		).toBeInTheDocument();
		expect(screen.getByText("Martillo")).toBeInTheDocument();
		expect(screen.getByText("Cant.")).toBeInTheDocument();
	});
});
