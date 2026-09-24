import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";

import { Spinner } from "@/components/ui/Spinner";

describe("Spinner", () => {
	it("muestra el texto de carga por defecto", () => {
		render(<Spinner />);
		expect(screen.getByRole("status")).toBeInTheDocument();
		expect(screen.getByText("Cargando…")).toBeInTheDocument();
	});

	it("muestra un label personalizado", () => {
		render(<Spinner label="Guardando ventas…" />);
		expect(screen.getByText("Guardando ventas…")).toBeInTheDocument();
	});
});
