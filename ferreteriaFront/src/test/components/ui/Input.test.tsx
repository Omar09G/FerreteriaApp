import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { CampoWidget, Input, Select } from "@/components/ui/Input";

describe("CampoWidget", () => {
	it("muestra label, hint y kbd de hotkey", () => {
		render(
			<CampoWidget label="Buscar" hint="Escribe algo" hotkey="F3">
				<input />
			</CampoWidget>,
		);
		expect(screen.getByText("Buscar")).toBeInTheDocument();
		expect(screen.getByText("Escribe algo")).toBeInTheDocument();
		expect(screen.getByText("F3")).toBeInTheDocument();
	});

	it("el error reemplaza al hint", () => {
		render(
			<CampoWidget label="L" hint="ayuda" error="requerido">
				<input />
			</CampoWidget>,
		);
		expect(screen.getByText("requerido")).toBeInTheDocument();
		expect(screen.queryByText("ayuda")).not.toBeInTheDocument();
	});
});

describe("Input", () => {
	it("escribe texto y dispara onChange", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		render(<Input label="Nombre" placeholder="Escribe" onChange={onChange} />);
		const campo = screen.getByPlaceholderText("Escribe");
		await user.type(campo, "Juan");
		expect(campo).toHaveValue("Juan");
		expect(onChange).toHaveBeenCalled();
	});

	it("marca aria-invalid con error", () => {
		render(<Input label="N" error="mal" />);
		expect(screen.getByLabelText(/N/)).toHaveAttribute("aria-invalid", "true");
	});

	it("respeta disabled", () => {
		render(<Input label="N" disabled />);
		expect(screen.getByLabelText(/N/)).toBeDisabled();
	});
});

describe("Select", () => {
	it("cambia el valor seleccionado", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		render(
			<Select label="Rol" onChange={onChange}>
				<option value="a">A</option>
				<option value="b">B</option>
			</Select>,
		);
		const sel = screen.getByLabelText(/Rol/);
		await user.selectOptions(sel, "b");
		expect((sel as HTMLSelectElement).value).toBe("b");
		expect(onChange).toHaveBeenCalled();
	});

	it("muestra el error", () => {
		render(
			<Select label="R" error="elige uno">
				<option value="">-</option>
			</Select>,
		);
		expect(screen.getByText("elige uno")).toBeInTheDocument();
	});
});
