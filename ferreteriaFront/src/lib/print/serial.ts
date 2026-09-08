type SerialPort = {
  open: (opts: { baudRate: number }) => Promise<void>;
  close: () => Promise<void>;
  writable: WritableStream<Uint8Array> | null;
  readable: ReadableStream | null;
  getInfo: () => { usbVendorId?: number; usbProductId?: number };
};

declare global {
  interface Navigator {
    serial?: {
      requestPort: (opts?: { filters?: { usbVendorId?: number }[] }) => Promise<SerialPort>;
      getPorts: () => Promise<SerialPort[]>;
    };
  }
}

const STORAGE_KEY = "ferreteria-print-silent";
const BAUD_RATE = 9600;

export function isSerialSupported(): boolean {
  return typeof navigator !== "undefined" && !!navigator.serial;
}

export function getSilentEnabled(): boolean {
  try { return localStorage.getItem(STORAGE_KEY) === "1"; } catch { return false; }
}
export function setSilentEnabled(v: boolean): void {
  try { localStorage.setItem(STORAGE_KEY, v ? "1" : "0"); } catch {}
}

let cachedPort: SerialPort | null = null;

export async function getAvailablePorts(): Promise<SerialPort[]> {
  if (!isSerialSupported()) return [];
  return navigator.serial!.getPorts();
}

export async function requestSerialPort(): Promise<SerialPort | null> {
  if (!isSerialSupported()) throw new Error("Web Serial no soportado en este navegador. Usa Chrome/Edge desktop en HTTPS o localhost.");
  const port = await navigator.serial!.requestPort();
  cachedPort = port;
  return port;
}

export async function openPort(port: SerialPort): Promise<void> {
  // Algunos puertos ya vienen abiertos
  try {
    await port.open({ baudRate: BAUD_RATE });
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e);
    if (!msg.includes("already open") && !msg.toLowerCase().includes("open")) throw e;
  }
}

export async function ensureConnected(): Promise<SerialPort | null> {
  if (!isSerialSupported()) return null;
  if (cachedPort) {
    try {
      // Intentar abrir si no lo está
      if (!cachedPort.writable) await openPort(cachedPort);
      return cachedPort;
    } catch { cachedPort = null; }
  }
  const ports = await getAvailablePorts();
  if (ports.length === 0) return null;
  cachedPort = ports[0];
  try {
    if (!cachedPort.writable) await openPort(cachedPort);
    return cachedPort;
  } catch { return null; }
}

export async function writeToPort(port: SerialPort, data: Uint8Array): Promise<void> {
  if (!port.writable) await openPort(port);
  const writer = port.writable!.getWriter();
  try {
    await writer.write(data);
  } finally {
    writer.releaseLock();
  }
}

export async function printViaSerial(data: Uint8Array): Promise<void> {
  const port = await ensureConnected();
  if (!port) throw new Error("Impresora no conectada. Ve a Administración > Configuración > Impresora y pulsa Conectar.");
  await writeToPort(port, data);
}

export async function disconnect(): Promise<void> {
  if (cachedPort) {
    try { await cachedPort.close(); } catch {}
    cachedPort = null;
  }
}

export function getCachedPortInfo(): string | null {
  if (!cachedPort) return null;
  try {
    const info = cachedPort.getInfo();
    if (info.usbVendorId) return `VID:${info.usbVendorId.toString(16)} PID:${info.usbProductId?.toString(16) ?? "?"}`;
    return "Puerto serie conectado";
  } catch { return "Conectado"; }
}
