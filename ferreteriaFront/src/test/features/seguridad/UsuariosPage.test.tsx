import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import UsuariosPage from "@/features/seguridad/UsuariosPage";
import {
	apiRoles,
	apiUsuarios,
} from "@/lib/api/admin";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
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
