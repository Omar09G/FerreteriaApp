import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import EmpleadosPage from "@/features/rrhh/EmpleadosPage";
import { apiBajaEmpleado, apiCrearEmpleado, apiEmpleados, apiRoles } from "@/lib/api/admin";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/admin", () => ({
	apiBajaEmpleado: vi.fn(),
	apiCrearEmpleado: vi.fn(),
	apiEmpleados: vi.fn(),
	apiRoles: vi.fn(),
}));

const EMPLEADO = {
	empleadoId: 3,
	puestoId: 1,
	puestoNombre: "Vendedor",
	nombre: "Juan",
	apellidoPaterno: "Pérez",
	apellidoMaterno: "López",
	curp: null,
	nss: null,
	telefono: "5551234567",
	email: "juan@example.com",
	calle: null,
	colonia: null,
	ciudadId: null,
	cp: null,
	fechaIngreso: "2025-01-15",
	fechaBaja: null,
	sueldoDiario: 350,
	activo: true,
	fotoUrl: null,
};

const ROL = {
	rolId: 2,
	clave: "VENDEDOR",
	nombre: "Vendedor",
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
					<EmpleadosPage />
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
	vi.mocked(apiEmpleados).mockResolvedValue({
		success: true,
		data: [EMPLEADO],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	} as never);
	vi.mocked(apiRoles).mockResolvedValue([ROL] as never);
});

describe("EmpleadosPage (smoke)", () => {
	it("renderiza título, tabla y botón de alta", async () => {
		renderPage();
		expect(screen.getByRole("heading", { name: "Empleados" })).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nuevo empleado/ }),
		).toBeInTheDocument();
		expect(await screen.findByText(/Pérez/)).toBeInTheDocument();
		expect(screen.getByText("Vendedor")).toBeInTheDocument();
		expect(screen.getByText("juan@example.com")).toBeInTheDocument();
		expect(screen.getByText("Activo")).toBeInTheDocument();
	});

	it("abre el diálogo de nuevo empleado", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: /Nuevo empleado/ }));
		expect(
			await screen.findByRole("heading", { name: "Nuevo empleado" }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Puesto/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Apellido paterno/)).toBeInTheDocument();
	});

	it("pide confirmación al dar de baja desde la fila", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: "Baja de Juan" }));
		expect(await screen.findByText("Confirmar baja")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Sí, dar de baja/ }),
		).toBeInTheDocument();
		expect(apiBajaEmpleado).not.toHaveBeenCalled();
		expect(apiCrearEmpleado).toBeDefined();
	});
});
