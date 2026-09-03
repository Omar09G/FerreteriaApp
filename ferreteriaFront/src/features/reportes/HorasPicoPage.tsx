import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
	Bar,
	BarChart,
	CartesianGrid,
	ResponsiveContainer,
	Tooltip,
	XAxis,
	YAxis,
} from "recharts";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { rangoFechas, type RangoFechas } from "@/lib/rango";
import { formatoMoneda, formatoNumero } from "@/lib/format";
import { apiHorasPico } from "@/lib/api/reportes";
import { esApiError } from "@/lib/api/client";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { ReporteHeader } from "./ReporteHeader";
import CardListReportes from "./CardListReportes";

export default function HorasPicoPage() {
	useDocumentTitle("Horas pico");
	const { error: mostrarError } = useToast();
	const [rango, setRango] = useState<RangoFechas>(() => rangoFechas());

	const { data, isLoading, error } = useQuery({
		queryKey: ["horas-pico", rango.inicio, rango.fin],
		queryFn: () => apiHorasPico(rango.inicio, rango.fin),
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const columnas: Columna<NonNullable<typeof data>[number]>[] = [
		{
			key: "h",
			header: "Hora",
			render: (v) => `${String(v.hora).padStart(2, "0")}:00`,
		},
		{
			key: "n",
			header: "Ventas",
			align: "right",
			render: (v) => formatoNumero(v.numVentas),
		},
		{
			key: "t",
			header: "Total acumulado",
			align: "right",
			render: (v) => formatoMoneda(v.totalAcumulado),
		},
		{
			key: "p",
			header: "Ticket promedio",
			align: "right",
			render: (v) => formatoMoneda(v.ticketPromedio),
		},
	];

	return (
		<div className="space-y-4">
			<ReporteHeader
				titulo="Ventas por hora"
				subtitulo="Horas de mayor actividad en el periodo."
				rango={rango}
				onChange={setRango}
			/>
			{isLoading && <Spinner />}
			{data && data.length > 0 && (
				<>
					<Card titulo="Número de ventas por hora">
						<ResponsiveContainer width="100%" height={280}>
							<BarChart data={data} margin={{ left: 8, right: 8 }}>
								<CartesianGrid strokeDasharray="3 3" stroke="#e7e5e4" />
								<XAxis
									dataKey="hora"
									tickFormatter={(h: number) => `${h}:00`}
									stroke="#57534e"
									fontSize={12}
								/>
								<YAxis stroke="#57534e" fontSize={12} />
								<Tooltip
									formatter={(value, name) => [
										formatoNumero(Number(value)),
										String(name),
									]}
									labelFormatter={(label) => `${String(label)}:00 hrs`}
								/>
								<Bar dataKey="numVentas" fill="#ea580c" radius={[3, 3, 0, 0]} />
							</BarChart>
						</ResponsiveContainer>
					</Card>
					<Card titulo="Detalle">
						<DataTable
							columnas={columnas}
							items={data}
							rowKey={(v) => v.hora}
							caption="Ventas por hora"
						/>
					</Card>
				</>
			)}
			{data && data.length === 0 && !isLoading && (
				<EmptyState
					title="Sin ventas en el periodo"
					descripcion="Cambia el rango de fechas para ver más información."
				/>
			)}
			<CardListReportes />
		</div>
	);
}
