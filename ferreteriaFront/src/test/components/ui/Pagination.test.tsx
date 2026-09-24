import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { Pagination } from "@/components/ui/Pagination";
import type { PageResult } from "@/lib/api/types";

const meta = (page: number, totalPages = 10): PageResult => ({
	page,
	size: 10,
	totalElements: 95,
	totalPages,
});

describe("Pagination", () => {
	it("no renderiza nada con una sola página", () => {
		const { container } = render(
			<Pagination meta={meta(0, 1)} onPage={vi.fn()} />,
		);
		expect(container).toBeEmptyDOMElement();
	});

	it("muestra el total de registros y la página actual", () => {
		render(<Pagination meta={meta(0)} onPage={vi.fn()} />);
		expect(screen.getByText("95 registro(s)")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "1" })).toHaveAttribute("aria-current", "page");
	});

	it("click en número llama onPage con el índice", async () => {
		const user = userEvent.setup();
		const onPage = vi.fn();
		render(<Pagination meta={meta(0)} onPage={onPage} />);
		await user.click(screen.getByRole("button", { name: "3" }));
		expect(onPage).toHaveBeenCalledWith(2);
	});

	it("anterior deshabilitado en la primera página", () => {
		render(<Pagination meta={meta(0)} onPage={vi.fn()} />);
		expect(screen.getByRole("button", { name: "Página anterior" })).toBeDisabled();
		expect(screen.getByRole("button", { name: "Página siguiente" })).not.toBeDisabled();
	});

	it("siguiente deshabilitado en la última página y navega atrás", async () => {
		const user = userEvent.setup();
		const onPage = vi.fn();
		render(<Pagination meta={meta(9)} onPage={onPage} />);
		expect(screen.getByRole("button", { name: "Página siguiente" })).toBeDisabled();
		await user.click(screen.getByRole("button", { name: "Página anterior" }));
		expect(onPage).toHaveBeenCalledWith(8);
	});
});
