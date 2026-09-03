import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { rangoFechas, type RangoFechas } from "@/lib/rango";
import { formatoFechaHora, formatoMoneda, formatoNumero } from "@/lib/format";
import { apiMovimientos } from "@/lib/api/reportes";
import { esApiError } from "@/lib/api/client";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Input } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { DateRangePicker } from "@/components/ui/DateRangePicker";
import { useToast } from "@/components/ui/Toast";
import type { MovimientoInventario } from "@/lib/api/types";

export default function MovimientosPage() {
	useDocumentTitle("Movimientos de inventario");
	const { error: mostrarError } = useToast();
	const [rango, setRango] = useState<RangoFechas>(() => rangoFechas());
	const [productoId, setProductoId] = useState("");
	const [almacenId, setAlmacenId] = useState("");
	const [filtro, setFiltro] = useState<{
		productoId?: number;
		almacenId?: number;
	}>({});
	const [page, setPage] = useState(0);

	const aplicarRango = (siguiente: RangoFechas) => {
		setRango(siguiente);
		setPage(0);
	};

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: [
			"movimientos",
			rango.inicio,
			rango.fin,
			filtro.productoId,
			filtro.almacenId,
			page,
		],
		queryFn: () =>
			apiMovimientos({
				inicio: rango.inicio,
				fin: rango.fin,
				productoId: filtro.productoId,
				almacenId: filtro.almacenId,
				page,
				size: 20,
			}),
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const aplicarFiltros = () => {
		setFiltro({
			productoId: productoId ? Number(productoId) : undefined,
			almacenId: almacenId ? Number(almacenId) : undefined,
		});
		setPage(0);
	};

	const quitarFiltros = () => {
		setProductoId("");
		setAlmacenId("");
		setFiltro({});
		setPage(0);
	};

	const columnas: Columna<MovimientoInventario>[] = [
		{
			key: "f",
			header: "Fecha",
			render: (v) => (
				<span className="whitespace-nowrap">
					{formatoFechaHora(v.creadoEn)}
				</span>
			),
		},
		{
			key: "p",
			header: "Producto",
			render: (v) => (
				<span className="font-medium text-ink">{v.productoNombre}</span>
			),
		},
		{ key: "a", header: "Almacén", render: (v) => v.almacenNombre },
		{
			key: "t",
			header: "Tipo",
			render: (v) =>
				v.tipo === "ENTRADA" ? (
					<Badge tone="success">Entrada</Badge>
				) : (
					<Badge tone="danger">Salida</Badge>
				),
		},
		{
			key: "c",
			header: "Cantidad",
			align: "right",
			render: (v) => formatoNumero(v.cantidad),
		},
		{
			key: "cu",
			header: "Costo unit.",
			align: "right",
			render: (v) => formatoMoneda(v.costoUnitario),
		},
		{ key: "m", header: "Motivo", render: (v) => v.motivoNombre ?? "—" },
		{
			key: "r",
			header: "Referencia",
			render: (v) => (v.refTabla ? `${v.refTabla}#${v.refId}` : "—"),
		},
	];

	return (
		<div className="space-y-4">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">
						Movimientos de inventario
					</h1>
					<p className="text-sm text-muted">
						Bitácora de entradas y salidas. Por defecto se muestran las de hoy.
					</p>
				</div>
				<DateRangePicker valor={rango} onChange={aplicarRango} />
			</header>

			<Card>
				<div className="flex flex-wrap items-end gap-2">
					<Input
						label="Producto (id)"
						value={productoId}
						onChange={(e) => setProductoId(e.target.value)}
						placeholder="Ej. 12"
						className="w-40"
						inputMode="numeric"
					/>
					<Input
						label="Almacén (id)"
						value={almacenId}
						onChange={(e) => setAlmacenId(e.target.value)}
						placeholder="Ej. 1"
						className="w-40"
						inputMode="numeric"
					/>
					<Button onClick={aplicarFiltros} disabled={isFetching}>
						<Search className="h-4 w-4" /> Filtrar
					</Button>
					{(filtro.productoId || filtro.almacenId) && (
						<Badge tone="info">
							Filtros activos
							<button
								type="button"
								onClick={quitarFiltros}
								className="ml-1 rounded-full px-1 hover:bg-blue-100"
								aria-label="Quitar filtros"
							>
								✕
							</button>
						</Badge>
					)}
				</div>
			</Card>

			{(isLoading || (isFetching && !data)) && <Spinner />}
			{data && (
				<Card titulo={`Movimientos (${data.meta.totalElements})`}>
					<DataTable
						columnas={columnas}
						items={data.data}
						rowKey={(v) => v.movimientoId}
						loading={isFetching}
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}
		</div>
	);
}
