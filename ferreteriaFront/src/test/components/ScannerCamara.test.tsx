import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ScannerCamara } from "@/components/ScannerCamara";

const decodeFromVideoDeviceMock = vi.hoisted(() => vi.fn());
const stopLectorMock = vi.hoisted(() => vi.fn());

vi.mock("@zxing/browser", () => ({
	BrowserMultiFormatReader: vi.fn(() => ({
		decodeFromVideoDevice: decodeFromVideoDeviceMock,
	})),
}));

const getUserMediaMock = vi.fn();
const stopTrackMock = vi.fn();
const rafCallbacks: FrameRequestCallback[] = [];
let rafId = 0;

function streamFalso(): MediaStream {
	return {
		getTracks: () => [{ stop: stopTrackMock }],
	} as unknown as MediaStream;
}

function definirMediaDevices() {
	Object.defineProperty(window.navigator, "mediaDevices", {
		value: { getUserMedia: getUserMediaMock },
		configurable: true,
	});
}

beforeEach(() => {
	vi.clearAllMocks();
	rafCallbacks.length = 0;
	rafId = 0;
	localStorage.clear();
	delete (window as unknown as { BarcodeDetector?: unknown }).BarcodeDetector;
	definirMediaDevices();
	getUserMediaMock.mockResolvedValue(streamFalso());
	vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue(undefined);
	vi.stubGlobal(
		"requestAnimationFrame",
		vi.fn((cb: FrameRequestCallback) => {
			rafCallbacks.push(cb);
			rafId += 1;
			return rafId;
		}),
	);
	vi.stubGlobal("cancelAnimationFrame", vi.fn());
	decodeFromVideoDeviceMock.mockImplementation(
		async (_device: unknown, _video: unknown, cb: (r: unknown) => void) => {
			cb({ getText: () => "ZX-999" });
			return { stop: stopLectorMock };
		},
	);
});

function correrRaf(t = 500) {
	const cbs = [...rafCallbacks];
	rafCallbacks.length = 0;
	for (const cb of cbs) void cb(t);
}

describe("ScannerCamara", () => {
	it("no toca la cámara cuando está cerrado", () => {
		render(
			<ScannerCamara abierto={false} onDetectado={vi.fn()} onCerrar={vi.fn()} />,
		);
		expect(screen.queryByText("Escanear código de barras")).not.toBeInTheDocument();
		expect(getUserMediaMock).not.toHaveBeenCalled();
	});

	it("usa BarcodeDetector nativo y avisa el código limpio una sola vez", async () => {
		const detectMock = vi.fn().mockResolvedValue([{ rawValue: "  ABC123  " }]);
		(window as unknown as { BarcodeDetector: unknown }).BarcodeDetector =
			class {
				detect = detectMock;
			};
		const onDetectado = vi.fn();
		render(
			<ScannerCamara abierto onDetectado={onDetectado} onCerrar={vi.fn()} />,
		);

		expect(screen.getByRole("status")).toBeInTheDocument();
		await waitFor(() => expect(getUserMediaMock).toHaveBeenCalled());
		await waitFor(() => expect(detectMock).not.toHaveBeenCalled());
		correrRaf();
		await vi.waitFor(() => expect(detectMock).toHaveBeenCalled());
		await vi.waitFor(() => expect(onDetectado).toHaveBeenCalledWith("ABC123"));
		expect(
			await screen.findByText("Apunta la cámara al código de barras…"),
		).toBeInTheDocument();

		// No duplica avisos aunque el detector siga hallando el mismo código
		detectMock.mockResolvedValue([{ rawValue: "ABC123" }]);
		correrRaf(1000);
		await new Promise((r) => setTimeout(r, 20));
		expect(onDetectado).toHaveBeenCalledTimes(1);
	});

	it("ignora detecciones vacías y reintenta en el siguiente frame", async () => {
		const detectMock = vi
			.fn()
			.mockResolvedValueOnce([{ rawValue: "   " }])
			.mockResolvedValueOnce([{ rawValue: "OK-7" }]);
		(window as unknown as { BarcodeDetector: unknown }).BarcodeDetector =
			class {
				detect = detectMock;
			};
		const onDetectado = vi.fn();
		render(
			<ScannerCamara abierto onDetectado={onDetectado} onCerrar={vi.fn()} />,
		);
		await waitFor(() => expect(getUserMediaMock).toHaveBeenCalled());
		correrRaf();
		await vi.waitFor(() => expect(detectMock).toHaveBeenCalledTimes(1));
		expect(onDetectado).not.toHaveBeenCalled();
		correrRaf(1000);
		await vi.waitFor(() => expect(onDetectado).toHaveBeenCalledWith("OK-7"));
	});

	it("tolera frames aún no listos (detect rechaza) sin romper el ciclo", async () => {
		const detectMock = vi
			.fn()
			.mockRejectedValueOnce(new Error("frame no listo"))
			.mockResolvedValueOnce([{ rawValue: "F-1" }]);
		(window as unknown as { BarcodeDetector: unknown }).BarcodeDetector =
			class {
				detect = detectMock;
			};
		const onDetectado = vi.fn();
		render(
			<ScannerCamara abierto onDetectado={onDetectado} onCerrar={vi.fn()} />,
		);
		await waitFor(() => expect(getUserMediaMock).toHaveBeenCalled());
		correrRaf();
		await vi.waitFor(() => expect(detectMock).toHaveBeenCalledTimes(1));
		correrRaf(1000);
		await vi.waitFor(() => expect(onDetectado).toHaveBeenCalledWith("F-1"));
	});

	it("usa ZXing como respaldo cuando no hay BarcodeDetector", async () => {
		const onDetectado = vi.fn();
		render(
			<ScannerCamara abierto onDetectado={onDetectado} onCerrar={vi.fn()} />,
		);
		await vi.waitFor(() => expect(decodeFromVideoDeviceMock).toHaveBeenCalled());
		expect(onDetectado).toHaveBeenCalledWith("ZX-999");
		expect(
			await screen.findByText("Apunta la cámara al código de barras…"),
		).toBeInTheDocument();
	});

	it("cae a ZXing si el constructor nativo lanza", async () => {
		(window as unknown as { BarcodeDetector: unknown }).BarcodeDetector =
			class {
				constructor() {
					throw new Error("formatos no soportados");
				}
			};
		const onDetectado = vi.fn();
		render(
			<ScannerCamara abierto onDetectado={onDetectado} onCerrar={vi.fn()} />,
		);
		await vi.waitFor(() => expect(decodeFromVideoDeviceMock).toHaveBeenCalled());
		expect(onDetectado).toHaveBeenCalledWith("ZX-999");
	});

	it("muestra el mensaje de permiso denegado (NotAllowedError)", async () => {
		const err = new DOMException("denied", "NotAllowedError");
		getUserMediaMock.mockRejectedValueOnce(err);
		render(
			<ScannerCamara abierto onDetectado={vi.fn()} onCerrar={vi.fn()} />,
		);
		expect(
			await screen.findByText(/Permiso de cámara denegado/),
		).toBeInTheDocument();
	});

	it("muestra el mensaje sin cámara (NotFoundError)", async () => {
		getUserMediaMock.mockRejectedValueOnce(
			new DOMException("none", "NotFoundError"),
		);
		render(
			<ScannerCamara abierto onDetectado={vi.fn()} onCerrar={vi.fn()} />,
		);
		expect(
			await screen.findByText(/No se encontró una cámara disponible/),
		).toBeInTheDocument();
	});

	it("muestra el mensaje genérico ante un fallo desconocido", async () => {
		getUserMediaMock.mockRejectedValueOnce(new Error("raro"));
		render(
			<ScannerCamara abierto onDetectado={vi.fn()} onCerrar={vi.fn()} />,
		);
		expect(
			await screen.findByText(/No se pudo abrir la cámara/),
		).toBeInTheDocument();
	});

	it("detiene el stream si se cierra antes de que abra la cámara", async () => {
		let resolver: ((s: MediaStream) => void) | undefined;
		getUserMediaMock.mockReturnValueOnce(
			new Promise<MediaStream>((r) => {
				resolver = r;
			}),
		);
		const onDetectado = vi.fn();
		const { unmount } = render(
			<ScannerCamara abierto onDetectado={onDetectado} onCerrar={vi.fn()} />,
		);
		unmount();
		resolver!(streamFalso());
		await vi.waitFor(() => expect(stopTrackMock).toHaveBeenCalled());
		expect(onDetectado).not.toHaveBeenCalled();
	});

	it("el botón Cancelar cierra y desmontar detiene la cámara", async () => {
		const user = userEvent.setup();
		const onCerrar = vi.fn();
		const { unmount } = render(
			<ScannerCamara abierto onDetectado={vi.fn()} onCerrar={onCerrar} />,
		);
		await waitFor(() => expect(getUserMediaMock).toHaveBeenCalled());
		await user.click(screen.getByRole("button", { name: "Cancelar" }));
		expect(onCerrar).toHaveBeenCalledOnce();
		unmount();
		expect(stopTrackMock).toHaveBeenCalled();
	});
});
