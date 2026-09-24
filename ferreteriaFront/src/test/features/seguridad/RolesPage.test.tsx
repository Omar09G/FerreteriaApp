import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import RolesPage from "@/features/seguridad/RolesPage";
import { apiRolesPaginado } from "@/lib/api/admin";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
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
