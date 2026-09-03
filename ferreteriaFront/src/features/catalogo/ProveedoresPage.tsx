import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Building2, Pencil, Search, Trash2 } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import {
	apiActualizarProveedor,
	apiCrearProveedor,
	apiEliminarProveedor,
	apiProveedoresPaginado,
} from "@/lib/api/catalogo";
import type { Proveedor, ProveedorRequest } from "@/lib/api/types";
import { formatoMoneda } from "@/lib/format";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

function numero(value: string): number | null {
	const limpio = value.trim();
	if (!limpio) return null;
	const n = Number(limpio);
	return Number.isFinite(n) ? n : null;
}

function ProveedorForm({
	proveedor,
	guardando,
	onGuardar,
	onClose,
}: {
	proveedor: Proveedor | null;
	guardando: boolean;
	onGuardar: (payload: ProveedorRequest) => void;
	onClose: () => void;
}) {
	const [razonSocial, setRazonSocial] = useState(proveedor?.razonSocial ?? "");
	const [rfc, setRfc] = useState(proveedor?.rfc ?? "");
	const [regimenFiscal, setRegimenFiscal] = useState(
		proveedor?.regimenFiscal ?? "",
	);
	const [email, setEmail] = useState(proveedor?.email ?? "");
	const [telefono, setTelefono] = useState(proveedor?.telefono ?? "");
	const [dias, setDias] = useState(
		proveedor?.diasCredito != null ? String(proveedor.diasCredito) : "",
	);
	const [limite, setLimite] = useState(
		proveedor?.limiteCredito != null ? String(proveedor.limiteCredito) : "",
	);
	const [intento, setIntento] = useState(false);

	const invalido = razonSocial.trim() === "";

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({
			razonSocial: razonSocial.trim(),
			rfc: rfc.trim().toUpperCase() || undefined,
			regimenFiscal: regimenFiscal.trim() || undefined,
			email: email.trim() || undefined,
			telefono: telefono.trim() || undefined,
			diasCredito: numero(dias) ?? undefined,
			limiteCredito: numero(limite) ?? undefined,
		});
	};

	return (
		<form
			onSubmit={enviar}
			className="grid grid-cols-1 gap-3 sm:grid-cols-2"
			noValidate
		>
			<div className="sm:col-span-2">
				<Input
					label="Razón social"
					required
					value={razonSocial}
					onChange={(e) => setRazonSocial(e.target.value)}
					placeholder="Nombre o razón social del proveedor"
				/>
			</div>
			<Input
				label="RFC"
				value={rfc}
				onChange={(e) => setRfc(e.target.value)}
				placeholder="XAXX010101000"
				className="uppercase"
				hint="Opcional"
			/>
			<Input
				label="Régimen fiscal"
				value={regimenFiscal}
				onChange={(e) => setRegimenFiscal(e.target.value)}
				placeholder="Ej. Persona moral"
			/>
			<Input
				label="Correo"
				type="email"
				value={email}
				onChange={(e) => setEmail(e.target.value)}
			/>
			<Input
				label="Teléfono"
				value={telefono}
				onChange={(e) => setTelefono(e.target.value)}
			/>
			<Input
				label="Días de crédito"
				type="number"
				inputMode="numeric"
				step="1"
				min="0"
				value={dias}
				onChange={(e) => setDias(e.target.value)}
			/>
			<Input
				label="Límite de crédito"
				type="number"
				inputMode="decimal"
				step="0.01"
				min="0"
				value={limite}
				onChange={(e) => setLimite(e.target.value)}
			/>
			{intento && invalido && (
				<p className="text-xs text-red-600 sm:col-span-2">
					La razón social es obligatoria.
				</p>
			)}
			<div className="flex justify-end gap-2 sm:col-span-2">
				<Button type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button type="submit" disabled={guardando}>
					{guardando ? "Guardando…" : "Guardar"}
				</Button>
			</div>
		</form>
	);
}

export default function ProveedoresPage() {
	useDocumentTitle("Proveedores");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();

	const [busqueda, setBusqueda] = useState("");
	const [filtroQ, setFiltroQ] = useState("");
	const [page, setPage] = useState(0);
	const [dialogoAbierto, setDialogoAbierto] = useState(false);
	const [editando, setEditando] = useState<Proveedor | null>(null);
	const [eliminarConfirmacion, setEliminarConfirmacion] =
		useState<Proveedor | null>(null);

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: ["proveedores-list", filtroQ, page],
		queryFn: () =>
			apiProveedoresPaginado({ q: filtroQ || undefined, page, size: 15 }),
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const invalidar = () =>
		queryClient.invalidateQueries({ queryKey: ["proveedores-list"] });

	const mutation = useMutation({
		mutationFn: (payload: { id: number | null; body: ProveedorRequest }) =>
			payload.id == null
				? apiCrearProveedor(payload.body)
				: apiActualizarProveedor(payload.id, payload.body),
		onSuccess: (_, vars) => {
			mostrarExito(
				vars.id == null ? "Proveedor creado." : "Proveedor actualizado.",
			);
			setDialogoAbierto(false);
			setEditando(null);
			invalidar();
		},
		onError: (err) => {
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err));
		},
	});

	const eliminar = useMutation({
		mutationFn: (id: number) => apiEliminarProveedor(id),
		onSuccess: () => {
			mostrarExito("Proveedor eliminado.");
			setEliminarConfirmacion(null);
			invalidar();
		},
		onError: (err) => {
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err));
		},
	});

	const columnas: Columna<Proveedor>[] = [
		{
			key: "r",
			header: "Razón social",
			render: (v) => (
				<span className="font-medium text-ink">{v.razonSocial}</span>
			),
		},
		{
			key: "rfc",
			header: "RFC",
			render: (v) => (
				<span className="font-mono text-xs text-muted">{v.rfc ?? "—"}</span>
			),
		},
		{
			key: "rf",
			header: "Régimen fiscal",
			render: (v) => v.regimenFiscal ?? "—",
		},
		{ key: "email", header: "Email", render: (v) => v.email ?? "—" },
		{ key: "tel", header: "Teléfono", render: (v) => v.telefono ?? "—" },
		{
			key: "dias",
			header: "Días crédito",
			align: "right",
			render: (v) => (v.diasCredito != null ? v.diasCredito : "—"),
		},
		{
			key: "lim",
			header: "Límite crédito",
			align: "right",
			render: (v) =>
				v.limiteCredito != null ? (
					<span className="tabular-nums">{formatoMoneda(v.limiteCredito)}</span>
				) : (
					"—"
				),
		},
		{
			key: "acc",
			header: "Acciones",
			align: "right",
			render: (v) => (
				<div className="flex justify-end gap-1">
					<button
						type="button"
						aria-label={`Editar ${v.razonSocial}`}
						className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
						onClick={() => {
							setEditando(v);
							setDialogoAbierto(true);
						}}
					>
						<Pencil className="h-4 w-4" />
					</button>
					<button
						type="button"
						aria-label={`Eliminar ${v.razonSocial}`}
						className="rounded p-1.5 text-muted hover:bg-red-50 hover:text-red-600"
						onClick={() => setEliminarConfirmacion(v)}
					>
						<Trash2 className="h-4 w-4" />
					</button>
				</div>
			),
		},
	];

	return (
		<div className="space-y-4">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">Proveedores</h1>
					<p className="text-sm text-muted">
						Proveedores de mercancía, su facturación y condiciones de crédito.
					</p>
				</div>
				<Button
					onClick={() => {
						setEditando(null);
						setDialogoAbierto(true);
					}}
				>
					<Building2 className="h-4 w-4" /> Nuevo proveedor
				</Button>
			</header>

			<Card>
				<div className="flex flex-wrap items-end gap-2">
					<Input
						label="Buscar"
						value={busqueda}
						onChange={(e) => setBusqueda(e.target.value)}
						onKeyDown={(e) => {
							if (e.key === "Enter") {
								setFiltroQ(busqueda.trim());
								setPage(0);
							}
						}}
						placeholder="Razón social, RFC o régimen"
						className="w-64"
					/>
					<Button
						onClick={() => {
							setFiltroQ(busqueda.trim());
							setPage(0);
						}}
						disabled={isFetching || busqueda === filtroQ}
					>
						<Search className="h-4 w-4" /> Buscar
					</Button>
					{filtroQ && (
						<Button
							variant="ghost"
							onClick={() => {
								setBusqueda("");
								setFiltroQ("");
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
				<Card titulo={`Resultados (${data.meta.totalElements})`}>
					<DataTable
						columnas={columnas}
						items={data.data}
						rowKey={(v) => v.proveedorId}
						loading={isFetching}
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}

			<Dialog
				open={dialogoAbierto}
				onClose={() => !mutation.isPending && setDialogoAbierto(false)}
				title={editando ? `Editar: ${editando.razonSocial}` : "Nuevo proveedor"}
				width="max-w-2xl"
			>
				<ProveedorForm
					proveedor={editando}
					guardando={mutation.isPending}
					onGuardar={(payload) =>
						mutation.mutate({
							id: editando?.proveedorId ?? null,
							body: payload,
						})
					}
					onClose={() => setDialogoAbierto(false)}
				/>
			</Dialog>

			<ConfirmDialog
				open={eliminarConfirmacion !== null}
				title="Confirmar eliminación"
				confirmLabel="Sí, eliminar"
				busy={eliminar.isPending}
				onCancel={() => setEliminarConfirmacion(null)}
				onConfirm={() =>
					eliminarConfirmacion &&
					eliminar.mutate(eliminarConfirmacion.proveedorId)
				}
			>
				<p className="text-sm text-ink">
					¿Eliminar el proveedor{" "}
					<span className="font-semibold">
						&quot;{eliminarConfirmacion?.razonSocial}&quot;
					</span>
					? Esta acción no se puede deshacer.
				</p>
			</ConfirmDialog>
		</div>
	);
}
