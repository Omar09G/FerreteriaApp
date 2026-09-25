import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import CajasAdminPage from "@/features/caja/CajasAdminPage";
import { ToastProvider } from "@/components/ui/Toast";
import {
	apiActualizarCaja,
	apiActualizarEstadoCaja,
	apiCajas,
	apiCrearCaja,
} from "@/lib/api/caja";
import { apiAlmacenes } from "@/lib/api/catalogo";
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

vi.mock("@/lib/api/caja", () => ({
	apiCajas: vi.fn(async () => [
		{
			cajaId: 1,
			nombre: "Caja Central",
			almacenId: 1,
			almacenNombre: "Central",
			activa: true,
		},
	]),
	apiCrearCaja: vi.fn(),
	apiActualizarCaja: vi.fn(),
	apiActualizarEstadoCaja: vi.fn(),
}));

vi.mock("@/lib/api/catalogo", () => ({
	apiAlmacenes: vi.fn(async () => [
		{
			almacenId: 1,
			nombre: "Central",
			direccion: null,
			telefono: null,
			esPuntoVenta: true,
			activo: true,
		},
	]),
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
	return render(<CajasAdminPage />, { wrapper });
}

afterEach(() => {
	useAuthStore.setState({ autenticado: false, usuario: null });
	vi.clearAllMocks();
});

describe("CajasAdminPage (smoke)", () => {
	it("como admin renderiza título, tabla y botón de alta", async () => {
		comoAdmin();
		renderPage();
		expect(
			screen.getByRole("heading", { name: "Administrar cajas" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: /nueva caja/i }),
		).toBeInTheDocument();
		expect(await screen.findByText("Caja Central")).toBeInTheDocument();
		expect(screen.getByText("Central")).toBeInTheDocument();
		expect(
			screen.getByRole("checkbox", { name: "Caja Caja Central activa" }),
		).toBeChecked();
		expect(screen.getByRole("button", { name: "Editar" })).toBeInTheDocument();
	});

	it("'Nueva caja' abre el diálogo de alta", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(screen.getByRole("button", { name: /nueva caja/i }));
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText("Nueva caja")).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/nombre de la caja/i),
		).toBeInTheDocument();
		expect(within(dialogo).getByLabelText(/almacén/i)).toBeInTheDocument();
	});

	it("'Editar' abre el diálogo precargado con el nombre", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(screen.getByRole("button", { name: "Editar" }));
		const dialogo = await screen.findByRole("dialog");
		expect(within(dialogo).getByText("Editar caja")).toBeInTheDocument();
		expect(
			within(dialogo).getByLabelText(/nombre de la caja/i),
		).toHaveValue("Caja Central");
	});

	it("sin rol administrador muestra aviso de permisos", () => {
		useAuthStore.setState({
			autenticado: true,
			usuario: { usuarioId: 2, username: "ven", roles: ["VENDEDOR"] },
		});
		renderPage();
		expect(
			screen.getByText("No tienes permisos para administrar cajas."),
		).toBeInTheDocument();
	});
});

const CAJA_INACTIVA = {
	cajaId: 2,
	nombre: "Caja Bodega",
	almacenId: 1,
	almacenNombre: "Central",
	activa: false,
};

const CAJAS = [
	{
		cajaId: 1,
		nombre: "Caja Central",
		almacenId: 1,
		almacenNombre: "Central",
		activa: true,
	},
	CAJA_INACTIVA,
];

describe("CajasAdminPage (profundización)", () => {
	it("crea una caja válida y muestra éxito", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiCrearCaja).mockResolvedValueOnce({ cajaId: 9 } as never);
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(screen.getByRole("button", { name: /nueva caja/i }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva caja" });
		await user.type(
			within(dialogo).getByLabelText(/nombre de la caja/i),
			"Caja Norte",
		);
		await user.selectOptions(within(dialogo).getByLabelText(/almacén/i), "1");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiCrearCaja)).toHaveBeenCalledWith({
				nombre: "Caja Norte",
				almacenId: 1,
				activa: true,
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Caja creada") }),
		);
	});

	it("valida nombre y almacén obligatorios", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(screen.getByRole("button", { name: /nueva caja/i }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva caja" });
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(
			await within(dialogo).findByText("Completa los campos obligatorios."),
		).toBeInTheDocument();
		expect(apiCrearCaja).not.toHaveBeenCalled();
	});

	it("muestra toast si crear falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiCrearCaja).mockRejectedValueOnce(new Error("nombre duplicado"));
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(screen.getByRole("button", { name: /nueva caja/i }));
		const dialogo = await screen.findByRole("dialog", { name: "Nueva caja" });
		await user.type(
			within(dialogo).getByLabelText(/nombre de la caja/i),
			"Caja Central",
		);
		await user.selectOptions(within(dialogo).getByLabelText(/almacén/i), "1");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("nombre duplicado"),
				}),
			),
		);
	});

	it("edita la caja y muestra éxito", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarCaja).mockResolvedValueOnce({ cajaId: 1 } as never);
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(screen.getByRole("button", { name: "Editar" }));
		const dialogo = await screen.findByRole("dialog", { name: "Editar caja" });
		const nombre = within(dialogo).getByLabelText(/nombre de la caja/i);
		await user.clear(nombre);
		await user.type(nombre, "Caja Matriz");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiActualizarCaja)).toHaveBeenCalledWith(
				1,
				expect.objectContaining({ nombre: "Caja Matriz", almacenId: 1 }),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Caja actualizada"),
			}),
		);
	});

	it("muestra toast si editar falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarCaja).mockRejectedValueOnce(
			new Error("no autorizado"),
		);
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(screen.getByRole("button", { name: "Editar" }));
		const dialogo = await screen.findByRole("dialog", { name: "Editar caja" });
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("no autorizado") }),
			),
		);
	});

	it("desactiva con confirmación y muestra éxito", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarEstadoCaja).mockResolvedValueOnce({
			cajaId: 1,
		} as never);
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(
			screen.getByRole("checkbox", { name: "Caja Caja Central activa" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Dar de baja la caja",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Dar de baja" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiActualizarEstadoCaja)).toHaveBeenCalledWith(1, false),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Caja dada de baja"),
			}),
		);
	});

	it("cancela la baja sin llamar al backend", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(
			screen.getByRole("checkbox", { name: "Caja Caja Central activa" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Dar de baja la caja",
		});
		await user.click(within(dialogo).getByRole("button", { name: /Cancelar/ }));
		await waitFor(() =>
			expect(
				screen.queryByRole("dialog", { name: "Dar de baja la caja" }),
			).not.toBeInTheDocument(),
		);
		expect(apiActualizarEstadoCaja).not.toHaveBeenCalled();
	});

	it("muestra toast si dar de baja falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarEstadoCaja).mockRejectedValueOnce(
			new Error("tiene turno"),
		);
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(
			screen.getByRole("checkbox", { name: "Caja Caja Central activa" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Dar de baja la caja",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Dar de baja" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("tiene turno") }),
			),
		);
	});

	it("reactiva directo al marcar una caja inactiva", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiCajas).mockResolvedValueOnce(CAJAS as never);
		vi.mocked(apiActualizarEstadoCaja).mockResolvedValueOnce({
			cajaId: 2,
		} as never);
		renderPage();
		await screen.findByText("Caja Bodega");
		await user.click(
			screen.getByRole("checkbox", { name: "Caja Caja Bodega activa" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiActualizarEstadoCaja)).toHaveBeenCalledWith(2, true),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Caja reactivada"),
			}),
		);
	});

	it("muestra toast si reactivar falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiCajas).mockResolvedValueOnce(CAJAS as never);
		vi.mocked(apiActualizarEstadoCaja).mockRejectedValueOnce(
			new Error("bloqueada"),
		);
		renderPage();
		await screen.findByText("Caja Bodega");
		await user.click(
			screen.getByRole("checkbox", { name: "Caja Caja Bodega activa" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("bloqueada") }),
			),
		);
	});

	it("muestra estado vacío cuando no hay cajas", async () => {
		comoAdmin();
		vi.mocked(apiCajas).mockResolvedValueOnce([]);
		renderPage();
		expect(await screen.findByText("Sin cajas")).toBeInTheDocument();
	});

	it("exporta las cajas visibles a Excel", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Caja Central");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^cajas-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});
});
