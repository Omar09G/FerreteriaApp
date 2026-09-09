import {
	forwardRef,
	useCallback,
	useRef,
	type ButtonHTMLAttributes,
	type ReactNode,
} from "react";
import { useHotkey } from "@/hooks/useHotkey";

type Variant = "primary" | "secondary" | "ghost" | "danger" | "success";
type Size = "sm" | "md" | "lg";

const VARIANTES: Record<Variant, string> = {
	primary: "bg-primary text-white hover:bg-primary-hover disabled:bg-line",
	secondary:
		"bg-surface text-ink border border-line hover:bg-warmbg disabled:bg-warmbg",
	ghost: "text-primary hover:bg-orange-100 disabled:text-muted",
	danger: "bg-red-600 text-white hover:bg-red-700 disabled:bg-line",
	success: "bg-green-600 text-white hover:bg-green-700 disabled:bg-line",
};

const TAMANOS: Record<Size, string> = {
	sm: "px-2.5 py-1.5 text-xs",
	md: "px-3.5 py-2 text-sm",
	lg: "px-5 py-2.5 text-base",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
	variant?: Variant;
	size?: Size;
	children?: ReactNode;
	/** Atajo visual + aria-keyshortcuts. Ej: "F2", "Ctrl+Enter". No suscribe listener, solo visual. */
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
			<button
				ref={setRef}
				type={type}
				aria-keyshortcuts={ariaKeyshortcuts}
				title={computedTitle}
				onClick={onClick}
				className={`inline-flex items-center justify-center gap-1.5 rounded-md font-medium transition-colors disabled:cursor-not-allowed disabled:text-muted ${VARIANTES[variant]} ${TAMANOS[size]} ${className}`}
				{...rest}
			>
				{shouldShowKbd && (
					<kbd className="rounded border border-current/20 bg-black/10 px-1 py-0.5 font-mono text-[10px] font-semibold leading-none tracking-wide">
						{hotkey}
					</kbd>
				)}
				{children}
			</button>
		);
	},
);
