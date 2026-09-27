import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import CatalogoCrudPage from "@/features/catalogo/CatalogoCrudPage";
import { ToastProvider } from "@/components/ui/Toast";
import Swal from "sweetalert2";
import { writeFile } from "xlsx";
import {
	apiCatalogoActualizar,
	apiCatalogoCrear,
	apiCatalogoDatos,
	apiCatalogoEliminar,
	apiCatalogoOpciones,
	apiCatalogosPaneles,
} from "@/lib/api/catalogos";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/catalogos", () => ({
	apiCatalogosPaneles: vi.fn(),
	apiCatalogoDatos: vi.fn(),
	apiCatalogoOpciones: vi.fn(),
	apiCatalogoCrear: vi.fn(),
	apiCatalogoActualizar: vi.fn(),
	apiCatalogoEliminar: vi.fn(),
}));

vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		aoa_to_sheet: vi.fn(() => ({})),
		book_append_sheet: vi.fn(),
	},
	writeFile: vi.fn(),
}));

const DESCRIPTOR = {
	clave: "estados",
	tabla: "cat.estados",
	nombre: "Estados",
	pk: "estado_id",
	soportaBajaLogica: true,
	listasValidas: { tipo_default: ["ENTRADA", "SALIDA"] },
	campos: [
		{
			nombre: "nombre",
			tipo: "TEXT",
			requerido: true,
			unico: true,
			esActivo: false,
			clavesEditables: true,
			etiqueta: "Nombre",
		},
		{
			nombre: "clave",
			tipo: "TEXT",
			requerido: false,
			unico: true,
			esActivo: false,
			clavesEditables: false,
			etiqueta: "Clave",
		},
		{
			nombre: "orden",
			tipo: "NUMERO",
			requerido: false,
			unico: false,
			esActivo: false,
			clavesEditables: false,
			etiqueta: "Orden",
		},
		{
			nombre: "tasa",
			tipo: "DECIMAL",
			requerido: false,
			unico: false,
			esActivo: false,
			clavesEditables: false,
			etiqueta: "Tasa",
		},
		{
			nombre: "vigente",
			tipo: "FECHA",
			requerido: false,
			unico: false,
			esActivo: false,
			clavesEditables: false,
			etiqueta: "Vigente",
		},
		{
			nombre: "es_fijo",
			tipo: "BOOLEAN",
			requerido: false,
			unico: false,
			esActivo: false,
			clavesEditables: false,
			etiqueta: "Es fijo",
		},
		{
			nombre: "tipo_default",
			tipo: "TEXT",
			requerido: false,
			unico: false,
			esActivo: false,
			clavesEditables: false,
			etiqueta: "Tipo default",
		},
		{
			nombre: "estado_id",
			tipo: "NUMERO",
			requerido: false,
			unico: false,
			esActivo: false,
			clavesEditables: false,
			etiqueta: "Estado",
			opcionesTabla: "estados",
			opcionesColumnas: ["nombre"],
		},
		{
			nombre: "activo",
			tipo: "BOOLEAN",
			requerido: false,
			unico: false,
			esActivo: true,
			clavesEditables: false,
			etiqueta: "Activo",
		},
	],
};

const FILAS = [
	{
		__pk: 1,
		nombre: "Aguascalientes",
		clave: "AGS",
		orden: 1,
		tasa: 1.5,
		vigente: "2026-01-01",
		es_fijo: true,
		tipo_default: "ENTRADA",
		estado_id: 1,
	},
	{
		__pk: 2,
		nombre: "Jalisco",
		clave: null,
		orden: null,
		tasa: null,
		vigente: null,
		es_fijo: false,
		tipo_default: "SALIDA",
		estado_id: 2,
	},
];

function pageOf(data: typeof FILAS, totalElements = data.length) {
	return {
		success: true,
		data,
		meta: { page: 0, size: 20, totalElements, totalPages: 1 },
	};
}

function renderAt(clave = "estados") {
	const qc = new QueryClient({
		defaultOptions: {
			queries: { retry: false },
			mutations: { retry: false },
		},
	});
	const wrapper = () => (
		<MemoryRouter initialEntries={[`/catalogos/${clave}`]}>
			<QueryClientProvider client={qc}>
				<ToastProvider>
					<Routes>
						<Route path="/catalogos/:clave" element={<CatalogoCrudPage />} />
					</Routes>
				</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>
	);
	return render(<div />, { wrapper });
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	vi.mocked(apiCatalogosPaneles).mockResolvedValue([DESCRIPTOR] as never);
	vi.mocked(apiCatalogoDatos).mockResolvedValue(pageOf(FILAS) as never);
	vi.mocked(apiCatalogoOpciones).mockResolvedValue([
		{ clave: 1, nombre: "Aguascalientes" },
		{ clave: 2, nombre: "Jalisco" },
	] as never);
});

describe("CatalogoCrudPage", () => {
	it("renderiza tabla con badges booleanos y em-dash en nulos", async () => {
		renderAt();
		expect(await screen.findByText("Aguascalientes")).toBeInTheDocument();
		expect(screen.getByText("Jalisco")).toBeInTheDocument();
		expect(screen.getAllByText("Sí")[0]).toBeInTheDocument();
		expect(screen.getByText("No")).toBeInTheDocument();
		// celdas nulas muestran "—"
		expect(screen.getAllByText("—").length).toBeGreaterThan(0);
		expect(
			screen.getByRole("button", { name: /Nuevo/ }),
		).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /Excel/i })).toBeInTheDocument();
	});

	it("avisa cuando la clave no tiene descriptor", async () => {
		vi.mocked(apiCatalogosPaneles).mockResolvedValue([]);
		renderAt("desconocido");
		expect(
			await screen.findByText("Catálogo no disponible."),
		).toBeInTheDocument();
	});

	it("busca por texto y re-consulta con q", async () => {
		const user = userEvent.setup();
		renderAt();
		await screen.findByText("Aguascalientes");
		await user.type(screen.getByPlaceholderText("Buscar…"), "jalis");
		await user.click(screen.getByRole("button", { name: /Buscar/ }));
		expect(apiCatalogoDatos).toHaveBeenLastCalledWith(
			expect.objectContaining({ clave: "estados", q: "jalis", page: 0 }),
		);
	});

	it("valida requeridos y crea con todos los tipos de campo", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCatalogoCrear).mockResolvedValue(undefined as never);
		renderAt();
		await screen.findByText("Aguascalientes");
		await user.click(screen.getByRole("button", { name: /Nuevo/ }));
		const dialogo = await screen.findByRole("dialog");
		// El form no usa noValidate: el submit nativo se bloquea en vacío.
		// fireEvent.submit salta la validación nativa y ejerce la rama React.
		const form = within(dialogo)
			.getByLabelText(/Nombre \*/)
			.closest("form")!;
		fireEvent.submit(form);
		expect(
			within(dialogo).getByText("Completa los campos obligatorios."),
		).toBeInTheDocument();
		expect(apiCatalogoCrear).not.toHaveBeenCalled();

		await user.type(within(dialogo).getByLabelText(/Nombre \*/), "Zacatecas");
		await user.type(within(dialogo).getByLabelText("Clave"), "ZAC");
		await user.type(within(dialogo).getByLabelText("Orden"), "3");
		await user.type(within(dialogo).getByLabelText("Tasa"), "2.5");
		await user.type(within(dialogo).getByLabelText("Vigente"), "2026-05-01");
		await user.selectOptions(within(dialogo).getByLabelText("Es fijo"), "true");
		await user.selectOptions(
			within(dialogo).getByLabelText("Tipo default"),
			"ENTRADA",
		);
		await user.selectOptions(within(dialogo).getByLabelText("Estado"), "1");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(apiCatalogoCrear).toHaveBeenCalledWith(
			"estados",
			expect.objectContaining({
				nombre: "Zacatecas",
				clave: "ZAC",
				orden: 3,
				tasa: 2.5,
				vigente: "2026-05-01",
				es_fijo: true,
				tipo_default: "ENTRADA",
				estado_id: 1,
			}),
		);
	});

	it("edita prellenando el formulario y bloquea la PK única", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCatalogoActualizar).mockResolvedValue(undefined as never);
		renderAt();
		await screen.findByText("Aguascalientes");
		await user.click(screen.getAllByRole("button", { name: "Editar" })[0]);
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByLabelText(/Nombre \*/)).toHaveValue(
			"Aguascalientes",
		);
		expect(within(dialogo).getByLabelText("Clave")).toBeDisabled();
		// La PK única no se edita: se cambia otro campo y se reenvía tal cual.
		await user.clear(within(dialogo).getByLabelText("Orden"));
		await user.type(within(dialogo).getByLabelText("Orden"), "9");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(apiCatalogoActualizar).toHaveBeenCalledWith(
			"estados",
			1,
			expect.objectContaining({ nombre: "Aguascalientes", clave: "AGS", orden: 9 }),
		);
	});

	it("elimina con confirmación y maneja error de crear", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCatalogoEliminar).mockResolvedValue(undefined as never);
		renderAt();
		await screen.findByText("Aguascalientes");
		await user.click(screen.getAllByRole("button", { name: "Eliminar" })[0]);
		expect(
			await screen.findByText(/Se conserva en el historial/),
		).toBeInTheDocument();
		await user.click(
			within(screen.getByRole("dialog")).getByRole("button", {
				name: "Eliminar",
			}),
		);
		expect(apiCatalogoEliminar).toHaveBeenCalledWith("estados", 1);
	});

	it("muestra error si crear falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCatalogoCrear).mockRejectedValue(new Error("falló red"));
		renderAt();
		await screen.findByText("Aguascalientes");
		await user.click(screen.getByRole("button", { name: /Nuevo/ }));
		const dialogo = await screen.findByRole("dialog");
		await user.type(within(dialogo).getByLabelText(/Nombre \*/), "Otro");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("falló red") }),
		);
		// el diálogo sigue abierto tras el error
		expect(screen.getByRole("dialog")).toBeInTheDocument();
	});

	it("pagina cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCatalogoDatos).mockResolvedValue(
			pageOf(FILAS, 25) as never,
		);
		// totalPages debe ser > 1 para paginar
		vi.mocked(apiCatalogoDatos).mockResolvedValue({
			success: true,
			data: FILAS,
			meta: { page: 0, size: 20, totalElements: 25, totalPages: 2 },
		} as never);
		renderAt();
		await screen.findByText("Aguascalientes");
		await user.click(screen.getByRole("button", { name: "Página siguiente" }));
		expect(apiCatalogoDatos).toHaveBeenLastCalledWith(
			expect.objectContaining({ page: 1 }),
		);
	});

	it("exporta a Excel lo visible", async () => {
		const user = userEvent.setup();
		renderAt();
		await screen.findByText("Aguascalientes");
		await user.click(screen.getByRole("button", { name: /Excel/i }));
		await waitFor(() =>
			expect(vi.mocked(writeFile)).toHaveBeenCalledTimes(1),
		);
	});
});
