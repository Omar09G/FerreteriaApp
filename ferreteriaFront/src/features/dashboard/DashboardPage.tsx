import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
	AlertTriangle,
	Banknote,
	Boxes,
	ClipboardList,
	ReceiptText,
	ShoppingBag,
	Store,
	TrendingUp,
} from "lucide-react";
import { Link } from "react-router-dom";

import type { ReactNode } from "react";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { rangoFechas, type RangoFechas } from "@/lib/rango";
import { formatoMoneda } from "@/lib/format";
import { apiDashboard } from "@/lib/api/reportes";
import { esApiError } from "@/lib/api/client";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import { DateRangePicker } from "@/components/ui/DateRangePicker";
import { useToast } from "@/components/ui/Toast";

function KPI({
	icono,
	label,
	valor,
	alerta,
}: {
	icono: ReactNode;
	label: string;
	valor: string;
	alerta?: "warn" | "danger";
}) {
	return (
		<Card className="p-5">
			<div className="flex items-center gap-3">
				<span
					className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${
						alerta === "danger"
							? "bg-red-100 text-red-600"
							: alerta === "warn"
								? "bg-amber-100 text-amber-600"
								: "bg-orange-100 text-primary"
					}`}
					aria-hidden
				>
					{icono}
				</span>
				<div className="min-w-0">
					<p className="text-xs uppercase tracking-wide text-muted">{label}</p>
					<p className="truncate text-lg font-bold tabular-nums text-ink">
						{valor}
					</p>
				</div>
			</div>
		</Card>
	);
}

export default function DashboardPage() {
	useDocumentTitle("Inicio");
	const { error: mostrarError } = useToast();
	const [rango, setRango] = useState<RangoFechas>(() => rangoFechas());

	const { data, isLoading, error } = useQuery({
		queryKey: ["dashboard", rango.inicio, rango.fin],
		queryFn: () => apiDashboard(rango.inicio, rango.fin),
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	return (
		<div className="space-y-6">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">Panel de control</h1>
					<p className="text-sm text-muted">
						Resumen del periodo seleccionado.
					</p>
				</div>
				<DateRangePicker valor={rango} onChange={setRango} />
			</header>

			{isLoading && <Spinner label="Cargando indicadores…" />}

			{data && (
				<>
					<div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
						<KPI
							icono={<TrendingUp className="h-5 w-5" />}
							label="Ventas en rango"
							valor={formatoMoneda(data.ventasEnRango)}
						/>
						<KPI
							icono={<ReceiptText className="h-5 w-5" />}
							label="Tickets"
							valor={String(data.ticketsEnRango)}
						/>
						<KPI
							icono={<ShoppingBag className="h-5 w-5" />}
							label="Ticket promedio"
							valor={formatoMoneda(data.ticketPromedioEnRango)}
						/>
						<KPI
							icono={<Banknote className="h-5 w-5" />}
							label="Saldo por cobrar"
							valor={formatoMoneda(data.saldoPorCobrar)}
							alerta={data.saldoPorCobrar > 0 ? "warn" : undefined}
						/>
						<KPI
							icono={<AlertTriangle className="h-5 w-5" />}
							label="Cobranza vencida"
							valor={formatoMoneda(data.cobranzaVencida)}
							alerta={data.cobranzaVencida > 0 ? "danger" : undefined}
						/>
						<KPI
							icono={<Boxes className="h-5 w-5" />}
							label="Valor de inventario"
							valor={formatoMoneda(data.valorInventario)}
						/>
						{data.productosAgotados > 0 ? (
							<Link to="/inventario/stock?soloBajoStock=1" className="block">
								<KPI
									icono={<ClipboardList className="h-5 w-5" />}
									label="Productos agotados"
									valor={String(data.productosAgotados)}
									alerta="danger"
								/>
							</Link>
						) : (
							<KPI
								icono={<ClipboardList className="h-5 w-5" />}
								label="Productos agotados"
								valor="0"
							/>
						)}
						<KPI
							icono={<TrendingUp className="h-5 w-5" />}
							label="Promociones activas"
							valor={String(data.promocionesActivas)}
						/>
						<KPI
							icono={<Store className="h-5 w-5" />}
							label="Cajas abiertas"
							valor={String(data.cajasAbiertas)}
						/>
					</div>

					<Card
						titulo={`Periodo seleccionado`}
						actions={
							<Link
								to="/reportes"
								className="text-sm text-primary hover:underline"
							>
								Ver reportes →
							</Link>
						}
					>
						<p className="text-sm text-muted">
							Consulta los reportes detallados (ventas por hora, días de mayor
							venta, productos y clientes) desde la sección Reportes, con el
							mismo rango de fechas.
						</p>
					</Card>
				</>
			)}
		</div>
	);
}
