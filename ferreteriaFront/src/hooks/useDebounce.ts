import { useEffect, useState } from "react";

/**
 * Debounce: retrasa la actualización del valor hasta que pasen `delay` ms
 * sin cambios. Útil para búsquedas que disparan queries por cada tecla.
 */
export function useDebounce<T>(value: T, delay = 250): T {
	const [debounced, setDebounced] = useState(value);
	useEffect(() => {
		const id = window.setTimeout(() => setDebounced(value), delay);
		return () => window.clearTimeout(id);
	}, [value, delay]);
	return debounced;
}
