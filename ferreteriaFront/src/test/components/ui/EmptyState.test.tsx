import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { EmptyState } from "@/components/ui/EmptyState";

describe("EmptyState", () => {
	it("muestra el título default", () => {
		render(<EmptyState />);
		expect(screen.getByText("Sin resultados")).toBeInTheDocument();
	});

	it("muestra título y descripción personalizados", () => {
		render(<EmptyState title="Vacío" descripcion="Nada por aquí" />);
		expect(screen.getByText("Vacío")).toBeInTheDocument();
		expect(screen.getByText("Nada por aquí")).toBeInTheDocument();
	});

	it("renderiza la acción y es clicable", async () => {
		const user = userEvent.setup();
		const onClick = vi.fn();
		render(<EmptyState title="T" action={<button onClick={onClick}>Crear</button>} />);
		await user.click(screen.getByRole("button", { name: "Crear" }));
		expect(onClick).toHaveBeenCalledTimes(1);
	});
});
