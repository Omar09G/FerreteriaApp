import { useEffect, useRef, useState } from "react";
import { Bell } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { useT } from "@/i18n";
import { apiMarcarLeida } from "@/lib/api/notificaciones";
import {
	marcarTodoLeido,
	rutaNotificacion,
	useNotificacionesStore,
} from "@/store/notificaciones";

function fechaCorta(iso: string): string {
	try {
		return new Date(iso).toLocaleString("es-MX", {
			day: "2-digit",
			month: "2-digit",
			hour: "2-digit",
			minute: "2-digit",
		});
	} catch {
		return "";
	}
}

/** Campana con contador de no leídas + desplegable de recientes. */
export function NotificacionBell() {
	const t = useT();
	const navigate = useNavigate();
	const [abierto, setAbierto] = useState(false);
	const items = useNotificacionesStore((s) => s.items);
	const noLeidas = useNotificacionesStore((s) => s.noLeidas);
	const marcarLeidaLocal = useNotificacionesStore((s) => s.marcarLeidaLocal);
	const ref = useRef<HTMLDivElement>(null);

	useEffect(() => {
		if (!abierto) return;
		const cerrar = (e: MouseEvent) => {
			if (ref.current && !ref.current.contains(e.target as Node)) setAbierto(false);
		};
		document.addEventListener("mousedown", cerrar);
		return () => document.removeEventListener("mousedown", cerrar);
	}, [abierto ]);

	const recientes = items.slice(0, 8);

	const abrir = (bandejaId: number, refTipo: string) => {
		setAbierto(false);
		marcarLeidaLocal(bandejaId);
		void apiMarcarLeida(bandejaId).catch(() => {
			// Optimista: el contador ya bajó; el historial lo corrige.
		});
		navigate(rutaNotificacion({ refTipo }));
	};

	return (
		<div ref={ref} className="relative">
			<button
				type="button"
				onClick={() => setAbierto((v) => !v)}
				className="relative rounded p-1.5 hover:bg-warmbg hover:text-primary"
				aria-label={t("notificaciones.campana")}
				title={t("notificaciones.campana")}
				aria-expanded={abierto}
			>
				<Bell className="h-4 w-4" />
				{noLeidas > 0 && (
					<span
						className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white"
						aria-live="polite"
					>
						{noLeidas > 99 ? "99+" : noLeidas}
					</span>
				)}
			</button>
			{abierto && (
				<div className="absolute right-0 z-50 mt-1 w-80 overflow-hidden rounded-md border border-line bg-surface shadow-lg">
					<div className="flex items-center justify-between border-b border-line px-3 py-2">
						<span className="text-sm font-semibold">{t("notificaciones.titulo")}</span>
						<button
							type="button"
							onClick={() => {
								setAbierto(false);
								void marcarTodoLeido();
							}}
							className="text-xs text-primary hover:underline"
						>
							{t("notificaciones.marcarTodas")}
						</button>
					</div>
					{recientes.length === 0 ? (
						<p className="px-3 py-6 text-center text-sm text-muted">
							{t("notificaciones.sinAvisos")}
						</p>
					) : (
						<ul className="max-h-80 overflow-y-auto">
							{recientes.map((n) => (
								<li key={n.bandejaId}>
									<button
										type="button"
										onClick={() => abrir(n.bandejaId, n.refTipo)}
										className="flex w-full items-start gap-2 px-3 py-2 text-left hover:bg-warmbg"
									>
										<span
											className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${n.leidaEn ? "bg-line" : "bg-primary"}`}
											aria-hidden="true"
										/>
										<span className="min-w-0">
											<span className="block truncate text-sm font-medium">{n.titulo}</span>
											{n.detalle && (
												<span className="block truncate text-xs text-muted">{n.detalle}</span>
											)}
											<span className="block text-[11px] text-muted">{fechaCorta(n.creadaEn)}</span>
										</span>
									</button>
								</li>
							))}
						</ul>
					)}
					<button
						type="button"
						onClick={() => {
							setAbierto(false);
							navigate("/notificaciones");
						}}
						className="block w-full border-t border-line px-3 py-2 text-center text-sm text-primary hover:underline"
					>
						{t("notificaciones.verTodas")}
					</button>
				</div>
			)}
		</div>
	);
}
