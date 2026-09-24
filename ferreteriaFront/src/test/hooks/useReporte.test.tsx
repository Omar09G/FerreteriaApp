import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useReporte } from "@/hooks/useReporte";

function clienteFresco() {
	return new QueryClient({
		defaultOptions: { queries: { retry: false, staleTime: 0 } },
	});
}

function renderReporte<T>(
	clave: string,
	fn: (inicio: string, fin: string) => Promise<T>,
	rango: { inicio: string; fin: string },
	client = clienteFresco(),
) {
	const wrapper = ({ children }: { children: ReactNode }) => (
		<QueryClientProvider client={client}>{children}</QueryClientProvider>
	);
	return {
		client,
		...renderHook(({ r }) => useReporte(clave, fn, r), {
			wrapper,
			initialProps: { r: rango },
		}),
	};
}

afterEach(() => {
	vi.restoreAllMocks();
});

describe("useReporte", () => {
	it("resuelve data con el rango dado y expone la query exitosa", async () => {
		const fn = vi.fn(async (inicio: string, fin: string) => `${inicio}|${fin}`);
		const { result } = renderReporte("ventas", fn, {
			inicio: "2026-01-01",
			fin: "2026-01-31",
		});
		await waitFor(() => expect(result.current.isSuccess).toBe(true));
		expect(result.current.data).toBe("2026-01-01|2026-01-31");
		expect(fn).toHaveBeenCalledWith("2026-01-01", "2026-01-31");
	});

	it("la key incluye inicio/fin: cambiar el rango re-ejecuta fn", async () => {
		const fn = vi.fn(async (inicio: string, fin: string) => `${inicio}|${fin}`);
		const client = clienteFresco();
		const { result, rerender } = renderReporte(
			"ventas",
			fn,
			{ inicio: "2026-01-01", fin: "2026-01-31" },
			client,
		);
		await waitFor(() => expect(result.current.isSuccess).toBe(true));
		expect(fn).toHaveBeenCalledTimes(1);

		rerender({ r: { inicio: "2026-02-01", fin: "2026-02-28" } });
		await waitFor(() =>
			expect(result.current.data).toBe("2026-02-01|2026-02-28"),
		);
		expect(fn).toHaveBeenCalledWith("2026-02-01", "2026-02-28");
		// Cada rango cachea por separado.
		expect(
			client.getQueryData(["ventas", "2026-01-01", "2026-01-31"]),
		).toBe("2026-01-01|2026-01-31");
		expect(
			client.getQueryData(["ventas", "2026-02-01", "2026-02-28"]),
		).toBe("2026-02-01|2026-02-28");
	});

	it("claves distintas no comparten caché", async () => {
		const fn = vi.fn(async () => "ok");
		const client = clienteFresco();
		const rango = { inicio: "2026-01-01", fin: "2026-01-31" };
		const a = renderReporte("ventas", fn, rango, client);
		await waitFor(() => expect(a.result.current.isSuccess).toBe(true));
		const b = renderReporte("compras", fn, rango, client);
		await waitFor(() => expect(b.result.current.isSuccess).toBe(true));
		expect(fn).toHaveBeenCalledTimes(2);
	});

	it("propaga el estado de error cuando fn rechaza", async () => {
		const fn = vi.fn(async () => {
			throw new Error("fallo reporte");
		});
		const { result } = renderReporte("ventas", fn, {
			inicio: "2026-01-01",
			fin: "2026-01-31",
		});
		await waitFor(() => expect(result.current.isError).toBe(true));
		expect(result.current.error).toBeInstanceOf(Error);
		expect(result.current.data).toBeUndefined();
	});

	it("expone isPending mientras la promesa no resuelve", async () => {
		let resolver!: (v: string) => void;
		const fn = vi.fn(
			() => new Promise<string>((res) => void (resolver = res)),
		);
		const { result } = renderReporte("caja", fn, {
			inicio: "2026-03-01",
			fin: "2026-03-31",
		});
		expect(result.current.isPending).toBe(true);
		resolver("listo");
		await waitFor(() => expect(result.current.isSuccess).toBe(true));
		expect(result.current.data).toBe("listo");
	});
});
