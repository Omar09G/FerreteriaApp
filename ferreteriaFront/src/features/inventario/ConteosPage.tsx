import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search, Trash2 } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { apiAlmacenes, apiProductos } from "@/lib/api/catalogo";
import { apiConteos, apiCrearConteo } from "@/lib/api/inventario";
import type {
	ConteoFisico,
	ConteoFisicoRequest,
	Producto,
} from "@/lib/api/types";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

const TONO_ESTADO: Record<string, "warning" | "success" | "danger"> = {
	EN_PROCESO: "warning",
	APLICADO: "success",
	CANCELADO: "danger",
};

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
				<Input
					label="Buscar producto"
					value={busqueda}
					onChange={(e) => setBusqueda(e.target.value)}
					onKeyDown={(e) => e.key === "Enter" && setQ(busqueda.trim())}
					placeholder="Artículo a contar"
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
									{p.codigo ?? "—"} · {p.unidadMedidaClave}
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
				<Button type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button type="submit" disabled={guardando}>
					{guardando ? "Guardando…" : "Registrar conteo"}
				</Button>
			</div>
		</form>
	);
}

export default function ConteosPage() {
	useDocumentTitle("Conteos físicos");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();

	const [page, setPage] = useState(0);
	const [dialogoAbierto, setDialogoAbierto] = useState(false);

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: ["conteos", page],
		queryFn: () => apiConteos({ page, size: 15 }),
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

	const columnas: Columna<ConteoFisico>[] = [
		{
			key: "alm",
			header: "Almacén",
			render: (v) => (
				<span className="font-medium text-ink">{v.almacenNombre}</span>
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
			render: (v) => v.observaciones ?? "—",
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
				<Button onClick={() => setDialogoAbierto(true)}>
					<Plus className="h-4 w-4" /> Nuevo conteo
				</Button>
			</header>

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
		</div>
	);
}
