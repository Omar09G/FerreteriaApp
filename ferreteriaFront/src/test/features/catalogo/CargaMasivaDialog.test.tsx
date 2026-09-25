import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

import CargaMasivaDialog from "@/features/catalogo/CargaMasivaDialog";
import { ToastProvider } from "@/components/ui/Toast";
import Swal from "sweetalert2";
import {
	apiCargaMasivaProductos,
	apiCategoriasArbol,
	apiMarcas,
	apiUnidadesMedida,
} from "@/lib/api/catalogo";
import { read, utils, writeFile } from "xlsx";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiCargaMasivaProductos: vi.fn(),
	apiCategoriasArbol: vi.fn(),
	apiMarcas: vi.fn(),
	apiUnidadesMedida: vi.fn(),
}));

vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		json_to_sheet: vi.fn(() => ({})),
		book_append_sheet: vi.fn(),
		sheet_to_json: vi.fn(),
	},
	writeFile: vi.fn(),
	read: vi.fn(),
}));

const CATS = [
	{
		categoriaId: 5,
		nombre: "Herramientas",
		categoriaPadreId: null,
		ruta: "Herramientas",
		nivel: 0,
		hijos: [
			{
				categoriaId: 6,
				nombre: "Manuales",
				categoriaPadreId: 5,
				ruta: "Herramientas · Manuales",
				nivel: 1,
			},
		],
	},
];
const MARCAS = [{ marcaId: 1, nombre: "Acme" }];
const UMS = [
	{ unidadId: 1, clave: "PZA", nombre: "Pieza", permiteFraccion: false },
];

const FILA_VALIDA = {
	codigo: "TAL-001",
	tipo: "producto",
	nombre: "Taladro",
	descripcion: "percutor",
	categoria: "Manuales",
	marca: "Acme",
	unidadMedida: "Pieza",
	costoActual: 100,
	precioMenudeo: 150,
	precioMayoreo: 140,
	aplicaIva: "SI",
	codigosBarras: "7501; 7502",
};

const FILA_VALIDA_CLAVE = {
	codigo: "",
	tipo: "SERVICIO",
	nombre: "Afilado",
	descripcion: "",
	categoria: "herramientas",
	marca: "",
	unidadMedida: "PZA",
	costoActual: "",
	precioMenudeo: "",
	precioMayoreo: "",
	aplicaIva: "NO",
	codigosBarras: "",
};

function renderDialog(onClose = vi.fn()) {
	const qc = new QueryClient({
		defaultOptions: {
			queries: { retry: false },
			mutations: { retry: false },
		},
	});
	const wrapper = ({ children }: { children: ReactNode }) => (
		<QueryClientProvider client={qc}>
			<ToastProvider>{children}</ToastProvider>
		</QueryClientProvider>
	);
	return {
		onClose,
		...render(<CargaMasivaDialog open onClose={onClose} />, { wrapper }),
	};
}

function xlsxFile() {
	const f = new File(["fake"], "productos.xlsx", {
		type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
	});
	Object.defineProperty(f, "arrayBuffer", {
		value: async () => new Uint8Array([1, 2, 3]).buffer,
	});
	return f;
}

async function subirArchivo(rows: Record<string, unknown>[]) {
	// Las validaciones usan los catálogos resueltos: esperar a que React Query
	// los tenga antes de disparar el change (el handler cierra sobre ellos).
	await waitFor(() => {
		expect(vi.mocked(apiCategoriasArbol)).toHaveBeenCalled();
		expect(vi.mocked(apiMarcas)).toHaveBeenCalled();
		expect(vi.mocked(apiUnidadesMedida)).toHaveBeenCalled();
	});
	await act(async () => {
		await new Promise((r) => setTimeout(r, 0));
	});
	vi.mocked(read).mockReturnValue({
		SheetNames: ["productos"],
		Sheets: { productos: {} },
	} as never);
	vi.mocked(utils.sheet_to_json).mockReturnValue(rows as never);
	const input = document.getElementById(
		"carga-masiva-xlsx",
	) as HTMLInputElement;
	fireEvent.change(input, { target: { files: [xlsxFile()] } });
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	vi.mocked(apiCategoriasArbol).mockResolvedValue(CATS as never);
	vi.mocked(apiMarcas).mockResolvedValue(MARCAS as never);
	vi.mocked(apiUnidadesMedida).mockResolvedValue(UMS as never);
});

describe("CargaMasivaDialog", () => {
	it("renderiza acciones principales", () => {
		renderDialog();
		expect(
			screen.getByRole("button", { name: /Descargar plantilla/ }),
		).toBeInTheDocument();
		expect(screen.getByText("Elegir archivo .xlsx")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Crear 0 productos/ }),
		).toBeDisabled();
	});

	it("descarga la plantilla con el nombre esperado", async () => {
		const user = userEvent.setup();
		renderDialog();
		await user.click(
			screen.getByRole("button", { name: /Descargar plantilla/ }),
		);
		expect(utils.book_new).toHaveBeenCalled();
		expect(utils.json_to_sheet).toHaveBeenCalledWith(
			[expect.objectContaining({ codigo: "TAL-001" })],
			expect.anything(),
		);
		expect(utils.book_append_sheet).toHaveBeenCalled();
		expect(writeFile).toHaveBeenCalledWith(
			expect.anything(),
			"plantilla-productos.xlsx",
		);
	});

	it("lista filas válidas (nombre, clave de unidad, categoría anidada)", async () => {
		renderDialog();
		await subirArchivo([FILA_VALIDA, FILA_VALIDA_CLAVE]);
		expect(
			await screen.findByText("2 de 2 filas listas para enviar."),
		).toBeInTheDocument();
		expect(screen.getByText(/Fila 2: Taladro/)).toBeInTheDocument();
		expect(screen.getByText(/Fila 3: Afilado/)).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Crear 2 productos/ }),
		).not.toBeDisabled();
	});

	it("muestra todos los errores de validación por fila", async () => {
		renderDialog();
		await subirArchivo([
			{
				...FILA_VALIDA,
				tipo: "XXX",
				nombre: "",
				categoria: "Desconocida",
				marca: "Falsa",
				unidadMedida: "ZZZ",
				costoActual: -5,
				precioMenudeo: "abc",
				codigosBarras: "a".repeat(51),
			},
			{ ...FILA_VALIDA, categoria: "", unidadMedida: "" },
		]);
		expect(
			await screen.findByText("0 de 2 filas listas para enviar."),
		).toBeInTheDocument();
		expect(screen.getByText(/tipo debe ser PRODUCTO/)).toBeInTheDocument();
		expect(screen.getByText(/nombre requerido/)).toBeInTheDocument();
		expect(
			screen.getByText(/categoría desconocida: Desconocida/),
		).toBeInTheDocument();
		expect(screen.getByText(/marca desconocida: Falsa/)).toBeInTheDocument();
		expect(
			screen.getByText(/unidad desconocida: ZZZ/),
		).toBeInTheDocument();
		expect(
			screen.getByText(/costoActual debe ser número ≥ 0/),
		).toBeInTheDocument();
		expect(
			screen.getByText(/precioMenudeo debe ser número ≥ 0/),
		).toBeInTheDocument();
		expect(
			screen.getByText(/máximo 50 caracteres/),
		).toBeInTheDocument();
		// NOTA: "duplicados en la fila" es rama muerta en el fuente
		// (!vistos.add() siempre es falsy: Set.add devuelve el set).
		expect(screen.getByText(/categoría requerida/)).toBeInTheDocument();
		expect(screen.getByText(/unidad requerida/)).toBeInTheDocument();
		expect(screen.getByText(/\(sin nombre\)/)).toBeInTheDocument();
	});

	it("avisa con libro sin hojas, hoja vacía y encabezados raros", async () => {
		renderDialog();
		await waitFor(() => {
			expect(vi.mocked(apiCategoriasArbol)).toHaveBeenCalled();
		});
		await act(async () => {
			await new Promise((r) => setTimeout(r, 0));
		});
		vi.mocked(read).mockReturnValue({
			SheetNames: [],
			Sheets: {},
		} as never);
		let input = document.getElementById(
			"carga-masiva-xlsx",
		) as HTMLInputElement;
		fireEvent.change(input, { target: { files: [xlsxFile()] } });
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("libro sin hojas"),
				}),
			);
		});

		vi.mocked(read).mockReturnValue({
			SheetNames: ["h"],
			Sheets: { h: {} },
		} as never);
		vi.mocked(utils.sheet_to_json).mockReturnValue([] as never);
		input = document.getElementById("carga-masiva-xlsx") as HTMLInputElement;
		fireEvent.change(input, { target: { files: [xlsxFile()] } });
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("no contiene filas de datos"),
				}),
			);
		});

		vi.mocked(utils.sheet_to_json).mockReturnValue([
			{ foo: "bar" },
		] as never);
		input = document.getElementById("carga-masiva-xlsx") as HTMLInputElement;
		fireEvent.change(input, { target: { files: [xlsxFile()] } });
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("encabezados no reconocidos"),
				}),
			);
		});
	});

	it("crea y cierra con carga limpia (mapea IVA, nulos y barras)", async () => {
		const user = userEvent.setup();
		const onClose = vi.fn();
		vi.mocked(apiCargaMasivaProductos).mockResolvedValue({
			creados: [{ productoId: 1 }, { productoId: 2 }],
			errores: [],
		} as never);
		renderDialog(onClose);
		await subirArchivo([FILA_VALIDA, FILA_VALIDA_CLAVE]);
		await screen.findByText("2 de 2 filas listas para enviar.");
		await user.click(screen.getByRole("button", { name: /Crear 2 productos/ }));
		expect(apiCargaMasivaProductos).toHaveBeenCalledWith([
			expect.objectContaining({
				codigo: "TAL-001",
				tipo: "PRODUCTO",
				categoriaId: 6,
				marcaId: 1,
				unidadMedidaId: 1,
				costoActual: 100,
				aplicaIva: true,
				codigosBarras: [{ codigo: "7501" }, { codigo: "7502" }],
			}),
			expect.objectContaining({
				codigo: undefined,
				tipo: "SERVICIO",
				categoriaId: 5,
				marcaId: null,
				unidadMedidaId: 1,
				costoActual: undefined,
				aplicaIva: false,
				codigosBarras: [],
			}),
		]);
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("2 productos creados."),
				}),
			);
		});
		expect(onClose).toHaveBeenCalled();
	});
	it("muestra reporte con errores del backend + validación local", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCargaMasivaProductos).mockResolvedValue({
			creados: [{ productoId: 1 }],
			errores: [{ fila: 1, codigo: "DUPLICADO", mensaje: "código ya existe" }],
		} as never);
		renderDialog();
		await subirArchivo([
			FILA_VALIDA,
			{ ...FILA_VALIDA, nombre: "", tipo: "XXX" },
		]);
		await screen.findByText("1 de 2 filas listas para enviar.");
		await user.click(screen.getByRole("button", { name: /Crear 1 productos/ }));
		expect(
			await screen.findByText("Resultado: 1 creados, 2 con error"),
		).toBeInTheDocument();
		// fila backend 1 → excel 2; validación local → excel 3
		expect(
			screen.getByText("Fila 2: [DUPLICADO] código ya existe"),
		).toBeInTheDocument();
		expect(screen.getByText(/Fila 3: \[VALIDACION_CLIENTE\]/)).toBeInTheDocument();
		const dialogo = screen.getByRole("dialog");
		expect(within(dialogo).getByText(/1 creados, 2 con error/)).toBeInTheDocument();
	});

	it("muestra error si el envío falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCargaMasivaProductos).mockRejectedValueOnce(
			new Error("error de red"),
		);
		renderDialog();
		await subirArchivo([FILA_VALIDA]);
		await screen.findByText("1 de 1 filas listas para enviar.");
		await user.click(screen.getByRole("button", { name: /Crear 1 productos/ }));
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("error de red"),
				}),
			);
		});
	});

	it("avisa con mensaje genérico si el fallo no es Error", async () => {
		renderDialog();
		await waitFor(() => {
			expect(vi.mocked(apiCategoriasArbol)).toHaveBeenCalled();
		});
		await act(async () => {
			await new Promise((r) => setTimeout(r, 0));
		});
		const f = new File(["fake"], "roto.xlsx", {
			type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
		});
		Object.defineProperty(f, "arrayBuffer", {
			value: async () => {
				throw "boom";
			},
		});
		const input = document.getElementById(
			"carga-masiva-xlsx",
		) as HTMLInputElement;
		fireEvent.change(input, { target: { files: [f] } });
		await vi.waitFor(() => {
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: "No se pudo leer el archivo .xlsx",
				}),
			);
		});
	});
});
