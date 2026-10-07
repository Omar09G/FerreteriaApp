import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { useT } from "@/i18n";
import {
	apiMarcarLeida,
	apiNotificaciones,
} from "@/lib/api/notificaciones";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import {
	marcarTodoLeido,
	rutaNotificacion,
	useNotificacionesStore,
} from "@/store/notificaciones";

/** Historial de la bandeja: paginado, clic para ir al origen y marcar leída. */
export default function NotificacionesPage() {
	const t = useT();
	const navigate = useNavigate();
	const { error: mostrarError } = useToast();
	const queryClient = useQueryClient();
	const [page, setPage] = useState(0);
	const marcarLeidaLocal = useNotificacionesStore((s) => s.marcarLeidaLocal);

	const { data, isFetching, isError } = useQuery({
		queryKey: ["notificaciones", page],
		queryFn: () => apiNotificaciones({ page, size: 20 }),
	});

	if (isError) {
		mostrarError(t("notificaciones.cargando"));
	}

	const abrir = (bandejaId: number, refTipo: string) => {
		marcarLeidaLocal(bandejaId);
		void apiMarcarLeida(bandejaId)
			.then(() => {
				void queryClient.invalidateQueries({ queryKey: ["notificaciones"] });
			})
			.catch(() => {
				// Optimista: el historial se refresca al volver.
			});
		navigate(rutaNotificacion({ refTipo }));
	};

	return (
		<Card
			titulo={t("notificaciones.titulo")}
			actions={
				<Button
					variant="secondary"
					size="sm"
					onClick={() => {
						void marcarTodoLeido().then(() => {
							void queryClient.invalidateQueries({ queryKey: ["notificaciones"] });
						});
					}}
				>
					{t("notificaciones.marcarTodas")}
				</Button>
			}
		>
			{isFetching && <Spinner />}
			{!isFetching && (!data || data.data.length === 0) && (
				<EmptyState
					title={t("notificaciones.titulo")}
					descripcion={t("notificaciones.sinAvisos")}
				/>
			)}
			{data && data.data.length > 0 && (
				<>
					<ul className="divide-y divide-line">
						{data.data.map((n) => (
							<li key={n.bandejaId}>
								<button
									type="button"
									onClick={() => abrir(n.bandejaId, n.refTipo)}
									className="flex w-full items-start gap-3 px-2 py-3 text-left hover:bg-warmbg"
								>
									<span
										className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${n.leidaEn ? "bg-line" : "bg-primary"}`}
										aria-hidden="true"
									/>
									<span className="min-w-0 flex-1">
										<span className="block text-sm font-medium">
											{n.titulo}
											<span className="ml-2 rounded bg-warmbg px-1.5 py-0.5 text-[11px] font-semibold text-muted">
												{t(`notificaciones.tipos.${n.tipo}`)}
											</span>
										</span>
										{n.detalle && (
											<span className="block truncate text-sm text-muted">{n.detalle}</span>
										)}
										<span className="block text-xs text-muted">
											{new Date(n.creadaEn).toLocaleString("es-MX")}
										</span>
									</span>
								</button>
							</li>
						))}
					</ul>
					<Pagination
						meta={{
							page: data.meta.page,
							size: data.meta.size,
							totalElements: data.meta.totalElements,
							totalPages: data.meta.totalPages,
						}}
						onPage={setPage}
					/>
				</>
			)}
		</Card>
	);
}
