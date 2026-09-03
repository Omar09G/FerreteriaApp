import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import CardListReportes from "./CardListReportes";
import { esApiError } from "@/lib/api/client";
import { useToast } from "@/components/ui/Toast";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { Spinner } from "@/components/ui/Spinner";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { formatoMoneda, formatoNumero } from "@/lib/format";
import { apiProductosSinMovimiento } from "@/lib/api/reportes";
import type { ProductosSinMovimiento } from "@/lib/api/types";
import { Badge } from "@/components/ui/Badge";
import { ReporteHeader } from "./ReporteHeader";

export default function ProductosSinMovimientoPage() {
	useDocumentTitle("Productos sin movimiento");
	const { error: mostrarError } = useToast();

	const { data, isLoading, error } = useQuery({
		queryKey: ["productos-sin-movimiento"],
		queryFn: () => apiProductosSinMovimiento(),
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const columnas: Columna<ProductosSinMovimiento>[] = [
		{ key: "productoId", header: "ID", render: (v) => v.productoId },
		{ key: "codigo", header: "Código", render: (v) => v.codigo },
		{ key: "producto", header: "Producto", render: (v) => v.producto },
		{ key: "categoria", header: "Categoría", render: (v) => v.categoria },
		{
			key: "stock",
			header: "Stock",
			align: "right",
			render: (v) => <Badge tone="default">{formatoNumero(v.stock)}</Badge>,
		},
		{
			key: "costoActual",
			header: "Costo actual",
			align: "right",
			render: (v) => formatoMoneda(v.costoActual),
		},
		{
			key: "dineroDetenidoEnEstante",
			header: "Dinero en estante",
			align: "right",
			render: (v) => (
				<Badge tone="info">{formatoMoneda(v.dineroDetenidoEnEstante)}</Badge>
			),
		},
		{
			key: "ultimaVenta",
			header: "Última venta",
			render: (v) => v.ultimaVenta,
		},
		{
			key: "diasSinVender",
			header: "Días sin vender",
			align: "right",
			render: (v) => (
				<Badge tone="warning">{formatoNumero(v.diasSinVender)}</Badge>
			),
		},
		{
			key: "prioridadPromocion",
			header: "Prioridad promoción",
			render: (v) => <Badge tone="danger">{v.prioridadPromocion}</Badge>,
		},
	];

	return (
		<div className="space-y-4">
			<ReporteHeader
				titulo="Productos sin Movimiento"
				subtitulo="Cuadratura de cortes por día en el periodo."
			/>
			{isLoading && <Spinner />}
			{data && data.length > 0 && (
				<Card titulo="Productos sin movimiento">
					<DataTable
						columnas={columnas}
						items={data}
						caption="Productos sin movimiento"
						rowKey={(v) => v.productoId}
					/>
				</Card>
			)}
			{data && data.length === 0 && (
				<EmptyState
					title="No hay productos sin movimiento"
					descripcion="No hay productos sin movimiento en el periodo seleccionado."
				/>
			)}
			<CardListReportes />
		</div>
	);
}
