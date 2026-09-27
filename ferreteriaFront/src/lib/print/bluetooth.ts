/**
 * Impresión ESC/POS por Bluetooth Low Energy (Web Bluetooth).
 * Cubre Android + laptops con Chrome/Edge. iOS Safari NO implementa
 * Web Bluetooth: ahí se usa el mismo port vía shell nativa (Capacitor,
 * ver nota al final) o impresora de red.
 *
 * Los bytes salen de `buildEscPosTicket` (escpos.ts): el transporte solo
 * trocea en chunks de 20 B (MTU BLE por defecto) sobre la primera
 * característica escribible del servicio (autodescubrimiento agnóstico
 * a marca: XPrinter, GOOJPRT, Munbyn, etc.).
 */

type BleCharacteristic = {
	uuid: string;
	properties: { write?: boolean; writeWithoutResponse?: boolean };
	writeValue?: (v: BufferSource) => Promise<void>;
	writeValueWithoutResponse?: (v: BufferSource) => Promise<void>;
};

type BleService = {
	uuid: string;
	getCharacteristics: () => Promise<BleCharacteristic[]>;
};

type BleServer = {
	connected: boolean;
	connect: () => Promise<BleServer>;
	disconnect: () => void;
	getPrimaryServices: () => Promise<BleService[]>;
};

type BleDevice = {
	id: string;
	name?: string;
	gatt?: BleServer;
	addEventListener: (t: string, fn: () => void) => void;
};

declare global {
	interface Navigator {
		bluetooth?: {
			getAvailability?: () => Promise<boolean>;
			getDevices?: () => Promise<BleDevice[]>;
			requestDevice: (opts: {
				acceptAllDevices?: boolean;
				optionalServices?: string[];
			}) => Promise<BleDevice>;
		};
	}
}

const STORAGE_KEY = "ferreteria-ble-printer";
const CHUNK = 20;
// Servicios BLE típicos de impresoras térmicas genéricas (UART Nordic,
//IFIYIWQQ EUYKJI WLKH DOZQUIO). Se piden como opcionales; el descubrimiento
// real es por característica escribible, así que otras marcas funcionan igual.
const OPTIONAL_SERVICES = [
	"6e400001-b5a3-f393-e0a9-e50e24dcca9e",
	"49535343-fe7b-4ae5-8fa9-9fafd205e455",
	"0000ff00-0000-1000-8000-00805f9b34fb",
];

export function isBluetoothSupported(): boolean {
	return typeof navigator !== "undefined" && !!navigator.bluetooth;
}

let cachedDevice: BleDevice | null = null;
let cachedChar: BleCharacteristic | null = null;

async function findWritableChar(server: BleServer): Promise<BleCharacteristic | null> {
	const services = await server.getPrimaryServices();
	for (const svc of services) {
		const chars = await svc.getCharacteristics();
		const direct = chars.find(
			(c) => c.properties.writeWithoutResponse || c.properties.write,
		);
		if (direct) return direct;
	}
	return null;
}

async function ensureServer(device: BleDevice): Promise<BleServer | null> {
	if (!device.gatt) return null;
	const server = device.gatt.connected
		? device.gatt
		: await device.gatt.connect();
	const char = await findWritableChar(server);
	if (!char) {
		server.disconnect();
		return null;
	}
	cachedDevice = device;
	cachedChar = char;
	try {
		localStorage.setItem(STORAGE_KEY, device.id);
	} catch {
		// sin persistencia igual funciona la sesión
	}
	return server;
}

/** Conecta a una impresora recordada (silencioso) o pide elegir una (gesto usuario). */
export async function ensurePrinter(): Promise<BleCharacteristic | null> {
	if (!isBluetoothSupported()) return null;
	if (cachedChar) return cachedChar;
	try {
		const known = await navigator.bluetooth!.getDevices?.();
		let wanted: string | null = null;
		try {
			wanted = localStorage.getItem(STORAGE_KEY);
		} catch {
			wanted = null;
		}
		const remembered = (known ?? []).find((d) => d.id === wanted);
		if (remembered) {
			const server = await ensureServer(remembered);
			if (server) return cachedChar;
		}
		const device = await navigator.bluetooth!.requestDevice({
			acceptAllDevices: true,
			optionalServices: OPTIONAL_SERVICES,
		});
		await ensureServer(device);
		return cachedChar;
	} catch {
		return null;
	}
}

async function writeChunks(char: BleCharacteristic, data: Uint8Array): Promise<void> {
	for (let i = 0; i < data.length; i += CHUNK) {
		const slice = data.slice(i, i + CHUNK);
		if (char.writeValueWithoutResponse) {
			await char.writeValueWithoutResponse(slice);
		} else {
			await char.writeValue?.(slice);
		}
	}
}

/** Imprime bytes ESC/POS por BLE. Retorna false si no hay Bluetooth o impresora. */
export async function printViaBluetooth(data: Uint8Array): Promise<boolean> {
	const char = await ensurePrinter();
	if (!char) return false;
	try {
		await writeChunks(char, data);
		return true;
	} catch {
		cachedChar = null;
		return false;
	}
}

export async function disconnectPrinter(): Promise<void> {
	try {
		cachedDevice?.gatt?.disconnect();
	} catch {
		// silencio
	}
	cachedDevice = null;
	cachedChar = null;
}

export function getCachedPrinterInfo(): string | null {
	if (cachedDevice?.name) return cachedDevice.name;
	try {
		return localStorage.getItem(STORAGE_KEY);
	} catch {
		return null;
	}
}
