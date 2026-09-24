import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { printTicketById } from "@/lib/print/ticket";

function montarTicket(id: string, inner = "<p>TICKET-OK</p>") {
	const el = document.createElement("div");
	el.id = id;
	el.innerHTML = inner;
	document.body.appendChild(el);
	return el;
}

function iframeActual(): HTMLIFrameElement | null {
	return document.querySelector("iframe");
}

beforeEach(() => {
	vi.useFakeTimers();
	document.body.innerHTML = "";
});

afterEach(() => {
	vi.useRealTimers();
	vi.restoreAllMocks();
	document.body.innerHTML = "";
});

describe("printTicketById", () => {
	it("no hace nada si el elemento no existe", () => {
		const open = vi.spyOn(window, "open").mockReturnValue(null);
		printTicketById("no-existe");
		expect(open).not.toHaveBeenCalled();
		expect(iframeActual()).toBeNull();
	});

	it("usa el id por defecto ticket-preview", () => {
		montarTicket("ticket-preview");
		printTicketById();
		expect(iframeActual()).not.toBeNull();
	});

	it("escribe el HTML del ticket en el iframe e imprime", () => {
		montarTicket("ticket-preview");
		printTicketById("ticket-preview");

		const iframe = iframeActual();
		expect(iframe).not.toBeNull();
		expect(iframe!.contentDocument?.documentElement.innerHTML).toContain(
			"TICKET-OK",
		);

		const print = vi.fn();
		Object.defineProperty(iframe!.contentWindow, "print", {
			value: print,
			configurable: true,
		});

		vi.advanceTimersByTime(250);
		expect(print).toHaveBeenCalledTimes(1);
	});

	it("limpia el iframe tras imprimir", () => {
		montarTicket("ticket-preview");
		printTicketById("ticket-preview");
		vi.advanceTimersByTime(250); // doPrint -> cleanup en 500ms y 2000ms
		expect(iframeActual()).not.toBeNull();
		vi.advanceTimersByTime(500);
		expect(iframeActual()).toBeNull();
	});

	it("afterprint también dispara la limpieza", () => {
		montarTicket("ticket-preview");
		printTicketById("ticket-preview");
		const iframe = iframeActual()!;
		iframe.contentWindow!.dispatchEvent(new Event("afterprint"));
		vi.advanceTimersByTime(500);
		expect(iframeActual()).toBeNull();
	});

	it("espera imágenes: imprime tras load y con fallback de 1500ms", () => {
		montarTicket(
			"ticket-preview",
			'<p>TICKET-OK</p><img src="logo.png" alt="logo">',
		);
		printTicketById("ticket-preview");
		const iframe = iframeActual()!;
		const doc = iframe.contentDocument!;
		expect(doc.images.length).toBeGreaterThan(0);

		const print = vi.fn();
		Object.defineProperty(iframe.contentWindow, "print", {
			value: print,
			configurable: true,
		});

		// Rama load: la imagen avisa y se imprime a los 100ms
		const img = doc.images[0];
		img.dispatchEvent(new Event("load"));
		vi.advanceTimersByTime(100);
		expect(print).toHaveBeenCalledTimes(1);
	});

	it("sin eventos de imagen usa el fallback de 1500ms", () => {
		montarTicket(
			"ticket-preview",
			'<p>TICKET-OK</p><img src="logo.png" alt="logo">',
		);
		printTicketById("ticket-preview");
		const iframe = iframeActual()!;
		const print = vi.fn();
		Object.defineProperty(iframe.contentWindow, "print", {
			value: print,
			configurable: true,
		});
		vi.advanceTimersByTime(1500);
		expect(print).toHaveBeenCalled();
	});

	it("limpia el iframe aunque print lance", () => {
		montarTicket("ticket-preview");
		printTicketById("ticket-preview");
		const iframe = iframeActual()!;
		Object.defineProperty(iframe.contentWindow, "print", {
			value: () => {
				throw new Error("print bloqueado");
			},
			configurable: true,
		});
		vi.advanceTimersByTime(250);
		vi.advanceTimersByTime(500);
		expect(iframeActual()).toBeNull();
	});

	it("imagen con error también dispara la impresión", () => {
		montarTicket(
			"ticket-preview",
			'<p>TICKET-OK</p><img src="logo.png" alt="logo">',
		);
		printTicketById("ticket-preview");
		const iframe = iframeActual()!;
		const print = vi.fn();
		Object.defineProperty(iframe.contentWindow, "print", {
			value: print,
			configurable: true,
		});
		iframe.contentDocument!.images[0].dispatchEvent(new Event("error"));
		vi.advanceTimersByTime(100);
		expect(print).toHaveBeenCalledTimes(1);
	});

	it("imagen ya completa imprime sin esperar eventos", () => {
		// Sin atributo src jsdom reporta img.complete === true, lo que cubre
		// la rama `if (complete) check()`. (Mockear
		// HTMLImageElement.prototype no sirve: el iframe tiene su propio
		// realm con prototipos distintos.)
		montarTicket(
			"ticket-preview",
			'<p>TICKET-OK</p><img alt="logo">',
		);
		printTicketById("ticket-preview");
		const iframe = iframeActual()!;
		const print = vi.fn();
		Object.defineProperty(iframe.contentWindow, "print", {
			value: print,
			configurable: true,
		});
		vi.advanceTimersByTime(100);
		expect(print).toHaveBeenCalledTimes(1);
	});

	it("sin contentWindow usa el fallback de window.open", () => {
		montarTicket("ticket-preview");
		const real = document.createElement.bind(document);
		vi.spyOn(document, "createElement").mockImplementation(
			(tag: string, options?: ElementCreationOptions) => {
				const el = real(tag, options) as HTMLIFrameElement;
				if (tag === "iframe") {
					Object.defineProperty(el, "contentWindow", { value: null });
				}
				return el;
			},
		);
		const write = vi.fn();
		const close = vi.fn();
		vi.spyOn(window, "open").mockReturnValue({
			document: { write, close },
		} as unknown as Window);

		printTicketById("ticket-preview");

		expect(window.open).toHaveBeenCalled();
		expect(String(write.mock.calls[0][0])).toContain("TICKET-OK");
	});

	it("fallback a window.open si el iframe falla", () => {
		montarTicket("ticket-preview");
		vi.spyOn(document, "createElement").mockImplementationOnce(() => {
			throw new Error("iframe bloqueado");
		});
		const write = vi.fn();
		const close = vi.fn();
		vi.spyOn(window, "open").mockReturnValue({
			document: { write, close },
		} as unknown as Window);

		printTicketById("ticket-preview");

		expect(window.open).toHaveBeenCalledWith("", "_blank", "width=400,height=700");
		expect(write).toHaveBeenCalledTimes(1);
		expect(String(write.mock.calls[0][0])).toContain("TICKET-OK");
		expect(close).toHaveBeenCalledTimes(1);
	});

	it("fallback silencioso si window.open devuelve null", () => {
		montarTicket("ticket-preview");
		vi.spyOn(document, "createElement").mockImplementationOnce(() => {
			throw new Error("iframe bloqueado");
		});
		vi.spyOn(window, "open").mockReturnValue(null);
		expect(() => printTicketById("ticket-preview")).not.toThrow();
	});
});
