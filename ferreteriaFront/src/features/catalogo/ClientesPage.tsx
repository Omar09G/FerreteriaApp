import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Search, Trash2, UserPlus } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import {
	apiActualizarCliente,
	apiClientes,
	apiCrearCliente,
	apiEliminarCliente,
} from "@/lib/api/catalogo";
import type { Cliente, ClienteRequest } from "@/lib/api/types";
import { formatoMoneda } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

function numero(value: string): number | null {
	const limpio = value.trim();
	if (!limpio) return null;
	const n = Number(limpio);
	return Number.isFinite(n) ? n : null;
}

function ClienteForm({
	cliente,
	guardando,
	onGuardar,
	onClose,
}: {
	cliente: Cliente | null;
	guardando: boolean;
	onGuardar: (payload: ClienteRequest) => void;
	onClose: () => void;
}) {
	const [tipoPersona, setTipoPersona] = useState<string>(
		cliente?.tipoPersona ?? "FISICA",
	);
	const [razonSocial, setRazonSocial] = useState(cliente?.razonSocial ?? "");
	const [nombreComercial, setNombreComercial] = useState(
		cliente?.nombreComercial ?? "",
	);
	const [rfc, setRfc] = useState(cliente?.rfc ?? "");
	const [telefono, setTelefono] = useState(cliente?.telefono ?? "");
	const [email, setEmail] = useState(cliente?.email ?? "");
	const [limite, setLimite] = useState(
		cliente?.limiteCredito != null ? String(cliente.limiteCredito) : "",
	);
	const [dias, setDias] = useState(
		cliente?.diasCredito != null ? String(cliente.diasCredito) : "",
	);
	const [mayorista, setMayorista] = useState(cliente?.esMayorista ?? false);
	const [intento, setIntento] = useState(false);

	const invalido = razonSocial.trim() === "";

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({
			tipoPersona: tipoPersona as "FISICA" | "MORAL",
			razonSocial: razonSocial.trim(),
			nombreComercial: nombreComercial.trim() || undefined,
			rfc: rfc.trim().toUpperCase() || undefined,
			telefono: telefono.trim() || undefined,
			email: email.trim() || undefined,
			limiteCredito: numero(limite),
			diasCredito: numero(dias),
			esMayorista: mayorista,
		});
	};

	return (
		<form
			onSubmit={enviar}
			className="grid grid-cols-1 gap-3 sm:grid-cols-2"
			noValidate
		>
			<Select
				label="Tipo de persona"
				value={tipoPersona}
				onChange={(e) => setTipoPersona(e.target.value)}
			>
				<option value="FISICA">Persona física</option>
				<option value="MORAL">Persona moral</option>
			</Select>
			<Input
				label="RFC"
				value={rfc}
				onChange={(e) => setRfc(e.target.value)}
				placeholder="XAXX010101000"
				className="uppercase"
				hint="Opcional; hasta 13 caracteres"
			/>
			<div className="sm:col-span-2">
				<Input
					label="Razón social"
					required
					value={razonSocial}
					onChange={(e) => setRazonSocial(e.target.value)}
					placeholder="Nombre o razón social del cliente"
				/>
			</div>
			<div className="sm:col-span-2">
				<Input
					label="Nombre comercial"
					value={nombreComercial}
					onChange={(e) => setNombreComercial(e.target.value)}
				/>
			</div>
			<Input
				label="Teléfono"
				value={telefono}
				onChange={(e) => setTelefono(e.target.value)}
			/>
			<Input
				label="Correo"
				type="email"
				value={email}
				onChange={(e) => setEmail(e.target.value)}
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
			<Input
				label="Días de crédito"
				type="number"
				inputMode="numeric"
				step="1"
				min="0"
				value={dias}
				onChange={(e) => setDias(e.target.value)}
			/>
			<label className="flex items-center gap-2 pt-2 text-sm">
				<input
					type="checkbox"
					checked={mayorista}
					onChange={(e) => setMayorista(e.target.checked)}
					className="h-4 w-4 accent-primary"
				/>
				<span className="font-medium text-ink">Cliente mayorista</span>
			</label>
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

export default function ClientesPage() {
	useDocumentTitle("Clientes");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();

	const [busqueda, setBusqueda] = useState("");
	const [filtroQ, setFiltroQ] = useState("");
	const [page, setPage] = useState(0);
	const [dialogoAbierto, setDialogoAbierto] = useState(false);
	const [editando, setEditando] = useState<Cliente | null>(null);
	const [eliminarConfirmacion, setEliminarConfirmacion] =
		useState<Cliente | null>(null);

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: ["clientes", filtroQ, page],
		queryFn: () => apiClientes({ q: filtroQ || undefined, page, size: 20 }),
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const invalidar = () =>
		queryClient.invalidateQueries({ queryKey: ["clientes"] });

	const mutation = useMutation({
		mutationFn: (payload: { id: number | null; body: ClienteRequest }) =>
			payload.id == null
				? apiCrearCliente(payload.body)
				: apiActualizarCliente(payload.id, payload.body),
		onSuccess: (_, vars) => {
			mostrarExito(
				vars.id == null ? "Cliente creado." : "Cliente actualizado.",
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
		mutationFn: (id: number) => apiEliminarCliente(id),
		onSuccess: () => {
			mostrarExito("Cliente desactivado.");
			setEliminarConfirmacion(null);
			invalidar();
		},
		onError: (err) => {
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err));
		},
	});

	const columnas: Columna<Cliente>[] = [
		{
			key: "r",
			header: "Razón social",
			render: (v) => (
				<span className="font-medium text-ink">{v.razonSocial}</span>
			),
		},
		{
			key: "c",
			header: "Nombre comercial",
			render: (v) => v.nombreComercial ?? "—",
		},
		{
			key: "t",
			header: "Persona",
			render: (v) =>
				v.tipoPersona === "MORAL" ? (
					<Badge tone="info">Moral</Badge>
				) : (
					<Badge tone="warning">Física</Badge>
				),
		},
		{
			key: "rfc",
			header: "RFC",
			render: (v) => (
				<span className="font-mono text-xs text-muted">{v.rfc ?? "—"}</span>
			),
		},
		{ key: "tel", header: "Teléfono", render: (v) => v.telefono ?? "—" },
		{
			key: "may",
			header: "Mayorista",
			render: (v) =>
				v.esMayorista ? (
					<Badge tone="success">Sí</Badge>
				) : (
					<Badge tone="default">No</Badge>
				),
		},
		{
			key: "cred",
			header: "Límite / días",
			align: "right",
			render: (v) =>
				v.limiteCredito != null
					? `${formatoMoneda(v.limiteCredito)} / ${v.diasCredito ?? 0}d`
					: "—",
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
						aria-label={`Desactivar ${v.razonSocial}`}
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
					<h1 className="text-xl font-bold text-ink">Clientes</h1>
					<p className="text-sm text-muted">
						Personas físicas y morales para facturación, crédito y mayoristas.
					</p>
				</div>
				<Button
					onClick={() => {
						setEditando(null);
						setDialogoAbierto(true);
					}}
				>
					<UserPlus className="h-4 w-4" /> Nuevo cliente
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
						placeholder="Nombre o razón social"
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
						rowKey={(v) => v.clienteId}
						loading={isFetching}
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}

			<Dialog
				open={dialogoAbierto}
				onClose={() => !mutation.isPending && setDialogoAbierto(false)}
				title={editando ? `Editar: ${editando.razonSocial}` : "Nuevo cliente"}
				width="max-w-2xl"
			>
				<ClienteForm
					cliente={editando}
					guardando={mutation.isPending}
					onGuardar={(payload) =>
						mutation.mutate({ id: editando?.clienteId ?? null, body: payload })
					}
					onClose={() => setDialogoAbierto(false)}
				/>
			</Dialog>

			<ConfirmDialog
				open={eliminarConfirmacion !== null}
				title="Confirmar desactivación"
				confirmLabel="Sí, desactivar"
				busy={eliminar.isPending}
				onCancel={() => setEliminarConfirmacion(null)}
				onConfirm={() =>
					eliminarConfirmacion &&
					eliminar.mutate(eliminarConfirmacion.clienteId)
				}
			>
				<p className="text-sm text-ink">
					¿Desactivar el cliente{" "}
					<span className="font-semibold">
						&quot;{eliminarConfirmacion?.razonSocial}&quot;
					</span>
					? Dejará de estar disponible en nuevos documentos, pero se conserva su
					historial.
				</p>
			</ConfirmDialog>
		</div>
	);
}
