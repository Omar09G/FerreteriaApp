import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import CatalogosIndexPage from "@/features/catalogo/CatalogosIndexPage";
import { ToastProvider } from "@/components/ui/Toast";
import { apiCatalogosPaneles } from "@/lib/api/catalogos";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/catalogos", () => ({
	apiCatalogosPaneles: vi.fn(),
}));

const panelesMock = vi.mocked(apiCatalogosPaneles);

function renderPage() {
	const qc = new QueryClient({
		defaultOptions: { queries: { retry: false } },
	});
	const wrapper = ({ children }: { children: ReactNode }) => (
		<MemoryRouter>
			<QueryClientProvider client={qc}>
				<ToastProvider>{children}</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>
	);
	return render(<CatalogosIndexPage />, { wrapper });
}

const PANELES = [
	{
		clave: "estados",
		tabla: "cat.estados",
		nombre: "Estados",
		pk: "estado_id",
		campos: [],
		soportaBajaLogica: false,
	},
	{
		clave: "inventado",
		tabla: "cat.inventado",
		nombre: "Inventado",
		pk: "id",
		campos: [],
		soportaBajaLogica: false,
	},
];

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
});

describe("CatalogosIndexPage", () => {
	it("muestra spinner mientras carga", () => {
		panelesMock.mockReturnValueOnce(new Promise(() => {}));
		renderPage();
		expect(screen.getByRole("status")).toBeInTheDocument();
		expect(
			screen.queryByRole("heading", { name: "Catálogos" }),
		).not.toBeInTheDocument();
	});

	it("lista los catálogos con enlaces a cada ficha", async () => {
		panelesMock.mockResolvedValueOnce(PANELES as never);
		renderPage();
		expect(await screen.findByText("Estados")).toBeInTheDocument();
		expect(screen.getByText("Inventado")).toBeInTheDocument();
		expect(screen.getByText("cat.estados")).toBeInTheDocument();
		const link = screen.getByRole("link", { name: /Estados/ });
		expect(link).toHaveAttribute("href", "/catalogos/estados");
		expect(
			screen.getByRole("link", { name: /Inventado/ }),
		).toHaveAttribute("href", "/catalogos/inventado");
	});

	it("muestra estado vacío sin catálogos", async () => {
		panelesMock.mockResolvedValueOnce([]);
		renderPage();
		expect(
			await screen.findByText("No hay catálogos disponibles."),
		).toBeInTheDocument();
	});
});
