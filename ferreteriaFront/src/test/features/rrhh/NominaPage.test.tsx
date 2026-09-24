import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import NominaPage from "@/features/rrhh/NominaPage";
import { apiEmpleados, apiNomina } from "@/lib/api/admin";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/admin", () => ({
	apiCancelarNomina: vi.fn(),
	apiCrearNomina: vi.fn(),
	apiEmpleados: vi.fn(),
	apiGenerarQuincena: vi.fn(),
	apiNomina: vi.fn(),
	apiPagarNomina: vi.fn(),
	apiPagarNominaLote: vi.fn(),
}));

const NOMINA = {
	nominaId: 7,
	empleadoId: 3,
	empleado: "Pérez López Juan",
	periodoIni: "2026-09-01",
	periodoFin: "2026-09-15",
	diasPagados: 15,
	percepciones: 5250,
	deducciones: 250,
	netoPagar: 5000,
	estado: "PENDIENTE",
	fechaPago: null,
	usuarioRegistraId: 1,
	notas: null,
};

const EMPLEADO = {
	empleadoId: 3,
	puestoId: 1,
	puestoNombre: "Vendedor",
	nombre: "Juan",
	apellidoPaterno: "Pérez",
	apellidoMaterno: "López",
	curp: null,
	nss: null,
	telefono: null,
	email: null,
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

function renderPage() {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>
					<NominaPage />
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
		meta: { page: 0, size: 100, totalElements: 1, totalPages: 1 },
	} as never);
	vi.mocked(apiNomina).mockResolvedValue({
		success: true,
		data: [NOMINA],
		meta: { page: 0, size: 15, totalElements: 1, totalPages: 1 },
	} as never);
});

describe("NominaPage (smoke)", () => {
	it("renderiza título, tabla y acciones principales", async () => {
		renderPage();
		expect(screen.getByRole("heading", { name: "Nómina" })).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Nueva nómina/ }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /Generar nómina/ }),
		).toBeInTheDocument();
		expect(await screen.findByText("Pérez López Juan")).toBeInTheDocument();
		expect(screen.getByText("PENDIENTE")).toBeInTheDocument();
		expect(screen.getByLabelText("Filtrar por estado")).toBeInTheDocument();
	});

	it("abre el diálogo de nueva nómina", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Nueva nómina/ }));
		expect(await screen.findByRole("heading", { name: "Nueva nómina" })).toBeInTheDocument();
		expect(screen.getByLabelText(/Empleado/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Días pagados/)).toBeInTheDocument();
	});

	it("abre el diálogo de generar nómina por quincena", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Pérez López Juan");
		await user.click(screen.getByRole("button", { name: /Generar nómina/ }));
		expect(
			await screen.findByText("Generar nómina por quincena"),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Quincena/)).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Generar" })).toBeInTheDocument();
	});
});
