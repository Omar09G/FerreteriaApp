import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Pencil } from "lucide-react";

import { apiAlmacenes } from "@/lib/api/catalogo";
import {
	apiActualizarCaja,
	apiActualizarEstadoCaja,
	apiCajas,
	apiCrearCaja,
} from "@/lib/api/caja";
import type { Almacen, Caja, CajaRequest } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Input";
import {
	CrudAdminTemplate,
	type CrudColumnasCtx,
	type CrudFormProps,
} from "@/components/admin/CrudAdminTemplate";

function CajaForm({
	guardando,
	registro,
	onGuardar,
	onClose,
}: CrudFormProps<Caja, CajaRequest>) {
	const almacenes = useQuery({
		queryKey: ["almacenes-caja"],
		queryFn: apiAlmacenes,
	});
	const listaAlmacenes: Almacen[] = almacenes.data ?? [];

	const [nombre, setNombre] = useState(registro?.nombre ?? "");
	const [almacenId, setAlmacenId] = useState<number | "">(
		registro?.almacenId ?? "",
	);
	const [activa, setActiva] = useState(registro?.activa ?? true);
	const [intento, setIntento] = useState(false);

	const invalido = nombre.trim() === "" || almacenId === "";

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({ nombre: nombre.trim(), almacenId: Number(almacenId), activa });
	};

	return (
		<form onSubmit={enviar} className="grid grid-cols-1 gap-3" noValidate>
			<Input
				label="Nombre de la caja"
				required
				value={nombre}
				onChange={(e) => setNombre(e.target.value)}
				placeholder="Ej. Caja Central"
			/>
			<Select
				label="Almacén"
				required
				value={almacenId}
				onChange={(e) =>
					setAlmacenId(e.target.value ? Number(e.target.value) : "")
				}
			>
				<option value="">Selecciona…</option>
				{listaAlmacenes.map((a) => (
					<option key={a.almacenId} value={a.almacenId}>
						{a.nombre}
					</option>
				))}
			</Select>
			<label className="flex items-center gap-2 text-sm text-ink">
				<input
					type="checkbox"
					checked={activa}
					onChange={(e) => setActiva(e.target.checked)}
					className="h-4 w-4 accent-primary"
				/>
				Activa
			</label>
			{intento && invalido && (
				<p className="text-sm text-red-600">
					Completa los campos obligatorios.
				</p>
			)}
			<div className="flex justify-end gap-2 pt-2">
				<Button hotkey="Esc" type="button" variant="ghost" onClick={onClose}>Cancelar
				</Button>
				<Button hotkey="Ctrl+Enter" type="submit" variant="primary" disabled={guardando}>
					{guardando ? "Guardando…" : "Guardar"}
				</Button>
			</div>
		</form>
	);
}

function columnasCaja({ toggle, editar }: CrudColumnasCtx<Caja>) {
	return [
		{
			key: "nombre",
			header: "Caja",
			exportar: (c: Caja) => c.nombre,
			render: (c: Caja) => <span className="font-medium text-ink">{c.nombre}</span>,
		},
		{ key: "almacen", header: "Almacén", exportar: (c: Caja) => c.almacenNombre, render: (c: Caja) => c.almacenNombre },
		{
			key: "activa",
			header: "Activa",
			render: (c: Caja) => (
				<input
					type="checkbox"
					checked={c.activa}
					onChange={() => toggle(c)}
					aria-label={`Caja ${c.nombre} activa`}
					className="h-4 w-4 accent-primary"
				/>
			),
		},
		{
			key: "__acciones",
			header: "Acciones",
			render: (c: Caja) => (
				<div className="flex gap-1">
					<Button hotkey="F4"
						variant="ghost"
						size="sm"
						aria-label="Editar"
						onClick={() => editar(c)}
					>
						<Pencil className="h-4 w-4" />
					</Button>
				</div>
			),
		},
	];
}

export default function CajasAdminPage() {
	return (
		<CrudAdminTemplate<Caja, CajaRequest>
			config={{
				titulo: "Administrar cajas",
				subtitulo:
					"Da de alta, modifica o da de baja las cajas que usan turnos de caja.",
				botonNuevo: "Nueva caja",
				dialogoNuevo: "Nueva caja",
				dialogoEditar: "Editar caja",
				sinPermiso: "No tienes permisos para administrar cajas.",
				queryKey: ["cajas-admin"],
				queryFn: apiCajas,
				queryKeysInvalidar: [["cajas-admin"], ["cajas"]],
				crear: apiCrearCaja,
				actualizar: apiActualizarCaja,
				cambiarEstado: apiActualizarEstadoCaja,
				getId: (c) => c.cajaId,
				getActivo: (c) => c.activa,
				conEstado: (c, activa) => ({ ...c, activa }),
				conCambios: (c, body) => ({ ...c, ...body }),
				columnas: columnasCaja,
				archivoExcel: "cajas",
				emptyTitle: "Sin cajas",
				emptyDescripcion: "No hay cajas registradas.",
				Form: CajaForm,
				bajaTitulo: "Dar de baja la caja",
				bajaConfirmLabel: "Dar de baja",
				bajaTexto: (c) => (
					<>
						¿Seguro que quieres dar de baja la caja{" "}
						<strong>{c.nombre}</strong>? Dejará de aparecer en el
						selector de cajas.
					</>
				),
				msgCreado: "Caja creada.",
				msgActualizado: "Caja actualizada.",
				msgBaja: "Caja dada de baja.",
				msgReactivado: "Caja reactivada.",
			}}
		/>
	);
}
