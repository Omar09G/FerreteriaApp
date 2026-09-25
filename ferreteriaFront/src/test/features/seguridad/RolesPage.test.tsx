import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import Swal from "sweetalert2";

import { ToastProvider } from "@/components/ui/Toast";
import RolesPage from "@/features/seguridad/RolesPage";
import {
	apiActualizarRol,
	apiCrearRol,
	apiEliminarRol,
	apiPermisos,
	apiPermisosDeRol,
	apiRolesPaginado,
	apiSetPermisosRol,
} from "@/lib/api/admin";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

const writeFile = vi.fn();
vi.mock("xlsx", () => ({
	utils: {
		book_new: vi.fn(() => ({})),
		aoa_to_sheet: vi.fn(() => ({})),
		book_append_sheet: vi.fn(),
	},
	writeFile: (...args: unknown[]) => writeFile(...args),
}));

vi.mock("@/lib/api/admin", () => ({
	apiActualizarRol: vi.fn(),
	apiCrearRol: vi.fn(),
	apiEliminarRol: vi.fn(),
	apiPermisos: vi.fn(),
	apiPermisosDeRol: vi.fn(),
	apiRoles: vi.fn(),
	apiRolesPaginado: vi.fn(),
	apiSetPermisosRol: vi.fn(),
}));

const ROL = {
	rolId: 1,
	clave: "ADMINISTRADOR",
	nombre: "Administrador",
	descripcion: "Acceso total",
	activo: true,
	permisos: ["USUARIOS_LEER", "ROLES_LEER"],
};

function renderPage() {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>
					<RolesPage />
				</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>,
	);
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	useAuthStore.setState({
		autenticado: true,
		usuario: { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] },
		lastActivityAt: Date.now(),
	});
	localStorage.clear();
	vi.mocked(apiRolesPaginado).mockResolvedValue({
		success: true,
		data: [ROL],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	} as never);
});

describe("RolesPage (smoke)", () => {
	it("renderiza título, tabla y botón de alta", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Roles y permisos" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nuevo rol/ }),
		).toBeInTheDocument();
		expect(await screen.findByText("ADMINISTRADOR")).toBeInTheDocument();
		expect(screen.getByText("Administrador")).toBeInTheDocument();
		expect(screen.getByText("Acceso total")).toBeInTheDocument();
	});

	it("abre el diálogo de nuevo rol", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(screen.getByRole("button", { name: /Nuevo rol/ }));
		expect(await screen.findByRole("heading", { name: "Nuevo rol" })).toBeInTheDocument();
		expect(screen.getByLabelText(/Clave/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Nombre/)).toBeInTheDocument();
	});

	it("abre el diálogo de edición desde la fila", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(
			screen.getByRole("button", { name: "Editar Administrador" }),
		);
		expect(
			await screen.findByText("Editar rol ADMINISTRADOR"),
		).toBeInTheDocument();
	});
});

const ROL_INACTIVO = {
	rolId: 2,
	clave: "CAJERO",
	nombre: "Cajero",
	descripcion: null,
	activo: false,
	permisos: ["VENTAS_COBRAR"],
};

const PERMISO = {
	clave: "USUARIOS_LEER",
	descripcion: "Leer usuarios",
};

describe("RolesPage (profundización)", () => {
	it("muestra rol inactivo y guion sin descripción", async () => {
		vi.mocked(apiRolesPaginado).mockResolvedValueOnce({
			success: true,
			data: [ROL, ROL_INACTIVO],
			meta: { page: 0, size: 15, totalElements: 2, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("CAJERO")).toBeInTheDocument();
		expect(screen.getByText("Inactivo")).toBeInTheDocument();
	});

	it("crea un rol válido en mayúsculas y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearRol).mockResolvedValueOnce({ ...ROL, rolId: 9 } as never);
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(screen.getByRole("button", { name: /Nuevo rol/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo rol" });
		await user.type(within(dialogo).getByLabelText(/Clave/), "cajero");
		await user.type(within(dialogo).getByLabelText(/Nombre/), "Cajero");
		await user.type(within(dialogo).getByLabelText(/Descripción/), "Opera caja");
		expect(
			within(dialogo).getByLabelText(/Clave/),
		).toHaveDisplayValue("CAJERO");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiCrearRol)).toHaveBeenCalledWith({
				clave: "CAJERO",
				nombre: "Cajero",
				descripcion: "Opera caja",
				activo: true,
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Rol creado") }),
		);
	});

	it("valida clave y nombre obligatorios al crear", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(screen.getByRole("button", { name: /Nuevo rol/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo rol" });
		await user.type(within(dialogo).getByLabelText(/Nombre/), "Cajero");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(
			await within(dialogo).findByText("Clave y nombre son obligatorios."),
		).toBeInTheDocument();
		expect(apiCrearRol).not.toHaveBeenCalled();
	});

	it("muestra toast si crear el rol falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearRol).mockRejectedValueOnce(new Error("clave duplicada"));
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(screen.getByRole("button", { name: /Nuevo rol/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo rol" });
		await user.type(within(dialogo).getByLabelText(/Clave/), "CAJERO");
		await user.type(within(dialogo).getByLabelText(/Nombre/), "Cajero");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("clave duplicada") }),
			),
		);
	});

	it("edita el rol con la clave bloqueada y guarda los cambios", async () => {
		const user = userEvent.setup();
		vi.mocked(apiActualizarRol).mockResolvedValueOnce({ ...ROL } as never);
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(
			screen.getByRole("button", { name: "Editar Administrador" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Editar rol ADMINISTRADOR",
		});
		expect(within(dialogo).getByLabelText(/Clave/)).toBeDisabled();
		const nombre = within(dialogo).getByLabelText(/Nombre/);
		await user.clear(nombre);
		await user.type(nombre, "Super Admin");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiActualizarRol)).toHaveBeenCalledWith(
				1,
				expect.objectContaining({ clave: "ADMINISTRADOR", nombre: "Super Admin" }),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Rol actualizado") }),
		);
	});

	it("muestra toast si actualizar el rol falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiActualizarRol).mockRejectedValueOnce(new Error("sin permiso"));
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(
			screen.getByRole("button", { name: "Editar Administrador" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Editar rol ADMINISTRADOR",
		});
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin permiso") }),
			),
		);
	});

	it("edita permisos del rol: alterna y guarda", async () => {
		const user = userEvent.setup();
		vi.mocked(apiPermisos).mockResolvedValueOnce([PERMISO] as never);
		vi.mocked(apiPermisosDeRol).mockResolvedValueOnce([] as never);
		vi.mocked(apiSetPermisosRol).mockResolvedValueOnce({ ok: true } as never);
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(
			screen.getByRole("button", { name: "Permisos de Administrador" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Permisos de ADMINISTRADOR",
		});
		const check = await within(dialogo).findByLabelText(/USUARIOS_LEER/);
		expect(check).not.toBeChecked();
		await user.click(check);
		await user.click(
			within(dialogo).getByRole("button", { name: /Guardar permisos/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiSetPermisosRol)).toHaveBeenCalledWith(1, [
				"USUARIOS_LEER",
			]),
		);
	});

	it("muestra toast si cargar los permisos del rol falla", async () => {
		vi.mocked(apiPermisos).mockResolvedValueOnce([PERMISO] as never);
		vi.mocked(apiPermisosDeRol).mockRejectedValueOnce(new Error("sin datos"));
		renderPage();
		const user = userEvent.setup();
		await screen.findByText("ADMINISTRADOR");
		await user.click(
			screen.getByRole("button", { name: "Permisos de Administrador" }),
		);
		await screen.findByRole("dialog", { name: "Permisos de ADMINISTRADOR" });
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin datos") }),
			),
		);
	});

	it("muestra toast si guardar permisos falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiPermisos).mockResolvedValueOnce([PERMISO] as never);
		vi.mocked(apiPermisosDeRol).mockResolvedValueOnce(["USUARIOS_LEER"] as never);
		vi.mocked(apiSetPermisosRol).mockRejectedValueOnce(
			new Error("no autorizado"),
		);
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(
			screen.getByRole("button", { name: "Permisos de Administrador" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Permisos de ADMINISTRADOR",
		});
		await within(dialogo).findByLabelText(/USUARIOS_LEER/);
		await user.click(
			within(dialogo).getByRole("button", { name: /Guardar permisos/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("no autorizado") }),
			),
		);
	});

	it("elimina el rol con confirmación", async () => {
		const user = userEvent.setup();
		vi.mocked(apiEliminarRol).mockResolvedValueOnce(undefined as never);
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(
			screen.getByRole("button", { name: "Eliminar Administrador" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar eliminación",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, eliminar" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiEliminarRol)).toHaveBeenCalledWith(1),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Rol eliminado") }),
		);
	});

	it("cancela la eliminación del rol sin llamar al backend", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(
			screen.getByRole("button", { name: "Eliminar Administrador" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar eliminación",
		});
		await user.click(within(dialogo).getByRole("button", { name: /Cancelar/ }));
		await waitFor(() =>
			expect(
				screen.queryByRole("dialog", { name: "Confirmar eliminación" }),
			).not.toBeInTheDocument(),
		);
		expect(apiEliminarRol).not.toHaveBeenCalled();
	});

	it("muestra toast si eliminar el rol falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiEliminarRol).mockRejectedValueOnce(
			new Error("rol en uso"),
		);
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(
			screen.getByRole("button", { name: "Eliminar Administrador" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar eliminación",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, eliminar" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("rol en uso") }),
			),
		);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiRolesPaginado).mockResolvedValue({
			success: true,
			data: [ROL],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiRolesPaginado)).toHaveBeenCalledWith(
				expect.objectContaining({ page: 1 }),
			),
		);
	});

	it("exporta los roles visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("ADMINISTRADOR");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^roles-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiRolesPaginado).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
