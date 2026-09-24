import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { Card } from "@/components/ui/Card";

describe("Card", () => {
	it("renderiza el contenido", () => {
		render(<Card>Cuerpo</Card>);
		expect(screen.getByText("Cuerpo")).toBeInTheDocument();
	});

	it("muestra título y acciones en el header", async () => {
		const user = userEvent.setup();
		const onAccion = vi.fn();
		render(
			<Card titulo="Ventas" actions={<button onClick={onAccion}>Editar</button>}>
				Cuerpo
			</Card>,
		);
		expect(screen.getByText("Ventas")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Editar" }));
		expect(onAccion).toHaveBeenCalledTimes(1);
	});

	it("no renderiza header sin título ni acciones", () => {
		const { container } = render(<Card>Solo cuerpo</Card>);
		expect(container.querySelector("header")).toBeNull();
	});
});
