import { describe, expect, it } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useDebounce } from "@/hooks/useDebounce";

describe("useDebounce", () => {
	it("retorna el valor inicial inmediatamente", () => {
		const { result } = renderHook(() => useDebounce("hola", 200));
		expect(result.current).toBe("hola");
	});

	it("debounced: actualiza tras el delay", async () => {
		const { result, rerender } = renderHook(
			({ value, delay }) => useDebounce(value, delay),
			{ initialProps: { value: "a", delay: 100 } },
		);
		expect(result.current).toBe("a");
		rerender({ value: "ab", delay: 100 });
		expect(result.current).toBe("a"); // aún no ha pasado el delay
		await act(async () => {
			await new Promise((r) => setTimeout(r, 120));
		});
		expect(result.current).toBe("ab");
	});

	it("resetea el timer si el valor cambia antes del delay", async () => {
		const { result, rerender } = renderHook(
			({ value }) => useDebounce(value, 100),
			{ initialProps: { value: "a" } },
		);
		rerender({ value: "ab" });
		await act(async () => {
			await new Promise((r) => setTimeout(r, 50));
		});
		rerender({ value: "abc" });
		await act(async () => {
			await new Promise((r) => setTimeout(r, 60));
		});
		expect(result.current).toBe("a"); // aún no 100ms desde "abc"
		await act(async () => {
			await new Promise((r) => setTimeout(r, 60));
		});
		expect(result.current).toBe("abc");
	});
});
