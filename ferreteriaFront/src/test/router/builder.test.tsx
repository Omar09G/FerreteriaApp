import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";

import { defineRoutes, withFallback } from "@/router/builder";

describe("defineRoutes", () => {
	it("devuelve las rutas tal cual", () => {
		const routes = defineRoutes(
			{ path: "/a", element: <div>A</div> },
			{ path: "/b", element: <div>B</div> },
		);
		expect(routes).toHaveLength(2);
		expect(routes[0].path).toBe("/a");
	});

	it("sin argumentos devuelve array vacío", () => {
		expect(defineRoutes()).toEqual([]);
	});
});

describe("withFallback", () => {
	it("asigna displayName y renderiza el lazy", async () => {
		const Page = withFallback(() =>
			Promise.resolve({ default: () => <div>Lazy OK</div> }),
		);
		expect(Page.displayName).toBe("withFallback");
		render(<Page />);
		await waitFor(() => expect(screen.getByText("Lazy OK")).toBeInTheDocument());
	});

	it("muestra el fallback personalizado mientras carga", () => {
		let resolver!: (v: { default: () => JSX.Element }) => void;
		const Page = withFallback(
			() => new Promise<{ default: () => JSX.Element }>((res) => (resolver = res)),
			<div>Cargando propio</div>,
		);
		render(<Page />);
		expect(screen.getByText("Cargando propio")).toBeInTheDocument();
		resolver({ default: () => <div>Listo</div> });
	});
});
