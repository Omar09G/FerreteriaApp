import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";

import { Badge, EstadoBadge } from "@/components/ui/Badge";

describe("Badge", () => {
	it("renderiza los children", () => {
		render(<Badge>Nuevo</Badge>);
		expect(screen.getByText("Nuevo")).toBeInTheDocument();
	});

	it("aplica el tono indicado", () => {
		render(<Badge tone="success">Pagado</Badge>);
		expect(screen.getByText("Pagado").className).toContain("text-success");
	});

	it("usa el tono default por omisión", () => {
		render(<Badge>Borrador</Badge>);
		expect(screen.getByText("Borrador").className).toContain("bg-warmbg");
	});
});

describe("EstadoBadge", () => {
	it("mapea COMPLETADA a success", () => {
		render(<EstadoBadge estado="COMPLETADA" />);
		expect(screen.getByText("COMPLETADA").className).toContain("text-success");
	});

	it("mapea CANCELADA a danger", () => {
		render(<EstadoBadge estado="CANCELADA" />);
		expect(screen.getByText("CANCELADA").className).toContain("text-error");
	});

	it("usa tono default para estados desconocidos", () => {
		render(<EstadoBadge estado="RARO_XYZ" />);
		expect(screen.getByText("RARO_XYZ").className).toContain("bg-warmbg");
	});
});
