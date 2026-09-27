import type { ReactElement } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ErrorBoundary } from "@/components/errors/ErrorBoundary";

vi.spyOn(console, "error").mockImplementation(() => {});

function Boom({ mensaje = "kaboom" }: { mensaje?: string }): ReactElement {
	throw new Error(mensaje);
}

describe("ErrorBoundary", () => {
	it("renderiza a los hijos cuando no hay error", () => {
		render(
			<ErrorBoundary>
				<div>Todo bien</div>
			</ErrorBoundary>,
		);
		expect(screen.getByText("Todo bien")).toBeInTheDocument();
		expect(screen.queryByRole("alert")).not.toBeInTheDocument();
	});

	it("atrapa el error de renderizado y muestra el fallback con el mensaje", () => {
		render(
			<ErrorBoundary>
				<Boom mensaje="falló el widget" />
			</ErrorBoundary>,
		);
		expect(screen.getByRole("alert")).toBeInTheDocument();
		expect(screen.getByText("Algo salió mal")).toBeInTheDocument();
		expect(screen.getByText("falló el widget")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Reintentar" })).toBeInTheDocument();
	});

	it("el botón Reintentar recupera la UI cuando el hijo ya no falla", async () => {
		const user = userEvent.setup();
		let fallar = true;
		function Intermitente() {
			if (fallar) throw new Error("una vez");
			return <div>recuperado</div>;
		}
		render(
			<ErrorBoundary>
				<Intermitente />
			</ErrorBoundary>,
		);
		expect(screen.getByText("Algo salió mal")).toBeInTheDocument();
		fallar = false;
		await user.click(screen.getByRole("button", { name: "Reintentar" }));
		expect(await screen.findByText("recuperado")).toBeInTheDocument();
	});
});
