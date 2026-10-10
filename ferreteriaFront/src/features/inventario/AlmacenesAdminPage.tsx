import { useState } from "react";
import { Pencil } from "lucide-react";

import {
	apiActualizarAlmacen,
	apiActualizarEstadoAlmacen,
	apiAlmacenesTodos,
	apiCrearAlmacen,
} from "@/lib/api/catalogo";
import type { Almacen, AlmacenRequest } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import {
	CrudAdminTemplate,
	type CrudColumnasCtx,
	type CrudFormProps,
} from "@/components/admin/CrudAdminTemplate";

function AlmacenForm({
	guardando,
	registro,
	onGuardar,
	onClose,
}: CrudFormProps<Almacen, AlmacenRequest>) {
	const [nombre, setNombre] = useState(registro?.nombre ?? "");
	const [direccion, setDireccion] = useState(registro?.direccion ?? "");
	const [telefono, setTelefono] = useState(registro?.telefono ?? "");
	const [esPuntoVenta, setEsPuntoVenta] = useState(
		registro?.esPuntoVenta ?? true,
	);
	const [intento, setIntento] = useState(false);

	const invalido = nombre.trim() === "";

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({
			nombre: nombre.trim(),
			direccion: direccion.trim() || null,
			telefono: telefono.trim() || null,
			esPuntoVenta,
		});
	};

	return (
		<form onSubmit={enviar} className="grid grid-cols-1 gap-3" noValidate>
			<Input
				label="Nombre del almacén"
				required
				value={nombre}
				onChange={(e) => setNombre(e.target.value)}
				placeholder="Ej. Almacén Central"
			/>
			<Input
				label="Dirección (opcional)"
				value={direccion}
				onChange={(e) => setDireccion(e.target.value)}
			/>
			<Input
				label="Teléfono (opcional)"
				value={telefono}
				onChange={(e) => setTelefono(e.target.value)}
			/>
			<label className="flex items-center gap-2 text-sm text-ink">
				<input
					type="checkbox"
					checked={esPuntoVenta}
					onChange={(e) => setEsPuntoVenta(e.target.checked)}
					className="h-4 w-4 accent-orange-600"
				/>
				Es punto de venta
			</label>
			{intento && invalido && (
				<p className="text-sm text-red-600">El nombre es obligatorio.</p>
			)}
			<div className="flex justify-end gap-2 pt-2">
				<Button hotkey="Esc" type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button hotkey="Ctrl+Enter" type="submit" variant="primary" disabled={guardando}>
					{guardando ? "Guardando…" : "Guardar"}
				</Button>
			</div>
		</form>
	);
}

function columnasAlmacen({ toggle, editar }: CrudColumnasCtx<Almacen>) {
	return [
		{
			key: "nombre",
			header: "Almacén",
			exportar: (a: Almacen) => a.nombre,
			render: (a: Almacen) => <span className="font-medium text-ink">{a.nombre}</span>,
		},
		{
			key: "direccion",
			header: "Dirección",
			exportar: (a: Almacen) => a.direccion ?? "—",
			render: (a: Almacen) => a.direccion ?? "—",
		},
		{ key: "telefono", header: "Teléfono", exportar: (a: Almacen) => a.telefono ?? "—", render: (a: Almacen) => a.telefono ?? "—" },
		{
			key: "esPuntoVenta",
			header: "Punto de venta",
			exportar: (a: Almacen) => (a.esPuntoVenta ? "Sí" : "No"),
			render: (a: Almacen) => (a.esPuntoVenta ? "Sí" : "No"),
		},
		{
			key: "activo",
			header: "Activo",
			render: (a: Almacen) => (
				<input
					type="checkbox"
					checked={a.activo}
					onChange={() => toggle(a)}
					aria-label={`Almacén ${a.nombre} activo`}
					className="h-4 w-4 accent-orange-600"
				/>
			),
		},
		{
			key: "__acciones",
			header: "Acciones",
			render: (a: Almacen) => (
				<div className="flex gap-1">
					<Button
						variant="ghost"
						size="sm"
						aria-label="Editar"
						onClick={() => editar(a)}
					>
						<Pencil className="h-4 w-4" />
					</Button>
				</div>
			),
		},
	];
}

export default function AlmacenesAdminPage() {
	return (
		<CrudAdminTemplate<Almacen, AlmacenRequest>
			config={{
				titulo: "Administrar almacenes",
				subtitulo:
					"Da de alta, modifica o da de baja los almacenes de la empresa.",
				botonNuevo: "Nuevo almacén",
				dialogoNuevo: "Nuevo almacén",
				dialogoEditar: "Editar almacén",
				sinPermiso: "No tienes permisos para administrar almacenes.",
				queryKey: ["almacenes-admin"],
				queryFn: apiAlmacenesTodos,
				queryKeysInvalidar: [["almacenes-admin"], ["almacenes"]],
				crear: apiCrearAlmacen,
				actualizar: apiActualizarAlmacen,
				cambiarEstado: apiActualizarEstadoAlmacen,
				getId: (a) => a.almacenId,
				getActivo: (a) => a.activo,
				columnas: columnasAlmacen,
				archivoExcel: "almacenes",
				emptyTitle: "Sin almacenes",
				emptyDescripcion: "No hay almacenes registrados.",
				Form: AlmacenForm,
				bajaTitulo: "Dar de baja el almacén",
				bajaConfirmLabel: "Dar de baja",
				bajaTexto: (a) => (
					<>
						¿Seguro que quieres dar de baja el almacén{" "}
						<strong>{a.nombre}</strong>? Dejará de aparecer en los
						selectores de almacén.
					</>
				),
				msgCreado: "Almacén creado.",
				msgActualizado: "Almacén actualizado.",
				msgBaja: "Almacén dado de baja.",
				msgReactivado: "Almacén reactivado.",
			}}
		/>
	);
}
