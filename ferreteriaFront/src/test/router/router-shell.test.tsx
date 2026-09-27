/**
 * Cobertura de router.tsx más allá de la estructura: renderiza los layouts
 * internos (RequiereAuth y ShellPrivada) extraídos de `router.routes` y el
 * catch-all NotFound. El data-router real choca con jsdom/undici al navegar,
 * así que se componen los elementos sobre MemoryRouter como en router.test.
 */
import type { ReactElement } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { router } from "@/router/router";
import { privateRoutes } from "@/router/registry";
import { useAuthStore } from "@/store/auth";

vi.mock("@/components/layout/AppShell", () => ({
	AppShell: () => <div>Shell-mock</div>,
}));

vi.mock("sweetalert2", () => ({
	default: { fire: vi.fn(), showLoading: vi.fn(), close: vi.fn() },
}));

afterEach(() => {
	useAuthStore.setState({ autenticado: false, usuario: null });
});

function layoutAuth(): { element: ReactElement; shell: ReactElement } {
	const layout = router.routes.find(
		(r) => r.path === undefined && Array.isArray(r.children),
	);
	expect(layout).toBeDefined();
	const shell = layout!.children!.find((c) => Array.isArray(c.children));
	expect(shell).toBeDefined();
	expect(shell!.children!.length).toBe(privateRoutes.length);
	return { element: layout!.element as ReactElement, shell: shell!.element as ReactElement };
}

function renderBajoLayout(elemento: ReactElement, ruta: string) {
	render(
		<MemoryRouter initialEntries={[ruta]}>
			<Routes>
				<Route element={elemento}>
					<Route path="/privada" element={<div>PrivadoX</div>} />
				</Route>
				<Route path="/login" element={<div>LoginX</div>} />
			</Routes>
		</MemoryRouter>,
	);
}

describe("router.tsx layouts internos", () => {
	it("RequiereAuth redirige a /login sin sesión", () => {
		renderBajoLayout(layoutAuth().element, "/privada");
		expect(screen.getByText("LoginX")).toBeInTheDocument();
		expect(screen.queryByText("PrivadoX")).not.toBeInTheDocument();
	});

	it("RequiereAuth deja pasar con sesión", () => {
		useAuthStore.setState({ autenticado: true, usuario: { roles: [] } as never });
		renderBajoLayout(layoutAuth().element, "/privada");
		expect(screen.getByText("PrivadoX")).toBeInTheDocument();
	});

	it("ShellPrivada monta Toast + AppShell", () => {
		const { shell } = layoutAuth();
		render(
			<MemoryRouter initialEntries={["/x"]}>
				<Routes>
					<Route element={shell}>
						<Route path="/x" element={<div>HijaX</div>} />
					</Route>
				</Routes>
			</MemoryRouter>,
		);
		expect(screen.getByText("Shell-mock")).toBeInTheDocument();
		// El AppShell mockeado no incluye <Outlet/>, así que la ruta hija no
		// renderiza: solo se verifica la composición Toast + AppShell.
	});

	it("el catch-all renderiza NotFound", () => {
		const catchAll = router.routes.find((r) => r.path === "*");
		expect(catchAll).toBeDefined();
		render(
			<MemoryRouter initialEntries={["/no-existe-xyz"]}>
				<Routes>
					<Route path="*" element={catchAll!.element as ReactElement} />
				</Routes>
			</MemoryRouter>,
		);
		expect(screen.getByText("Página no encontrada")).toBeInTheDocument();
	});
});
