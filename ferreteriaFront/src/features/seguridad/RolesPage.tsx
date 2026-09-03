import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, ShieldCheck, Trash2 } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import {
	apiActualizarRol,
	apiCrearRol,
	apiEliminarRol,
	apiPermisos,
	apiPermisosDeRol,
	apiRolesPaginado,
	apiSetPermisosRol,
} from "@/lib/api/admin";
import type { Permiso, Rol, RolRequest } from "@/lib/api/types";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

function RolForm({
	rol,
	guardando,
	onGuardar,
	onClose,
}: {
	rol: Rol | null;
	guardando: boolean;
	onGuardar: (body: RolRequest) => void;
	onClose: () => void;
}) {
	const [clave, setClave] = useState(rol?.clave ?? "");
	const [nombre, setNombre] = useState(rol?.nombre ?? "");
	const [descripcion, setDescripcion] = useState(rol?.descripcion ?? "");
	const [activo, setActivo] = useState(rol?.activo ?? true);
	const [intento, setIntento] = useState(false);

	const invalido = clave.trim() === "" || nombre.trim() === "";

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({
			clave: clave.trim().toUpperCase(),
			nombre: nombre.trim(),
			descripcion: descripcion.trim() || undefined,
			activo,
		});
	};

	return (
		<form
			onSubmit={enviar}
			className="grid grid-cols-1 gap-3 sm:grid-cols-2"
			noValidate
		>
			<Input
				label="Clave"
				required
				disabled={rol !== null}
				value={clave}
				onChange={(e) => setClave(e.target.value.toUpperCase())}
				hint={rol === null ? "Mayúsculas y guion bajo." : undefined}
			/>
			<Input
				label="Nombre"
				required
				value={nombre}
				onChange={(e) => setNombre(e.target.value)}
			/>
			<div className="sm:col-span-2">
				<Input
					label="Descripción"
					value={descripcion}
					onChange={(e) => setDescripcion(e.target.value)}
				/>
			</div>
			<label className="flex items-center gap-2 text-sm sm:col-span-2">
				<input
					type="checkbox"
					checked={activo}
					onChange={(e) => setActivo(e.target.checked)}
					className="accent-primary"
				/>
				Activo
			</label>
			{intento && invalido && (
				<p className="text-xs text-red-600 sm:col-span-2">
					Clave y nombre son obligatorios.
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

function PermisosDialog({ rol, onClose }: { rol: Rol; onClose: () => void }) {
	const { error: mostrarError } = useToast();
	const queryClient = useQueryClient();
	const [seleccionados, setSeleccionados] = useState<string[] | null>(null);

	const permisos = useQuery({ queryKey: ["permisos"], queryFn: apiPermisos });
	const permisosDeRol = useQuery({
		queryKey: ["roles", "permisos", rol.rolId],
		queryFn: () => apiPermisosDeRol(rol.rolId),
	});

	useEffect(() => {
		if (permisos.error)
			mostrarError(
				esApiError(permisos.error)
					? permisos.error.mensajeParaUsuario()
					: String(permisos.error),
			);
	}, [permisos.error, mostrarError]);

	useEffect(() => {
		if (permisosDeRol.error)
			mostrarError(
				esApiError(permisosDeRol.error)
					? permisosDeRol.error.mensajeParaUsuario()
					: String(permisosDeRol.error),
			);
	}, [permisosDeRol.error, mostrarError]);

	const chequeados = seleccionados ?? permisosDeRol.data ?? [];

	const setPermisos = useMutation({
		mutationFn: (v: { id: number; permisos: string[] }) =>
			apiSetPermisosRol(v.id, v.permisos),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["roles"] });
			onClose();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const toggle = (clave: string) =>
		setSeleccionados((prev) => {
			const actual = prev ?? permisosDeRol.data ?? [];
			return actual.includes(clave)
				? actual.filter((p) => p !== clave)
				: [...actual, clave];
		});

	return (
		<div className="space-y-3">
			{permisos.isLoading || permisosDeRol.isLoading ? (
				<Spinner />
			) : (
				<div className="flex flex-wrap gap-2">
					{permisos.data?.map((p: Permiso) => (
						<label
							key={p.clave}
							className="flex items-center gap-1.5 rounded-md border border-line px-2 py-1 text-xs"
						>
							<input
								type="checkbox"
								checked={chequeados.includes(p.clave)}
								onChange={() => toggle(p.clave)}
								className="accent-primary"
							/>
							<span className="font-medium text-ink">{p.clave}</span>
							<span className="text-muted">{p.descripcion}</span>
						</label>
					))}
				</div>
			)}
			<div className="flex justify-end gap-2">
				<Button variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button
					disabled={setPermisos.isPending}
					onClick={() =>
						setPermisos.mutate({ id: rol.rolId, permisos: chequeados })
					}
				>
					<ShieldCheck className="h-4 w-4" /> Guardar permisos
				</Button>
			</div>
		</div>
	);
}

export default function RolesPage() {
	useDocumentTitle("Roles y permisos");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();

	const [page, setPage] = useState(0);
	const [dialogoCreado, setDialogoCreado] = useState(false);
	const [editando, setEditando] = useState<Rol | null>(null);
	const [permisosDe, setPermisosDe] = useState<Rol | null>(null);
	const [eliminarConfirmacion, setEliminarConfirmacion] = useState<Rol | null>(
		null,
	);

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: ["roles", "roles-container", page],
		queryFn: () => apiRolesPaginado({ page, size: 15 }),
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const invalidar = () =>
		queryClient.invalidateQueries({ queryKey: ["roles"] });

	const crear = useMutation({
		mutationFn: (body: RolRequest) => apiCrearRol(body),
		onSuccess: () => {
			mostrarExito("Rol creado.");
			setDialogoCreado(false);
			invalidar();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const actualizar = useMutation({
		mutationFn: (v: { id: number; body: RolRequest }) =>
			apiActualizarRol(v.id, v.body),
		onSuccess: () => {
			mostrarExito("Rol actualizado.");
			setEditando(null);
			invalidar();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const eliminar = useMutation({
		mutationFn: (id: number) => apiEliminarRol(id),
		onSuccess: () => {
			mostrarExito("Rol eliminado.");
			setEliminarConfirmacion(null);
			invalidar();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const columnas: Columna<Rol>[] = [
		{
			key: "clave",
			header: "Clave",
			render: (v) => <span className="font-medium text-ink">{v.clave}</span>,
		},
		{ key: "nombre", header: "Nombre", render: (v) => v.nombre },
		{ key: "desc", header: "Descripción", render: (v) => v.descripcion ?? "—" },
		{
			key: "act",
			header: "Activo",
			render: (v) =>
				v.activo ? (
					<Badge tone="success">Activo</Badge>
				) : (
					<Badge tone="danger">Inactivo</Badge>
				),
		},
		{
			key: "permisos",
			header: "Permisos",
			render: (v) => <span className="tabular-nums">{v.permisos.length}</span>,
		},
		{
			key: "acc",
			header: "Acciones",
			align: "right",
			render: (v) => (
				<div className="flex justify-end gap-1">
					<button
						type="button"
						aria-label={`Editar ${v.nombre}`}
						className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
						onClick={() => setEditando(v)}
					>
						<Pencil className="h-4 w-4" />
					</button>
					<button
						type="button"
						aria-label={`Permisos de ${v.nombre}`}
						className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
						onClick={() => setPermisosDe(v)}
					>
						<ShieldCheck className="h-4 w-4" />
					</button>
					<button
						type="button"
						aria-label={`Eliminar ${v.nombre}`}
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
					<h1 className="text-xl font-bold text-ink">Roles y permisos</h1>
					<p className="text-sm text-muted">
						Administración de roles y sus permisos. Solo administrador.
					</p>
				</div>
				<Button onClick={() => setDialogoCreado(true)}>
					<Plus className="h-4 w-4" /> Nuevo rol
				</Button>
			</header>

			{(isLoading || (isFetching && !data)) && <Spinner />}
			{data && (
				<Card titulo={`Roles (${data.meta.totalElements})`}>
					<DataTable
						columnas={columnas}
						items={data.data}
						rowKey={(v) => v.rolId}
						loading={isFetching}
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}

			<Dialog
				open={dialogoCreado}
				onClose={() => !crear.isPending && setDialogoCreado(false)}
				title="Nuevo rol"
			>
				<RolForm
					rol={null}
					guardando={crear.isPending}
					onGuardar={(body) => crear.mutate(body)}
					onClose={() => setDialogoCreado(false)}
				/>
			</Dialog>

			<Dialog
				open={editando !== null}
				onClose={() => setEditando(null)}
				title={`Editar rol ${editando?.clave ?? ""}`}
			>
				<RolForm
					rol={editando}
					guardando={actualizar.isPending}
					onGuardar={(body) =>
						editando && actualizar.mutate({ id: editando.rolId, body })
					}
					onClose={() => setEditando(null)}
				/>
			</Dialog>

			<Dialog
				open={permisosDe !== null}
				onClose={() => setPermisosDe(null)}
				title={`Permisos de ${permisosDe?.clave ?? ""}`}
				width="max-w-2xl"
			>
				{permisosDe && (
					<PermisosDialog
						rol={permisosDe}
						onClose={() => setPermisosDe(null)}
					/>
				)}
			</Dialog>

			<ConfirmDialog
				open={eliminarConfirmacion !== null}
				title="Confirmar eliminación"
				confirmLabel="Sí, eliminar"
				busy={eliminar.isPending}
				onCancel={() => setEliminarConfirmacion(null)}
				onConfirm={() =>
					eliminarConfirmacion && eliminar.mutate(eliminarConfirmacion.rolId)
				}
			>
				<p className="text-sm text-ink">
					¿Eliminar el rol{" "}
					<span className="font-semibold">
						&quot;{eliminarConfirmacion?.nombre}&quot;
					</span>
					? Esta acción no se puede deshacer.
				</p>
			</ConfirmDialog>
		</div>
	);
}
