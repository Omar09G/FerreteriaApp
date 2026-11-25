import type { HTMLAttributes, ReactNode } from "react";

type Tone = "default" | "success" | "danger" | "warning" | "info";

const TONE: Record<Tone, string> = {
	default: "bg-warmbg text-ink border-line",
	success: "bg-success/10 text-success border-success/25",
	danger: "bg-error/10 text-error border-error/25",
	warning: "bg-warning/10 text-warning border-warning/25",
	info: "bg-info/10 text-info border-info/25",
};

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
	tone?: Tone;
	children: ReactNode;
}

export function Badge({
	tone = "default",
	className = "",
	children,
	...rest
}: BadgeProps) {
	return (
		<span
			className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium ${TONE[tone]} ${className}`}
			{...rest}
		>
			{children}
		</span>
	);
}

const SEMAFORO_ESTADOS: Record<string, Tone> = {
	COMPLETADA: "success",
	ACTIVA: "success",
	ABIERTA: "success",
	LIQUIDADA: "success",
	CERRADO: "success",
	ABIERTO: "info",
	VIGENTE: "info",
	EN_PROCESO: "info",
	BORRADOR: "default",
	PENDIENTE: "warning",
	PARCIAL: "warning",
	PROGRAMADA: "warning",
	EMITIDA: "info",
	CANCELADA: "danger",
	VENCIDA: "danger",
	EXPIRADA: "danger",
	DEVUELTA_PARCIAL: "warning",
	DEVUELTA_TOTAL: "default",
	APLICADO: "success",
	CONVERTIDA: "success",
	FINALIZADA: "default",
};

export function EstadoBadge({ estado }: { estado: string }) {
	const tone = SEMAFORO_ESTADOS[estado.toUpperCase()] ?? "default";
	return <Badge tone={tone}>{estado}</Badge>;
}
