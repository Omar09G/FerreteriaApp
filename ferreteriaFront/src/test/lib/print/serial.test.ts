import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	disconnect,
	ensureConnected,
	getAvailablePorts,
	getCachedPortInfo,
	getSilentEnabled,
	isSerialSupported,
	openPort,
	printViaSerial,
	requestSerialPort,
	setSilentEnabled,
	writeToPort,
} from "@/lib/print/serial";

type MockPort = {
	open: ReturnType<typeof vi.fn>;
	close: ReturnType<typeof vi.fn>;
	writable: { getWriter: ReturnType<typeof vi.fn> } | null;
	readable: null;
	getInfo: ReturnType<typeof vi.fn>;
};

function mockPort(over: Partial<MockPort> = {}) {
	const write = vi.fn().mockResolvedValue(undefined);
	const releaseLock = vi.fn();
	const getWriter = vi.fn(() => ({ write, releaseLock }));
	const port: MockPort = {
		open: vi.fn().mockResolvedValue(undefined),
		close: vi.fn().mockResolvedValue(undefined),
		writable: { getWriter },
		readable: null,
		getInfo: vi.fn(() => ({})),
		...over,
	};
	return { port, write, releaseLock, getWriter };
}

function conSerial(impl: {
	requestPort?: ReturnType<typeof vi.fn>;
	getPorts?: ReturnType<typeof vi.fn>;
}) {
	vi.stubGlobal("navigator", {
		serial: {
			requestPort: impl.requestPort ?? vi.fn(),
			getPorts: impl.getPorts ?? vi.fn().mockResolvedValue([]),
		},
	});
}

beforeEach(async () => {
	localStorage.clear();
	await disconnect();
});

afterEach(async () => {
	await disconnect();
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

describe("isSerialSupported", () => {
	it("false sin navigator.serial", () => {
		vi.stubGlobal("navigator", {});
		expect(isSerialSupported()).toBe(false);
	});

	it("true con navigator.serial", () => {
		conSerial({});
		expect(isSerialSupported()).toBe(true);
	});
});

describe("silent flag en localStorage", () => {
	it("false por defecto y roundtrip 1/0", () => {
		expect(getSilentEnabled()).toBe(false);
		setSilentEnabled(true);
		expect(getSilentEnabled()).toBe(true);
		setSilentEnabled(false);
		expect(getSilentEnabled()).toBe(false);
	});

	it("false si localStorage lanza", () => {
		vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
			throw new Error("bloqueado");
		});
		expect(getSilentEnabled()).toBe(false);
	});

	it("set no lanza si localStorage falla", () => {
		vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
			throw new Error("bloqueado");
		});
		expect(() => setSilentEnabled(true)).not.toThrow();
	});
});

describe("getAvailablePorts / requestSerialPort", () => {
	it("getAvailablePorts devuelve [] sin soporte", async () => {
		vi.stubGlobal("navigator", {});
		await expect(getAvailablePorts()).resolves.toEqual([]);
	});

	it("getAvailablePorts delega a getPorts", async () => {
		const { port } = mockPort();
		conSerial({ getPorts: vi.fn().mockResolvedValue([port]) });
		await expect(getAvailablePorts()).resolves.toEqual([port]);
	});

	it("requestSerialPort lanza sin soporte", async () => {
		vi.stubGlobal("navigator", {});
		await expect(requestSerialPort()).rejects.toThrow(
			"Web Serial no soportado",
		);
	});

	it("requestSerialPort guarda el puerto en caché", async () => {
		const { port } = mockPort();
		conSerial({ requestPort: vi.fn().mockResolvedValue(port) });
		await expect(requestSerialPort()).resolves.toBe(port);
		// ensureConnected reutiliza la caché sin llamar getPorts
		const getPorts = vi.fn().mockResolvedValue([]);
		vi.stubGlobal("navigator", {
			serial: { requestPort: vi.fn(), getPorts },
		});
		await expect(ensureConnected()).resolves.toBe(port);
		expect(getPorts).not.toHaveBeenCalled();
	});
});

describe("openPort", () => {
	it("abre con baudRate 9600", async () => {
		const { port } = mockPort();
		await openPort(port as never);
		expect(port.open).toHaveBeenCalledWith({ baudRate: 9600 });
	});

	it("tolera puerto ya abierto", async () => {
		const { port } = mockPort({
			open: vi.fn().mockRejectedValue(new Error("already open")),
		});
		await expect(openPort(port as never)).resolves.toBeUndefined();
	});

	it("tolera variantes de mensaje con open", async () => {
		const { port } = mockPort({
			open: vi.fn().mockRejectedValue(new Error("Port is Open")),
		});
		await expect(openPort(port as never)).resolves.toBeUndefined();
	});

	it("re-lanza errores ajenos a open", async () => {
		const { port } = mockPort({
			open: vi.fn().mockRejectedValue(new Error("permiso denegado")),
		});
		await expect(openPort(port as never)).rejects.toThrow(
			"permiso denegado",
		);
	});

	it("re-lanza rechazos que no son Error", async () => {
		const { port } = mockPort({
			open: vi.fn().mockRejectedValue("fallo-plano"),
		});
		await expect(openPort(port as never)).rejects.toBe("fallo-plano");
	});
});

describe("ensureConnected", () => {
	it("null sin soporte", async () => {
		vi.stubGlobal("navigator", {});
		await expect(ensureConnected()).resolves.toBeNull();
	});

	it("null sin puertos disponibles", async () => {
		conSerial({ getPorts: vi.fn().mockResolvedValue([]) });
		await expect(ensureConnected()).resolves.toBeNull();
	});

	it("abre el primer puerto disponible", async () => {
		const { port } = mockPort({ writable: null });
		conSerial({ getPorts: vi.fn().mockResolvedValue([port]) });
		await expect(ensureConnected()).resolves.toBe(port);
		expect(port.open).toHaveBeenCalledWith({ baudRate: 9600 });
	});

	it("null si abrir el puerto falla", async () => {
		const { port } = mockPort({
			writable: null,
			open: vi.fn().mockRejectedValue(new Error("sin permiso")),
		});
		conSerial({ getPorts: vi.fn().mockResolvedValue([port]) });
		await expect(ensureConnected()).resolves.toBeNull();
	});

	it("abre el puerto cacheado si aún no tiene writable", async () => {
		const { port } = mockPort({ writable: null });
		conSerial({ requestPort: vi.fn().mockResolvedValue(port) });
		await requestSerialPort();
		// writable listo tras simular apertura
		port.writable = mockPort().port.writable;
		await expect(ensureConnected()).resolves.toBe(port);
	});

	it("limpia la caché si el puerto cacheado falla y usa getPorts", async () => {
		const malo = mockPort({
			writable: null,
			open: vi.fn().mockRejectedValue(new Error("desconectado")),
		});
		const bueno = mockPort();
		conSerial({
			requestPort: vi.fn().mockResolvedValue(malo.port),
			getPorts: vi.fn().mockResolvedValue([bueno.port]),
		});
		await requestSerialPort();
		await expect(ensureConnected()).resolves.toBe(bueno.port);
	});
});

describe("writeToPort / printViaSerial", () => {
	it("escribe y libera el writer", async () => {
		const { port, write, releaseLock } = mockPort();
		const data = new Uint8Array([1, 2, 3]);
		await writeToPort(port as never, data);
		expect(write).toHaveBeenCalledWith(data);
		expect(releaseLock).toHaveBeenCalled();
	});

	it("abre el puerto si no hay writable", async () => {
		const { port, write } = mockPort({ writable: null });
		// openPort con éxito pero writable sigue null -> writeToPort lanzaría;
		// simulamos apertura que deja writable listo
		const writerMocks = (() => {
			const w = vi.fn().mockResolvedValue(undefined);
			const r = vi.fn();
			return { w, r };
		})();
		port.open.mockImplementation(async () => {
			port.writable = {
				getWriter: vi.fn(() => ({
					write: writerMocks.w,
					releaseLock: writerMocks.r,
				})),
			};
		});
		await writeToPort(port as never, new Uint8Array([9]));
		expect(port.open).toHaveBeenCalled();
		expect(writerMocks.w).toHaveBeenCalled();
		expect(write).not.toHaveBeenCalled();
	});

	it("printViaSerial lanza mensaje de ayuda sin impresora", async () => {
		conSerial({ getPorts: vi.fn().mockResolvedValue([]) });
		await expect(printViaSerial(new Uint8Array([1]))).rejects.toThrow(
			"Impresora no conectada",
		);
	});

	it("printViaSerial escribe al puerto conectado", async () => {
		const { port, write } = mockPort();
		conSerial({ getPorts: vi.fn().mockResolvedValue([port]) });
		const data = new Uint8Array([0x1b, 0x40]);
		await printViaSerial(data);
		expect(write).toHaveBeenCalledWith(data);
	});
});

describe("disconnect / getCachedPortInfo", () => {
	it("sin caché es no-op e info null", async () => {
		await expect(disconnect()).resolves.toBeUndefined();
		expect(getCachedPortInfo()).toBeNull();
	});

	it("cierra el puerto y limpia la caché", async () => {
		const { port } = mockPort();
		conSerial({ requestPort: vi.fn().mockResolvedValue(port) });
		await requestSerialPort();
		expect(getCachedPortInfo()).toBe("Puerto serie conectado");
		await disconnect();
		expect(port.close).toHaveBeenCalled();
		expect(getCachedPortInfo()).toBeNull();
	});

	it("muestra VID/PID en hexadecimal", async () => {
		const { port } = mockPort({
			getInfo: vi.fn(() => ({ usbVendorId: 0x2341, usbProductId: 0x43 })),
		});
		conSerial({ requestPort: vi.fn().mockResolvedValue(port) });
		await requestSerialPort();
		expect(getCachedPortInfo()).toBe("VID:2341 PID:43");
	});

	it("PID ? cuando solo hay vendorId", async () => {
		const { port } = mockPort({
			getInfo: vi.fn(() => ({ usbVendorId: 0x1234 })),
		});
		conSerial({ requestPort: vi.fn().mockResolvedValue(port) });
		await requestSerialPort();
		expect(getCachedPortInfo()).toBe("VID:1234 PID:?");
	});

	it("disconnect limpia aunque close falle", async () => {
		const { port } = mockPort({
			close: vi.fn().mockRejectedValue(new Error("ya cerrado")),
		});
		conSerial({ requestPort: vi.fn().mockResolvedValue(port) });
		await requestSerialPort();
		await expect(disconnect()).resolves.toBeUndefined();
		expect(getCachedPortInfo()).toBeNull();
	});

	it("devuelve Conectado si getInfo lanza", async () => {
		const { port } = mockPort({
			getInfo: vi.fn(() => {
				throw new Error("sin info");
			}),
		});
		conSerial({ requestPort: vi.fn().mockResolvedValue(port) });
		await requestSerialPort();
		expect(getCachedPortInfo()).toBe("Conectado");
	});
});
