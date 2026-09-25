import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { AppShell } from "@/components/layout/AppShell";
import { useAuthStore } from "@/store/auth";
import { useUiStore } from "@/store/ui";
import type { MeResponse } from "@/lib/api/types";
import { apiCambiarPassword, apiLogout } from "@/lib/api/endpoints";

const toastMocks = vi.hoisted(() => ({
	success: vi.fn(),
	error: vi.fn(),
	info: vi.fn(),
	warning: vi.fn(),
	toast: vi.fn(),
	loading: vi.fn(),
}));

vi.mock("@/components/ui/Toast", () => ({
	ToastProvider: ({ children }: { children: React.ReactNode }) => (
		<>{children}</>
	),
	useToast: () => toastMocks,
}));

vi.mock("@/lib/api/endpoints", () => ({
	apiLogout: vi.fn(),
	apiCambiarPassword: vi.fn(),
	apiMe: vi.fn(),
}));

vi.mock("@/hooks/useInactivityTimeout", () => ({
	useInactivityTimeout: vi.fn(),
}));

function usuarioCon(over: Partial<MeResponse> = {}): MeResponse {
	return {
		usuarioId: 1,
		username: "admin",
		roles: ["ADMINISTRADOR"],
		...over,
	} as MeResponse;
}

function renderShell(ruta = "/dashboard") {
	render(
		<MemoryRouter initialEntries={[ruta]}>
			<Routes>
				<Route element={<AppShell />}>
					<Route path="/dashboard" element={<div>ContenidoDash</div>} />
					<Route path="/login" element={<div>PaginaLogin</div>} />
				</Route>
			</Routes>
		</MemoryRouter>,
	);
}

function dialogoPassword() {
	return screen.getByRole("dialog", { name: "Cambiar contraseña" });
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	useAuthStore.setState({
		autenticado: true,
		usuario: usuarioCon(),
		lastActivityAt: 0,
	});
	useUiStore.setState({ tema: "light", idioma: "es" });
	localStorage.clear();
	vi.mocked(apiLogout).mockResolvedValue({ ok: true } as never);
	vi.mocked(apiCambiarPassword).mockResolvedValue({ ok: true } as never);
});

describe("AppShell", () => {
	it("renderiza marca, versión, navegación y el outlet", () => {
		renderShell();
		// El <p> parte el texto en varios nodos ("marca", " - ", versión, que
		// viene del .env y puede variar entre entornos).
		expect(
			screen.getByText(
				(_, el) =>
					el?.tagName === "P" &&
					(el?.textContent ?? "").startsWith("El Tornillo Feliz - "),
			),
		).toBeInTheDocument();
		expect(screen.getByText("ContenidoDash")).toBeInTheDocument();
		expect(
			screen.getByRole("navigation", { name: "Navegación principal" }),
		).toBeInTheDocument();
		// Ítems visibles para ADMINISTRADOR (los links evitan el choque con
		// el subtítulo "Punto de venta" del encabezado).
		expect(
			screen.getByRole("link", { name: "Punto de venta" }),
		).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "Usuarios" })).toBeInTheDocument();
	});

	it("muestra el perfil con nombre del empleado y roles", () => {
		useAuthStore.setState({
			usuario: usuarioCon({
				empleado: { nombreCompleto: "Admin Uno" } as never,
				roles: ["ADMINISTRADOR", "GERENTE"],
			}),
		});
		renderShell();
		expect(screen.getAllByText("Admin Uno").length).toBeGreaterThan(0);
		expect(
			screen.getAllByText("ADMINISTRADOR, GERENTE").length,
		).toBeGreaterThan(0);
	});

	it("oculta ítems de administrador a un VENDEDOR", () => {
		useAuthStore.setState({
			usuario: usuarioCon({ username: "ven", roles: ["VENDEDOR"] }),
		});
		renderShell();
		expect(
			screen.queryByRole("link", { name: "Usuarios" }),
		).not.toBeInTheDocument();
		expect(
			screen.queryByRole("link", { name: "Catálogos" }),
		).not.toBeInTheDocument();
		expect(
			screen.getByRole("link", { name: "Punto de venta" }),
		).toBeInTheDocument();
	});

	it("muestra 'Sin rol' y el username cuando no hay perfil extendido", () => {
		useAuthStore.setState({
			usuario: usuarioCon({ username: "solo", empleado: undefined, roles: [] }),
		});
		renderShell();
		expect(screen.getAllByText("solo").length).toBeGreaterThan(0);
		expect(screen.getAllByText("Sin rol").length).toBeGreaterThan(0);
	});

	it("alterna tema e idioma desde Preferencias", async () => {
		const user = userEvent.setup({ delay: null });
		renderShell();
		expect(screen.getByText("EN")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Cambiar tema" }));
		expect(useUiStore.getState().tema).toBe("dark");
		await user.click(screen.getByRole("button", { name: "Cambiar idioma" }));
		expect(useUiStore.getState().idioma).toBe("en");
		expect(screen.getByText("ES")).toBeInTheDocument();
	});

	it("abre y cierra el drawer móvil", async () => {
		const user = userEvent.setup({ delay: null });
		renderShell();
		await user.click(screen.getByRole("button", { name: "Abrir menú" }));
		expect(screen.getByText("Menú")).toBeInTheDocument();
		// Cerrar tocando el backdrop
		const drawer = screen.getByRole("dialog");
		await user.click(drawer.firstElementChild as Element);
		expect(screen.queryByText("Menú")).not.toBeInTheDocument();
		// Reabrir y cerrar con el botón X
		await user.click(screen.getByRole("button", { name: "Abrir menú" }));
		expect(screen.getByText("Menú")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Cerrar menú" }));
		expect(screen.queryByText("Menú")).not.toBeInTheDocument();
	});

	it("cierra sesión: llama al backend, limpia el store y navega a /login", async () => {
		const user = userEvent.setup({ delay: null });
		renderShell();
		const botones = screen.getAllByRole("button", { name: "Cerrar sesión" });
		await user.click(botones[0]);
		await vi.waitFor(() => {
			expect(apiLogout).toHaveBeenCalledOnce();
		});
		expect(useAuthStore.getState().autenticado).toBe(false);
		expect(await screen.findByText("PaginaLogin")).toBeInTheDocument();
	});

	it("cierra sesión aunque el backend falle (best-effort)", async () => {
		const user = userEvent.setup({ delay: null });
		vi.mocked(apiLogout).mockRejectedValueOnce(new Error("red caída"));
		renderShell();
		const botones = screen.getAllByRole("button", { name: "Cerrar sesión" });
		await user.click(botones[0]);
		await vi.waitFor(() => {
			expect(useAuthStore.getState().autenticado).toBe(false);
		});
		expect(await screen.findByText("PaginaLogin")).toBeInTheDocument();
	});

	it("valida la nueva contraseña (mínimo 8 y confirmación)", async () => {
		const user = userEvent.setup({ delay: null });
		renderShell();
		await user.click(screen.getByRole("button", { name: "Cambiar contraseña" }));
		expect(dialogoPassword()).toBeInTheDocument();

		// El label incluye " *" por required: se busca por regex.
		await user.type(screen.getByLabelText(/Contraseña actual/), "anterior1");
		await user.type(screen.getByLabelText(/Nueva contraseña \(mín\. 8\)/), "corta");
		await user.type(screen.getByLabelText(/Confirmar nueva contraseña/), "corta");
		await user.click(screen.getByRole("button", { name: "Guardar" }));
		expect(
			await screen.findByText(
				"La nueva contraseña debe tener al menos 8 caracteres.",
			),
		).toBeInTheDocument();
		expect(apiCambiarPassword).not.toHaveBeenCalled();

		await user.clear(screen.getByLabelText(/Nueva contraseña \(mín\. 8\)/));
		await user.type(
			screen.getByLabelText(/Nueva contraseña \(mín\. 8\)/),
			"nueva1234",
		);
		await user.clear(screen.getByLabelText(/Confirmar nueva contraseña/));
		await user.type(
			screen.getByLabelText(/Confirmar nueva contraseña/),
			"otra1234",
		);
		await user.click(screen.getByRole("button", { name: "Guardar" }));
		expect(
			await screen.findByText("La confirmación no coincide."),
		).toBeInTheDocument();
		expect(apiCambiarPassword).not.toHaveBeenCalled();
	});

	it("cambia la contraseña con datos válidos y notifica éxito", async () => {
		const user = userEvent.setup({ delay: null });
		renderShell();
		await user.click(screen.getByRole("button", { name: "Cambiar contraseña" }));
		await user.type(screen.getByLabelText(/Contraseña actual/), "anterior1");
		await user.type(
			screen.getByLabelText(/Nueva contraseña \(mín\. 8\)/),
			"nueva1234",
		);
		await user.type(
			screen.getByLabelText(/Confirmar nueva contraseña/),
			"nueva1234",
		);
		await user.click(screen.getByRole("button", { name: "Guardar" }));
		await vi.waitFor(() => {
			expect(apiCambiarPassword).toHaveBeenCalledWith({
				passwordActual: "anterior1",
				nuevaPassword: "nueva1234",
			});
		});
		expect(toastMocks.success).toHaveBeenCalledWith("Contraseña actualizada.");
		await vi.waitFor(() => {
			expect(
				screen.queryByRole("dialog", { name: "Cambiar contraseña" }),
			).not.toBeInTheDocument();
		});
	});

	it("muestra el error del backend al cambiar la contraseña", async () => {
		const user = userEvent.setup({ delay: null });
		vi.mocked(apiCambiarPassword).mockRejectedValueOnce(new Error("backend caído"));
		renderShell();
		await user.click(screen.getByRole("button", { name: "Cambiar contraseña" }));
		await user.type(screen.getByLabelText(/Contraseña actual/), "anterior1");
		await user.type(
			screen.getByLabelText(/Nueva contraseña \(mín\. 8\)/),
			"nueva1234",
		);
		await user.type(
			screen.getByLabelText(/Confirmar nueva contraseña/),
			"nueva1234",
		);
		await user.click(screen.getByRole("button", { name: "Guardar" }));
		await vi.waitFor(() => {
			expect(toastMocks.error).toHaveBeenCalledWith("Error: backend caído");
		});
		// El diálogo sigue abierto para reintentar
		expect(dialogoPassword()).toBeInTheDocument();
	});
});
