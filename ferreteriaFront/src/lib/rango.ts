import { aLocalDate, hoyLocal } from "./format";

export interface RangoFechas {
	inicio: string;
	fin: string;
}

/** Construye un rango válido; por defecto, el día actual (inicio = fin = hoy). */
export function rangoFechas(
	fechaInicio?: string | null,
	fechaFin?: string | null,
): RangoFechas {
	const inicio = fechaInicio || hoyLocal();
	const fin = fechaFin || inicio;
	if (fin < inicio) {
		throw new Error("La fecha de inicio no puede ser posterior a la de fin.");
	}
	return { inicio, fin };
}

export interface PresetRango {
	id: string;
}

export function construirPreset(id: string): RangoFechas {
	const hoy = new Date();
	switch (id) {
		case "ayer": {
			const ayer = new Date(hoy);
			ayer.setDate(ayer.getDate() - 1);
			const dia = aLocalDate(ayer);
			return { inicio: dia, fin: dia };
		}
		case "ultimos-7": {
			const ini = new Date(hoy);
			ini.setDate(ini.getDate() - 6);
			return { inicio: aLocalDate(ini), fin: hoyLocal() };
		}
		case "mes": {
			return {
				inicio: `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, "0")}-01`,
				fin: hoyLocal(),
			};
		}
		default:
			return { inicio: hoyLocal(), fin: hoyLocal() };
	}
}

export const PRESETS_RANGO: PresetRango[] = [
	{ id: "hoy" },
	{ id: "ayer" },
	{ id: "ultimos-7" },
	{ id: "mes" },
];
