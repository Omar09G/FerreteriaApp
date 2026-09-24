import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { Button } from "@/components/ui/Button";

describe("Button", () => {
	it("renderiza los children", () => {
		render(<Button>Guardar</Button>);
		expect(screen.getByRole("button", { name: "Guardar" })).toBeInTheDocument();
	});

	it("dispara onClick al hacer click", async () => {
		const user = userEvent.setup();
		const onClick = vi.fn();
		render(<Button onClick={onClick}>Click</Button>);
		await user.click(screen.getByRole("button", { name: "Click" }));
		expect(onClick).toHaveBeenCalledTimes(1);
	});

	it("no dispara onClick cuando está deshabilitado", async () => {
		const user = userEvent.setup();
		const onClick = vi.fn();
		render(
			<Button disabled onClick={onClick}>
				Bloqueado
			</Button>,
		);
		await user.click(screen.getByRole("button", { name: "Bloqueado" }));
		expect(onClick).not.toHaveBeenCalled();
	});

	it("muestra el kbd del hotkey y lo oculta si está deshabilitado", () => {
		const { rerender } = render(<Button hotkey="F2">Cobrar</Button>);
		expect(screen.getByText("F2")).toBeInTheDocument();
		expect(screen.getByRole("button")).toHaveAttribute("aria-keyshortcuts", "F2");

		rerender(
			<Button hotkey="F2" disabled>
				Cobrar
			</Button>,
		);
		expect(screen.queryByText("F2")).not.toBeInTheDocument();
	});

	it("aplica variante y tamaño en las clases", () => {
		render(
			<Button variant="danger" size="lg">
				Eliminar
			</Button>,
		);
		const btn = screen.getByRole("button", { name: "Eliminar" });
		expect(btn.className).toContain("bg-red-600");
		expect(btn.className).toContain("px-5");
	});
});
