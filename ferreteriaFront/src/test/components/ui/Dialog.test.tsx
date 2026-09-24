import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { Dialog } from "@/components/ui/Dialog";

describe("Dialog", () => {
	it("no renderiza nada si open es false", () => {
		const { container } = render(
			<Dialog open={false} onClose={vi.fn()} title="T">
				Hola
			</Dialog>,
		);
		expect(container).toBeEmptyDOMElement();
	});

	it("muestra título, contenido y footer", () => {
		render(
			<Dialog open onClose={vi.fn()} title="Mi diálogo" footer={<button>Pie</button>}>
				Contenido
			</Dialog>,
		);
		expect(screen.getByRole("dialog", { name: "Mi diálogo" })).toBeInTheDocument();
		expect(screen.getByText("Contenido")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Pie" })).toBeInTheDocument();
	});

	it("cierra con el botón X", async () => {
		const user = userEvent.setup();
		const onClose = vi.fn();
		render(
			<Dialog open onClose={onClose} title="T">
				C
			</Dialog>,
		);
		await user.click(
			screen
				.getByRole("dialog")
				.querySelector('button[aria-label="Cerrar"]') as HTMLElement,
		);
		expect(onClose).toHaveBeenCalledTimes(1);
	});

	it("cierra con Escape", () => {
		const onClose = vi.fn();
		render(
			<Dialog open onClose={onClose} title="T">
				C
			</Dialog>,
		);
		fireEvent.keyDown(document, { key: "Escape" });
		expect(onClose).toHaveBeenCalled();
	});

	it("cierra con click en el overlay", () => {
		const onClose = vi.fn();
		const { container } = render(
			<Dialog open onClose={onClose} title="T">
				C
			</Dialog>,
		);
		const overlay = container.firstElementChild as HTMLElement;
		fireEvent.mouseDown(overlay);
		expect(onClose).toHaveBeenCalledTimes(1);
	});
});
