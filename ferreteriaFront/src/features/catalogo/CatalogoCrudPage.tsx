import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Search, Trash2 } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { mensajeError } from "@/lib/api/client";
import {
	apiCatalogoActualizar,
	apiCatalogoCrear,
	apiCatalogoDatos,
	apiCatalogoEliminar,
	apiCatalogoOpciones,
} from "@/lib/api/catalogos";
import type {
	CampoCatalogo,
	CatalogoDescriptor,
	FilaCatalogo,
} from "@/lib/api/types";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { useToast } from "@/components/ui/Toast";

const TAMANIO = 20;

/** Convierte el valor del campo a cadena para mostrar en tabla. */
function textoCelda(v: unknown): string {
	if (v === null || v === undefined) return "—";
	return String(v);
}

/** Convierte una fila cruda al payload tipado que envía al backend. */
function aPayload(
	campos: CampoCatalogo[],
	form: Record<string, string>,
	editar: boolean,
): Record<string, unknown> {
	const payload: Record<string, unknown> = {};
	for (const c of campos) {
		if (c.esActivo) continue;
		const raw = form[c.nombre] ?? "";
		if (c.tipo === "BOOLEAN") {
			payload[c.nombre] = raw === "true";
		} else if (c.tipo === "NUMERO") {
			payload[c.nombre] = raw === "" ? null : Number(raw);
		} else if (c.tipo === "DECIMAL") {
			payload[c.nombre] = raw === "" ? null : Number(raw);
		} else if (c.tipo === "FECHA") {
			payload[c.nombre] = raw === "" ? null : raw;
		} else {
			payload[c.nombre] = raw;
		}
		// En edición se reenvía la PK string tal cual (no cambia).
		if (editar && c.unico) payload[c.nombre] = raw;
	}
	return payload;
}

function CatalogoForm({
	descriptor,
	opciones,
	registro,
	guardando,
	onGuardar,
	onClose,
}: {
	descriptor: CatalogoDescriptor;
	opciones: Record<string, Record<string, unknown>[]>;
	registro: FilaCatalogo | null;
	guardando: boolean;
	onGuardar: (payload: Record<string, unknown>) => void;
	onClose: () => void;
}) {
	const campos = descriptor.campos.filter((c) => !c.esActivo);
	const [form, setForm] = useState<Record<string, string>>(() => {
		const inicial: Record<string, string> = {};
		for (const c of campos) {
			const v = registro ? registro[c.nombre] : undefined;
			inicial[c.nombre] = v === null || v === undefined ? "" : String(v);
		}
		return inicial;
	});
	const [intento, setIntento] = useState(false);

	const invalido = campos.some(
		(c) => c.requerido && (form[c.nombre] ?? "").trim() === "",
	);

	const set = (nombre: string, val: string) =>
		setForm((f) => ({ ...f, [nombre]: val }));

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar(aPayload(campos, form, Boolean(registro)));
	};

	const renderControl = (c: CampoCatalogo) => {
		const etiqueta = c.requerido ? `${c.etiqueta} *` : c.etiqueta;
		if (c.opcionesTabla) {
			const opts = opciones[c.nombre] ?? [];
			return (
				<Select
					key={c.nombre}
					label={etiqueta}
					required={c.requerido}
					value={form[c.nombre] ?? ""}
					onChange={(e) => set(c.nombre, e.target.value)}
				>
					<option value="">— Seleccionar —</option>
					{opts.map((o, i) => {
						const raw = o as unknown as Record<string, unknown> & { clave: unknown; texto?: unknown };
						const valor = String(raw.clave);
						const textoArray = Array.isArray(raw.texto) ? (raw.texto as unknown[]).map(String).filter(Boolean).join(" · ") : "";
						const textoCols =
							c.opcionesColumnas
								?.map((col) => String(raw[col] ?? ""))
								.filter(Boolean)
								.join(" · ") || "";
						const texto = textoArray || textoCols || valor;
						return (
							<option key={`${valor}-${i}`} value={valor}>
								{texto}
							</option>
						);
					})}
				</Select>
			);
		}
		if (descriptor.listasValidas?.[c.nombre]) {
			return (
				<Select
					key={c.nombre}
					label={etiqueta}
					required={c.requerido}
					value={form[c.nombre] ?? ""}
					onChange={(e) => set(c.nombre, e.target.value)}
				>
					<option value="">— Seleccionar —</option>
					{descriptor.listasValidas[c.nombre].map((op) => (
						<option key={op} value={op}>
							{op}
						</option>
					))}
				</Select>
			);
		}
		if (c.tipo === "BOOLEAN") {
			return (
				<Select
					key={c.nombre}
					label={etiqueta}
					required={c.requerido}
					value={form[c.nombre] ?? ""}
					onChange={(e) => set(c.nombre, e.target.value)}
				>
					<option value="false">No</option>
					<option value="true">Sí</option>
				</Select>
			);
		}
		const esNum = c.tipo === "NUMERO" || c.tipo === "DECIMAL";
		const esFecha = c.tipo === "FECHA";
		return (
			<Input
				key={c.nombre}
				label={etiqueta}
				required={c.requerido}
				type={esNum ? "number" : esFecha ? "date" : "text"}
				step={c.tipo === "DECIMAL" ? "any" : undefined}
				value={form[c.nombre] ?? ""}
				onChange={(e) => set(c.nombre, e.target.value)}
				disabled={Boolean(registro) && c.unico && !c.opcionesTabla}
			/>
		);
	};

	return (
		<form onSubmit={enviar} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
			{campos.map(renderControl)}
			{intento && invalido && (
				<p className="col-span-full text-sm text-red-600">
					Completa los campos obligatorios.
				</p>
			)}
			<div className="col-span-full flex justify-end gap-2 pt-2">
				<Button type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button type="submit" variant="primary" disabled={guardando}>
					{guardando ? "Guardando…" : "Guardar"}
				</Button>
			</div>
		</form>
	);
}

export default function CatalogoCrudPage({ clave }: { clave: string }) {
	const { success: toastExito, error: toastError } = useToast();
	const queryClient = useQueryClient();
	const [q, setQ] = useState("");
	const [buscado, setBuscado] = useState("");
	const [page, setPage] = useState(0);
	const [dialogoAbierto, setDialogoAbierto] = useState(false);
	const [editing, setEditing] = useState<FilaCatalogo | null>(null);
	const [aEliminar, setAEliminar] = useState<FilaCatalogo | null>(null);

	useDocumentTitle("Catálogos");

	// Descriptor (panel) del catálogo.
	const { data: paneles } = useQuery({
		queryKey: ["catalogos-paneles"],
		queryFn: async () =>
			(await import("@/lib/api/catalogos")).apiCatalogosPaneles(),
		staleTime: Infinity,
	});
	const descriptor = paneles?.find((p) => p.clave === clave);

	// Datos paginados.
	const { data, isFetching } = useQuery({
		queryKey: ["catalogo-datos", clave, buscado, page],
		queryFn: () =>
			apiCatalogoDatos({ clave, q: buscado || undefined, page, size: TAMANIO }),
		enabled: Boolean(descriptor),
		placeholderData: (prev) => prev,
	});

	// Opciones de los campos FK (para dropdowns).
	const { data: camposOpciones } = useQuery({
		queryKey: ["catalogo-opciones", clave],
		queryFn: async () => {
			const fkCampos = (descriptor?.campos ?? []).filter((c) =>
				Boolean(c.opcionesTabla),
			);
			const entradas = await Promise.all(
				fkCampos.map(
					async (c) =>
						[
							c.nombre,
							await apiCatalogoOpciones(clave, c.nombre),
						] as const,
				),
			);
			return Object.fromEntries(entradas);
		},
		enabled: Boolean(descriptor?.campos.some((c) => Boolean(c.opcionesTabla))),
	});

	const mutationCrear = useMutation({
		mutationFn: (body: Record<string, unknown>) =>
			apiCatalogoCrear(clave, body),
		onSuccess: () => {
			toastExito("Registro creado.");
			queryClient.invalidateQueries({ queryKey: ["catalogo-datos", clave] });
			setDialogoAbierto(false);
			setEditing(null);
		},
		onError: (e) => toastError(mensajeError(e)),
	});

	const mutationEditar = useMutation({
		mutationFn: (body: Record<string, unknown>) =>
			apiCatalogoActualizar(clave, editing?.__pk ?? "", body),
		onSuccess: () => {
			toastExito("Registro actualizado.");
			queryClient.invalidateQueries({ queryKey: ["catalogo-datos", clave] });
			setDialogoAbierto(false);
			setEditing(null);
		},
		onError: (e) => toastError(mensajeError(e)),
	});

	const mutationEliminar = useMutation({
		mutationFn: (id: string | number) => apiCatalogoEliminar(clave, id),
		onSuccess: () => {
			toastExito("Registro eliminado.");
			queryClient.invalidateQueries({ queryKey: ["catalogo-datos", clave] });
			setAEliminar(null);
		},
		onError: (e) => toastError(mensajeError(e)),
	});

	const columnas = useMemo<Columna<FilaCatalogo>[]>(() => {
		if (!descriptor) return [];
		const cols: Columna<FilaCatalogo>[] = descriptor.campos
			.filter((c) => !c.esActivo)
			.map((c) => ({
				key: c.nombre,
				header: c.etiqueta,
				render: (item) =>
					c.tipo === "BOOLEAN" ? (
						item[c.nombre] ? (
							<Badge tone="success">Sí</Badge>
						) : (
							<Badge tone="default">No</Badge>
						)
					) : (
						textoCelda(item[c.nombre])
					),
			}));
		cols.push({
			key: "__acciones",
			header: "Acciones",
			render: (item) => (
				<div className="flex gap-1">
					<Button
						variant="ghost"
						size="sm"
						onClick={() => {
							setEditing(item);
							setDialogoAbierto(true);
						}}
						aria-label="Editar"
					>
						<Pencil className="h-4 w-4" />
					</Button>
					{descriptor.soportaBajaLogica && (
						<Button
							variant="ghost"
							size="sm"
							onClick={() => setAEliminar(item)}
							aria-label="Eliminar"
						>
							<Trash2 className="h-4 w-4" />
						</Button>
					)}
				</div>
			),
		});
		return cols;
	}, [descriptor]);

	if (!descriptor) {
		return <div className="p-6 text-muted">Catálogo no disponible.</div>;
	}

	const abrirNuevo = () => {
		setEditing(null);
		setDialogoAbierto(true);
	};

	return (
		<div className="space-y-4 p-6">
			<div className="flex items-center justify-between">
				<div>
					<h1 className="text-xl font-semibold text-ink">
						{descriptor.nombre}
					</h1>
					<p className="text-sm text-muted">
						Catálogo de administración ·{" "}
						{descriptor.campos.filter((c) => !c.esActivo).length} campos ·{" "}
						{descriptor.tabla}
					</p>
				</div>
				<Button onClick={abrirNuevo} variant="primary">
					<Plus className="h-4 w-4" /> Nuevo
				</Button>
			</div>

			<Card className="space-y-4">
				<form
					className="flex items-center gap-2"
					onSubmit={(e) => {
						e.preventDefault();
						setBuscado(q.trim());
						setPage(0);
					}}
				>
					<Input
						icono={<Search className="h-4 w-4" />}
						placeholder="Buscar…"
						value={q}
						onChange={(e) => setQ(e.target.value)}
						className="max-w-xs"
					/>
					<Button type="submit" variant="secondary">
						Buscar
					</Button>
				</form>

				<DataTable
					columnas={columnas}
					items={data?.data}
					loading={isFetching}
					rowKey={(f) => String(f.__pk)}
					emptyTitle="Sin registros"
					emptyDescripcion="No hay datos que mostrar en este catálogo."
				/>

				{data && data.meta.totalElements > 0 && (
					<Pagination meta={data.meta} onPage={setPage} />
				)}
			</Card>

			<Dialog
				open={dialogoAbierto}
				onClose={() => {
					if (mutationCrear.isPending || mutationEditar.isPending) return;
					setDialogoAbierto(false);
					setEditing(null);
				}}
				title={
					editing ? `Editar ${descriptor.nombre}` : `Nuevo ${descriptor.nombre}`
				}
				width="max-w-2xl"
			>
				<CatalogoForm
					descriptor={descriptor}
					opciones={camposOpciones ?? {}}
					registro={editing}
					guardando={mutationCrear.isPending || mutationEditar.isPending}
					onGuardar={(payload) =>
						editing
							? mutationEditar.mutate(payload)
							: mutationCrear.mutate(payload)
					}
					onClose={() => {
						if (mutationCrear.isPending || mutationEditar.isPending) return;
						setDialogoAbierto(false);
						setEditing(null);
					}}
				/>
			</Dialog>

			<ConfirmDialog
				open={Boolean(aEliminar)}
				title={`Eliminar ${descriptor.nombre}`}
				confirmLabel="Eliminar"
				busy={mutationEliminar.isPending}
				onCancel={() => setAEliminar(null)}
				onConfirm={() => aEliminar && mutationEliminar.mutate(aEliminar.__pk)}
			>
				¿Deseas desactivar este registro? Se conserva en el historial pero
				dejará de estar disponible.
			</ConfirmDialog>
		</div>
	);
}
