import { useEffect, useState } from "react";
import { Medal } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useReporte } from "@/hooks/useReporte";
import { rangoFechas, type RangoFechas } from "@/lib/rango";
import { formatoFecha, formatoMoneda, formatoNumero } from "@/lib/format";
import { apiTopProductos } from "@/lib/api/reportes";
import { esApiError } from "@/lib/api/client";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { ExportarExcel } from "@/components/ui/ExportarExcel";
import { EmptyState } from "@/components/ui/EmptyState";
import { ChartSkeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";
import { ReporteHeader } from "./ReporteHeader";
import type { TopProducto } from "@/lib/api/types";
import CardListReportes from "./CardListReportes";

export default function TopProductosPage() {
	useDocumentTitle("Top productos");
	const { error: mostrarError } = useToast();
	const [rango, setRango] = useState<RangoFechas>(() => rangoFechas());

	const { data, isLoading, error } = useReporte("top-productos", apiTopProductos, rango);

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const posicion = (ranking: number) =>
		ranking === 1 ? (
			<Badge tone="warning">
				<Medal className="h-3 w-3" /> 1°
			</Badge>
		) : (
			<Badge tone="default">{ranking}°</Badge>
		);

	const columnas: Columna<TopProducto>[] = [
		{
			key: "p",
			header: "Posición",
			exportar: (v) => v.rankingMes,
			render: (v) => posicion(v.rankingMes),
		},
		{
			key: "c",
			header: "Código",
			exportar: (v) => v.codigo ?? "—",
			render: (v) => v.codigo ?? "—",
		},
		{
			key: "n",
			header: "Producto",
			exportar: (v) => v.producto,
			render: (v) => <span className="font-medium text-ink">{v.producto}</span>,
		},
		{
			key: "cat",
			header: "Categoría",
			exportar: (v) => v.categoria,
			render: (v) => v.categoria,
		},
		{
			key: "u",
			header: "Unidades",
			align: "right",
			exportar: (v) => formatoNumero(v.unidadesVendidas),
			render: (v) => formatoNumero(v.unidadesVendidas),
		},
		{
			key: "i",
			header: "Ingreso",
			align: "right",
			exportar: (v) => formatoMoneda(v.ingresoTotal),
			render: (v) => formatoMoneda(v.ingresoTotal),
		},
		{
			key: "c2",
			header: "Costo",
			align: "right",
			exportar: (v) => formatoMoneda(v.costoTotal),
			render: (v) => formatoMoneda(v.costoTotal),
		},
		{
			key: "ut",
			header: "Utilidad",
			align: "right",
			exportar: (v) => formatoMoneda(v.utilidad),
			render: (v) => formatoMoneda(v.utilidad),
		},
	];

	return (
		<div className="space-y-4">
			<ReporteHeader
				titulo="Productos más vendidos"
				subtitulo={`Periodo: ${formatoFecha(rango.inicio)} – ${formatoFecha(rango.fin)}.`}
				rango={rango}
				onChange={setRango}
			/>
			{isLoading && <ChartSkeleton />}
			{data && data.length > 0 && (
				<Card
					titulo="Ranking del periodo"
					actions={
						<ExportarExcel columnas={columnas} items={data} archivo="top-productos" />
					}
				>
					<DataTable
						columnas={columnas}
						items={data}
						rowKey={(v) => v.productoId}
						caption="Top productos"
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
