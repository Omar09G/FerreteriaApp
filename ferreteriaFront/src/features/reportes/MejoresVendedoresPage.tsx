import { useEffect, useState } from "react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useReporte } from "@/hooks/useReporte";
import { rangoFechas, type RangoFechas } from "@/lib/rango";
import { formatoFecha, formatoMoneda, formatoNumero } from "@/lib/format";
import { apiMejoresVendedores } from "@/lib/api/reportes";
import { esApiError } from "@/lib/api/client";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { ExportarExcel } from "@/components/ui/ExportarExcel";
import { EmptyState } from "@/components/ui/EmptyState";
import { ChartSkeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";
import { ReporteHeader } from "./ReporteHeader";
import type { MejorVendedor } from "@/lib/api/types";
import CardListReportes from "./CardListReportes";

export default function MejoresVendedoresPage() {
	useDocumentTitle("Mejores vendedores");
	const { error: mostrarError } = useToast();
	const [rango, setRango] = useState<RangoFechas>(() => rangoFechas());

	const { data, isLoading, error } = useReporte("mejores-vendedores", apiMejoresVendedores, rango);

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const columnas: Columna<MejorVendedor>[] = [
		{
			key: "r",
			header: "Ranking",
			exportar: (v) => v.rankingMes,
			render: (v) => <span className="font-medium">{v.rankingMes}°</span>,
		},
		{
			key: "n",
			header: "Vendedor",
			exportar: (v) => v.vendedor,
			render: (v) => v.vendedor,
		},
		{
			key: "c",
			header: "Ventas",
			align: "right",
			exportar: (v) => formatoNumero(v.numVentas),
			render: (v) => formatoNumero(v.numVentas),
		},
		{
			key: "t",
			header: "Total vendido",
			align: "right",
			exportar: (v) => formatoMoneda(v.totalVendido),
			render: (v) => formatoMoneda(v.totalVendido),
		},
		{
			key: "p",
			header: "Ticket promedio",
			align: "right",
			exportar: (v) => formatoMoneda(v.ticketPromedio),
			render: (v) => formatoMoneda(v.ticketPromedio),
		},
		{
			key: "u",
			header: "Utilidad",
			align: "right",
			exportar: (v) => formatoMoneda(v.utilidadGenerada),
			render: (v) => formatoMoneda(v.utilidadGenerada),
		},
	];

	return (
		<div className="space-y-4">
			<ReporteHeader
				titulo="Mejores vendedores"
				subtitulo={`Periodo: ${formatoFecha(rango.inicio)} – ${formatoFecha(rango.fin)}.`}
				rango={rango}
				onChange={setRango}
			/>
			{isLoading && <ChartSkeleton />}
			{data && data.length > 0 && (
				<Card
					titulo="Ranking del periodo"
					actions={
						<ExportarExcel columnas={columnas} items={data} archivo="mejores-vendedores" />
					}
				>
					<DataTable
						columnas={columnas}
						items={data}
						rowKey={(v) => v.usuarioId}
						caption="Mejores vendedores"
					/>
				</Card>
			)}
			{data && data.length === 0 && !isLoading && (
				<EmptyState
					title="Sin ventas en el periodo"
					descripcion="Cambia el rango de fechas."
				/>
			)}
			<CardListReportes />
		</div>
	);
}
