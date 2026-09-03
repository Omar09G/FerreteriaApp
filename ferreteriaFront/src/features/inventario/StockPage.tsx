import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	AlertTriangle,
	ArrowDownCircle,
	ArrowUpCircle,
	Sliders,
	Warehouse,
} from "lucide-react";
import { useSearchParams } from "react-router-dom";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { apiAlmacenes } from "@/lib/api/catalogo";
import { apiStock } from "@/lib/api/reportes";
import { apiCrearMovimiento } from "@/lib/api/inventario";
import {
	MOTIVOS_MOVIMIENTO,
	type Inventario,
	type MovimientoInventarioRequest,
} from "@/lib/api/types";
import { formatoNumero } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

interface AjusteTarget {
	productoId: number;
	productoNombre: string;
	almacenId: number;
	almacenNombre: string;
	stockActual: number;
}

function AjusteDialog({
	target,
	onClose,
}: {
	target: AjusteTarget | null;
	onClose: () => void;
}) {
	const { success, error } = useToast();
	const queryClient = useQueryClient();
	const [tipo, setTipo] = useState<"ENTRADA" | "SALIDA">("ENTRADA");
	const [cantidad, setCantidad] = useState("");
	const [motivoId, setMotivoId] = useState<number>(6);
	const [costo, setCosto] = useState("");
	const [nota, setNota] = useState("");
	const [intento, setIntento] = useState(false);

	const motivosFiltrados = useMemo(
		() => MOTIVOS_MOVIMIENTO.filter((m) => m.tipoDefault === tipo),
		[tipo],
	);

	// Si el motivoId seleccionado no aplica al tipo actual, usa el primero de la lista filtrada.
	const motivoEfectivo = motivosFiltrados.some((m) => m.id === motivoId)
		? motivoId
		: (motivosFiltrados[0]?.id ?? 6);

	const cantidadNum = Number(cantidad);
	const nuevoStock = target
		? tipo === "ENTRADA"
			? target.stockActual + cantidadNum
			: target.stockActual - cantidadNum
		: 0;
	const invalido =
		!target ||
		cantidad === "" ||
		Number.isNaN(cantidadNum) ||
		cantidadNum <= 0 ||
		Number.isNaN(nuevoStock) ||
		nuevoStock < 0;

	const ajuste = useMutation({
		mutationFn: () => {
			if (!target) throw new Error("Sin fila seleccionada");
			const body: MovimientoInventarioRequest = {
				productoId: target.productoId,
				almacenId: target.almacenId,
				tipo,
				cantidad: cantidadNum,
				motivoId,
				nota: nota.trim() || undefined,
			};
			const costoNum = Number(costo);
			if (costo.trim() !== "" && !Number.isNaN(costoNum) && costoNum >= 0) {
				body.costoUnitario = costoNum;
			}
			return apiCrearMovimiento(body);
		},
		onSuccess: (m) => {
			success(
				`${tipo === "ENTRADA" ? "Entrada" : "Salida"} registrada: ${formatoNumero(m.cantidad)} uds.`,
			);
			queryClient.invalidateQueries({ queryKey: ["stock"] });
			queryClient.invalidateQueries({ queryKey: ["movimientos"] });
			queryClient.invalidateQueries({ queryKey: ["dashboard"] });
			onClose();
		},
		onError: (err) =>
			error(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const guardar = () => {
		setIntento(true);
		if (invalido || !target) return;
		ajuste.mutate();
	};

	return (
		<Dialog
			open={target !== null}
			onClose={() => !ajuste.isPending && onClose()}
			title="Ajustar existencia"
			width="max-w-md"
		>
			{target && (
				<div className="space-y-3">
					<div className="rounded-md bg-canvas p-3 text-sm">
						<p className="font-medium text-ink">{target.productoNombre}</p>
						<p className="text-xs text-muted">
							<Warehouse className="mr-1 inline-block h-3.5 w-3.5" />
							{target.almacenNombre}
						</p>
						<p className="mt-1 text-xs text-muted">
							Existencia actual:{" "}
							<span className="font-mono font-semibold text-ink">
								{formatoNumero(target.stockActual)}
							</span>{" "}
							uds.
						</p>
					</div>

					<div className="grid grid-cols-2 gap-3">
						<div>
							<span className="mb-1 block text-sm font-medium text-ink">
								Tipo de movimiento
							</span>
							<div className="grid grid-cols-2 gap-1 rounded-md border border-line bg-surface p-1">
								<button
									type="button"
									disabled={ajuste.isPending}
									onClick={() => setTipo("ENTRADA")}
									className={`flex items-center justify-center gap-1 rounded px-2 py-1.5 text-xs font-medium transition-colors ${
										tipo === "ENTRADA"
											? "bg-green-600 text-white"
											: "text-muted hover:bg-warmbg"
									}`}
								>
									<ArrowUpCircle className="h-4 w-4" /> Entrada
								</button>
								<button
									type="button"
									disabled={ajuste.isPending}
									onClick={() => setTipo("SALIDA")}
									className={`flex items-center justify-center gap-1 rounded px-2 py-1.5 text-xs font-medium transition-colors ${
										tipo === "SALIDA"
											? "bg-red-600 text-white"
											: "text-muted hover:bg-warmbg"
									}`}
								>
									<ArrowDownCircle className="h-4 w-4" /> Salida
								</button>
							</div>
						</div>
						<Input
							label="Cantidad"
							type="number"
							inputMode="decimal"
							step="0.001"
							min="0.001"
							required
							value={cantidad}
							onChange={(e) => setCantidad(e.target.value)}
							placeholder="Ej. 5"
						/>
					</div>

					<Select
						label="Motivo"
						required
						value={motivoEfectivo}
						onChange={(e) => setMotivoId(Number(e.target.value))}
						hint="Filtrado según el tipo de movimiento."
					>
						{motivosFiltrados.map((m) => (
							<option key={m.id} value={m.id}>
								{m.nombre}
							</option>
						))}
					</Select>

					<div className="grid grid-cols-2 gap-3">
						<Input
							label="Costo unitario (opcional)"
							type="number"
							inputMode="decimal"
							step="0.01"
							min="0"
							value={costo}
							onChange={(e) => setCosto(e.target.value)}
							placeholder="Solo entradas"
						/>
						<Input
							label="Nota (opcional)"
							value={nota}
							onChange={(e) => setNota(e.target.value)}
							placeholder="Ej. Conteo físico del 15/sep"
						/>
					</div>

					<div className="rounded-md border border-line p-3 text-sm">
						<div className="flex items-center justify-between">
							<span className="text-muted">Existencia resultante</span>
							<span
								className={`font-mono font-semibold ${
									nuevoStock < 0 ? "text-red-600" : "text-ink"
								}`}
							>
								{Number.isNaN(nuevoStock) ? "—" : formatoNumero(nuevoStock)}{" "}
								uds.
							</span>
						</div>
						{intento && invalido && (
							<p className="mt-1 text-xs text-red-600">
								{cantidadNum <= 0
									? "La cantidad debe ser mayor a 0."
									: nuevoStock < 0
										? "La salida dejaría el stock en negativo."
										: "Revisa los datos del movimiento."}
							</p>
						)}
					</div>

					<div className="flex justify-end gap-2 pt-1">
						<Button
							variant="ghost"
							disabled={ajuste.isPending}
							onClick={onClose}
						>
							Cancelar
						</Button>
						<Button disabled={invalido || ajuste.isPending} onClick={guardar}>
							{tipo === "ENTRADA" ? (
								<ArrowUpCircle className="h-4 w-4" />
							) : (
								<ArrowDownCircle className="h-4 w-4" />
							)}
							{ajuste.isPending
								? "Registrando…"
								: `Registrar ${tipo === "ENTRADA" ? "entrada" : "salida"}`}
						</Button>
					</div>
				</div>
			)}
		</Dialog>
	);
}

export default function StockPage() {
	useDocumentTitle("Existencias");
	const { error: mostrarError } = useToast();
	const [searchParams] = useSearchParams();
	const [almacenId, setAlmacenId] = useState<number | "">(() => {
		const v = Number(searchParams.get("almacen"));
		return Number.isFinite(v) && v > 0 ? v : "";
	});
	const [soloBajoStock, setSoloBajoStock] = useState<boolean>(
		() => searchParams.get("soloBajoStock") === "1",
	);
	const [page, setPage] = useState(0);
	const [ajusteTarget, setAjusteTarget] = useState<AjusteTarget | null>(null);

	const almacenes = useQuery({
		queryKey: ["almacenes"],
		queryFn: apiAlmacenes,
	});

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: ["stock", almacenId, soloBajoStock, page],
		queryFn: () =>
			apiStock({
				almacenId: almacenId || undefined,
				soloBajoStock: soloBajoStock || undefined,
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

	const cambiarAlmacen = (v: string) => {
		setAlmacenId(v ? Number(v) : "");
		setPage(0);
	};
	const cambiarBajoStock = (b: boolean) => {
		setSoloBajoStock(b);
		setPage(0);
	};

	const columnas: Columna<Inventario>[] = [
		{
			key: "c",
			header: "Código",
			render: (v) => (
				<span className="font-mono text-xs text-muted">
					{v.productoCodigo ?? "—"}
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
		{
			key: "a",
			header: "Almacén",
			render: (v) => (
				<span className="inline-flex items-center gap-1 text-sm">
					<Warehouse className="h-3.5 w-3.5 text-muted" />
					{v.almacenNombre}
				</span>
			),
		},
		{
			key: "stock",
			header: "Existencia",
			align: "right",
			render: (v) => (
				<span
					className={
						v.stock <= v.stockMinimo
							? "font-medium text-amber-700"
							: "tabular-nums"
					}
				>
					{formatoNumero(v.stock)}
				</span>
			),
		},
		{
			key: "min",
			header: "Stock mín.",
			align: "right",
			render: (v) => (
				<span className="tabular-nums text-muted">
					{formatoNumero(v.stockMinimo)}
				</span>
			),
		},
		{
			key: "res",
			header: "Reservado",
			align: "right",
			render: (v) =>
				v.reservado ? (
					<span className="tabular-nums">{formatoNumero(v.reservado)}</span>
				) : (
					"—"
				),
		},
		{
			key: "estado",
			header: "Estado",
			render: (v) =>
				v.stock <= 0 ? (
					<Badge tone="danger">Agotado</Badge>
				) : v.stock <= v.stockMinimo ? (
					<Badge tone="warning">Bajo stock</Badge>
				) : (
					<Badge tone="success">Disponible</Badge>
				),
		},
		{
			key: "acc",
			header: "Acciones",
			align: "right",
			render: (v) => (
				<Button
					variant="secondary"
					size="sm"
					onClick={() =>
						setAjusteTarget({
							productoId: v.productoId,
							productoNombre: v.productoNombre,
							almacenId: v.almacenId,
							almacenNombre: v.almacenNombre,
							stockActual: v.stock,
						})
					}
					aria-label={`Ajustar stock de ${v.productoNombre}`}
					title="Registrar entrada o salida manual"
				>
					<Sliders className="h-3.5 w-3.5" /> Ajustar
				</Button>
			),
		},
	];

	return (
		<div className="space-y-4">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">Existencias</h1>
					<p className="text-sm text-muted">
						Nivel de stock por almacén y productos en riesgo de desabasto. Usa{" "}
						<span className="font-medium">Ajustar</span> para registrar entradas
						o salidas manuales.
					</p>
				</div>
				<Button
					variant="secondary"
					onClick={() => {
						const primera = data?.data[0];
						if (!primera) {
							mostrarError("No hay filas en la página actual para ajustar.");
							return;
						}
						setAjusteTarget({
							productoId: primera.productoId,
							productoNombre: primera.productoNombre,
							almacenId: primera.almacenId,
							almacenNombre: primera.almacenNombre,
							stockActual: primera.stock,
						});
					}}
					disabled={!data?.data?.length}
					title="Ajustar la primera fila visible"
				>
					<Sliders className="h-4 w-4" /> Ajuste rápido
				</Button>
			</header>

			<Card>
				<div className="flex flex-wrap items-end gap-2">
					<Select
						label="Almacén"
						value={almacenId}
						onChange={(e) => cambiarAlmacen(e.target.value)}
						className="w-60"
					>
						<option value="">Todos</option>
						{almacenes.data?.map((a) => (
							<option key={a.almacenId} value={a.almacenId}>
								{a.nombre}
							</option>
						))}
					</Select>
					<Button
						variant={soloBajoStock ? "primary" : "secondary"}
						onClick={() => cambiarBajoStock(!soloBajoStock)}
						className={soloBajoStock ? "" : "text-amber-700"}
					>
						<AlertTriangle className="h-4 w-4" />{" "}
						{soloBajoStock ? "Mostrando solo bajo stock" : "Solo bajo stock"}
					</Button>
					{(almacenId !== "" || soloBajoStock) && (
						<Button
							variant="ghost"
							onClick={() => {
								setAlmacenId("");
								setSoloBajoStock(false);
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
				<Card titulo={`Filas (${data.meta.totalElements})`}>
					<DataTable
						columnas={columnas}
						items={data.data}
						rowKey={(v) => `${v.productoId}-${v.almacenId}`}
						loading={isFetching}
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}

			<AjusteDialog
				target={ajusteTarget}
				onClose={() => setAjusteTarget(null)}
			/>
		</div>
	);
}
