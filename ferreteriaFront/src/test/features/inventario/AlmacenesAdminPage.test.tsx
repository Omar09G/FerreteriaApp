import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import AlmacenesAdminPage from "@/features/inventario/AlmacenesAdminPage";
import { ToastProvider } from "@/components/ui/Toast";
import { useAuthStore } from "@/store/auth";

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiAlmacenesTodos: vi.fn(async () => [
		{
			almacenId: 1,
			nombre: "Central",
			direccion: "Av. Juárez 123",
			telefono: "5551234567",
			esPuntoVenta: true,
			activo: true,
		},
	]),
	apiCrearAlmacen: vi.fn(),
	apiActualizarAlmacen: vi.fn(),
	apiActualizarEstadoAlmacen: vi.fn(),
}));

function comoAdmin() {
	useAuthStore.setState({
		autenticado: true,
		usuario: { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] },
	});
}

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
	return render(<AlmacenesAdminPage />, { wrapper });
}

afterEach(() => {
	useAuthStore.setState({ autenticado: false, usuario: null });
	vi.clearAllMocks();
});

describe("AlmacenesAdminPage (smoke)", () => {
	it("como admin renderiza título, tabla y botón de alta", async () => {
		comoAdmin();
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Administrar almacenes" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /nuevo almacén/i }),
		).toBeInTheDocument();
		expect(await screen.findByText("Central")).toBeInTheDocument();
		expect(screen.getByText("Av. Juárez 123")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Editar" })).toBeInTheDocument();
	});

	it("'Nuevo almacén' abre el diálogo de alta", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Central");
		await user.click(screen.getByRole("button", { name: /nuevo almacén/i }));
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText("Nuevo almacén")).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/Nombre del almacén/),
		).toBeInTheDocument();
	});

	it("'Editar' abre el diálogo precargado con el nombre", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Central");
		await user.click(screen.getByRole("button", { name: "Editar" }));
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText("Editar almacén")).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/Nombre del almacén/),
		).toHaveValue("Central");
	});

	it("sin rol administrador muestra aviso de permisos", () => {
		useAuthStore.setState({
			autenticado: true,
			usuario: { usuarioId: 2, username: "ven", roles: ["VENDEDOR"] },
		});
		renderPage();
		expect(
			screen.getByText("No tienes permisos para administrar almacenes."),
		).toBeInTheDocument();
	});
});
