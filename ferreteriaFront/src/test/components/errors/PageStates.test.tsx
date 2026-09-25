import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useRouteError } from "react-router-dom";

import {
	AccessDenied,
	ErrorPagina,
	NotFound,
} from "@/components/errors/PageStates";

vi.mock("react-router-dom", async (importOriginal) => {
	const actual =
		await importOriginal<typeof import("react-router-dom")>();
	return { ...actual, useRouteError: vi.fn(() => undefined) };
});

const mockUseRouteError = vi.mocked(useRouteError);

afterEach(() => {
	mockUseRouteError.mockReturnValue(undefined);
	vi.restoreAllMocks();
});

describe("NotFound", () => {
	it("muestra el mensaje y el enlace al inicio", () => {
		render(
			<MemoryRouter>
				<NotFound />
			</MemoryRouter>,
		);
		expect(screen.getByText("Página no encontrada")).toBeInTheDocument();
		expect(
			screen.getByText("La sección que buscas no existe o ya no está disponible."),
		).toBeInTheDocument();
		const enlace = screen.getByRole("link", { name: "Ir al inicio" });
		expect(enlace).toHaveAttribute("href", "/dashboard");
	});
});

describe("AccessDenied", () => {
	it("explica la falta de permiso del rol", () => {
		render(<AccessDenied />);
		expect(screen.getByText("Acceso denegado")).toBeInTheDocument();
		expect(
			screen.getByText(/Tu rol no tiene permiso para ver esta sección/),
		).toBeInTheDocument();
	});
});

describe("ErrorPagina", () => {
	it("muestra el estado HTTP cuando el router entrega una respuesta de error", () => {
		mockUseRouteError.mockReturnValue({
			status: 404,
			statusText: "No encontrado",
			internal: false,
			data: null,
		} as never);
		render(<ErrorPagina />);
		expect(screen.getByRole("alert")).toBeInTheDocument();
		expect(screen.getByText("No se pudo mostrar la página")).toBeInTheDocument();
		expect(screen.getByText("404 No encontrado")).toBeInTheDocument();
	});

	it("muestra el mensaje de un Error genérico", () => {
		mockUseRouteError.mockReturnValue(new Error("explotó el loader") as never);
		render(<ErrorPagina />);
		expect(screen.getByText("explotó el loader")).toBeInTheDocument();
	});

	it("usa el texto inesperado cuando el error es desconocido", () => {
		mockUseRouteError.mockReturnValue("cadena rara" as never);
		render(<ErrorPagina />);
		expect(screen.getByText("Ocurrió un error inesperado.")).toBeInTheDocument();
	});

	it("Reintentar recarga la página vía history.go(0)", async () => {
		const user = userEvent.setup();
		mockUseRouteError.mockReturnValue(new Error("x") as never);
		const goSpy = vi.spyOn(window.history, "go").mockImplementation(() => {});
		render(<ErrorPagina />);
		await user.click(screen.getByRole("button", { name: "Reintentar" }));
		expect(goSpy).toHaveBeenCalledWith(0);
	});
});
