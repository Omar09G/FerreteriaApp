import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
	disconnectPrinter,
	ensurePrinter,
	getCachedPrinterInfo,
	isBluetoothSupported,
	printViaBluetooth,
} from "@/lib/print/bluetooth";

function mockChar(over = {}) {
	return {
		uuid: "ff01",
		properties: { writeWithoutResponse: true },
		writeValueWithoutResponse: vi.fn().mockResolvedValue(undefined),
		writeValue: vi.fn().mockResolvedValue(undefined),
		...over,
	};
}

function mockServer(chars: unknown[]) {
	const service = { uuid: "ff00", getCharacteristics: vi.fn().mockResolvedValue(chars) };
	return {
		connected: false,
		connect: null as unknown,
		disconnect: vi.fn(),
		getPrimaryServices: vi.fn().mockResolvedValue([service]),
	};
}

function conBluetooth(impl: {
	getDevices?: ReturnType<typeof vi.fn>;
	requestDevice?: ReturnType<typeof vi.fn>;
}) {
	vi.stubGlobal("navigator", {
		bluetooth: {
			getDevices: impl.getDevices ?? vi.fn().mockResolvedValue([]),
			requestDevice: impl.requestDevice ?? vi.fn(),
		},
	});
}

function deviceConChar(char: ReturnType<typeof mockChar>, id = "dev-1") {
	const server = mockServer([char]);
	server.connected = false;
	const connect = vi.fn().mockResolvedValue({ ...server, connected: true });
	const device = {
		id,
		name: "XP-58",
		gatt: { ...server, connect },
		addEventListener: vi.fn(),
	};
	return { device, server, connect, char };
}

beforeEach(async () => {
	localStorage.clear();
	await disconnectPrinter();
});

afterEach(async () => {
	await disconnectPrinter();
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

describe("isBluetoothSupported", () => {
	it("false sin navigator.bluetooth", () => {
		vi.stubGlobal("navigator", {});
		expect(isBluetoothSupported()).toBe(false);
	});

	it("true con navigator.bluetooth", () => {
		conBluetooth({});
		expect(isBluetoothSupported()).toBe(true);
	});
});

describe("ensurePrinter", () => {
	it("null si no hay soporte", async () => {
		vi.stubGlobal("navigator", {});
		expect(await ensurePrinter()).toBeNull();
	});

	it("reconecta en silencio a la impresora recordada", async () => {
		const { device, char } = deviceConChar(mockChar());
		localStorage.setItem("ferreteria-ble-printer", "dev-1");
		const requestDevice = vi.fn();
		conBluetooth({ getDevices: vi.fn().mockResolvedValue([device]), requestDevice });

		const got = await ensurePrinter();

		expect(got).not.toBeNull();
		expect(requestDevice).not.toHaveBeenCalled();
		expect(getCachedPrinterInfo()).toBe("XP-58");
		expect(char.writeValueWithoutResponse).not.toHaveBeenCalled();
	});

	it("pide elegir impresora si no hay recordada", async () => {
		const { device } = deviceConChar(mockChar(), "dev-9");
		const requestDevice = vi.fn().mockResolvedValue(device);
		conBluetooth({ requestDevice });

		const got = await ensurePrinter();

		expect(got).not.toBeNull();
		expect(requestDevice).toHaveBeenCalledOnce();
		expect(localStorage.getItem("ferreteria-ble-printer")).toBe("dev-9");
	});

	it("null si ninguna característica es escribible", async () => {
		const char = mockChar({ properties: {} });
		const { device } = deviceConChar(char);
		conBluetooth({ requestDevice: vi.fn().mockResolvedValue(device) });

		expect(await ensurePrinter()).toBeNull();
	});

	it("null si el usuario cancela el diálogo", async () => {
		conBluetooth({ requestDevice: vi.fn().mockRejectedValue(new Error("cancel")) });

		expect(await ensurePrinter()).toBeNull();
	});
});

describe("printViaBluetooth", () => {
	it("trocea en chunks de 20 B", async () => {
		const { device, char } = deviceConChar(mockChar());
		conBluetooth({ requestDevice: vi.fn().mockResolvedValue(device) });

		const ok = await printViaBluetooth(new Uint8Array(45).fill(0x1b));

		expect(ok).toBe(true);
		const writes = char.writeValueWithoutResponse.mock.calls.map(
			(c) => (c[0] as Uint8Array).length,
		);
		expect(writes).toEqual([20, 20, 5]);
	});

	it("usa writeValue si no hay writeWithoutResponse", async () => {
		const char = mockChar({ properties: { write: true }, writeValueWithoutResponse: undefined });
		const { device } = deviceConChar(char);
		conBluetooth({ requestDevice: vi.fn().mockResolvedValue(device) });

		expect(await printViaBluetooth(new Uint8Array([1, 2, 3]))).toBe(true);
		expect(char.writeValue).toHaveBeenCalledOnce();
	});

	it("false si falla la escritura y limpia el caché", async () => {
		const char = mockChar();
		char.writeValueWithoutResponse.mockRejectedValueOnce(new Error("drop"));
		const { device } = deviceConChar(char);
		conBluetooth({ requestDevice: vi.fn().mockResolvedValue(device) });

		expect(await printViaBluetooth(new Uint8Array([1]))).toBe(false);
		// segundo intento reconecta desde cero
		char.writeValueWithoutResponse.mockResolvedValue(undefined);
		expect(await printViaBluetooth(new Uint8Array([1]))).toBe(true);
	});

	it("false sin impresora", async () => {
		conBluetooth({ requestDevice: vi.fn().mockRejectedValue(new Error("cancel")) });

		expect(await printViaBluetooth(new Uint8Array([1]))).toBe(false);
	});
});

describe("disconnectPrinter", () => {
	it("desconecta el GATT y olvida el caché", async () => {
		const { device, server } = deviceConChar(mockChar());
		conBluetooth({ requestDevice: vi.fn().mockResolvedValue(device) });
		await ensurePrinter();

		await disconnectPrinter();

		expect(server.disconnect).toHaveBeenCalled();
		expect(getCachedPrinterInfo()).toBe("dev-1"); // el id persiste en storage
	});
});
