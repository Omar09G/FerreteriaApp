import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import Swal from "sweetalert2";

import AlmacenesAdminPage from "@/features/inventario/AlmacenesAdminPage";
import { ToastProvider } from "@/components/ui/Toast";
import {
	apiActualizarAlmacen,
	apiActualizarEstadoAlmacen,
	apiAlmacenesTodos,
	apiCrearAlmacen,
} from "@/lib/api/catalogo";
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

const ALMACEN_INACTIVO = {
	almacenId: 2,
	nombre: "Bodega",
	direccion: null,
	telefono: null,
	esPuntoVenta: false,
	activo: false,
};

describe("AlmacenesAdminPage (profundización)", () => {
	it("muestra guiones sin dirección ni teléfono y No sin punto de venta", async () => {
		comoAdmin();
		vi.mocked(apiAlmacenesTodos).mockResolvedValueOnce([
			{
				almacenId: 1,
				nombre: "Central",
				direccion: "Av. Juárez 123",
				telefono: "5551234567",
				esPuntoVenta: true,
				activo: true,
			},
			ALMACEN_INACTIVO,
		] as never);
		renderPage();
		expect(await screen.findByText("Bodega")).toBeInTheDocument();
		expect(screen.getByText("No")).toBeInTheDocument();
	});

	it("crea un almacén válido y muestra éxito", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiCrearAlmacen).mockResolvedValueOnce({
			almacenId: 9,
		} as never);
		renderPage();
		await screen.findByText("Central");
		await user.click(screen.getByRole("button", { name: /nuevo almacén/i }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo almacén" });
		await user.type(
			within(dialogo).getByLabelText(/Nombre del almacén/),
			"Bodega Sur",
		);
		await user.type(
			within(dialogo).getByLabelText(/Dirección/),
			"Calle Sur 1",
		);
		await user.click(within(dialogo).getByLabelText("Es punto de venta"));
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiCrearAlmacen)).toHaveBeenCalledWith({
				nombre: "Bodega Sur",
				direccion: "Calle Sur 1",
				telefono: null,
				esPuntoVenta: false,
			}),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({ text: expect.stringContaining("Almacén creado") }),
		);
	});

	it("valida el nombre obligatorio al crear", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Central");
		await user.click(screen.getByRole("button", { name: /nuevo almacén/i }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo almacén" });
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		expect(
			await within(dialogo).findByText("El nombre es obligatorio."),
		).toBeInTheDocument();
		expect(apiCrearAlmacen).not.toHaveBeenCalled();
	});

	it("muestra toast si crear falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiCrearAlmacen).mockRejectedValueOnce(
			new Error("nombre duplicado"),
		);
		renderPage();
		await screen.findByText("Central");
		await user.click(screen.getByRole("button", { name: /nuevo almacén/i }));
		const dialogo = await screen.findByRole("dialog", { name: "Nuevo almacén" });
		await user.type(
			within(dialogo).getByLabelText(/Nombre del almacén/),
			"Central",
		);
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({
					text: expect.stringContaining("nombre duplicado"),
				}),
			),
		);
	});

	it("edita el almacén y muestra éxito", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarAlmacen).mockResolvedValueOnce({
			almacenId: 1,
		} as never);
		renderPage();
		await screen.findByText("Central");
		await user.click(screen.getByRole("button", { name: "Editar" }));
		const dialogo = await screen.findByRole("dialog", { name: "Editar almacén" });
		const nombre = within(dialogo).getByLabelText(/Nombre del almacén/);
		await user.clear(nombre);
		await user.type(nombre, "Central Norte");
		await user.click(within(dialogo).getByRole("button", { name: /Guardar/ }));
		await waitFor(() =>
			expect(vi.mocked(apiActualizarAlmacen)).toHaveBeenCalledWith(
				1,
				expect.objectContaining({ nombre: "Central Norte" }),
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Almacén actualizado"),
			}),
		);
	});

	it("muestra toast si editar falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarAlmacen).mockRejectedValueOnce(
			new Error("no autorizado"),
		);
		renderPage();
		await screen.findByText("Central");
		await user.click(screen.getByRole("button", { name: "Editar" }));
		const dialogo = await screen.findByRole("dialog", { name: "Editar almacén" });
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
		vi.mocked(apiActualizarEstadoAlmacen).mockResolvedValueOnce({
			almacenId: 1,
		} as never);
		renderPage();
		await screen.findByText("Central");
		await user.click(
			screen.getByRole("checkbox", { name: "Almacén Central activo" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Dar de baja el almacén",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Dar de baja" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiActualizarEstadoAlmacen)).toHaveBeenCalledWith(
				1,
				false,
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Almacén dado de baja"),
			}),
		);
	});

	it("cancela la baja sin llamar al backend", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Central");
		await user.click(
			screen.getByRole("checkbox", { name: "Almacén Central activo" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Dar de baja el almacén",
		});
		await user.click(within(dialogo).getByRole("button", { name: /Cancelar/ }));
		await waitFor(() =>
			expect(
				screen.queryByRole("dialog", { name: "Dar de baja el almacén" }),
			).not.toBeInTheDocument(),
		);
		expect(apiActualizarEstadoAlmacen).not.toHaveBeenCalled();
	});

	it("muestra toast si dar de baja falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiActualizarEstadoAlmacen).mockRejectedValueOnce(
			new Error("tiene stock"),
		);
		renderPage();
		await screen.findByText("Central");
		await user.click(
			screen.getByRole("checkbox", { name: "Almacén Central activo" }),
		);
		const dialogo = await screen.findByRole("dialog", {
			name: "Dar de baja el almacén",
		});
		await user.click(
			within(dialogo).getByRole("button", { name: "Dar de baja" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("tiene stock") }),
			),
		);
	});

	it("reactiva directo al marcar un almacén inactivo", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiAlmacenesTodos).mockResolvedValueOnce([
			{
				almacenId: 1,
				nombre: "Central",
				direccion: "Av. Juárez 123",
				telefono: "5551234567",
				esPuntoVenta: true,
				activo: true,
			},
			ALMACEN_INACTIVO,
		] as never);
		vi.mocked(apiActualizarEstadoAlmacen).mockResolvedValueOnce({
			almacenId: 2,
		} as never);
		renderPage();
		await screen.findByText("Bodega");
		await user.click(
			screen.getByRole("checkbox", { name: "Almacén Bodega activo" }),
		);
		await waitFor(() =>
			expect(vi.mocked(apiActualizarEstadoAlmacen)).toHaveBeenCalledWith(
				2,
				true,
			),
		);
		expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
			expect.objectContaining({
				text: expect.stringContaining("Almacén reactivado"),
			}),
		);
	});

	it("muestra toast si reactivar falla", async () => {
		comoAdmin();
		const user = userEvent.setup();
		vi.mocked(apiAlmacenesTodos).mockResolvedValueOnce([
			{
				almacenId: 1,
				nombre: "Central",
				direccion: "Av. Juárez 123",
				telefono: "5551234567",
				esPuntoVenta: true,
				activo: true,
			},
			ALMACEN_INACTIVO,
		] as never);
		vi.mocked(apiActualizarEstadoAlmacen).mockRejectedValueOnce(
			new Error("bloqueado"),
		);
		renderPage();
		await screen.findByText("Bodega");
		await user.click(
			screen.getByRole("checkbox", { name: "Almacén Bodega activo" }),
		);
		await waitFor(() =>
			expect(vi.mocked(Swal.fire)).toHaveBeenCalledWith(
				expect.objectContaining({ text: expect.stringContaining("bloqueado") }),
			),
		);
	});

	it("muestra estado vacío cuando no hay almacenes", async () => {
		comoAdmin();
		vi.mocked(apiAlmacenesTodos).mockResolvedValueOnce([]);
		renderPage();
		expect(await screen.findByText("Sin almacenes")).toBeInTheDocument();
	});

	it("exporta los almacenes visibles a Excel", async () => {
		comoAdmin();
		const user = userEvent.setup();
		renderPage();
		await screen.findByText("Central");
		await user.click(screen.getByRole("button", { name: /excel/i }));
		await waitFor(() => expect(writeFile).toHaveBeenCalledTimes(1));
		expect(String(writeFile.mock.calls[0][1])).toMatch(
			/^almacenes-\d{4}-\d{2}-\d{2}\.xlsx$/,
		);
	});
});
