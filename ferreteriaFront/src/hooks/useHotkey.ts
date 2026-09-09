import { useCallback, useEffect, useRef } from "react";

export type HotkeyCombo =
	| "F1"
	| "F2"
	| "F3"
	| "F4"
	| "F5"
	| "F6"
	| "F7"
	| "F8"
	| "F9"
	| "F10"
	| "F11"
	| "F12"
	| "Escape"
	| "Enter"
	| "Ctrl+Enter"
	| "Ctrl+S"
	| "Ctrl+F"
	| "Ctrl+N"
	| string;

interface UseHotkeyOptions {
	enabled?: boolean;
	preventDefault?: boolean;
	ignoreInputs?: boolean;
}

function parseCombo(combo: string) {
	const parts = combo.split("+").map((p) => p.trim().toLowerCase());
	const ctrl = parts.includes("ctrl") || parts.includes("control");
	const alt = parts.includes("alt");
	const shift = parts.includes("shift");
	const meta = parts.includes("meta") || parts.includes("cmd");
	const key = parts[parts.length - 1];
	return { ctrl, alt, shift, meta, key };
}

function matchesEvent(e: KeyboardEvent, combo: string): boolean {
	const { ctrl, alt, shift, meta, key } = parseCombo(combo);
	if (ctrl !== e.ctrlKey) return false;
	if (alt !== e.altKey) return false;
	if (shift !== e.shiftKey) return false;
	if (meta !== e.metaKey) return false;
	const eventKey = e.key.length === 1 ? e.key.toLowerCase() : e.key.toLowerCase();
	const targetKey = key.toLowerCase();
	// F-keys case insensitive
	if (targetKey.startsWith("f") && eventKey.startsWith("f")) {
		return eventKey === targetKey;
	}
	return eventKey === targetKey;
}

function isTypingTarget(target: EventTarget | null): boolean {
	if (!(target instanceof HTMLElement)) return false;
	const tag = target.tagName;
	if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return true;
	if (target.isContentEditable) return true;
	return false;
}

/**
 * Hook central para accesos rapidos.
 * - Unico listener por instancia, estable via useCallback + ref.
 * - No secuestra escritura: ignora F1-F12/Ctrl+Enter fuera de inputs, pero ignora letra sola dentro de input.
 * - Respeta enabled para no disparar cuando Dialog deshabilita o condicion no se cumple.
 */
export function useHotkey(
	combo: HotkeyCombo,
	handler: () => void,
	options: UseHotkeyOptions = {},
) {
	const { enabled = true, preventDefault = true, ignoreInputs = true } = options;
	const handlerRef = useRef(handler);
	const comboRef = useRef(combo);

	useEffect(() => {
		handlerRef.current = handler;
	}, [handler]);
	useEffect(() => {
		comboRef.current = combo;
	}, [combo]);

	const cb = useCallback(
		(e: KeyboardEvent) => {
			if (!enabled) return;
			if (!matchesEvent(e, comboRef.current)) return;
			// Ignorar si usuario escribe en input y el atajo es letra sin modificador
			const { ctrl, alt, meta, key } = parseCombo(comboRef.current);
			const hasModifier = ctrl || alt || meta;
			const isFunctionKey = key.startsWith("f") || key === "escape" || key === "enter";
			if (ignoreInputs && !hasModifier && !isFunctionKey && isTypingTarget(e.target)) {
				return;
			}
			// Para F-keys y Ctrl+ combos, permitir incluso dentro de input pero prevenir default
			if (preventDefault) e.preventDefault();
			handlerRef.current();
		},
		[enabled, preventDefault, ignoreInputs],
	);

	useEffect(() => {
		if (!enabled) return;
		window.addEventListener("keydown", cb);
		return () => window.removeEventListener("keydown", cb);
	}, [cb, enabled]);
}

/**
 * Varianta multi-combo: registra varios atajos con el mismo handler o mapa.
 */
export function useHotkeys(
	combos: HotkeyCombo[],
	handler: (combo: HotkeyCombo) => void,
	options: UseHotkeyOptions = {},
) {
	const handlerRef = useRef(handler);
	const combosRef = useRef(combos);

	useEffect(() => {
		handlerRef.current = handler;
	}, [handler]);
	useEffect(() => {
		combosRef.current = combos;
	}, [combos]);

	const { enabled = true, preventDefault = true, ignoreInputs = true } = options;

	const cb = useCallback(
		(e: KeyboardEvent) => {
			if (!enabled) return;
			for (const c of combosRef.current) {
				if (!matchesEvent(e, c)) continue;
				const { ctrl, alt, meta, key } = parseCombo(c);
				const hasModifier = ctrl || alt || meta;
				const isFunctionKey = key.startsWith("f") || key === "escape" || key === "enter";
				if (ignoreInputs && !hasModifier && !isFunctionKey && isTypingTarget(e.target)) {
					continue;
				}
				if (preventDefault) e.preventDefault();
				handlerRef.current(c);
				return;
			}
		},
		[enabled, preventDefault, ignoreInputs],
	);

	useEffect(() => {
		if (!enabled) return;
		window.addEventListener("keydown", cb);
		return () => window.removeEventListener("keydown", cb);
	}, [cb, enabled]);
}
