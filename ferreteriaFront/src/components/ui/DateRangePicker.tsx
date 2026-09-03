import { useMemo } from "react";
import { CalendarRange } from "lucide-react";

import { useT } from "@/i18n";
import { aLocalDate, hoyLocal } from "@/lib/format";
import {
	construirPreset,
	PRESETS_RANGO,
	rangoFechas,
	type RangoFechas,
} from "@/lib/rango";

interface DateRangePickerProps {
	valor: RangoFechas;
	onChange: (rango: RangoFechas) => void;
}

const ayer = new Date();
ayer.setDate(ayer.getDate() - 1);

/** Selector de rango: presets rápidos + fechas libres. */
export function DateRangePicker({ valor, onChange }: DateRangePickerProps) {
	const t = useT();
	const presetPropio = useMemo(() => {
		const hoy = hoyLocal();
		const siete = new Date();
		siete.setDate(siete.getDate() - 6);
		const ini7 = aLocalDate(siete);
		const iniMes = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, "0")}-01`;
		if (valor.inicio === valor.fin && valor.fin === hoy) return "hoy";
		if (valor.inicio === aLocalDate(ayer) && valor.fin === aLocalDate(ayer))
			return "ayer";
		if (valor.inicio === ini7 && valor.fin === hoy) return "ultimos-7";
		if (valor.inicio === iniMes && valor.fin === hoy) return "mes";
		return "personalizado";
	}, [valor]);

	const aplicarPreset = (id: string) => {
		if (id === "personalizado") return;
		onChange(construirPreset(id));
	};

	return (
		<div className="flex flex-wrap items-end gap-2" data-testid="rango-fechas">
			<CalendarRange className="mb-2 h-5 w-5 text-primary" aria-hidden />
			{PRESETS_RANGO.map((p) => (
				<button
					key={p.id}
					type="button"
					onClick={() => aplicarPreset(p.id)}
					className={`h-9 rounded-md border px-2.5 text-xs font-medium transition-colors ${
						presetPropio === p.id
							? "border-primary bg-primary text-white"
							: "border-line bg-surface text-muted hover:bg-warmbg"
					}`}
				>
					{t(`rango.${p.id}`)}
				</button>
			))}
			<span className="mx-1 self-center text-xs text-muted" aria-hidden>
				─
			</span>
			<label className="flex items-center gap-1 text-xs text-muted">
				{t("rango.del")}
				<input
					type="date"
					value={valor.inicio}
					max={valor.fin}
					onChange={(e) => {
						if (e.target.value)
							onChange(rangoFechas(e.target.value, valor.fin));
					}}
					className="h-9 rounded-md border border-line bg-surface text-sm text-ink"
				/>
			</label>
			<label className="flex items-center gap-1 text-xs text-muted">
				{t("rango.al")}
				<input
					type="date"
					value={valor.fin}
					min={valor.inicio}
					max={hoyLocal()}
					onChange={(e) => {
						if (e.target.value)
							onChange(rangoFechas(valor.inicio, e.target.value));
					}}
					className="h-9 rounded-md border border-line bg-surface text-sm text-ink"
				/>
			</label>
		</div>
	);
}
