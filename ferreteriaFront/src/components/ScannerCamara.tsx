import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Spinner } from "@/components/ui/Spinner";

/**
 * Escáner de códigos de barras con la cámara del dispositivo.
 *
 * Estrategia en dos niveles (sin pesar el bundle inicial):
 * 1. `BarcodeDetector` nativo (Chrome/Edge Android) — cero dependencias.
 * 2. `@zxing/browser` por import dinámico (Safari iOS, Firefox).
 *
 * Requiere contexto seguro: en `http://` de LAN el navegador bloquea la
 * cámara (`navigator.mediaDevices` indefinido) — ver `camaraDisponible()`
 * y PLAN_APP_MOVIL (HTTPS con mkcert). En `localhost` sí funciona.
 */

const FORMATOS = [
	"ean_13",
	"ean_8",
	"upc_a",
	"upc_e",
	"code_128",
	"code_39",
	"qr_code",
];

interface DeteccionNativa {
	rawValue?: string;
}

interface DetectorNativo {
	detect(origen: HTMLVideoElement): Promise<DeteccionNativa[]>;
}

type ConstructorDetector = new (opciones?: {
	formats: string[];
}) => DetectorNativo;

declare global {
	interface Window {
		BarcodeDetector?: ConstructorDetector;
	}
}

interface ControlesLector {
	stop(): void;
}

function mensajeErrorCamara(e: unknown): string {
	if (e instanceof DOMException) {
		if (e.name === "NotAllowedError")
			return "Permiso de cámara denegado. Actívalo en el navegador e inténtalo de nuevo.";
		if (e.name === "NotFoundError" || e.name === "OverconstrainedError")
			return "No se encontró una cámara disponible en este dispositivo.";
	}
	return "No se pudo abrir la cámara. Revisa el permiso e inténtalo de nuevo.";
}

export function ScannerCamara({
	abierto,
	onDetectado,
	onCerrar,
}: {
	abierto: boolean;
	onDetectado: (codigo: string) => void;
	onCerrar: () => void;
}) {
	const videoRef = useRef<HTMLVideoElement>(null);
	const rafRef = useRef(0);
	const streamRef = useRef<MediaStream | null>(null);
	const lectorRef = useRef<ControlesLector | null>(null);
	const detectadoRef = useRef(false);
	const [estado, setEstado] = useState<"iniciando" | "escaneando" | "error">(
		"iniciando",
	);
	const [error, setError] = useState("");

	useEffect(() => {
		if (!abierto) return;
		// Intencional: al abrir se reinicia el estado del escaneo anterior.
		/* eslint-disable react-hooks/set-state-in-effect */
		detectadoRef.current = false;
		setEstado("iniciando");
		setError("");
		/* eslint-enable react-hooks/set-state-in-effect */
		let cancelado = false;

		const detener = () => {
			cancelAnimationFrame(rafRef.current);
			lectorRef.current?.stop();
			lectorRef.current = null;
			streamRef.current?.getTracks().forEach((t) => t.stop());
			streamRef.current = null;
		};

		const avisar = (codigo: string) => {
			const limpio = codigo.trim();
			if (!limpio || detectadoRef.current) return;
			detectadoRef.current = true;
			onDetectado(limpio);
		};

		const escanearNativo = (detector: DetectorNativo, video: HTMLVideoElement) => {
			setEstado("escaneando");
			let ultimo = 0;
			const ciclo = async (t: number) => {
				if (cancelado || detectadoRef.current) return;
				if (t - ultimo > 250) {
					ultimo = t;
					try {
						const hallados = await detector.detect(video);
						const codigo = hallados
							.map((h) => h.rawValue?.trim())
							.find((v) => v);
						if (codigo) {
							avisar(codigo);
							return;
						}
					} catch {
						// Frame aún no listo; se reintenta en el siguiente ciclo.
					}
				}
				rafRef.current = requestAnimationFrame(ciclo);
			};
			rafRef.current = requestAnimationFrame(ciclo);
		};

		const iniciar = async () => {
			try {
				const stream = await navigator.mediaDevices.getUserMedia({
					video: { facingMode: "environment" },
					audio: false,
				});
				if (cancelado) {
					stream.getTracks().forEach((t) => t.stop());
					return;
				}
				streamRef.current = stream;
				const video = videoRef.current;
				if (!video) return;
				video.srcObject = stream;
				await video.play();

				const Ctor = window.BarcodeDetector;
				if (Ctor) {
					try {
						escanearNativo(new Ctor({ formats: FORMATOS }), video);
						return;
					} catch {
						// Formatos no soportados: se cae al lector ZXing.
					}
				}
				const { BrowserMultiFormatReader } = await import("@zxing/browser");
				if (cancelado || detectadoRef.current) return;
				const lector = new BrowserMultiFormatReader();
				setEstado("escaneando");
				const controles = await lector.decodeFromVideoDevice(
					undefined,
					video,
					(resultado) => {
						if (resultado) avisar(resultado.getText());
					},
				);
				lectorRef.current = controles;
			} catch (e) {
				if (!cancelado) {
					setError(mensajeErrorCamara(e));
					setEstado("error");
				}
			}
		};

		iniciar();
		return () => {
			cancelado = true;
			detener();
		};
	}, [abierto, onDetectado]);

	return (
		<Dialog
			open={abierto}
			onClose={onCerrar}
			title="Escanear código de barras"
			width="max-w-md"
		>
			<div className="space-y-3">
				<div className="overflow-hidden rounded-md bg-black">
					<video
						ref={videoRef}
						className="h-64 w-full object-cover"
						playsInline
						muted
					/>
				</div>
				{estado === "iniciando" && <Spinner />}
				{estado === "escaneando" && (
					<p className="text-sm text-muted">
						Apunta la cámara al código de barras…
					</p>
				)}
				{estado === "error" && (
					<p className="text-sm text-red-600">{error}</p>
				)}
				<div className="flex justify-end">
					<Button type="button" variant="ghost" onClick={onCerrar}>
						Cancelar
					</Button>
				</div>
			</div>
		</Dialog>
	);
}
