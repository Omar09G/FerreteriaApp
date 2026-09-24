import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, useRoutes } from "react-router-dom";

vi.mock("@/components/layout/AppShell", () => ({
	AppShell: () => (
		<div>
			Shell <Outlet />
		</div>
	),
}));

vi.mock("@/features/auth/Login", () => ({
	default: () => <div>Pagina Login</div>,
}));

import { router } from "@/router/router";
import { privateRoutes, publicRoutes } from "@/router/registry";
import { RequiereAuth } from "@/router/guards";
import { useAuthStore } from "@/store/auth";

afterEach(() => {
	useAuthStore.setState({ autenticado: false, usuario: null });
});

/** Misma composición que router.tsx pero sobre MemoryRouter (el data-router
 *  de react-router choca con jsdom/undici en este entorno al navegar). */
function AppCompuesta() {
	return useRoutes([
		...publicRoutes,
		{
			element: <RequiereAuth />,
			children: [{ element: <div>Shell</div>, children: privateRoutes }],
		},
	]);
}

function renderEn(ruta: string) {
	render(
		<MemoryRouter initialEntries={[ruta]}>
			<AppCompuesta />
		</MemoryRouter>,
	);
}

describe("router", () => {
	it("incluye rutas públicas, layout privado y catch-all", () => {
		const top = router.routes;
		expect(top.some((r) => r.path === "/login")).toBe(true);
		expect(top.some((r) => r.path === "*")).toBe(true);
		// Layout privado sin path con children (RequiereAuth -> Shell -> privadas)
		const layout = top.find((r) => r.path === undefined && r.children);
		expect(layout).toBeDefined();
		const shell = layout!.children![0];
		expect(shell.children!.length).toBe(privateRoutes.length);
	});

	it("renderiza /login como ruta pública", async () => {
		renderEn("/login");
		expect(await screen.findByText("Pagina Login")).toBeInTheDocument();
	});

	it("redirige a /login al entrar a privada sin auth", async () => {
		renderEn("/dashboard");
		expect(await screen.findByText("Pagina Login")).toBeInTheDocument();
		expect(screen.queryByText("Shell")).not.toBeInTheDocument();
	});
});
