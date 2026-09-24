import { afterEach, describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { RedirigirSiAutenticado, RequiereAuth, RequiereRol } from "@/router/guards";
import { useAuthStore } from "@/store/auth";

afterEach(() => {
	useAuthStore.setState({ autenticado: false, usuario: null });
});

function sesion(roles: string[] = ["VENDEDOR"]) {
	useAuthStore.setState({
		autenticado: true,
		usuario: { roles } as unknown as NonNullable<ReturnType<typeof useAuthStore.getState>["usuario"]>,
	});
}

describe("RequiereAuth", () => {
	function App() {
		return (
			<MemoryRouter initialEntries={["/privada"]}>
				<Routes>
					<Route element={<RequiereAuth />}>
						<Route path="/privada" element={<div>Privado</div>} />
					</Route>
					<Route path="/login" element={<div>Login</div>} />
				</Routes>
			</MemoryRouter>
		);
	}

	it("redirige a /login sin autenticación", () => {
		render(<App />);
		expect(screen.getByText("Login")).toBeInTheDocument();
		expect(screen.queryByText("Privado")).not.toBeInTheDocument();
	});

	it("permite el acceso con autenticación", () => {
		sesion();
		render(<App />);
		expect(screen.getByText("Privado")).toBeInTheDocument();
	});
});

describe("RequiereRol", () => {
	it("muestra acceso denegado sin el rol (vía Outlet)", () => {
		sesion(["VENDEDOR"]);
		render(
			<MemoryRouter initialEntries={["/admin"]}>
				<Routes>
					<Route element={<RequiereRol roles={["ADMINISTRADOR"]} />}>
						<Route path="/admin" element={<div>Secreto</div>} />
					</Route>
				</Routes>
			</MemoryRouter>,
		);
		expect(screen.queryByText("Secreto")).not.toBeInTheDocument();
		expect(screen.getByText("Acceso denegado")).toBeInTheDocument();
	});

	it("muestra el outlet con el rol requerido", () => {
		sesion(["ADMINISTRADOR"]);
		render(
			<MemoryRouter initialEntries={["/admin"]}>
				<Routes>
					<Route element={<RequiereRol roles={["ADMINISTRADOR"]} />}>
						<Route path="/admin" element={<div>Secreto</div>} />
					</Route>
				</Routes>
			</MemoryRouter>,
		);
		expect(screen.getByText("Secreto")).toBeInTheDocument();
	});

	it("muestra los children con el rol requerido", () => {
		sesion(["ADMINISTRADOR"]);
		render(
			<MemoryRouter>
				<RequiereRol roles={["ADMINISTRADOR"]}>
					<div>Secreto</div>
				</RequiereRol>
			</MemoryRouter>,
		);
		expect(screen.getByText("Secreto")).toBeInTheDocument();
	});
});

describe("RedirigirSiAutenticado", () => {
	it("redirige a /dashboard si ya hay sesión", () => {
		sesion();
		render(
			<MemoryRouter initialEntries={["/login"]}>
				<Routes>
					<Route
						path="/login"
						element={
							<RedirigirSiAutenticado>
								<div>Login</div>
							</RedirigirSiAutenticado>
						}
					/>
					<Route path="/dashboard" element={<div>Dashboard</div>} />
				</Routes>
			</MemoryRouter>,
		);
		expect(screen.getByText("Dashboard")).toBeInTheDocument();
	});

	it("muestra el contenido sin sesión", () => {
		render(
			<MemoryRouter initialEntries={["/login"]}>
				<Routes>
					<Route
						path="/login"
						element={
							<RedirigirSiAutenticado>
								<div>Login</div>
							</RedirigirSiAutenticado>
						}
					/>
				</Routes>
			</MemoryRouter>,
		);
		expect(screen.getByText("Login")).toBeInTheDocument();
	});
});
