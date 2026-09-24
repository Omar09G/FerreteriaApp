import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { DateRangePicker } from "@/components/ui/DateRangePicker";
import { construirPreset } from "@/lib/rango";
import { hoyLocal } from "@/lib/format";

describe("DateRangePicker", () => {
	it("renderiza presets e inputs de fecha", () => {
		const hoy = hoyLocal();
		render(<DateRangePicker valor={{ inicio: hoy, fin: hoy }} onChange={vi.fn()} />);
		expect(screen.getByTestId("rango-fechas")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Hoy" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Ayer" })).toBeInTheDocument();
		const inputs = screen.getAllByDisplayValue(hoy);
		expect(inputs).toHaveLength(2);
	});

	it("click en preset llama onChange con el preset construido", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		const hoy = hoyLocal();
		render(<DateRangePicker valor={{ inicio: hoy, fin: hoy }} onChange={onChange} />);
		await user.click(screen.getByRole("button", { name: "Ayer" }));
		expect(onChange).toHaveBeenCalledWith(construirPreset("ayer"));
	});

	it("cambiar la fecha de inicio llama onChange", () => {
		const onChange = vi.fn();
		render(
			<DateRangePicker valor={{ inicio: "2026-09-01", fin: "2026-09-10" }} onChange={onChange} />,
		);
		const [ini] = screen.getAllByDisplayValue(/2026-09-0/);
		fireEvent.change(ini, { target: { value: "2026-09-03" } });
		expect(onChange).toHaveBeenCalledWith({ inicio: "2026-09-03", fin: "2026-09-10" });
	});

	it("cambiar la fecha de fin llama onChange", () => {
		const onChange = vi.fn();
		render(
			<DateRangePicker valor={{ inicio: "2026-09-01", fin: "2026-09-10" }} onChange={onChange} />,
		);
		const [, fin] = screen.getAllByDisplayValue(/2026-09-/);
		fireEvent.change(fin, { target: { value: "2026-09-12" } });
		expect(onChange).toHaveBeenCalledWith({ inicio: "2026-09-01", fin: "2026-09-12" });
	});
});
