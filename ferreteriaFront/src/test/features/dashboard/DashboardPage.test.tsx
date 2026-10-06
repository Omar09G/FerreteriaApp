import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { ToastProvider } from "@/components/ui/Toast";
import DashboardPage from "@/features/dashboard/DashboardPage";
import {
  apiDashboard,
  apiEnviarInforme,
  apiInformeEstado,
  apiNarrativa,
} from "@/lib/api/reportes";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
  default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/reportes", () => ({
  apiDashboard: vi.fn(),
  apiEnviarInforme: vi.fn(),
  apiInformeEstado: vi.fn(),
  apiNarrativa: vi.fn(),
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
  vi.mocked(apiInformeEstado).mockResolvedValue({
    fechaInicio: "2026-10-02",
    fechaFin: "2026-10-02",
    yaEnviado: false,
    estado: null,
    enviadoEn: null,
  } as never);
  vi.mocked(apiEnviarInforme).mockResolvedValue({
    fechaInicio: "2026-10-02",
    fechaFin: "2026-10-02",
    destinatarios: 2,
    emailsEnviados: 1,
    whatsappEnviados: 1,
  } as never);
  vi.mocked(apiNarrativa).mockResolvedValue({
    fecha: "2026-10-05",
    ventasHoy: 12300,
    ventasAyer: 10000,
    cambioPct: 23,
    ticketsHoy: 23,
    ticketPromedioHoy: 534.78,
    productoEstrella: "Cemento Tolteca",
    estrellaIngreso: 5000,
    estrellaUnidades: 40,
  } as never);
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

  it("muestra Enviar informe a GERENTE y lo envía con el mismo rango", async () => {
    renderPage();
    const boton = await screen.findByRole("button", { name: /Enviar informe/ });
    fireEvent.click(boton);
    await waitFor(() => expect(apiEnviarInforme).toHaveBeenCalledOnce());
    const [inicio, fin] = vi.mocked(apiDashboard).mock.calls[0];
    expect(apiInformeEstado).toHaveBeenCalledWith(inicio, fin);
    expect(apiEnviarInforme).toHaveBeenCalledWith(inicio, fin);
  });

  it("si ya fue enviado pide confirmación antes de reenviar", async () => {
    vi.mocked(apiInformeEstado).mockResolvedValue({
      fechaInicio: "2026-10-02",
      fechaFin: "2026-10-02",
      yaEnviado: true,
      estado: "ENVIADA",
      enviadoEn: "2026-10-02T13:00:00Z",
    } as never);
    renderPage();
    const boton = await screen.findByRole("button", { name: /Enviar informe/ });
    fireEvent.click(boton);
    const dialogo = await screen.findByRole("dialog", {
      name: "Informe ya enviado",
    });
    expect(apiEnviarInforme).not.toHaveBeenCalled();
    const reenviar = screen.getByRole("button", { name: "Sí, reenviar" });
    expect(dialogo).toContainElement(reenviar);
    fireEvent.click(reenviar);
    await waitFor(() => expect(apiEnviarInforme).toHaveBeenCalledOnce());
  });

  it("oculta Enviar informe a VENDEDOR", async () => {
    useAuthStore.setState({
      autenticado: true,
      usuario: { usuarioId: 2, username: "vendedor", roles: ["VENDEDOR"] },
      lastActivityAt: Date.now(),
    });
    renderPage();
    await screen.findByText("Ventas en rango");
    expect(
      screen.queryByRole("button", { name: /Enviar informe/ }),
    ).not.toBeInTheDocument();
  });

  it("muestra la narrativa hoy vs ayer con estrella", async () => {
    renderPage();
    expect(
      await screen.findByText("Hoy vendiste 23% más que ayer."),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(/El producto estrella fue Cemento Tolteca/),
    ).toBeInTheDocument();
    expect(apiNarrativa).toHaveBeenCalledOnce();
  });

  it("muestra aviso cuando no hay ventas hoy", async () => {
    vi.mocked(apiNarrativa).mockResolvedValueOnce({
      fecha: "2026-10-05",
      ventasHoy: 0,
      ventasAyer: 0,
      cambioPct: null,
      ticketsHoy: 0,
      ticketPromedioHoy: 0,
      productoEstrella: null,
      estrellaIngreso: null,
      estrellaUnidades: null,
    } as never);
    renderPage();
    expect(await screen.findByText("Aún no hay ventas hoy. ¡A vender!")).toBeInTheDocument();
  });
});
