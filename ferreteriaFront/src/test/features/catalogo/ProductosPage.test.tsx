import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import ProductosPage from "@/features/catalogo/ProductosPage";
import { ToastProvider } from "@/components/ui/Toast";
import Swal from "sweetalert2";
import { writeFile } from "xlsx";
import {
	apiActualizarProducto,
	apiCategoriasArbol,
	apiCrearProducto,
	apiEliminarProducto,
	apiMarcas,
	apiProductos,
	apiUnidadesMedida,
} from "@/lib/api/catalogo";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiActualizarProducto: vi.fn(),
	apiCategoriasArbol: vi.fn(),
	apiCrearProducto: vi.fn(),
	apiEliminarProducto: vi.fn(),
	apiMarcas: vi.fn(),
	apiProductos: vi.fn(),
	apiUnidadesMedida: vi.fn(),
	apiCargaMasivaProductos: vi.fn(),
}));

vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		json_to_sheet: vi.fn(() => ({})),
		aoa_to_sheet: vi.fn(() => ({})),
		book_append_sheet: vi.fn(),
	},
	writeFile: vi.fn(),
	read: vi.fn(() => ({ SheetNames: [], Sheets: {} })),
}));

const PRODUCTO = {
	productoId: 10,
	codigo: "MAR-001",
	tipo: "PRODUCTO",
	nombre: "Martillo",
	descripcion: null,
	categoriaId: 1,
	categoriaNombre: "Herramientas",
	marcaId: null,
	marcaNombre: null,
	unidadMedidaId: 1,
	unidadMedidaClave: "PZA",
	costoActual: 30,
	precioMenudeo: 50,
	precioMayoreo: null,
	aplicaIva: true,
	stockActual: 100,
	imagenUrl: null,
	codigosBarras: ["750100000001"],
};

const SERVICIO = {
	...PRODUCTO,
	productoId: 11,
	codigo: null,
	tipo: "SERVICIO",
	nombre: "Afilado",
	categoriaNombre: "Servicios",
	unidadMedidaClave: "SRV",
	codigosBarras: [],
};

const RENTA = {
	...PRODUCTO,
	productoId: 12,
	tipo: "HERRAMIENTA_RENTA",
	nombre: "Rotomartillo renta",
	codigosBarras: [],
};

const CATS = [
	{
		categoriaId: 1,
		nombre: "Herramientas",
		categoriaPadreId: null,
		ruta: "Herramientas",
		nivel: 0,
		hijos: [
			{
				categoriaId: 2,
				nombre: "Manuales",
				categoriaPadreId: 1,
				ruta: "Herramientas · Manuales",
				nivel: 1,
			},
		],
	},
];
const MARCAS = [{ marcaId: 1, nombre: "Truper" }];
const UMS = [{ unidadId: 1, clave: "PZA", nombre: "Pieza", permiteFraccion: false }];

function pageOf(data: unknown[]) {
	return {
		success: true,
		data,
		meta: { page: 0, size: 20, totalElements: data.length, totalPages: 1 },
	};
}

function renderPage() {
	const qc = new QueryClient({
		defaultOptions: {
			queries: { retry: false },
			mutations: { retry: false },
		},
	});
	const wrapper = ({ children }: { children: ReactNode }) => (
		<MemoryRouter>
			<QueryClientProvider client={qc}>
				<ToastProvider>{children}</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>
	);
	return render(<ProductosPage />, { wrapper });
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	vi.mocked(apiProductos).mockResolvedValue(
		pageOf([PRODUCTO, SERVICIO, RENTA]) as never,
	);
	vi.mocked(apiCategoriasArbol).mockResolvedValue(CATS as never);
	vi.mocked(apiMarcas).mockResolvedValue(MARCAS as never);
	vi.mocked(apiUnidadesMedida).mockResolvedValue(UMS as never);
});

describe("ProductosPage", () => {
	it("renderiza título, filas y badges por tipo", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Productos" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Martillo")).toBeInTheDocument();
		expect(screen.getByText("Afilado")).toBeInTheDocument();
		const tabla = screen.getByRole("table");
		expect(within(tabla).getByText("Producto")).toBeInTheDocument();
		expect(within(tabla).getByText("Servicio")).toBeInTheDocument();
		expect(within(tabla).getByText("Renta")).toBeInTheDocument();
		expect(screen.getByText("Resultados (3)")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /Excel/i })).toBeInTheDocument();
	});

	it("busca por nombre y filtra por tipo, luego limpia", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		await user.type(screen.getByLabelText(/Buscar/), "marti{enter}");
		expect(apiProductos).toHaveBeenLastCalledWith(
			expect.objectContaining({ q: "marti", page: 0 }),
		);
		await user.selectOptions(screen.getByLabelText("Tipo"), "SERVICIO");
		expect(apiProductos).toHaveBeenLastCalledWith(
			expect.objectContaining({ q: undefined, tipo: "SERVICIO" }),
		);
		await user.click(
			await screen.findByRole("button", { name: "Limpiar" }),
		);
		expect(apiProductos).toHaveBeenLastCalledWith(
			expect.objectContaining({ q: undefined, tipo: undefined }),
		);
	});

	it("valida y crea un producto nuevo", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearProducto).mockResolvedValue(PRODUCTO as never);
		renderPage();
		await screen.findByText("Martillo");
		await user.click(screen.getByRole("button", { name: /Nuevo producto/ }));
		const dialogo = await screen.findByRole("dialog");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(
			within(dialogo).getByText("Completa nombre, categoría y unidad de medida."),
		).toBeInTheDocument();
		expect(apiCrearProducto).not.toHaveBeenCalled();

		await user.type(within(dialogo).getByLabelText(/Nombre/), "Desarmador");
		await user.selectOptions(
			within(dialogo).getByLabelText(/Categoría/),
			"2",
		);
		await user.selectOptions(
			within(dialogo).getByLabelText(/Unidad de medida/),
			"1",
		);
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(apiCrearProducto).toHaveBeenCalledWith(
			expect.objectContaining({
				nombre: "Desarmador",
				categoriaId: 2,
				unidadMedidaId: 1,
				tipo: "PRODUCTO",
			}),
		);
	});

	it("edita y desactiva con confirmación", async () => {
		const user = userEvent.setup();
		vi.mocked(apiActualizarProducto).mockResolvedValue(PRODUCTO as never);
		vi.mocked(apiEliminarProducto).mockResolvedValue(undefined as never);
		renderPage();
		await screen.findByText("Martillo");
		await user.click(screen.getByRole("button", { name: "Editar Martillo" }));
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByLabelText(/Nombre/)).toHaveValue("Martillo");
		await user.clear(within(dialogo).getByLabelText("Precio menudeo"));
		await user.type(within(dialogo).getByLabelText("Precio menudeo"), "55");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(apiActualizarProducto).toHaveBeenCalledWith(
			10,
			expect.objectContaining({ precioMenudeo: 55 }),
		);

		await user.click(
			screen.getByRole("button", { name: "Desactivar Martillo" }),
		);
		expect(
			await screen.findByText("Confirmar desactivación"),
		).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Sí, desactivar" }));
		expect(apiEliminarProducto).toHaveBeenCalledWith(10);
	});

	it("abre el diálogo de carga masiva", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		await user.click(screen.getByRole("button", { name: /Carga masiva/ }));
		expect(
			await screen.findByText("Descargar plantilla"),
		).toBeInTheDocument();
	});

	it("muestra error si la carga inicial falla", async () => {
		vi.mocked(apiProductos).mockRejectedValueOnce(new Error("sin red"));
		renderPage();
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin red") }),
			);
		});
	});

	it("exporta a Excel lo visible", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Martillo");
		await user.click(screen.getByRole("button", { name: /Excel/i }));
		await waitFor(() =>
			expect(vi.mocked(writeFile)).toHaveBeenCalledTimes(1),
		);
	});
});
