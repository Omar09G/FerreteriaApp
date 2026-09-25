import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import Swal from "sweetalert2";

import { ToastProvider } from "@/components/ui/Toast";
import EmpleadosPage from "@/features/rrhh/EmpleadosPage";
import { apiBajaEmpleado, apiCrearEmpleado, apiEmpleados, apiRoles } from "@/lib/api/admin";
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

const EMPLEADO_BAJA = {
	empleadoId: 4,
	puestoId: 5,
	puestoNombre: "Almacenista",
	nombre: "Ana",
	apellidoPaterno: "Ruiz",
	apellidoMaterno: null,
	curp: null,
	nss: null,
	telefono: null,
	email: null,
	calle: null,
	colonia: null,
	ciudadId: null,
	cp: null,
	fechaIngreso: null,
	fechaBaja: "2026-02-01",
	sueldoDiario: 300,
	activo: false,
	fotoUrl: "https://tienda.example.com/fotos/ana.png",
};

describe("EmpleadosPage (profundización)", () => {
	it("muestra empleado dado de baja con foto, guiones y sin botón de baja", async () => {
		vi.mocked(apiEmpleados).mockResolvedValueOnce({
			success: true,
			data: [EMPLEADO_BAJA],
			meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
		} as never);
		renderPage();
		expect(await screen.findByText("Baja")).toBeInTheDocument();
		expect(screen.getByAltText("Ana Ruiz")).toBeInTheDocument();
		expect(
			screen.queryByRole("button", { name: "Baja de Ana" }),
		).not.toBeInTheDocument();
	});

	it("registra un empleado válido con usuario del sistema y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearEmpleado).mockResolvedValueOnce({ ...EMPLEADO, empleadoId: 9 } as never);
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: /Nuevo empleado/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo empleado" });
		await user.selectOptions(within(dialogo).getByLabelText(/Puesto/), "4");
		await user.type(within(dialogo).getByLabelText(/^Nombre/), "Pedro");
		await user.type(within(dialogo).getByLabelText(/Apellido paterno/), "Sosa");
		await user.type(within(dialogo).getByLabelText(/CURP/), "sosa010101habc");
		await user.type(within(dialogo).getByLabelText(/Sueldo diario/), "400");
		await user.type(within(dialogo).getByLabelText(/^Usuario/), "pedro1");
		await user.type(within(dialogo).getByLabelText(/Contraseña/), "secreto123");
		await user.click(within(dialogo).getByLabelText("Vendedor"));
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiCrearEmpleado)).toHaveBeenCalledWith({
				puestoId: 4,
				nombre: "Pedro",
				apellidoPaterno: "Sosa",
				apellidoMaterno: undefined,
				curp: "SOSA010101HABC",
				nss: undefined,
				telefono: undefined,
				email: undefined,
				sueldoDiario: 400,
				fotoUrl: undefined,
				username: "pedro1",
				password: "secreto123",
				roles: ["VENDEDOR"],
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Empleado registrado"),
			}),
		);
	});

	it("registra sin usuario del sistema cuando no se captura acceso", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearEmpleado).mockResolvedValueOnce({ ...EMPLEADO, empleadoId: 10 } as never);
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: /Nuevo empleado/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo empleado" });
		await user.selectOptions(within(dialogo).getByLabelText(/Puesto/), "5");
		await user.type(within(dialogo).getByLabelText(/^Nombre/), "Luz");
		await user.type(within(dialogo).getByLabelText(/Apellido paterno/), "Díaz");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiCrearEmpleado)).toHaveBeenCalledWith(
				expect.objectContaining({ puestoId: 5, nombre: "Luz" }),
			),
		);
		const body = vi.mocked(apiCrearEmpleado).mock.calls[0][0] as Record<
			string,
			unknown
		>;
		expect(body).not.toHaveProperty("username");
	});

	it("valida campos obligatorios y contraseña corta del usuario opcional", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: /Nuevo empleado/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo empleado" });
		await user.type(within(dialogo).getByLabelText(/^Nombre/), "Pedro");
		await user.type(within(dialogo).getByLabelText(/^Usuario/), "pedro1");
		await user.type(within(dialogo).getByLabelText(/Contraseña/), "corta");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(
			await within(dialogo).findByText(
				"Completa los campos obligatorios y revisa la contraseña.",
			),
		).toBeInTheDocument();
		expect(apiCrearEmpleado).not.toHaveBeenCalled();
	});

	it("alterna roles en el formulario antes de guardar", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: /Nuevo empleado/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo empleado" });
		const check = within(dialogo).getByLabelText("Vendedor");
		await user.click(check);
		expect(check).toBeChecked();
		await user.click(check);
		expect(check).not.toBeChecked();
	});

	it("muestra toast si registrar falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiCrearEmpleado).mockRejectedValueOnce(new Error("curp duplicada"));
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: /Nuevo empleado/ }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo empleado" });
		await user.selectOptions(within(dialogo).getByLabelText(/Puesto/), "4");
		await user.type(within(dialogo).getByLabelText(/^Nombre/), "Pedro");
		await user.type(within(dialogo).getByLabelText(/Apellido paterno/), "Sosa");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("curp duplicada") }),
			),
		);
	});

	it("confirma la baja y muestra éxito", async () => {
		const user = userEvent.setup();
		vi.mocked(apiBajaEmpleado).mockResolvedValueOnce(undefined as never);
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: "Baja de Juan" }));
		const dialogo = await screen.findByRole("dialog", { name: "Confirmar baja" });
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, dar de baja" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiBajaEmpleado)).toHaveBeenCalledWith(3),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Empleado dado de baja"),
			}),
		);
	});

	it("cancela la baja sin llamar al backend", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: "Baja de Juan" }));
		const dialogo = await screen.findByRole("dialog", { name: "Confirmar baja" });
		await user.click(within(dialogo).getByRole("button", { name: /Cancelar/ }));
		await waitFor(() =>
			expect(
				screen.queryByRole("dialog", { name: "Confirmar baja" }),
			).not.toBeInTheDocument(),
		);
		expect(apiBajaEmpleado).not.toHaveBeenCalled();
	});

	it("muestra toast si la baja falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiBajaEmpleado).mockRejectedValueOnce(
			new Error("tiene adeudos"),
		);
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: "Baja de Juan" }));
		const dialogo = await screen.findByRole("dialog", { name: "Confirmar baja" });
		await user.click(
			within(dialogo).getByRole("button", { name: "Sí, dar de baja" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("tiene adeudos") }),
			),
		);
	});

	it("pagina la lista cuando hay más de una página", async () => {
		const user = userEvent.setup();
		vi.mocked(apiEmpleados).mockResolvedValue({
			success: true,
			data: [EMPLEADO],
			meta: { page: 0, size: 15, totalElements: 30, totalPages: 2 },
		} as never);
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: /Página siguiente/i }));
		await waitFor(() =>
			expect(vi.mocked(apiEmpleados)).toHaveBeenCalledWith(1),
		);
	});

	it("exporta los empleados visibles a Excel", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText(/Pérez/);
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^empleados-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});

	it("muestra toast si la lista falla al cargar", async () => {
		vi.mocked(apiEmpleados).mockRejectedValueOnce(new Error("sin conexión"));
		renderPage();
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("sin conexión") }),
			),
		);
	});
});
