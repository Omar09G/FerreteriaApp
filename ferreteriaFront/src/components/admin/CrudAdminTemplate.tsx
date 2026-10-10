import { useState, type ComponentType, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { useTieneRol } from "@/store/auth";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { ExportarExcel } from "@/components/ui/ExportarExcel";
import { Dialog } from "@/components/ui/Dialog";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

export interface CrudFormProps<TItem, TRequest> {
	registro: TItem | null;
	guardando: boolean;
	onGuardar: (body: TRequest) => void;
	onClose: () => void;
}

export interface CrudColumnasCtx<TItem> {
	/** Activo → pide confirmación de baja; inactivo → reactiva directo. */
	toggle: (item: TItem) => void;
	editar: (item: TItem) => void;
}

export interface CrudAdminConfig<TItem, TRequest> {
	titulo: string;
	subtitulo: string;
	botonNuevo: string;
	dialogoNuevo: string;
	dialogoEditar: string;
	sinPermiso: string;
	queryKey: string[];
	queryFn: () => Promise<TItem[]>;
	/** Queries a invalidar tras cada mutación (la propia + selectores públicos). */
	queryKeysInvalidar: string[][];
	crear: (body: TRequest) => Promise<unknown>;
	actualizar: (id: number, body: TRequest) => Promise<unknown>;
	cambiarEstado: (id: number, activo: boolean) => Promise<unknown>;
	getId: (item: TItem) => number;
	getActivo: (item: TItem) => boolean;
	columnas: (ctx: CrudColumnasCtx<TItem>) => Columna<TItem>[];
	archivoExcel: string;
	emptyTitle: string;
	emptyDescripcion: string;
	Form: ComponentType<CrudFormProps<TItem, TRequest>>;
	bajaTitulo: string;
	bajaConfirmLabel: string;
	bajaTexto: (item: TItem) => ReactNode;
	msgCreado: string;
	msgActualizado: string;
	msgBaja: string;
	msgReactivado: string;
}

/**
 * Plantilla para páginas admin de alta/baja/cambio (cajas, almacenes, …).
 * Comportamiento unificado: 4 mutaciones con toast + invalidación,
 * baja con confirmación, reactivación directa y gate ADMINISTRADOR.
 */
export function CrudAdminTemplate<TItem, TRequest>({
	config,
}: {
	config: CrudAdminConfig<TItem, TRequest>;
}) {
	useDocumentTitle(config.titulo);
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();
	const puedeAdministrar = useTieneRol(["ADMINISTRADOR"]);

	const [dialogoAbierto, setDialogoAbierto] = useState(false);
	const [editing, setEditing] = useState<TItem | null>(null);
	const [aDesactivar, setADesactivar] = useState<TItem | null>(null);

	const lista = useQuery({ queryKey: config.queryKey, queryFn: config.queryFn });

	const invalidar = () => {
		for (const key of config.queryKeysInvalidar) {
			queryClient.invalidateQueries({ queryKey: key });
		}
	};

	const mostrarErrorMutacion = (err: unknown) =>
		mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err));

	const crear = useMutation({
		mutationFn: (body: TRequest) => config.crear(body),
		onSuccess: () => {
			mostrarExito(config.msgCreado);
			setDialogoAbierto(false);
			invalidar();
		},
		onError: mostrarErrorMutacion,
	});

	const editar = useMutation({
		mutationFn: (body: TRequest) =>
			config.actualizar(config.getId(editing!), body),
		onSuccess: () => {
			mostrarExito(config.msgActualizado);
			setDialogoAbierto(false);
			setEditing(null);
			invalidar();
		},
		onError: mostrarErrorMutacion,
	});

	const desactivar = useMutation({
		mutationFn: (id: number) => config.cambiarEstado(id, false),
		onSuccess: () => {
			mostrarExito(config.msgBaja);
			setADesactivar(null);
			invalidar();
		},
		onError: mostrarErrorMutacion,
	});

	const reactivar = useMutation({
		mutationFn: (id: number) => config.cambiarEstado(id, true),
		onSuccess: () => {
			mostrarExito(config.msgReactivado);
			invalidar();
		},
		onError: mostrarErrorMutacion,
	});

	const toggle = (item: TItem) => {
		if (config.getActivo(item)) {
			setADesactivar(item);
		} else {
			reactivar.mutate(config.getId(item));
		}
	};

	const abrirEdicion = (item: TItem) => {
		setEditing(item);
		setDialogoAbierto(true);
	};

	const columnas = config.columnas({ toggle, editar: abrirEdicion });
	const ver = lista.data ?? [];
	const { Form } = config;

	if (!puedeAdministrar) {
		return <div className="p-6 text-muted">{config.sinPermiso}</div>;
	}

	return (
		<div className="space-y-4 p-6">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">{config.titulo}</h1>
					<p className="text-sm text-muted">{config.subtitulo}</p>
				</div>
				<Button
					hotkey="F4"
					onClick={() => {
						setEditing(null);
						setDialogoAbierto(true);
					}}
					variant="primary"
				>
					<Plus className="h-4 w-4" /> {config.botonNuevo}
				</Button>
			</header>

			<Card
				actions={
					<ExportarExcel
						columnas={columnas}
						items={ver}
						archivo={config.archivoExcel}
					/>
				}
			>
				{lista.isLoading ? (
					<Spinner />
				) : (
					<DataTable
						columnas={columnas}
						items={ver}
						rowKey={(item) => config.getId(item)}
						emptyTitle={config.emptyTitle}
						emptyDescripcion={config.emptyDescripcion}
						animada
					/>
				)}
			</Card>

			<Dialog
				open={dialogoAbierto}
				onClose={() => setDialogoAbierto(false)}
				title={editing ? config.dialogoEditar : config.dialogoNuevo}
				width="max-w-md"
			>
				<Form
					guardando={crear.isPending || editar.isPending}
					registro={editing}
					onGuardar={(body) =>
						editing ? editar.mutate(body) : crear.mutate(body)
					}
					onClose={() => setDialogoAbierto(false)}
				/>
			</Dialog>

			<ConfirmDialog
				open={Boolean(aDesactivar)}
				title={config.bajaTitulo}
				confirmLabel={config.bajaConfirmLabel}
				busy={desactivar.isPending}
				onCancel={() => setADesactivar(null)}
				onConfirm={() =>
					aDesactivar && desactivar.mutate(config.getId(aDesactivar))
				}
			>
				{aDesactivar && config.bajaTexto(aDesactivar)}
			</ConfirmDialog>
		</div>
	);
}
