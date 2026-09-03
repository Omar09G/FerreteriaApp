import { useQuery } from "@tanstack/react-query";

import type { RangoFechas } from "@/lib/rango";

/** Queries de reportes acotadas por rango; key incluye inicio/fin para re-fetch al cambiar. */
export function useReporte<T>(
	clave: string,
	fn: (inicio: string, fin: string) => Promise<T>,
	rango: RangoFechas,
) {
	return useQuery({
		queryKey: [clave, rango.inicio, rango.fin],
		queryFn: () => fn(rango.inicio, rango.fin),
	});
}
