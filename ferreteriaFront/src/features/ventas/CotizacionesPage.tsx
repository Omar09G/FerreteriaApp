import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search, ShoppingCart, Trash2 } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import { apiAlmacenes, apiClientes, apiProductos } from "@/lib/api/catalogo";
import {
	apiConvertirCotizacion,
	apiCotizaciones,
	apiCrearCotizacion,
} from "@/lib/api/venta";
import {
	FORMAS_PAGO,
	type Cotizacion,
	type CotizacionRequest,
	type Producto,
} from "@/lib/api/types";
import { formatoFecha, formatoFechaHora, formatoMoneda } from "@/lib/format";
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
	precioUnitario: number;
}

function CotizacionForm({
	guardando,
	onGuardar,
	onClose,
}: {
	guardando: boolean;
	onGuardar: (payload: CotizacionRequest) => void;
	onClose: () => void;
}) {
	const clientes = useQuery({
		queryKey: ["clientes-cotizacion"],
		queryFn: () => apiClientes({ page: 0, size: 50 }),
	});
	const [clienteId, setClienteId] = useState<string>("");
	const [vigenciaHasta, setVigenciaHasta] = useState("");
	const [partidas, setPartidas] = useState<Partida[]>([]);
	const [busqueda, setBusqueda] = useState("");
	const [q, setQ] = useState("");
	const [intento, setIntento] = useState(false);

	const resultados = useQuery({
		queryKey: ["productos-cotizacion", q],
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
					precioUnitario: p.precioMenudeo,
				},
			];
		});
	};

	const total = partidas.reduce(
		(acc, x) => acc + x.cantidad * x.precioUnitario,
		0,
	);
	const invalido =
		partidas.length === 0 ||
		partidas.some((x) => x.cantidad < 1 || x.precioUnitario < 0);

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({
			clienteId: clienteId ? Number(clienteId) : undefined,
			vigenciaHasta: vigenciaHasta.trim() || undefined,
			detalles: partidas.map((x) => ({
				productoId: x.productoId,
				cantidad: x.cantidad,
				precioUnitario: x.precioUnitario,
			})),
		});
	};

	return (
		<form onSubmit={enviar} className="space-y-3" noValidate>
			<div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
				<Select
					label="Cliente (opcional)"
					value={clienteId}
					onChange={(e) => setClienteId(e.target.value)}
				>
					<option value="">Público general</option>
					{clientes.data?.data.map((c) => (
						<option key={c.clienteId} value={c.clienteId}>
							{c.razonSocial}
						</option>
					))}
				</Select>
				<Input
					label="Vigencia hasta (opcional)"
					type="date"
					value={vigenciaHasta}
					onChange={(e) => setVigenciaHasta(e.target.value)}
				/>
			</div>

			<div className="flex flex-wrap items-end gap-2">
				<Input
					label="Buscar producto"
					value={busqueda}
					onChange={(e) => setBusqueda(e.target.value)}
					onKeyDown={(e) => {
						if (e.key === "Enter") setQ(busqueda.trim());
					}}
					placeholder="Artículo a cotizar"
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
			{q && resultados.isLoading && <Spinner />}
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
									{p.codigo ?? "—"} · {formatoMoneda(p.precioMenudeo)}
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
								min={1}
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
								value={x.precioUnitario}
								onChange={(e) =>
									setPartidas((prev) =>
										prev.map((y) =>
											y.productoId === x.productoId
												? { ...y, precioUnitario: Number(e.target.value) }
												: y,
										),
									)
								}
								className="w-24 rounded border border-line px-1 py-0.5 text-right text-sm"
								aria-label={`Precio de ${x.nombre}`}
							/>
							<span className="w-24 shrink-0 text-right text-sm font-semibold tabular-nums">
								{formatoMoneda(x.cantidad * x.precioUnitario)}
							</span>
						</div>
					))}
					<div className="flex items-center justify-between pt-1 text-sm font-bold text-ink">
						<span>Total estimado</span>
						<span className="tabular-nums">{formatoMoneda(total)}</span>
					</div>
				</div>
			)}

			{intento && invalido && (
				<p className="text-xs text-red-600">
					Agrega al menos una partida con cantidad y precio válidos.
				</p>
			)}
			<div className="flex justify-end gap-2">
				<Button type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button type="submit" disabled={guardando}>
					{guardando ? "Guardando…" : "Registrar cotización"}
				</Button>
			</div>
		</form>
	);
}

function ConvertirForm({
	cotizacion,
	guardando,
	onGuardar,
	onClose,
}: {
	cotizacion: Cotizacion;
	guardando: boolean;
	onGuardar: (almacenId: number, formaPagoId: number, cajaId: number) => void;
	onClose: () => void;
}) {
	const almacenes = useQuery({
		queryKey: ["almacenes"],
		queryFn: apiAlmacenes,
	});
	const cajas = useQuery({ queryKey: ["cajas"], queryFn: apiCajas });
	const [almacenId, setAlmacenId] = useState<number | "">("");
	const [cajaId, setCajaId] = useState<number | "">("");
	const [formaPagoId, setFormaPagoId] = useState<number>(1);
	const [intento, setIntento] = useState(false);

	const cajasDeAlmacen =
		cajas.data?.filter(
			(c) => c.almacenId === (almacenId === "" ? -1 : Number(almacenId)),
		) ?? [];

	const turno = useQuery({
		queryKey: ["turnoActual", cajaId],
		queryFn: () => apiTurnoActual(Number(cajaId)),
		enabled: cajaId !== "",
		retry: false,
	});
	const cajaConTurno = cajaId !== "" && turno.isSuccess;
	const cajaSinTurno = cajaId !== "" && turno.isError;

	const invalido = almacenId === "" || cajaId === "" || cajaSinTurno;

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar(Number(almacenId), formaPagoId, Number(cajaId));
	};

	return (
		<form onSubmit={enviar} className="space-y-3" noValidate>
			<div className="rounded-md bg-canvas px-3 py-2 text-sm">
				<p className="font-medium text-ink">
					{cotizacion.folio} · {cotizacion.clienteNombre ?? "Público general"}
				</p>
				<p className="text-muted">
					Total:{" "}
					<span className="font-semibold tabular-nums text-ink">
						{formatoMoneda(cotizacion.total)}
					</span>
				</p>
			</div>
			<div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
				<Select
					label="Almacén"
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
					required
					value={formaPagoId}
					onChange={(e) => setFormaPagoId(Number(e.target.value))}
				>
					{FORMAS_PAGO.map((f) => (
						<option key={f.id} value={f.id}>
							{f.nombre}
						</option>
					))}
				</Select>
			</div>
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
			{cajaId !== "" && turno.isLoading && (
				<p className="text-xs text-muted">Verificando turno abierto…</p>
			)}
			{cajaConTurno && (
				<p className="text-xs text-emerald-600">
					Turno abierto: la venta se asociará a esa caja.
				</p>
			)}
			{cajaSinTurno && (
				<p className="text-xs text-red-600">
					Esta caja no tiene un turno abierto. Ábrelo en el POS para poder
					registrar la venta.
				</p>
			)}
			<p className="text-xs text-muted">
				La cotización se registrará como venta asociada al turno abierto de la
				caja y dejará de estar vigente.
			</p>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button type="submit" disabled={guardando}>
					{guardando ? "Convirtiendo…" : "Convertir a venta"}
				</Button>
			</div>
			{intento && invalido && (
				<p className="text-xs text-red-600">
					Selecciona el almacén, una caja con turno abierto y la forma de pago.
				</p>
			)}
		</form>
	);
}

const toneCotizacion = (estado: string) =>
	estado === "VIGENTE"
		? "success"
		: estado === "CONVERTIDA"
			? "info"
			: estado === "EXPIRADA"
				? "warning"
				: "danger";

export default function CotizacionesPage() {
	useDocumentTitle("Cotizaciones");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();

	const [estado, setEstado] = useState<string>("");
	const [rango, setRango] = useState<RangoFechas | null>(null);
	const [page, setPage] = useState(0);
	const [nuevaAbierta, setNuevaAbierta] = useState(false);
	const [aConvertir, setAConvertir] = useState<Cotizacion | null>(null);

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: ["cotizaciones", estado, rango?.inicio, rango?.fin, page],
		queryFn: () =>
			apiCotizaciones({
				estado: estado || undefined,
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
		mutationFn: (body: CotizacionRequest) => apiCrearCotizacion(body),
		onSuccess: () => {
			mostrarExito("Cotización creada.");
			setNuevaAbierta(false);
			queryClient.invalidateQueries({ queryKey: ["cotizaciones"] });
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const convertir = useMutation({
		mutationFn: (v: {
			id: number;
			almacenId: number;
			formaPagoId: number;
			cajaId: number;
		}) => apiConvertirCotizacion(v.id, v.almacenId, v.formaPagoId, v.cajaId),
		onSuccess: (cot) => {
			mostrarExito(`Cotización ${cot.folio} convertida a venta.`);
			setAConvertir(null);
			queryClient.invalidateQueries({ queryKey: ["cotizaciones"] });
			queryClient.invalidateQueries({ queryKey: ["ventas"] });
			queryClient.invalidateQueries({ queryKey: ["dashboard"] });
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const columnas: Columna<Cotizacion>[] = [
		{
			key: "folio",
			header: "Folio",
			render: (v) => <span className="font-medium text-ink">{v.folio}</span>,
		},
		{
			key: "cliente",
			header: "Cliente",
			render: (v) => v.clienteNombre ?? "Público general",
		},
		{
			key: "fecha",
			header: "Fecha",
			render: (v) => (
				<span className="whitespace-nowrap">{formatoFechaHora(v.fecha)}</span>
			),
		},
		{
			key: "vig",
			header: "Vigencia",
			render: (v) => (
				<span className="tabular-nums">{formatoFecha(v.vigenciaHasta)}</span>
			),
		},
		{
			key: "sub",
			header: "Subtotal",
			align: "right",
			render: (v) => (
				<span className="tabular-nums">{formatoMoneda(v.subtotal)}</span>
			),
		},
		{
			key: "iva",
			header: "IVA",
			align: "right",
			render: (v) => (
				<span className="tabular-nums">{formatoMoneda(v.iva)}</span>
			),
		},
		{
			key: "tot",
			header: "Total",
			align: "right",
			render: (v) => (
				<span className="tabular-nums font-medium">
					{formatoMoneda(v.total)}
				</span>
			),
		},
		{
			key: "est",
			header: "Estado",
			render: (v) => <Badge tone={toneCotizacion(v.estado)}>{v.estado}</Badge>,
		},
		{
			key: "acc",
			header: "Acciones",
			align: "right",
			render: (v) => (
				<div className="flex justify-end gap-1">
					{v.estado === "VIGENTE" && (
						<button
							type="button"
							aria-label="Convertir a venta"
							title="Convertir a venta"
							className="rounded p-1.5 text-primary hover:bg-primary-50"
							onClick={() => setAConvertir(v)}
						>
							<ShoppingCart className="h-4 w-4" />
						</button>
					)}
				</div>
			),
		},
	];

	return (
		<div className="space-y-4">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">Cotizaciones</h1>
					<p className="text-sm text-muted">
						Cotizaciones a clientes y su conversión a venta.
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
					<Button onClick={() => setNuevaAbierta(true)}>
						<Plus className="h-4 w-4" /> Nueva cotización
					</Button>
				</div>
			</header>

			<Card>
				<div className="flex flex-wrap items-end gap-2">
					<Select
						label="Estado"
						value={estado}
						onChange={(e) => {
							setEstado(e.target.value);
							setPage(0);
						}}
						className="w-56"
					>
						<option value="">Todas</option>
						<option value="VIGENTE">Vigente</option>
						<option value="CONVERTIDA">Convertida</option>
						<option value="EXPIRADA">Expirada</option>
						<option value="CANCELADA">Cancelada</option>
					</Select>
					{estado !== "" && (
						<Button
							variant="ghost"
							onClick={() => {
								setEstado("");
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
				<Card titulo={`Cotizaciones (${data.meta.totalElements})`}>
					<DataTable
						columnas={columnas}
						items={data.data}
						rowKey={(v) => v.cotizacionId}
						loading={isFetching}
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}

			<Dialog
				open={nuevaAbierta}
				onClose={() => !crear.isPending && setNuevaAbierta(false)}
				title="Nueva cotización"
				width="max-w-2xl"
			>
				<CotizacionForm
					guardando={crear.isPending}
					onGuardar={(body) => crear.mutate(body)}
					onClose={() => setNuevaAbierta(false)}
				/>
			</Dialog>

			<Dialog
				open={aConvertir !== null}
				onClose={() => !convertir.isPending && setAConvertir(null)}
				title="Convertir a venta"
				width="max-w-lg"
			>
				{aConvertir && (
					<ConvertirForm
						cotizacion={aConvertir}
						guardando={convertir.isPending}
						onGuardar={(almacenId, formaPagoId, cajaId) =>
							convertir.mutate({
								id: aConvertir.cotizacionId,
								almacenId,
								formaPagoId,
								cajaId,
							})
						}
						onClose={() => setAConvertir(null)}
					/>
				)}
			</Dialog>
		</div>
	);
}
