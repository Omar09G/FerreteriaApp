import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";

import App from "@/App";
import { useAuthStore } from "@/store/auth";
import { useUiStore } from "@/store/ui";
import { apiMe } from "@/lib/api/endpoints";
import { ensureCsrfCookie } from "@/lib/api/client";
import type { MeResponse } from "@/lib/api/types";

vi.mock("@/lib/api/endpoints", () => ({
	apiLogin: vi.fn(),
	apiRefresh: vi.fn(),
	apiLogout: vi.fn(),
	apiCambiarPassword: vi.fn(),
	apiMe: vi.fn(),
	apiEliminar: vi.fn(),
}));

vi.mock("@/lib/api/client", () => ({
	ensureCsrfCookie: vi.fn(),
}));

vi.mock("@/router/router", () => ({
	router: { routes: [] },
}));

vi.mock("react-router-dom", async (importOriginal) => {
	const actual =
		await importOriginal<typeof import("react-router-dom")>();
	return {
		...actual,
		RouterProvider: () => <div>RouterMock</div>,
	};
});

const ME: MeResponse = {
	usuarioId: 7,
	username: "cajera",
	roles: ["VENDEDOR"],
} as MeResponse;

let changeHandlers: Array<() => void> = [];

function mockMatchMedia(matches: boolean) {
	changeHandlers = [];
	Object.defineProperty(window, "matchMedia", {
		value: vi.fn().mockImplementation(() => ({
			matches,
			media: "(prefers-color-scheme: dark)",
			addEventListener: vi.fn((_t: string, fn: () => void) => {
				changeHandlers.push(fn);
			}),
			removeEventListener: vi.fn(),
		})),
		configurable: true,
	});
}

beforeEach(() => {
	vi.clearAllMocks();
	localStorage.clear();
	useAuthStore.setState({ autenticado: false, usuario: null, lastActivityAt: 0 });
	useUiStore.setState({ tema: "light", idioma: "es" });
	localStorage.clear();
	document.documentElement.classList.remove("dark");
	document.documentElement.lang = "";
	mockMatchMedia(false);
	vi.mocked(ensureCsrfCookie).mockResolvedValue(undefined as never);
	vi.mocked(apiMe).mockResolvedValue(ME);
});

describe("App", () => {
	it("aplica tema oscuro e idioma español al documento", async () => {
		useUiStore.setState({ tema: "dark", idioma: "es" });
		render(<App />);
		expect(document.documentElement.classList.contains("dark")).toBe(true);
		expect(document.documentElement.lang).toBe("es-MX");
		expect(screen.getByText("RouterMock")).toBeInTheDocument();
		await vi.waitFor(() => {
			expect(ensureCsrfCookie).toHaveBeenCalledOnce();
		});
	});

	it("sin tema oscuro quita la clase y con inglés usa lang 'en'", () => {
		useUiStore.setState({ tema: "light", idioma: "en" });
		render(<App />);
		expect(document.documentElement.classList.contains("dark")).toBe(false);
		expect(document.documentElement.lang).toBe("en");
	});

	it("en modo sistema sigue los cambios del SO", () => {
		useUiStore.setState({ tema: "system", idioma: "es" });
		mockMatchMedia(true);
		render(<App />);
		expect(document.documentElement.classList.contains("dark")).toBe(true);
		expect(changeHandlers.length).toBeGreaterThan(0);
	});

	it("garantiza la cookie CSRF una sola vez al montar", async () => {
		render(<App />);
		await vi.waitFor(() => {
			expect(ensureCsrfCookie).toHaveBeenCalledOnce();
		});
	});

	it("con /auth/me 200 confirma la sesión en el store", async () => {
		render(<App />);
		await vi.waitFor(() => {
			expect(useAuthStore.getState().autenticado).toBe(true);
		});
		expect(useAuthStore.getState().usuario?.username).toBe("cajera");
	});

	it("con /auth/me 401 limpia la sesión", async () => {
		useAuthStore.setState({ autenticado: true, usuario: ME });
		vi.mocked(apiMe).mockRejectedValueOnce(new Error("401"));
		render(<App />);
		await vi.waitFor(() => {
			expect(useAuthStore.getState().autenticado).toBe(false);
		});
		expect(useAuthStore.getState().usuario).toBeNull();
	});
});
