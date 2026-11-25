import {
	forwardRef,
	useCallback,
	useRef,
	type ButtonHTMLAttributes,
	type ReactNode,
} from "react";
import { motion } from "motion/react";
import { useHotkey } from "@/hooks/useHotkey";

type Variant = "primary" | "secondary" | "ghost" | "danger" | "success";
type Size = "sm" | "md" | "lg";

const VARIANTES: Record<Variant, string> = {
	primary: "bg-primary text-on-primary hover:bg-primary-hover disabled:bg-line",
	secondary:
		"bg-surface text-ink border border-line hover:bg-warmbg disabled:bg-warmbg",
	ghost: "text-primary hover:bg-warmbg disabled:text-muted",
	danger: "bg-error-solid text-white hover:bg-error-solid-hover disabled:bg-line",
	success:
		"bg-success-solid text-white hover:bg-success-solid-hover disabled:bg-line",
};

const TAMANOS: Record<Size, string> = {
	sm: "px-2.5 py-1.5 text-xs",
	md: "px-3.5 py-2 text-sm",
	lg: "px-5 py-2.5 text-base",
};

interface ButtonProps
	extends Omit<
		ButtonHTMLAttributes<HTMLButtonElement>,
		// motion.button redefine estos handlers con firma propia (PanInfo);
		// se excluyen del API: ningún botón del POS/admin los usa.
		| "onDrag"
		| "onDragEnd"
		| "onDragEnter"
		| "onDragExit"
		| "onDragLeave"
		| "onDragOver"
		| "onDragStart"
		| "onDrop"
		| "onAnimationStart"
		| "onAnimationEnd"
		| "onAnimationIteration"
	> {
	variant?: Variant;
	size?: Size;
	children?: ReactNode;
	/** Atajo (ej: "F2", "Ctrl+Enter"): muestra <kbd>, aria-keyshortcuts y
	 *  suscribe el listener que activa el botón. No dupliques con useHotkey
	 *  en la página para la misma tecla: una sola propiedad por atajo. */
	hotkey?: string;
	/** Muestra el <kbd> dentro del boton. Default true si hay hotkey y no disabled. */
	showHotkey?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
	function Button(
		{
			variant = "primary",
			size = "md",
			className = "",
			type = "button",
			children,
			hotkey,
			showHotkey = true,
			title,
			onClick,
			...rest
		},
		ref,
	) {
		const isDisabled = Boolean(rest.disabled);
		const shouldShowKbd = Boolean(hotkey && showHotkey && !isDisabled);
		const ariaKeyshortcuts = hotkey && !isDisabled ? hotkey : undefined;
		const computedTitle = title ?? (hotkey && !isDisabled ? `Atajo: ${hotkey}` : undefined);

		const innerRef = useRef<HTMLButtonElement>(null);

		const setRef = useCallback(
			(node: HTMLButtonElement | null) => {
				innerRef.current = node;
				if (typeof ref === "function") ref(node);
				else if (ref) (ref as React.MutableRefObject<HTMLButtonElement | null>).current = node;
			},
			[ref],
		);

		const handleHotkey = useCallback(() => {
			if (isDisabled) return;
			innerRef.current?.click();
		}, [isDisabled]);

		useHotkey(hotkey ?? "", handleHotkey, {
			enabled: Boolean(hotkey && !isDisabled),
		});

	return (
		// whileTap global: feedback háptico en POS y admin con un solo cambio.
		// transition-colors solo anima colores (sin conflicto con el transform
		// inline de motion); reduced-motion lo apaga vía MotionConfig.
		<motion.button
			ref={setRef}
			type={type}
			aria-keyshortcuts={ariaKeyshortcuts}
			title={computedTitle}
			onClick={onClick}
			whileTap={isDisabled ? undefined : { scale: 0.97 }}
			className={`inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition-colors duration-150 disabled:cursor-not-allowed disabled:text-muted ${VARIANTES[variant]} ${TAMANOS[size]} ${className}`}
			{...rest}
		>
			{shouldShowKbd && (
				<kbd className="rounded border border-current/20 bg-black/10 px-1 py-0.5 font-mono text-[10px] font-semibold leading-none tracking-wide">
					{hotkey}
				</kbd>
			)}
			{children}
		</motion.button>
	);
	},
);
