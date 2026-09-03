import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { rangoFechas, type RangoFechas } from "@/lib/rango";
import { formatoFecha, formatoMoneda, formatoNumero } from "@/lib/format";
import { apiMejoresVendedores } from "@/lib/api/reportes";
import { esApiError } from "@/lib/api/client";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { ReporteHeader } from "./ReporteHeader";
import type { MejorVendedor } from "@/lib/api/types";
import CardListReportes from "./CardListReportes";

export default function MejoresVendedoresPage() {
	useDocumentTitle("Mejores vendedores");
	const { error: mostrarError } = useToast();
	const [rango, setRango] = useState<RangoFechas>(() => rangoFechas());

	const { data, isLoading, error } = useQuery({
		queryKey: ["mejores-vendedores", rango.inicio, rango.fin],
		queryFn: () => apiMejoresVendedores(rango.inicio, rango.fin),
	});

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
			render: (v) => <span className="font-medium">{v.rankingMes}°</span>,
		},
		{ key: "n", header: "Vendedor", render: (v) => v.vendedor },
		{
			key: "c",
			header: "Ventas",
			align: "right",
			render: (v) => formatoNumero(v.numVentas),
		},
		{
			key: "t",
			header: "Total vendido",
			align: "right",
			render: (v) => formatoMoneda(v.totalVendido),
		},
		{
			key: "p",
			header: "Ticket promedio",
			align: "right",
			render: (v) => formatoMoneda(v.ticketPromedio),
		},
		{
			key: "u",
			header: "Utilidad",
			align: "right",
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
			{isLoading && <Spinner />}
			{data && data.length > 0 && (
				<Card titulo="Ranking del periodo">
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
