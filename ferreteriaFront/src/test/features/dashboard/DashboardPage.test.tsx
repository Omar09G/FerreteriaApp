import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import DashboardPage from "@/features/dashboard/DashboardPage";
import { apiDashboard } from "@/lib/api/reportes";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/reportes", () => ({
	apiDashboard: vi.fn(),
}));

const RESUMEN = {
	ventasEnRango: 125000,
	ticketsEnRango: 320,
	ticketPromedioEnRango: 390.63,
	saldoPorCobrar: 15000,
	cobranzaVencida: 2500,
	valorInventario: 800000,
	productosAgotados: 0,
	promocionesActivas: 3,
	cajasAbiertas: 2,
	devolucionesEnRango: 1,
	totalDevueltoEnRango: 500,
};

function renderPage() {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>
					<DashboardPage />
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
		usuario: { usuarioId: 1, username: "gerente", roles: ["GERENTE"] },
		lastActivityAt: Date.now(),
	});
	localStorage.clear();
	vi.mocked(apiDashboard).mockResolvedValue({ ...RESUMEN } as never);
});

describe("DashboardPage (smoke)", () => {
	it("renderiza título y todos los KPIs", async () => {
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Panel de control" }),
		).toBeInTheDocument();
		for (const kpi of [
			"Ventas en rango",
			"Tickets",
			"Devoluciones",
			"Ticket promedio",
			"Saldo por cobrar",
			"Cobranza vencida",
			"Valor de inventario",
			"Productos agotados",
			"Promociones activas",
			"Cajas abiertas",
		]) {
			expect(await screen.findByText(kpi)).toBeInTheDocument();
		}
		expect(screen.getByText("320")).toBeInTheDocument();
	});

	it("muestra el enlace a reportes detallados", async () => {
		renderPage();
		await screen.findByText("Ventas en rango");
		const enlace = screen.getByRole("link", { name: /Ver reportes/ });
		expect(enlace).toBeInTheDocument();
		expect(enlace).toHaveAttribute("href", "/reportes");
		expect(apiDashboard).toHaveBeenCalledOnce();
	});
});
