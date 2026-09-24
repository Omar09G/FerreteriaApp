import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { RangoFiltro } from "@/components/ui/RangoFiltro";
import { construirPreset } from "@/lib/rango";

describe("RangoFiltro", () => {
	it("sin valor muestra botón para activar el filtro", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		render(<RangoFiltro valor={null} onChange={onChange} />);
		await user.click(screen.getByRole("button", { name: /Filtrar por rango/ }));
		expect(onChange).toHaveBeenCalledTimes(1);
		expect(onChange.mock.calls[0][0]).toEqual(
			expect.objectContaining({ inicio: expect.any(String), fin: expect.any(String) }),
		);
	});

	it("con valor muestra el picker y Todos limpia el filtro", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		render(<RangoFiltro valor={construirPreset("hoy")} onChange={onChange} />);
		expect(screen.getByTestId("rango-fechas")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Todos" }));
		expect(onChange).toHaveBeenCalledWith(null);
	});
});
