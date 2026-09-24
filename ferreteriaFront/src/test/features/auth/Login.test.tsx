import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

import Login from "@/features/auth/Login";
import { apiLogin } from "@/lib/api/endpoints";
import { ensureCsrfCookie, mensajeError } from "@/lib/api/client";
import { useAuthStore } from "@/store/auth";

vi.mock("@/lib/api/endpoints", () => ({
	apiLogin: vi.fn(),
}));

vi.mock("@/lib/api/client", () => ({
	ensureCsrfCookie: vi.fn(),
	mensajeError: vi.fn((e: unknown) =>
		e instanceof Error ? e.message : String(e),
	),
}));

const TOKEN = {
	accessToken: "tok-abc",
	expiresInSeconds: 900,
	usuario: { usuarioId: 1, username: "admin", roles: ["ADMINISTRADOR"] },
};

function renderPage() {
	return render(
		<MemoryRouter initialEntries={["/login"]}>
			<Login />
		</MemoryRouter>,
	);
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	useAuthStore.setState({
		autenticado: false,
		usuario: null,
		lastActivityAt: 0,
	});
	localStorage.clear();
	vi.mocked(ensureCsrfCookie).mockResolvedValue(undefined as never);
	vi.mocked(apiLogin).mockResolvedValue({ ...TOKEN } as never);
});

describe("Login (smoke)", () => {
	it("renderiza marca, campos y botón de ingreso", () => {
		renderPage();
		expect(screen.getByText("El Tornillo Feliz")).toBeInTheDocument();
		expect(screen.getByText("Sistema de punto de venta")).toBeInTheDocument();
		expect(screen.getByLabelText(/Usuario/)).toBeInTheDocument();
		expect(screen.getByLabelText(/Contraseña/)).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "Ingresar" }),
		).toBeInTheDocument();
	});

	it("envía credenciales y abre sesión al ingresar", async () => {
		const user = userEvent.setup();
		renderPage();
		await user.type(screen.getByLabelText(/Usuario/), "admin");
		await user.type(screen.getByLabelText(/Contraseña/), "secreto123");
		await user.click(screen.getByRole("button", { name: "Ingresar" }));
		await vi.waitFor(() => {
			expect(ensureCsrfCookie).toHaveBeenCalledOnce();
			expect(apiLogin).toHaveBeenCalledWith({
				username: "admin",
				password: "secreto123",
			});
		});
		expect(useAuthStore.getState().autenticado).toBe(true);
		expect(useAuthStore.getState().usuario?.username).toBe("admin");
	});

	it("muestra el error cuando el login falla", async () => {
		const user = userEvent.setup();
		vi.mocked(apiLogin).mockRejectedValueOnce(new Error("Credenciales inválidas"));
		renderPage();
		await user.type(screen.getByLabelText(/Usuario/), "admin");
		await user.type(screen.getByLabelText(/Contraseña/), "mal");
		await user.click(screen.getByRole("button", { name: "Ingresar" }));
		expect(await screen.findByText("Credenciales inválidas")).toBeInTheDocument();
		expect(mensajeError).toHaveBeenCalled();
		expect(useAuthStore.getState().autenticado).toBe(false);
	});
});
