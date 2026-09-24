import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useHotkey, useHotkeys } from "@/hooks/useHotkey";

function disparar(
	tecla: string,
	init: KeyboardEventInit = {},
	objetivo: EventTarget = window,
) {
	const ev = new KeyboardEvent("keydown", {
		bubbles: true,
		cancelable: true,
		key: tecla,
		...init,
	});
	act(() => {
		objetivo.dispatchEvent(ev);
	});
	return ev;
}

function inputAnexado(): HTMLInputElement {
	const input = document.createElement("input");
	document.body.appendChild(input);
	return input;
}

afterEach(() => {
	document.body.innerHTML = "";
	vi.restoreAllMocks();
});

describe("useHotkey", () => {
	it("dispara el handler con tecla de función (F1)", () => {
		const handler = vi.fn();
		renderHook(() => useHotkey("F1", handler));
		disparar("F1");
		expect(handler).toHaveBeenCalledTimes(1);
	});

	it("no dispara con una tecla distinta", () => {
		const handler = vi.fn();
		renderHook(() => useHotkey("F1", handler));
		disparar("F2");
		expect(handler).not.toHaveBeenCalled();
	});

	it("detecta combos con modificador (Ctrl+S) sin importar mayúsculas", () => {
		const handler = vi.fn();
		renderHook(() => useHotkey("Ctrl+S", handler));
		disparar("S", { ctrlKey: true });
		expect(handler).toHaveBeenCalledTimes(1);
		// Sin Ctrl no coincide.
		disparar("s");
		expect(handler).toHaveBeenCalledTimes(1);
	});

	it("detecta Ctrl+Enter y Escape", () => {
		const enter = vi.fn();
		const esc = vi.fn();
		renderHook(() => useHotkey("Ctrl+Enter", enter));
		renderHook(() => useHotkey("Escape", esc));
		disparar("Enter", { ctrlKey: true });
		disparar("Escape");
		expect(enter).toHaveBeenCalledTimes(1);
		expect(esc).toHaveBeenCalledTimes(1);
	});

	it("con enabled:false no registra listener ni dispara", () => {
		const handler = vi.fn();
		const { rerender } = renderHook(
			({ enabled }) => useHotkey("F1", handler, { enabled }),
			{ initialProps: { enabled: false } },
		);
		disparar("F1");
		expect(handler).not.toHaveBeenCalled();
		// Al habilitar, el mismo evento sí dispara.
		rerender({ enabled: true });
		disparar("F1");
		expect(handler).toHaveBeenCalledTimes(1);
	});

	it("ignora letra sola dentro de un input, pero permite F-keys", () => {
		const letra = vi.fn();
		const f1 = vi.fn();
		renderHook(() => useHotkey("a", letra));
		renderHook(() => useHotkey("F1", f1));
		const input = inputAnexado();
		disparar("a", {}, input);
		expect(letra).not.toHaveBeenCalled();
		disparar("F1", {}, input);
		expect(f1).toHaveBeenCalledTimes(1);
	});

	it("ignora letra sola en textarea y en elementos contenteditable", () => {
		const handler = vi.fn();
		renderHook(() => useHotkey("b", handler));
		const area = document.createElement("textarea");
		document.body.appendChild(area);
		disparar("b", {}, area);
		const editable = document.createElement("div");
		// jsdom no implementa isContentEditable: se stubbea la bandera que
		// el hook consulta (isTypingTarget → target.isContentEditable).
		Object.defineProperty(editable, "isContentEditable", {
			value: true,
			configurable: true,
		});
		document.body.appendChild(editable);
		disparar("b", {}, editable);
		expect(handler).not.toHaveBeenCalled();
	});

	it("dispara letra sola sobre un objetivo no editable (div)", () => {
		const handler = vi.fn();
		renderHook(() => useHotkey("c", handler));
		const div = document.createElement("div");
		document.body.appendChild(div);
		disparar("c", {}, div);
		expect(handler).toHaveBeenCalledTimes(1);
	});
	it("con ignoreInputs:false dispara letra sola dentro de input", () => {
		const handler = vi.fn();
		renderHook(() => useHotkey("a", handler, { ignoreInputs: false }));
		const input = inputAnexado();
		disparar("a", {}, input);
		expect(handler).toHaveBeenCalledTimes(1);
	});

	it("previene el comportamiento por defecto salvo preventDefault:false", () => {
		const conPrevent = vi.fn();
		renderHook(() => useHotkey("F1", conPrevent));
		const ev1 = disparar("F1");
		expect(ev1.defaultPrevented).toBe(true);

		const sinPrevent = vi.fn();
		const { unmount } = renderHook(() =>
			useHotkey("F2", sinPrevent, { preventDefault: false }),
		);
		const ev2 = disparar("F2");
		expect(ev2.defaultPrevented).toBe(false);
		expect(sinPrevent).toHaveBeenCalledTimes(1);
		unmount();
	});

	it("reacciona al cambio de combo vía rerender", () => {
		const handler = vi.fn();
		const { rerender } = renderHook(({ combo }) => useHotkey(combo, handler), {
			initialProps: { combo: "F1" },
		});
		disparar("F2");
		expect(handler).not.toHaveBeenCalled();
		rerender({ combo: "F2" });
		disparar("F2");
		expect(handler).toHaveBeenCalledTimes(1);
		disparar("F1");
		expect(handler).toHaveBeenCalledTimes(1);
	});

	it("usa siempre el último handler (ref estable, sin re-listener)", () => {
		const primero = vi.fn();
		const segundo = vi.fn();
		const { rerender } = renderHook(({ h }) => useHotkey("F1", h), {
			initialProps: { h: primero },
		});
		rerender({ h: segundo });
		disparar("F1");
		expect(primero).not.toHaveBeenCalled();
		expect(segundo).toHaveBeenCalledTimes(1);
	});

	it("al desmontar remueve el listener", () => {
		const handler = vi.fn();
		const { unmount } = renderHook(() => useHotkey("F1", handler));
		unmount();
		disparar("F1");
		expect(handler).not.toHaveBeenCalled();
	});
});

describe("useHotkeys", () => {
	it("dispara con el combo coincidente e informa cuál fue", () => {
		const handler = vi.fn();
		renderHook(() => useHotkeys(["F1", "F2"], handler));
		disparar("F2");
		expect(handler).toHaveBeenCalledTimes(1);
		expect(handler).toHaveBeenCalledWith("F2");
		disparar("F3");
		expect(handler).toHaveBeenCalledTimes(1);
	});

	it("solo ejecuta el primer combo que coincide", () => {
		const handler = vi.fn();
		renderHook(() => useHotkeys(["Ctrl+S", "s"], handler));
		disparar("s", { ctrlKey: true });
		expect(handler).toHaveBeenCalledTimes(1);
		expect(handler).toHaveBeenCalledWith("Ctrl+S");
	});

	it("ignora letra sola dentro de input pero permite F-key", () => {
		const handler = vi.fn();
		renderHook(() => useHotkeys(["a", "F1"], handler));
		const input = inputAnexado();
		disparar("a", {}, input);
		expect(handler).not.toHaveBeenCalled();
		disparar("F1", {}, input);
		expect(handler).toHaveBeenCalledWith("F1");
	});

	it("con enabled:false no dispara", () => {
		const handler = vi.fn();
		renderHook(() => useHotkeys(["F1"], handler, { enabled: false }));
		disparar("F1");
		expect(handler).not.toHaveBeenCalled();
	});

	it("al desmontar remueve el listener", () => {
		const handler = vi.fn();
		const { unmount } = renderHook(() => useHotkeys(["F1"], handler));
		unmount();
		disparar("F1");
		expect(handler).not.toHaveBeenCalled();
	});
});
