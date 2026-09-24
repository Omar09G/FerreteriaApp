import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ConfirmDialog } from "@/components/ui/ConfirmDialog";

describe("ConfirmDialog", () => {
	it("no renderiza nada si open es false", () => {
		const { container } = render(
			<ConfirmDialog open={false} title="Eliminar" onCancel={vi.fn()} onConfirm={vi.fn()} />,
		);
		expect(container).toBeEmptyDOMElement();
	});

	it("muestra título, mensaje default y botones", () => {
		render(
			<ConfirmDialog open title="Eliminar venta" onCancel={vi.fn()} onConfirm={vi.fn()} />,
		);
		expect(screen.getByText("Eliminar venta")).toBeInTheDocument();
		expect(screen.getByText("¿Confirmar acción?")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Confirmar" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Cancelar" })).toBeInTheDocument();
	});

	it("llama onConfirm y onCancel al hacer click", async () => {
		const user = userEvent.setup();
		const onCancel = vi.fn();
		const onConfirm = vi.fn();
		render(
			<ConfirmDialog
				open
				title="Título"
				confirmLabel="Sí, eliminar"
				cancelLabel="Volver"
				onCancel={onCancel}
				onConfirm={onConfirm}
			/>,
		);
		await user.click(screen.getByRole("button", { name: "Sí, eliminar" }));
		await user.click(screen.getByRole("button", { name: "Volver" }));
		expect(onConfirm).toHaveBeenCalledTimes(1);
		expect(onCancel).toHaveBeenCalledTimes(1);
	});

	it("en busy muestra Procesando y deshabilita botones", () => {
		render(
			<ConfirmDialog open busy title="T" onCancel={vi.fn()} onConfirm={vi.fn()} />,
		);
		expect(screen.getByRole("button", { name: "Procesando…" })).toBeDisabled();
		expect(screen.getByRole("button", { name: "Cancelar" })).toBeDisabled();
	});
});
