import { renderHook, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";

import { usePager } from "@/hooks/usePager";

function renderPager(rutaInicial = "/") {
	const wrapper = ({ children }: { children: ReactNode }) => (
		<MemoryRouter initialEntries={[rutaInicial]}>{children}</MemoryRouter>
	);
	return renderHook(() => usePager(), { wrapper });
}

describe("usePager", () => {
	it("valores por defecto: page 0, size 20, sin sort ni q", () => {
		const { result } = renderPager();
		expect(result.current.page).toBe(0);
		expect(result.current.size).toBe(20);
		expect(result.current.sort).toBeUndefined();
		expect(result.current.q).toBeUndefined();
		expect(result.current.params).toEqual({ page: 0, size: 20 });
	});

	it("lee page/size/sort/q desde la URL", () => {
		const { result } = renderPager("/?page=3&size=50&sort=nombre&q=tuerca");
		expect(result.current.page).toBe(3);
		expect(result.current.size).toBe(50);
		expect(result.current.sort).toBe("nombre");
		expect(result.current.q).toBe("tuerca");
		expect(result.current.params).toEqual({
			page: 3,
			size: 50,
			sort: "nombre",
			q: "tuerca",
		});
	});

	it("page negativa se normaliza a 0", () => {
		const { result } = renderPager("/?page=-5");
		expect(result.current.page).toBe(0);
	});

	it("size se limita al rango 1..100 (clampSize)", () => {
		expect(renderPager("/?size=999").result.current.size).toBe(100);
		expect(renderPager("/?size=0").result.current.size).toBe(1);
		expect(renderPager("/?size=-3").result.current.size).toBe(1);
	});

	it("setPage actualiza y page=0 elimina el parámetro", () => {
		const { result } = renderPager("/?page=2&size=20");
		act(() => result.current.setPage(4));
		expect(result.current.page).toBe(4);
		act(() => result.current.setPage(0));
		expect(result.current.page).toBe(0);
	});

	it("setSize actualiza y resetea page", () => {
		const { result } = renderPager("/?page=4");
		act(() => result.current.setSize(50));
		expect(result.current.size).toBe(50);
		expect(result.current.page).toBe(0);
	});

	it("setSort fija y limpia el parámetro", () => {
		const { result } = renderPager("/");
		act(() => result.current.setSort("precio"));
		expect(result.current.sort).toBe("precio");
		expect(result.current.params.sort).toBe("precio");
		act(() => result.current.setSort(undefined));
		expect(result.current.sort).toBeUndefined();
		expect("sort" in result.current.params).toBe(false);
	});

	it("setQ fija, limpia y resetea page", () => {
		const { result } = renderPager("/?page=3");
		act(() => result.current.setQ("martillo"));
		expect(result.current.q).toBe("martillo");
		expect(result.current.page).toBe(0);
		act(() => result.current.setQ(undefined));
		expect(result.current.q).toBeUndefined();
		expect("q" in result.current.params).toBe(false);
	});

	it("los setters conservan los demás parámetros de la URL", () => {
		const { result } = renderPager("/?size=30&sort=nombre&q=clavo");
		act(() => result.current.setPage(2));
		expect(result.current.size).toBe(30);
		expect(result.current.sort).toBe("nombre");
		expect(result.current.q).toBe("clavo");
		expect(result.current.params).toEqual({
			page: 2,
			size: 30,
			sort: "nombre",
			q: "clavo",
		});
	});
});
