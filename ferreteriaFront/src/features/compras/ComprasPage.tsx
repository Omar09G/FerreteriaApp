import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search, Trash2 } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import { apiAlmacenes, apiProductos, apiProveedores } from "@/lib/api/catalogo";
import { apiCompras, apiCrearCompra } from "@/lib/api/compras";
import {
	FORMAS_PAGO,
	type Compra,
	type CompraRequest,
	type Producto,
} from "@/lib/api/types";
import { formatoFechaHora, formatoMoneda } from "@/lib/format";
import type { RangoFechas } from "@/lib/rango";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { RangoFiltro } from "@/components/ui/RangoFiltro";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

interface Partida {
	productoId: number;
	nombre: string;
	cantidad: number;
	costoUnitario: number;
}

function CompraForm({
	guardando,
	onGuardar,
	onClose,
}: {
	guardando: boolean;
	onGuardar: (payload: CompraRequest) => void;
	onClose: () => void;
}) {
	const proveedores = useQuery({
		queryKey: ["proveedores"],
		queryFn: () => apiProveedores(),
	});
	const almacenes = useQuery({
		queryKey: ["almacenes"],
		queryFn: apiAlmacenes,
	});
	const cajas = useQuery({ queryKey: ["cajas"], queryFn: apiCajas });
	const [proveedorId, setProveedorId] = useState<number | "">("");
	const [almacenId, setAlmacenId] = useState<number | "">("");
	const [cajaId, setCajaId] = useState<number | "">("");
	const [formaPagoId, setFormaPagoId] = useState<number>(4);
	const [factura, setFactura] = useState("");
	const [notas, setNotas] = useState("");
	const [partidas, setPartidas] = useState<Partida[]>([]);
	const [busqueda, setBusqueda] = useState("");
	const [q, setQ] = useState("");
	const [intento, setIntento] = useState(false);

	const resultados = useQuery({
		queryKey: ["productos-compra", q],
		queryFn: () => apiProductos({ q: q || undefined, page: 0, size: 20 }),
		enabled: q.length > 0,
	});

	const agregar = (p: Producto) => {
		setPartidas((prev) => {
			const exist = prev.find((x) => x.productoId === p.productoId);
			if (exist) return prev;
			return [
				...prev,
				{
					productoId: p.productoId,
					nombre: p.nombre,
					cantidad: 1,
					costoUnitario: p.costoActual || 0,
				},
			];
		});
		setBusqueda("");
		setQ("");
	};

	const total = partidas.reduce(
		(acc, x) => acc + x.cantidad * x.costoUnitario,
		0,
	);

	const esCredito =
		FORMAS_PAGO.find((f) => f.id === formaPagoId)?.clave === "CREDITO";
	const cajasDeAlmacen =
		cajas.data?.filter(
			(c) => c.almacenId === (almacenId === "" ? -1 : Number(almacenId)),
		) ?? [];
	const turno = useQuery({
		queryKey: ["turnoActual", cajaId],
		queryFn: () => apiTurnoActual(Number(cajaId)),
		enabled: cajaId !== "" && !esCredito,
		retry: false,
	});
	const cajaConTurno = cajaId !== "" && turno.isSuccess;
	const cajaSinTurno = cajaId !== "" && turno.isError;

	const invalido =
		proveedorId === "" ||
		almacenId === "" ||
		partidas.length === 0 ||
		partidas.some((x) => x.cantidad <= 0 || x.costoUnitario < 0) ||
		(!esCredito && (cajaId === "" || cajaSinTurno));

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({
			proveedorId: Number(proveedorId),
			almacenId: Number(almacenId),
			formaPagoId,
			cajaId: esCredito ? 0 : Number(cajaId),
			facturaProveedor: factura.trim() || undefined,
			notas: notas.trim() || undefined,
			detalles: partidas.map((x) => ({
				productoId: x.productoId,
				cantidad: x.cantidad,
				costoUnitario: x.costoUnitario,
			})),
		});
	};

	return (
		<form onSubmit={enviar} className="space-y-3" noValidate>
			<div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
				<Select
					label="Proveedor"
					required
					value={proveedorId}
					onChange={(e) =>
						setProveedorId(e.target.value ? Number(e.target.value) : "")
					}
				>
					<option value="">Selecciona…</option>
					{proveedores.data?.map((p) => (
						<option key={p.proveedorId} value={p.proveedorId}>
							{p.razonSocial}
						</option>
					))}
				</Select>
				<Select
					label="Almacén de entrada"
					required
					value={almacenId}
					onChange={(e) => {
						setAlmacenId(e.target.value ? Number(e.target.value) : "");
						setCajaId("");
					}}
				>
					<option value="">Selecciona…</option>
					{almacenes.data?.map((a) => (
						<option key={a.almacenId} value={a.almacenId}>
							{a.nombre}
						</option>
					))}
				</Select>
				<Select
					label="Forma de pago"
					value={formaPagoId}
					onChange={(e) => setFormaPagoId(Number(e.target.value))}
				>
					{FORMAS_PAGO.filter((f) => f.id !== 6).map((f) => (
						<option key={f.id} value={f.id}>
							{f.nombre}
						</option>
					))}
				</Select>
				{!esCredito && (
					<Select
						label="Caja"
						required
						value={cajaId}
						onChange={(e) =>
							setCajaId(e.target.value ? Number(e.target.value) : "")
						}
					>
						<option value="">Selecciona…</option>
						{cajasDeAlmacen.map((c) => (
							<option key={c.cajaId} value={c.cajaId}>
								{c.nombre}
							</option>
						))}
					</Select>
				)}
				<Input
					label="Factura del proveedor"
					value={factura}
					onChange={(e) => setFactura(e.target.value)}
					placeholder="Ej. F-10235"
				/>
			</div>

			{!esCredito && cajaId !== "" && turno.isLoading && (
				<p className="text-xs text-muted">Verificando turno abierto…</p>
			)}
			{!esCredito && cajaConTurno && (
				<p className="text-xs text-emerald-600">
					Turno abierto: el pago se registrará como salida en esa caja.
				</p>
			)}
			{!esCredito && cajaSinTurno && (
				<p className="text-xs text-red-600">
					Esta caja no tiene un turno abierto. Ábrelo en el POS para poder
					registrar la compra.
				</p>
			)}

			<div className="flex flex-wrap items-end gap-2">
				<Input
					label="Buscar producto"
					value={busqueda}
					onChange={(e) => setBusqueda(e.target.value)}
					onKeyDown={(e) => e.key === "Enter" && setQ(busqueda.trim())}
					placeholder="Artículo a comprar"
					className="w-72"
				/>
				<Button
					variant="secondary"
					disabled={resultados.isFetching || busqueda.trim() === q}
					onClick={() => setQ(busqueda.trim())}
				>
					<Search className="h-4 w-4" /> Buscar
				</Button>
			</div>
			{q && resultados.data && (
				<div className="max-h-40 overflow-auto rounded-md border border-line">
					{resultados.data.data.length === 0 && (
						<p className="p-3 text-sm text-muted">Sin coincidencias.</p>
					)}
					{resultados.data.data.map((p) => (
						<button
							key={p.productoId}
							type="button"
							onClick={() => agregar(p)}
							className="flex w-full items-center justify-between gap-3 border-b border-line px-3 py-1.5 text-left hover:bg-primary-50"
						>
							<span className="min-w-0">
								<span className="block truncate text-sm font-medium text-ink">
									{p.nombre}
								</span>
								<span className="text-xs text-muted">
									{p.codigo ?? "—"} · coste {formatoMoneda(p.costoActual)}
								</span>
							</span>
							<Plus className="h-4 w-4 shrink-0 text-primary" />
						</button>
					))}
				</div>
			)}

			{partidas.length > 0 && (
				<div className="space-y-1.5">
					{partidas.map((x) => (
						<div
							key={x.productoId}
							className="flex items-center gap-2 rounded-md border border-line px-2 py-1.5"
						>
							<button
								type="button"
								aria-label="Quitar"
								className="text-muted hover:text-red-600"
								onClick={() =>
									setPartidas((prev) =>
										prev.filter((y) => y.productoId !== x.productoId),
									)
								}
							>
								<Trash2 className="h-4 w-4" />
							</button>
							<span className="min-w-0 flex-1 truncate text-sm font-medium text-ink">
								{x.nombre}
							</span>
							<input
								type="number"
								inputMode="decimal"
								min={0}
								step="1"
								value={x.cantidad}
								onChange={(e) =>
									setPartidas((prev) =>
										prev.map((y) =>
											y.productoId === x.productoId
												? { ...y, cantidad: Number(e.target.value) }
												: y,
										),
									)
								}
								className="w-16 rounded border border-line px-1 py-0.5 text-right text-sm"
								aria-label={`Cantidad de ${x.nombre}`}
							/>
							<input
								type="number"
								inputMode="decimal"
								min={0}
								step="0.01"
								value={x.costoUnitario}
								onChange={(e) =>
									setPartidas((prev) =>
										prev.map((y) =>
											y.productoId === x.productoId
												? { ...y, costoUnitario: Number(e.target.value) }
												: y,
										),
									)
								}
								className="w-24 rounded border border-line px-1 py-0.5 text-right text-sm"
								aria-label={`Costo de ${x.nombre}`}
							/>
							<span className="w-24 shrink-0 text-right text-sm font-semibold tabular-nums">
								{formatoMoneda(x.cantidad * x.costoUnitario)}
							</span>
						</div>
					))}
					<div className="flex items-center justify-between pt-1 text-sm font-bold text-ink">
						<span>Total estimado</span>
						<span className="tabular-nums">{formatoMoneda(total)}</span>
					</div>
				</div>
			)}

			<Input
				label="Notas (opcional)"
				value={notas}
				onChange={(e) => setNotas(e.target.value)}
			/>
			{intento && invalido && (
				<p className="text-xs text-red-600">
					Completa proveedor, almacén y al menos una partida.
				</p>
			)}
			<div className="flex justify-end gap-2">
				<Button type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button type="submit" disabled={guardando}>
					{guardando ? "Guardando…" : "Registrar compra"}
				</Button>
			</div>
		</form>
	);
}

export default function ComprasPage() {
	useDocumentTitle("Compras");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();

	const [proveedorId, setProveedorId] = useState<number | "">("");
	const [rango, setRango] = useState<RangoFechas | null>(null);
	const [page, setPage] = useState(0);
	const [dialogoAbierto, setDialogoAbierto] = useState(false);

	const proveedores = useQuery({
		queryKey: ["proveedores-list"],
		queryFn: () => apiProveedores(),
	});

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: ["compras", proveedorId, rango?.inicio, rango?.fin, page],
		queryFn: () =>
			apiCompras({
				proveedorId: proveedorId || undefined,
				desde: rango?.inicio,
				hasta: rango?.fin,
				page,
				size: 15,
			}),
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const crear = useMutation({
		mutationFn: (body: CompraRequest) => apiCrearCompra(body),
		onSuccess: () => {
			mostrarExito("Compra registrada: entradas de inventario creadas.");
			setDialogoAbierto(false);
			queryClient.invalidateQueries({ queryKey: ["compras"] });
			queryClient.invalidateQueries({ queryKey: ["stock"] });
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const columnas: Columna<Compra>[] = [
		{
			key: "fol",
			header: "Folio",
			render: (v) => <span className="font-medium text-ink">{v.folio}</span>,
		},
		{ key: "p", header: "Proveedor", render: (v) => v.proveedor },
		{ key: "f", header: "Factura", render: (v) => v.facturaProveedor ?? "—" },
		{ key: "alm", header: "Almacén", render: (v) => v.almacen },
		{ key: "tipo", header: "Pago", render: (v) => v.formaPago },
		{
			key: "fecha",
			header: "Fecha",
			render: (v) => (
				<span className="whitespace-nowrap">{formatoFechaHora(v.fecha)}</span>
			),
		},
		{
			key: "estado",
			header: "Estado",
			render: (v) => (
				<Badge tone={v.estado === "RECIBIDA" ? "success" : "info"}>
					{v.estado}
				</Badge>
			),
		},
		{
			key: "total",
			header: "Total",
			align: "right",
			render: (v) => (
				<span className="tabular-nums font-medium">
					{formatoMoneda(v.total)}
				</span>
			),
		},
	];

	return (
		<div className="space-y-4">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">Compras</h1>
					<p className="text-sm text-muted">
						Órdenes de compra a proveedores y su entrada a inventario.
					</p>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					<RangoFiltro
						valor={rango}
						onChange={(siguiente) => {
							setRango(siguiente);
							setPage(0);
						}}
					/>
					<Button onClick={() => setDialogoAbierto(true)}>
						<Plus className="h-4 w-4" /> Nueva compra
					</Button>
				</div>
			</header>

			<Card>
				<div className="flex flex-wrap items-end gap-2">
					<Select
						label="Proveedor"
						value={proveedorId}
						onChange={(e) => {
							setProveedorId(e.target.value ? Number(e.target.value) : "");
							setPage(0);
						}}
						className="w-72"
					>
						<option value="">Todos</option>
						{proveedores.data?.map((p) => (
							<option key={p.proveedorId} value={p.proveedorId}>
								{p.razonSocial}
							</option>
						))}
					</Select>
					{proveedorId !== "" && (
						<Button
							variant="ghost"
							onClick={() => {
								setProveedorId("");
								setPage(0);
							}}
						>
							Limpiar
						</Button>
					)}
				</div>
			</Card>

			{(isLoading || (isFetching && !data)) && <Spinner />}
			{data && (
				<Card titulo={`Compras (${data.meta.totalElements})`}>
					<DataTable
						columnas={columnas}
						items={data.data}
						rowKey={(v) => v.compraId}
						loading={isFetching}
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}

			<Dialog
				open={dialogoAbierto}
				onClose={() => !crear.isPending && setDialogoAbierto(false)}
				title="Nueva compra"
				width="max-w-2xl"
			>
				<CompraForm
					guardando={crear.isPending}
					onGuardar={(body) => crear.mutate(body)}
					onClose={() => setDialogoAbierto(false)}
				/>
			</Dialog>
		</div>
	);
}
