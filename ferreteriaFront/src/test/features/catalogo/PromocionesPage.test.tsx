import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import PromocionesPage from "@/features/catalogo/PromocionesPage";
import { ToastProvider } from "@/components/ui/Toast";
import Swal from "sweetalert2";
import { writeFile } from "xlsx";
import {
	apiActualizarPromocion,
	apiCrearPromocion,
	apiEliminarPromocion,
	apiPromocion,
	apiPromociones,
} from "@/lib/api/promociones";
import { apiCategoriasArbol, apiProductos } from "@/lib/api/catalogo";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/promociones", () => ({
	apiActualizarPromocion: vi.fn(),
	apiCrearPromocion: vi.fn(),
	apiEliminarPromocion: vi.fn(),
	apiPromocion: vi.fn(),
	apiPromociones: vi.fn(),
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiCategoriasArbol: vi.fn(),
	apiProductos: vi.fn(),
}));

vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		aoa_to_sheet: vi.fn(() => ({})),
		book_append_sheet: vi.fn(),
	},
	writeFile: vi.fn(),
}));

const PROMO = {
	promocionId: 1,
	nombre: "Lunes de descuento",
	descripcion: "10% lunes",
	tipo: "DESCUENTO_PRODUCTO",
	valorPct: 10,
	valorMonto: undefined,
	precioEspecial: undefined,
	compraMinTotal: undefined,
	compraMinCantidad: undefined,
	lleva: undefined,
	paga: undefined,
	maxUsosTotal: 100,
	maxUsosCliente: undefined,
	usosActual: 5,
	vigenciaDesde: "2026-09-01T13:00:00Z",
	vigenciaHasta: "2026-09-30T13:00:00Z",
	diasSemana: [1, 2, 3, 4, 5],
	horaDesde: "07:00",
	horaHasta: "20:00",
	soloMayoristas: false,
	estado: "ACTIVA",
	productos: [10],
	categorias: [1],
	usuarioId: 1,
	creadoEn: "2026-09-01T13:00:00Z",
};

const NXM = {
	...PROMO,
	promocionId: 2,
	nombre: "Lleva 3 paga 2",
	tipo: "NXM",
	valorPct: undefined,
	lleva: 3,
	paga: 2,
	estado: "PROGRAMADA",
	usosActual: 0,
	productos: [],
	categorias: [],
};

const PRECIO = {
	...PROMO,
	promocionId: 3,
	nombre: "Precio especial martillo",
	tipo: "PRECIO_ESPECIAL",
	valorPct: undefined,
	precioEspecial: 99.9,
	estado: "FINALIZADA",
	usosActual: 0,
	productos: [],
	categorias: [],
};

const MONTO = {
	...PROMO,
	promocionId: 4,
	nombre: "Monto fijo total",
	tipo: "DESCUENTO_TOTAL_VENTA",
	valorPct: undefined,
	valorMonto: 50,
	maxUsosTotal: undefined,
	vigenciaHasta: undefined,
	estado: "CANCELADA",
	usosActual: 0,
	productos: [],
	categorias: [],
};

const CATS = [
	{
		categoriaId: 1,
		nombre: "Herramientas",
		categoriaPadreId: null,
		ruta: "Herramientas",
		nivel: 0,
	},
	{
		categoriaId: 2,
		nombre: "Fijación",
		categoriaPadreId: null,
		ruta: "Fijación",
		nivel: 0,
	},
];

const PRODS = [
	{
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
		codigosBarras: [],
	},
	{
		productoId: 11,
		codigo: "DES-002",
		tipo: "PRODUCTO",
		nombre: "Desarmador",
		descripcion: null,
		categoriaId: 1,
		categoriaNombre: "Herramientas",
		marcaId: null,
		marcaNombre: null,
		unidadMedidaId: 1,
		unidadMedidaClave: "PZA",
		costoActual: 20,
		precioMenudeo: 35,
		precioMayoreo: null,
		aplicaIva: true,
		codigosBarras: [],
	},
];

function pageOf(data: unknown[]) {
	return {
		success: true,
		data,
		meta: { page: 0, size: 15, totalElements: data.length, totalPages: 1 },
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
	return render(<PromocionesPage />, { wrapper });
}

function comoAdmin() {
	useAuthStore.setState({
		autenticado: true,
		usuario: { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] },
		lastActivityAt: Date.now(),
	});
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	comoAdmin();
	vi.mocked(apiPromociones).mockResolvedValue(
		pageOf([PROMO, NXM, PRECIO, MONTO]) as never,
	);
	vi.mocked(apiPromocion).mockResolvedValue(PROMO as never);
	vi.mocked(apiCategoriasArbol).mockResolvedValue(CATS as never);
	vi.mocked(apiProductos).mockResolvedValue(pageOf(PRODS) as never);
});

describe("PromocionesPage", () => {
	it("renderiza título, filas, badges y botón nueva (admin)", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Promociones" }),
		).toBeInTheDocument();
		expect(await screen.findByText("Lunes de descuento")).toBeInTheDocument();
		expect(screen.getByText("Lleva 3 paga 2")).toBeInTheDocument();
		expect(screen.getByText("10%")).toBeInTheDocument();
		expect(screen.getByText("3 × 2")).toBeInTheDocument();
		expect(screen.getByText("$99.90")).toBeInTheDocument();
		expect(screen.getByText("$50.00")).toBeInTheDocument();
		expect(
			within(screen.getByRole("table")).getByText("CANCELADA"),
		).toBeInTheDocument();
		expect(screen.getAllByText("0 prod · 0 cat")).toHaveLength(3);
		expect(within(screen.getByRole("table")).getByText("ACTIVA")).toBeInTheDocument();
		expect(screen.getByText("1 prod · 1 cat")).toBeInTheDocument();
		expect(screen.getByText("5 / 100")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nueva promoción/ }),
		).toBeInTheDocument();
	});

	it("sin rol de administración oculta acciones y botón nueva", async () => {
		useAuthStore.setState({
			autenticado: true,
			usuario: { usuarioId: 2, username: "caja", roles: ["CAJERO"] },
			lastActivityAt: Date.now(),
		});
		renderPage();
		await screen.findByText("Lunes de descuento");
		expect(
			screen.queryByRole("button", { name: /Nueva promoción/ }),
		).not.toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: /Editar Lunes/ }),
		).not.toBeInTheDocument();
	});

	it("filtra por nombre, tipo y estado; limpiar resetea", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.type(screen.getByLabelText("Nombre contiene"), "lunes");
		expect(apiPromociones).toHaveBeenLastCalledWith(
			expect.objectContaining({ nombre: "lunes", page: 0 }),
		);
		await user.selectOptions(screen.getByLabelText("Tipo"), "NXM");
		expect(apiPromociones).toHaveBeenLastCalledWith(
			expect.objectContaining({ tipo: "NXM" }),
		);
		await user.selectOptions(screen.getByLabelText("Estado"), "ACTIVA");
		expect(apiPromociones).toHaveBeenLastCalledWith(
			expect.objectContaining({ estado: "ACTIVA" }),
		);
		await user.click(screen.getByRole("button", { name: "Limpiar" }));
		expect(apiPromociones).toHaveBeenLastCalledWith({ page: 0, size: 15 });
	});

	it("valida nombre y crea promoción de descuento", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearPromocion).mockResolvedValue(PROMO as never);
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(screen.getByRole("button", { name: /Nueva promoción/ }));
		const dialogo = await screen.findByRole("dialog");
		await user.click(
			within(dialogo).getByRole("button", { name: /Crear promoción/ }),
		);
		expect(
			within(dialogo).getByText("Obligatorio"),
		).toBeInTheDocument();
		expect(apiCrearPromocion).not.toHaveBeenCalled();

		await user.type(
			within(dialogo).getByLabelText(/Nombre/),
			"Promo nueva",
		);
		await user.type(within(dialogo).getByLabelText("Valor (%)"), "15");
		await user.click(
			within(dialogo).getByRole("button", { name: /Crear promoción/ }),
		);
		expect(apiCrearPromocion).toHaveBeenCalledWith(
			expect.objectContaining({ nombre: "Promo nueva", valorPct: 15 }),
		);
	});

	it("muestra campos lleva/paga con tipo NXM y precio con PRECIO_ESPECIAL", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(screen.getByRole("button", { name: /Nueva promoción/ }));
		const dialogo = await screen.findByRole("dialog");
		await user.selectOptions(within(dialogo).getByLabelText(/Tipo/), "NXM");
		expect(within(dialogo).getByLabelText(/Lleva/)).toBeInTheDocument();
		expect(within(dialogo).getByLabelText(/Paga/)).toBeInTheDocument();
		await user.selectOptions(
			within(dialogo).getByLabelText(/Tipo/),
			"PRECIO_ESPECIAL",
		);
		expect(
			within(dialogo).getByLabelText(/Precio especial/),
		).toBeInTheDocument();
		expect(within(dialogo).queryByLabelText(/Lleva/)).not.toBeInTheDocument();
	});

	it("edita cargando el detalle y guarda cambios", async () => {
		const user = userEvent.setup();
		vi.mocked(apiActualizarPromocion).mockResolvedValue(PROMO as never);
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(
			screen.getByRole("button", { name: "Editar Lunes de descuento" }),
		);
		const dialogo = await screen.findByRole("dialog");
		expect(
			await within(dialogo).findByLabelText(/Nombre/),
		).toHaveValue("Lunes de descuento");
		expect(
			within(dialogo).getByText("Usos actuales: 5"),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: /Guardar cambios/ }),
		);
		expect(apiActualizarPromocion).toHaveBeenCalledWith(
			1,
			expect.objectContaining({ nombre: "Lunes de descuento" }),
		);
	});

	it("elimina con confirmación y bloquea botón con usos", async () => {
		const user = userEvent.setup();
		vi.mocked(apiEliminarPromocion).mockResolvedValue(undefined as never);
		renderPage();
		await screen.findByText("Lunes de descuento");
		// con usos > 0 el botón está deshabilitado
		expect(
			screen.getByRole("button", { name: "Eliminar Lunes de descuento" }),
		).toBeDisabled();
		await user.click(
			screen.getByRole("button", { name: "Eliminar Lleva 3 paga 2" }),
		);
		expect(
			await screen.findByText("Eliminar promoción"),
		).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Sí" }));
		expect(apiEliminarPromocion).toHaveBeenCalledWith(2);
	});

	it("selecciona/quita todas las categorías y un toggle individual", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(screen.getByRole("button", { name: /Nueva promoción/ }));
		const dialogo = await screen.findByRole("dialog");
		await within(dialogo).findByText("Herramientas");
		await user.click(
			within(dialogo).getByRole("button", { name: "Seleccionar todas" }),
		);
		expect(
			within(dialogo).getByRole("button", { name: "Quitar todas" }),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: "Quitar todas" }),
		);
		expect(
			within(dialogo).getByRole("button", { name: "Seleccionar todas" }),
		).toBeInTheDocument();
		// toggle individual de categoría
		const check = within(dialogo).getByRole("checkbox", {
			name: "Herramientas",
		});
		await user.click(check);
		expect(check).toBeChecked();
	});

	it("agrega y quita productos, agregar/quitar todos", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(screen.getByRole("button", { name: /Nueva promoción/ }));
		const dialogo = await screen.findByRole("dialog");
		await within(dialogo).findByText("Disponibles");
		await user.click(
			within(dialogo).getByRole("button", { name: "Agregar todos" }),
		);
		expect(
			within(dialogo).getByRole("button", { name: "Quitar todos" }),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: "Quitar todos" }),
		);
		expect(
			within(dialogo).getByText("Ninguno. Agrega productos a la izquierda."),
		).toBeInTheDocument();
		await user.click(
			within(dialogo).getByRole("button", { name: /MAR-001.*Martillo/ }),
		);
		expect(
			within(dialogo).getByRole("button", { name: /quitar/ }),
		).toBeInTheDocument();
	});

	it("exige al menos un día aplicable", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearPromocion).mockResolvedValue(PROMO as never);
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(screen.getByRole("button", { name: /Nueva promoción/ }));
		const dialogo = await screen.findByRole("dialog");
		await user.type(
			within(dialogo).getByLabelText(/Nombre/),
			"Sin días",
		);
		// desmarca los 7 días
		const dias = within(dialogo).getAllByRole("checkbox", { name: /^[LMJVS D]$/ });
		for (const d of dias) await user.click(d);
		await user.click(
			within(dialogo).getByRole("button", { name: /Crear promoción/ }),
		);
		expect(
			within(dialogo).getByText("Selecciona al menos un día."),
		).toBeInTheDocument();
		expect(apiCrearPromocion).not.toHaveBeenCalled();
	});

	it("muestra error si crear falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearPromocion).mockRejectedValueOnce(
			new Error("promo inválida"),
		);
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(screen.getByRole("button", { name: /Nueva promoción/ }));
		const dialogo = await screen.findByRole("dialog");
		await user.type(within(dialogo).getByLabelText(/Nombre/), "Promo X");
		await user.click(
			within(dialogo).getByRole("button", { name: /Crear promoción/ }),
		);
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("promo inválida"),
				}),
			);
		});
	});

	it("exporta a Excel lo visible", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(screen.getByRole("button", { name: /Excel/i }));
		await waitFor(() =>
			expect(vi.mocked(writeFile)).toHaveBeenCalledTimes(1),
		);
	});

	it("muestra 'Sin coincidencias' al buscar producto sin resultados", async () => {
		const user = userEvent.setup();
		vi.mocked(apiProductos).mockResolvedValue(pageOf([]) as never);
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(screen.getByRole("button", { name: /Nueva promoción/ }));
		const dialogo = await screen.findByRole("dialog");
		expect(
			await within(dialogo).findByText("Sin coincidencias."),
		).toBeInTheDocument();
	});

	it("muestra 'Guardando…' mientras crea", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearPromocion).mockReturnValueOnce(new Promise(() => {}));
		renderPage();
		await screen.findByText("Lunes de descuento");
		await user.click(screen.getByRole("button", { name: /Nueva promoción/ }));
		const dialogo = await screen.findByRole("dialog");
		await user.type(within(dialogo).getByLabelText(/Nombre/), "Promo lenta");
		await user.click(
			within(dialogo).getByRole("button", { name: /Crear promoción/ }),
		);
		expect(
			await within(dialogo).findByText("Guardando…"),
		).toBeInTheDocument();
	});
});
