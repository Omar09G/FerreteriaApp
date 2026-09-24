import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";

import { ChartSkeleton, Skeleton, TableSkeleton } from "@/components/ui/Skeleton";

describe("Skeleton", () => {
	it("renderiza gráfico más líneas", () => {
		const { container } = render(<Skeleton lines={2} />);
		const region = container.firstElementChild as HTMLElement;
		expect(region).toHaveAttribute("aria-busy", "true");
		// 1 bloque gráfico + 2 líneas
		expect(region.querySelectorAll("div")).toHaveLength(3);
	});

	it("ChartSkeleton renderiza un bloque", () => {
		const { container } = render(<ChartSkeleton />);
		expect(container.firstElementChild).toHaveAttribute("aria-busy", "true");
	});

	it("TableSkeleton renderiza N filas", () => {
		const { container } = render(<TableSkeleton rows={4} />);
		expect(container.firstElementChild?.querySelectorAll("div")).toHaveLength(4);
	});

	it("TableSkeleton usa 5 filas por defecto", () => {
		const { container } = render(<TableSkeleton />);
		expect(container.firstElementChild?.querySelectorAll("div")).toHaveLength(5);
	});
});
