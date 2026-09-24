import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import AuditoriaPage from "@/features/seguridad/AuditoriaPage";
import { apiAuditoria, apiTablasAuditoria } from "@/lib/api/auditoria";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/auditoria", () => ({
	apiAuditoria: vi.fn(),
	apiTablasAuditoria: vi.fn(),
}));

const REGISTRO = {
	auditoriaId: 10,
	esquema: "seg",
	tabla: "usuarios",
	registroId: 5,
	accion: "UPDATE",
	datosAnteriores: '{"activo":true}',
	datosNuevos: '{"activo":false}',
	usuarioId: 1,
	usuario: "admin",
	creadoEn: "2026-03-01T12:00:00Z",
};

function renderPage() {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>
					<AuditoriaPage />
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
	vi.mocked(apiTablasAuditoria).mockResolvedValue([
		{ esquema: "seg", tabla: "usuarios" },
	] as never);
	vi.mocked(apiAuditoria).mockResolvedValue({
		success: true,
		data: [REGISTRO],
		meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
	} as never);
});

describe("AuditoriaPage (smoke)", () => {
	it("renderiza título, filtros y columnas", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Auditoría", level: 1 }),
		).toBeInTheDocument();
		expect(screen.getByLabelText(/Esquema/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Tabla/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Acción/)).toBeInTheDocument();
		expect(
			screen.getByPlaceholderText("Ej. admin"),
		).toBeInTheDocument();
		expect(await screen.findByText("UPDATE")).toBeInTheDocument();
		expect(screen.getByText("Fecha")).toBeInTheDocument();
		expect(screen.getByText("Esquema/Tabla")).toBeInTheDocument();
		expect(screen.getByText("Cambios")).toBeInTheDocument();
	});

	it("expande el detalle de cambios al pulsar Ver", async () => {
		const user = userEvent.setup();
		renderPage();
		const ver = await screen.findByRole("button", { name: "Ver" });
		await user.click(ver);
		expect(screen.getByText("Anterior")).toBeInTheDocument();
		expect(screen.getByText("Nuevo")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Ocultar" }),
		).toBeInTheDocument();
	});

	it("filtrar por usuario vuelve a pedir datos con el filtro", async () => {
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("UPDATE");
		const llamadasAntes = vi.mocked(apiAuditoria).mock.calls.length;
		await user.type(screen.getByPlaceholderText("Ej. admin"), "admin");
		await vi.waitFor(() => {
			const llamadas = vi
				.mocked(apiAuditoria)
				.mock.calls.slice(llamadasAntes)
				.map((c) => c[0]);
			expect(
				llamadas.some(
					(f) => f && (f as { usuario?: string }).usuario === "admin",
				),
			).toBe(true);
		});
	});
});
