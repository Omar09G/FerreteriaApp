import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { renderHook } from "@testing-library/react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";

const SUFIJO = "El Tornillo Feliz";

describe("useDocumentTitle (FRONT-EST-001)", () => {
	const ORIGINAL_TITLE = document.title;

	beforeEach(() => {
		document.title = "titulo-inicial";
	});

	afterEach(() => {
		document.title = ORIGINAL_TITLE;
	});

	it("con segmento: compone `<segmento> │ <SUFIJO>`", () => {
		renderHook(() => useDocumentTitle("Productos"));
		expect(document.title).toBe(`Productos │ ${SUFIJO}`);
	});

	it("sin segmento: deja solo el sufijo del producto", () => {
		renderHook(() => useDocumentTitle());
		expect(document.title).toBe(SUFIJO);
	});

	it("reacciona al cambio de segmento en rerender", () => {
		const { rerender } = renderHook(
			({ titulo }) => useDocumentTitle(titulo),
			{ initialProps: { titulo: "Dashboard" } },
		);
		expect(document.title).toBe(`Dashboard │ ${SUFIJO}`);
		rerender({ titulo: "Reportes" });
		expect(document.title).toBe(`Reportes │ ${SUFIJO}`);
	});

	it("al desmontar: restaura el sufijo del producto", () => {
		const { unmount } = renderHook(() => useDocumentTitle("Caja"));
		expect(document.title).toBe(`Caja │ ${SUFIJO}`);
		unmount();
		expect(document.title).toBe(SUFIJO);
	});
});
