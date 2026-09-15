import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, Plus, Search, Trash2 } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { apiAlmacenes, apiProductos } from "@/lib/api/catalogo";
import { apiConteos, apiCrearConteo } from "@/lib/api/inventario";
import type {
	ConteoFisico,
	ConteoFisicoRequest,
	Producto,
} from "@/lib/api/types";
import { formatoFechaHora, formatoNumero } from "@/lib/format";
import type { RangoFechas } from "@/lib/rango";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { CodigosBarras } from "@/components/ui/CodigosBarras";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { RangoFiltro } from "@/components/ui/RangoFiltro";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

const TONO_ESTADO: Record<string, "warning" | "success" | "danger"> = {
	EN_PROCESO: "warning",
	APLICADO: "success",
	CANCELADO: "danger",
};

const ESTADOS_CONTEO = ["EN_PROCESO", "APLICADO", "CANCELADO"];

interface Partida {
	productoId: number;
	nombre: string;
	cantidadFisica: number;
}

function ConteoForm({
	guardando,
	onGuardar,
	onClose,
}: {
	guardando: boolean;
	onGuardar: (body: ConteoFisicoRequest) => void;
	onClose: () => void;
}) {
	const almacenes = useQuery({
		queryKey: ["almacenes-conteo"],
		queryFn: () => apiAlmacenes(),
	});
	const [almacenId, setAlmacenId] = useState<number | "">("");
	const [observaciones, setObservaciones] = useState("");
	const [partidas, setPartidas] = useState<Partida[]>([]);
	const [busqueda, setBusqueda] = useState("");
	const [q, setQ] = useState("");
	const [intento, setIntento] = useState(false);

	const resultados = useQuery({
		queryKey: ["productos-conteo", q],
		queryFn: () => apiProductos({ q: q || undefined, page: 0, size: 20 }),
		enabled: q.length > 0,
	});

	const agregar = (p: Producto) => {
		setPartidas((prev) => {
			const exist = prev.find((x) => x.productoId === p.productoId);
			if (exist) return prev;
			return [
				...prev,
				{ productoId: p.productoId, nombre: p.nombre, cantidadFisica: 0 },
			];
		});
	};

	const invalido =
		almacenId === "" ||
		partidas.length === 0 ||
		partidas.some(
			(x) => !Number.isFinite(x.cantidadFisica) || x.cantidadFisica < 0,
		);

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({
			almacenId: Number(almacenId),
			observaciones: observaciones.trim() || undefined,
			detalles: partidas.map((x) => ({
				productoId: x.productoId,
				cantidadFisica: x.cantidadFisica,
			})),
		});
	};

	return (
		<form onSubmit={enviar} className="space-y-3" noValidate>
			<Select
				label="Almacén"
				required
				value={almacenId}
				onChange={(e) =>
					setAlmacenId(e.target.value ? Number(e.target.value) : "")
				}
			>
				<option value="">Selecciona…</option>
				{almacenes.data?.map((a) => (
					<option key={a.almacenId} value={a.almacenId}>
						{a.nombre}
					</option>
				))}
			</Select>

			<div className="flex flex-wrap items-end gap-2">
				<Input hotkey="F3"
					label="Buscar producto"
					value={busqueda}
					onChange={(e) => setBusqueda(e.target.value)}
					onKeyDown={(e) => e.key === "Enter" && setQ(busqueda.trim())}
					placeholder="Artículo a contar"
					className="w-72"
				/>
				<Button hotkey="F3"
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
									{p.codigo ?? "—"} · {p.unidadMedidaClave}<CodigosBarras codigos={p.codigosBarras} max={1} />
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
								step="0.001"
								value={x.cantidadFisica}
								onChange={(e) =>
									setPartidas((prev) =>
										prev.map((y) =>
											y.productoId === x.productoId
												? { ...y, cantidadFisica: Number(e.target.value) }
												: y,
										),
									)
								}
								className="w-20 rounded border border-line px-1 py-0.5 text-right text-sm"
								aria-label={`Cantidad física de ${x.nombre}`}
							/>
						</div>
					))}
					<p className="text-xs text-muted">
						Cantidad física contada; el sistema calcula la diferencia contra el
						stock.
					</p>
				</div>
			)}

			<Input
				label="Observaciones (opcional)"
				value={observaciones}
				onChange={(e) => setObservaciones(e.target.value)}
			/>
			{intento && invalido && (
				<p className="text-xs text-red-600">
					Selecciona almacén y agrega al menos una partida con cantidad válida.
				</p>
			)}
			<div className="flex justify-end gap-2">
				<Button hotkey="Esc" type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button hotkey="Ctrl+Enter" type="submit" disabled={guardando}>
					{guardando ? "Guardando…" : "Registrar conteo"}
				</Button>
			</div>
		</form>
	);
}

function tonoDiferencia(diferencia: number | null | undefined) {
	if (diferencia === null || diferencia === undefined) return "default";
	if (diferencia === 0) return "default";
	return diferencia > 0 ? "success" : "danger";
}

export default function ConteosPage() {
	useDocumentTitle("Conteos físicos");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();

	const [page, setPage] = useState(0);
	const [dialogoAbierto, setDialogoAbierto] = useState(false);
	const [vistaDetalle, setVistaDetalle] = useState<ConteoFisico | null>(null);
	const [rango, setRango] = useState<RangoFechas | null>(null);
	const [almacenId, setAlmacenId] = useState("");
	const [estado, setEstado] = useState("");

	const almacenes = useQuery({
		queryKey: ["almacenes-conteo-filtro"],
		queryFn: () => apiAlmacenes(),
	});

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: [
			"conteos",
			page,
			rango?.inicio,
			rango?.fin,
			almacenId,
			estado,
		],
		queryFn: () =>
			apiConteos({
				page,
				size: 15,
				fechaInicio: rango?.inicio,
				fechaFin: rango?.fin,
				almacenId: almacenId ? Number(almacenId) : undefined,
				estado: estado || undefined,
			}),
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const crear = useMutation({
		mutationFn: (body: ConteoFisicoRequest) => apiCrearConteo(body),
		onSuccess: () => {
			mostrarExito("Conteo físico registrado.");
			setDialogoAbierto(false);
			queryClient.invalidateQueries({ queryKey: ["conteos"] });
			queryClient.invalidateQueries({ queryKey: ["stock"] });
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const cambiarRango = (siguiente: RangoFechas | null) => {
		setRango(siguiente);
		setPage(0);
	};

	const columnas: Columna<ConteoFisico>[] = [
		{
			key: "fecha",
			header: "Fecha",
			render: (v) => (
				<span className="whitespace-nowrap">
					{formatoFechaHora(v.fecha)}
				</span>
			),
		},
		{
			key: "alm",
			header: "Almacén",
			render: (v) => (
				<span className="font-medium text-ink">{v.almacenNombre ?? "—"}</span>
			),
		},
		{
			key: "usr",
			header: "Contó",
			render: (v) => v.usuarioNombre ?? `#${v.usuarioId}`,
		},
		{
			key: "part",
			header: "Partidas",
			align: "right",
			render: (v) => formatoNumero(v.totalPartidas),
		},
		{
			key: "dif",
			header: "Diferencia",
			align: "right",
			render: (v) => (
				<Badge tone={tonoDiferencia(v.diferenciaTotal)}>
					{formatoNumero(v.diferenciaTotal)}
				</Badge>
			),
		},
		{
			key: "estado",
			header: "Estado",
			render: (v) => (
				<Badge tone={TONO_ESTADO[v.estado] ?? "default"}>{v.estado}</Badge>
			),
		},
		{
			key: "obs",
			header: "Observaciones",
			render: (v) => (
				<span className="block max-w-56 truncate" title={v.observaciones ?? ""}>
					{v.observaciones ?? "—"}
				</span>
			),
		},
		{
			key: "detalle",
			header: "Detalle",
			render: (v) => (
				<Button
					variant="ghost"
					size="sm"
					title={`Ver conteo #${v.conteoId}`}
					aria-label={`Ver detalle del conteo ${v.conteoId}`}
					onClick={() => setVistaDetalle(v)}
				>
					<Eye className="h-4 w-4" />
				</Button>
			),
		},
	];

	return (
		<div className="space-y-4">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">Conteos físicos</h1>
					<p className="text-sm text-muted">
						Inventarios físicos realizados para conciliar contra el stock.
					</p>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					<RangoFiltro valor={rango} onChange={cambiarRango} />
					<Button hotkey="F4" onClick={() => setDialogoAbierto(true)}>
						<Plus className="h-4 w-4" /> Nuevo conteo
					</Button>
				</div>
			</header>

			<Card>
				<div className="flex flex-wrap items-end gap-2">
					<Select
						label="Almacén"
						value={almacenId}
						onChange={(e) => {
							setAlmacenId(e.target.value);
							setPage(0);
						}}
						className="w-56"
					>
						<option value="">Todos</option>
						{almacenes.data?.map((a) => (
							<option key={a.almacenId} value={a.almacenId}>
								{a.nombre}
							</option>
						))}
					</Select>
					<Select
						label="Estado"
						value={estado}
						onChange={(e) => {
							setEstado(e.target.value);
							setPage(0);
						}}
						className="w-48"
					>
						<option value="">Todos</option>
						{ESTADOS_CONTEO.map((e) => (
							<option key={e} value={e}>
								{e}
							</option>
						))}
					</Select>
				</div>
			</Card>

			{(isLoading || (isFetching && !data)) && <Spinner />}
			{data && (
				<Card titulo={`Conteos físicos (${data.meta.totalElements})`}>
					<DataTable
						columnas={columnas}
						items={data.data}
						rowKey={(v) => v.conteoId}
						loading={isFetching}
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}

			<Dialog
				open={dialogoAbierto}
				onClose={() => !crear.isPending && setDialogoAbierto(false)}
				title="Nuevo conteo"
				width="max-w-2xl"
			>
				<ConteoForm
					guardando={crear.isPending}
					onGuardar={(body) => crear.mutate(body)}
					onClose={() => setDialogoAbierto(false)}
				/>
			</Dialog>

			<Dialog
				open={vistaDetalle !== null}
				onClose={() => setVistaDetalle(null)}
				title={
					vistaDetalle
						? `Conteo #${vistaDetalle.conteoId} · ${vistaDetalle.almacenNombre ?? ""}`
						: ""
				}
				width="max-w-2xl"
			>
				{vistaDetalle && (
					<div className="space-y-3">
						<dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm sm:grid-cols-4">
							<div>
								<dt className="text-xs text-muted">Fecha</dt>
								<dd className="font-medium text-ink">
									{formatoFechaHora(vistaDetalle.fecha)}
								</dd>
							</div>
							<div>
								<dt className="text-xs text-muted">Contó</dt>
								<dd className="font-medium text-ink">
									{vistaDetalle.usuarioNombre ?? `#${vistaDetalle.usuarioId}`}
								</dd>
							</div>
							<div>
								<dt className="text-xs text-muted">Partidas</dt>
								<dd className="font-medium text-ink">
									{formatoNumero(vistaDetalle.totalPartidas)}
								</dd>
							</div>
							<div>
								<dt className="text-xs text-muted">Diferencia</dt>
								<dd>
									<Badge tone={tonoDiferencia(vistaDetalle.diferenciaTotal)}>
										{formatoNumero(vistaDetalle.diferenciaTotal)}
									</Badge>
								</dd>
							</div>
						</dl>
						{vistaDetalle.observaciones && (
							<p className="text-sm text-muted">
								{vistaDetalle.observaciones}
							</p>
						)}
						<div className="overflow-x-auto rounded-md border border-line">
							<table className="w-full min-w-full border-collapse text-sm">
								<thead>
									<tr className="border-b border-line bg-warmbg text-left text-xs uppercase tracking-wide text-muted">
										<th scope="col" className="px-3 py-2 font-medium">Producto</th>
										<th scope="col" className="px-3 py-2 text-right font-medium">Sistema</th>
										<th scope="col" className="px-3 py-2 text-right font-medium">Física</th>
										<th scope="col" className="px-3 py-2 text-right font-medium">Diferencia</th>
									</tr>
								</thead>
								<tbody className="divide-y divide-line">
									{vistaDetalle.detalles.map((d) => (
										<tr key={d.productoId} className="hover:bg-orange-50/40">
											<td className="px-3 py-2">
												<span className="block font-medium text-ink">
													{d.productoNombre ?? `#${d.productoId}`}
												</span>
												<span className="text-xs text-muted">
													{d.productoCodigo ?? "—"}
												</span>
											</td>
											<td className="px-3 py-2 text-right tabular-nums">
												{formatoNumero(d.cantidadSistema)}
											</td>
											<td className="px-3 py-2 text-right tabular-nums">
												{formatoNumero(d.cantidadFisica)}
											</td>
											<td className="px-3 py-2 text-right tabular-nums">
												<Badge tone={tonoDiferencia(d.diferencia)}>
													{formatoNumero(d.diferencia)}
												</Badge>
											</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					</div>
				)}
			</Dialog>
		</div>
	);
}
