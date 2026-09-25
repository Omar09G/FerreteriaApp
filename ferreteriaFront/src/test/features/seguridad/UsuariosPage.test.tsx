import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import Swal from "sweetalert2";

import { ToastProvider } from "@/components/ui/Toast";
import UsuariosPage from "@/features/seguridad/UsuariosPage";
import {
	apiCrearUsuario,
	apiEliminarUsuario,
	apiResetPassword,
	apiRoles,
	apiSetRolesUsuario,
	apiUsuarios,
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
	apiCrearUsuario: vi.fn(),
	apiEliminarUsuario: vi.fn(),
	apiResetPassword: vi.fn(),
	apiRoles: vi.fn(),
	apiSetRolesUsuario: vi.fn(),
	apiUsuarios: vi.fn(),
}));

const META = { page: 0, size: 15, totalElements: 1, totalPages: 1 };

const USUARIO = {
	usuarioId: 1,
	username: "admin1",
	email: "admin1@example.com",
	empleadoId: null,
	activo: true,
	roles: ["ADMINISTRADOR"],
	empleado: null,
	ultimoLogin: null,
	creadoEn: "2026-01-01T12:00:00Z",
};

const ROL = {
	rolId: 1,
	clave: "ADMINISTRADOR",
	nombre: "Administrador",
	descripcion: null,
	activo: true,
	permisos: [],
};

function renderPage() {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>
					<UsuariosPage />
				</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>,
	);
}

function paginar(data: unknown[]) {
	return { success: true, data, meta: META };
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
	vi.mocked(apiUsuarios).mockResolvedValue(paginar([USUARIO]) as never);
	vi.mocked(apiRoles).mockResolvedValue([ROL] as never);
});

describe("UsuariosPage (smoke)", () => {
	it("renderiza título, tabla y botón de alta", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Usuarios y roles" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nuevo usuario/ }),
		).toBeInTheDocument();
		expect(await screen.findByText("admin1")).toBeInTheDocument();
		expect(screen.getByText("admin1@example.com")).toBeInTheDocument();
		expect(screen.getByText("ADMINISTRADOR")).toBeInTheDocument();
	});

	it("abre el diálogo de nuevo usuario", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: /Nuevo usuario/ }));
		expect(
			await screen.findByRole("heading", { name: "Nuevo usuario" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Usuario/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Correo/)).toBeInTheDocument();
	});

	it("abre el diálogo de edición de roles desde la fila", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("admin1");
		await user.click(
			screen.getByRole("button", { name: "Editar roles de admin1" }),
		);
		expect(await screen.findByText("Roles de admin1")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Guardar roles/ }),
		).toBeInTheDocument();
	});
});

const USUARIO_INACTIVO = {
	usuarioId: 2,
	username: "cajero1",
	email: "cajero1@example.com",
	empleadoId: 9,
	activo: false,
	roles: ["CAJERO"],
	empleado: { nombreCompleto: "María Cajera" },
	ultimoLogin: "2026-02-01T09:00:00Z",
	creadoEn: "2026-01-02T12:00:00Z",
};

describe("UsuariosPage (profundización)", () => {
	it("muestra empleado, estado inactivo y último login en la tabla", async () => {
		vi.mocked(apiUsuarios).mockResolvedValueOnce(
			paginar([USUARIO, USUARIO_INACTIVO]) as never,
		);
		renderPage();
		expect(await screen.findByText("cajero1")).toBeInTheDocument();
		expect(screen.getByText("Inactivo")).toBeInTheDocument();
		expect(screen.getByText("María Cajera")).toBeInTheDocument();
	});

	it("crea un usuario válido con rol y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearUsuario).mockResolvedValueOnce({ ...USUARIO, usuarioId: 3 } as never);
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: /Nuevo usuario/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo usuario" });
		await user.type(within(dialogo).getByLabelText(/Usuario/), "nuevo1");
		await user.type(within(dialogo).getByLabelText(/Correo/), "nuevo1@example.com");
		await user.type(within(dialogo).getByLabelText(/Contraseña/), "secreto123");
		await user.click(within(dialogo).getByLabelText("Administrador"));
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiCrearUsuario)).toHaveBeenCalledWith({
				username: "nuevo1",
				email: "nuevo1@example.com",
				password: "secreto123",
				roles: ["ADMINISTRADOR"],
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Usuario creado") }),
		);
	});

	it("valida el formulario de alta y no llama al backend si es inválido", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: /Nuevo usuario/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo usuario" });
		await user.type(within(dialogo).getByLabelText(/Usuario/), "nuevo1");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(
			await within(dialogo).findByText(
				"Usuario, correo y contraseña (mín. 8) son obligatorios.",
			),
		).toBeInTheDocument();
		expect(apiCrearUsuario).not.toHaveBeenCalled();
	});

	it("alterna roles en el formulario de alta antes de guardar", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: /Nuevo usuario/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo usuario" });
		const check = within(dialogo).getByLabelText("Administrador");
		await user.click(check);
		expect(check).toBeChecked();
		await user.click(check);
		expect(check).not.toBeChecked();
	});

	it("muestra toast si crear el usuario falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearUsuario).mockRejectedValueOnce(new Error("username duplicado"));
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: /Nuevo usuario/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo usuario" });
		await user.type(within(dialogo).getByLabelText(/Usuario/), "nuevo1");
		await user.type(within(dialogo).getByLabelText(/Correo/), "nuevo1@example.com");
		await user.type(within(dialogo).getByLabelText(/Contraseña/), "secreto123");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("username duplicado") }),
			),
		);
	});

	it("guarda los roles editados del usuario", async () => {
		const user = userEvent.setup();
		vi.mocked(apiSetRolesUsuario).mockResolvedValueOnce({ ...USUARIO } as never);
		renderPage();
		await screen.findByText("admin1");
		await user.click(
			screen.getByRole("button", { name: "Editar roles de admin1" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Roles de admin1",
		});
		const check = within(dialogo).getByLabelText("Administrador");
		expect(check).toBeChecked();
		await user.click(check);
		await user.click(check);
		await user.click(
			within(dialogo).getByRole("button", { name: /Guardar roles/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiSetRolesUsuario)).toHaveBeenCalledWith(1, [
				"ADMINISTRADOR",
			]),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Roles actualizados") }),
		);
	});

	it("muestra toast si guardar roles falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiSetRolesUsuario).mockRejectedValueOnce(new Error("rol inválido"));
		renderPage();
		await screen.findByText("admin1");
		await user.click(
			screen.getByRole("button", { name: "Editar roles de admin1" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Roles de admin1",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: /Guardar roles/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("rol inválido") }),
			),
		);
	});

	it("restablece la contraseña con una válida y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiResetPassword).mockResolvedValueOnce({ ok: true } as never);
		renderPage();
		await screen.findByText("admin1");
		await user.click(
			screen.getByRole("button", { name: "Restablecer contraseña de admin1" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Restablecer contraseña de admin1",
		});
		await user.type(
			within(dialogo).getByLabelText(/Nueva contraseña/),
			"nueva1234",
		);
		await user.click(
			within(dialogo).getByRole("button", { name: /Restablecer/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiResetPassword)).toHaveBeenCalledWith(1, "nueva1234"),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Contraseña restablecida"),
			}),
		);
	});

	it("valida el mínimo de 8 caracteres al restablecer", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("admin1");
		await user.click(
			screen.getByRole("button", { name: "Restablecer contraseña de admin1" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Restablecer contraseña de admin1",
		});
		await user.type(within(dialogo).getByLabelText(/Nueva contraseña/), "corta");
		await user.click(
			within(dialogo).getByRole("button", { name: /Restablecer/ }),
		);
		expect(
			await within(dialogo).findByText(
				"La contraseña debe tener al menos 8 caracteres.",
			),
		).toBeInTheDocument();
		expect(apiResetPassword).not.toHaveBeenCalled();
	});

	it("restablece con Enter cuando la contraseña es válida", async () => {
		vi.mocked(apiResetPassword).mockResolvedValueOnce({ ok: true } as never);
		renderPage();
		await screen.findByText("admin1");
		const user = userEvent.setup();
		await user.click(
			screen.getByRole("button", { name: "Restablecer contraseña de admin1" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Restablecer contraseña de admin1",
		});
		const input = within(dialogo).getByLabelText(/Nueva contraseña/);
		await user.type(input, "nueva1234");
		fireEvent.keyDown(input, { key: "Enter" });
		await waitFor(() =>
			expect(vi.mocked(apiResetPassword)).toHaveBeenCalledWith(1, "nueva1234"),
		);
	});

	it("muestra toast si restablecer la contraseña falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiResetPassword).mockRejectedValueOnce(new Error("error interno"));
		renderPage();
		await screen.findByText("admin1");
		await user.click(
			screen.getByRole("button", { name: "Restablecer contraseña de admin1" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Restablecer contraseña de admin1",
		});
		await user.type(
			within(dialogo).getByLabelText(/Nueva contraseña/),
			"nueva1234",
		);
		await user.click(
			within(dialogo).getByRole("button", { name: /Restablecer/ }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("error interno") }),
			),
		);
	});

	it("elimina con confirmación y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiEliminarUsuario).mockResolvedValueOnce({ ok: true } as never);
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: "Eliminar admin1" }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar eliminación",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, eliminar" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiEliminarUsuario)).toHaveBeenCalledWith(1),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Usuario eliminado") }),
		);
	});

	it("cancela la eliminación sin llamar al backend", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: "Eliminar admin1" }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar eliminación",
		});
		await user.click(within(dialogo).getByRole("button", { name: /Cancelar/ }));
		await waitFor(() =>
			expect(
				screen.queryByRole("dialog", { name: "Confirmar eliminación" }),
			).not.toBeInTheDocument(),
		);
		expect(apiEliminarUsuario).not.toHaveBeenCalled();
	});

	it("muestra toast si eliminar falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiEliminarUsuario).mockRejectedValueOnce(
			new Error("no se puede eliminar"),
		);
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: "Eliminar admin1" }));
		const dialogo = await screen.findByRole("dialog", {
			name: "Confirmar eliminación",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, eliminar" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("no se puede eliminar"),
				}),
			),
		);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiUsuarios).mockResolvedValue({
			success: true,
			data: [USUARIO],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiUsuarios)).toHaveBeenCalledWith(1),
		);
	});

	it("exporta los usuarios visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("admin1");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^usuarios-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiUsuarios).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
