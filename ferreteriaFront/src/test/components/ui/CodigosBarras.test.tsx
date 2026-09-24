import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";

import { CodigosBarras } from "@/components/ui/CodigosBarras";

describe("CodigosBarras", () => {
	it("no renderiza nada sin códigos", () => {
		const { container: c1 } = render(<CodigosBarras codigos={[]} />);
		const { container: c2 } = render(<CodigosBarras codigos={null} />);
		const { container: c3 } = render(<CodigosBarras />);
		expect(c1).toBeEmptyDOMElement();
		expect(c2).toBeEmptyDOMElement();
		expect(c3).toBeEmptyDOMElement();
	});

	it("lista hasta max insignias más contador", () => {
		render(<CodigosBarras codigos={["A1", "B2", "C3"]} max={2} />);
		expect(screen.getByText("A1")).toBeInTheDocument();
		expect(screen.getByText("B2")).toBeInTheDocument();
		expect(screen.queryByText("C3")).not.toBeInTheDocument();
		expect(screen.getByText("+1")).toBeInTheDocument();
	});

	it("variante compacto muestra el primero truncado más contador", () => {
		render(<CodigosBarras codigos={["AAA", "BBB"]} variante="compacto" />);
		expect(screen.getByText("AAA")).toBeInTheDocument();
		expect(screen.getByText("+1")).toBeInTheDocument();
	});

	it("compacto sin extras no muestra contador", () => {
		render(<CodigosBarras codigos={["SOLO"]} variante="compacto" />);
		expect(screen.getByText("SOLO")).toBeInTheDocument();
		expect(screen.queryByText(/\+/)).not.toBeInTheDocument();
	});
});
