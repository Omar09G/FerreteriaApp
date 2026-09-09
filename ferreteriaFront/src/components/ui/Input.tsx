import {
	forwardRef,
	useCallback,
	useEffect,
	useRef,
	type InputHTMLAttributes,
	type ReactNode,
	type SelectHTMLAttributes,
} from "react";
import { useHotkey } from "@/hooks/useHotkey";

interface CampoProps {
	label?: string;
	error?: string;
	required?: boolean;
	hint?: string;
	/** Atajo para aria-keyshortcuts. No va en placeholder (se borra al escribir), se muestra en hint/label. */
	hotkey?: string;
}

export function CampoWidget({
	label,
	error,
	required,
	hint,
	hotkey,
	children,
}: {
	label?: string;
	error?: string;
	required?: boolean;
	hint?: string;
	hotkey?: string;
	children: ReactNode;
}) {
	return (
		<label className="flex flex-col gap-1 text-sm">
			{label && (
				<span className="font-medium text-ink inline-flex items-center gap-1.5">
					{label}
					{hotkey && (
						<kbd className="rounded border border-line bg-canvas px-1 py-0.5 font-mono text-[10px] font-semibold leading-none tracking-wide text-muted">
							{hotkey}
						</kbd>
					)}
					{required && <span className="text-red-600"> *</span>}
				</span>
			)}
			{children}
			{hint && !error && <span className="text-xs text-muted">{hint}</span>}
			{error && <span className="text-xs text-red-600">{error}</span>}
		</label>
	);
}

const BASE =
	"rounded-md border border-line bg-surface px-3 py-2 text-sm text-ink placeholder:text-muted focus:outline-2 focus:outline-primary disabled:bg-warmbg";

export const Input = forwardRef<
	HTMLInputElement,
	InputHTMLAttributes<HTMLInputElement> & CampoProps & { icono?: ReactNode }
>(function Input(
	{ label, error, hint, hotkey, required, icono, className = "", disabled, ...rest },
	ref,
) {
	const innerRef = useRef<HTMLInputElement>(null);
	const setRef = useCallback(
		(node: HTMLInputElement | null) => {
			innerRef.current = node;
			if (typeof ref === "function") ref(node);
			else if (ref) (ref as React.MutableRefObject<HTMLInputElement | null>).current = node;
		},
		[ref],
	);
	useEffect(() => {
		// Sync disabled state no necesita handler
	}, [disabled]);
	const focusInput = useCallback(() => {
		if (disabled) return;
		innerRef.current?.focus();
		innerRef.current?.select?.();
	}, [disabled]);
	useHotkey(hotkey ?? "", focusInput, { enabled: Boolean(hotkey && !disabled) });

	return (
		<CampoWidget label={label} error={error} hint={hint} hotkey={hotkey} required={required}>
			<span className="relative block">
				{icono && (
					<span className="pointer-events-none absolute inset-y-0 left-2.5 flex items-center">
						{icono}
					</span>
				)}
				<input
					ref={setRef}
					required={required}
					disabled={disabled}
					aria-invalid={Boolean(error)}
					aria-keyshortcuts={hotkey || undefined}
					className={`${BASE} ${icono ? "pl-9" : ""} ${error ? "border-red-500" : ""} ${className}`}
					{...rest}
				/>
			</span>
		</CampoWidget>
	);
});

export const Select = forwardRef<
	HTMLSelectElement,
	SelectHTMLAttributes<HTMLSelectElement> & CampoProps
>(function Select(
	{ label, error, hint, hotkey, required, className = "", disabled, children, ...rest },
	ref,
) {
	const innerRef = useRef<HTMLSelectElement>(null);
	const setRef = useCallback(
		(node: HTMLSelectElement | null) => {
			innerRef.current = node;
			if (typeof ref === "function") ref(node);
			else if (ref) (ref as React.MutableRefObject<HTMLSelectElement | null>).current = node;
		},
		[ref],
	);
	const focusSelect = useCallback(() => {
		if (disabled) return;
		innerRef.current?.focus();
	}, [disabled]);
	useHotkey(hotkey ?? "", focusSelect, { enabled: Boolean(hotkey && !disabled) });

	return (
		<CampoWidget label={label} error={error} hint={hint} hotkey={hotkey} required={required}>
			<select
				ref={setRef}
				required={required}
				disabled={disabled}
				aria-invalid={Boolean(error)}
				aria-keyshortcuts={hotkey || undefined}
				className={`${BASE} ${error ? "border-red-500" : ""} ${className}`}
				{...rest}
			>
				{children}
			</select>
		</CampoWidget>
	);
});
