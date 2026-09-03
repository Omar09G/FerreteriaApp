import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Banknote, CalendarPlus, XCircle } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import {
	apiCancelarNomina,
	apiCrearNomina,
	apiEmpleados,
	apiNomina,
	apiPagarNomina,
} from "@/lib/api/admin";
import type { Empleado, Nomina, NominaRequest } from "@/lib/api/types";
import {
	aLocalDate,
	formatoFecha,
	formatoMoneda,
	hoyLocal,
} from "@/lib/format";
import type { RangoFechas } from "@/lib/rango";
import { EstadoBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { RangoFiltro } from "@/components/ui/RangoFiltro";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

function NominaForm({
	empleados,
	guardando,
	onGuardar,
	onClose,
}: {
	empleados: Empleado[] | undefined;
	guardando: boolean;
	onGuardar: (payload: NominaRequest) => void;
	onClose: () => void;
}) {
	const hoy = hoyLocal();
	const finSugerido = useMemo(() => {
		const d = new Date();
		d.setDate(d.getDate() + 14);
		return aLocalDate(d);
	}, []);

	const [empleadoId, setEmpleadoId] = useState<number | "">("");
	const [periodoIni, setPeriodoIni] = useState(hoy);
	const [periodoFin, setPeriodoFin] = useState(finSugerido);
	const [diasPagados, setDiasPagados] = useState("14");
	const [percepciones, setPercepciones] = useState("");
	const [deducciones, setDeducciones] = useState("");
	const [notas, setNotas] = useState("");
	const [intento, setIntento] = useState(false);

	const periodoInvalido = periodoFin < periodoIni;
	const invalido =
		empleadoId === "" ||
		periodoIni.trim() === "" ||
		periodoFin.trim() === "" ||
		periodoInvalido ||
		diasPagados.trim() === "" ||
		Number(diasPagados) < 1 ||
		percepciones.trim() === "" ||
		Number(percepciones) < 0 ||
		deducciones.trim() === "" ||
		Number(deducciones) < 0;

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({
			empleadoId: Number(empleadoId),
			periodoIni,
			periodoFin,
			diasPagados: Number(diasPagados),
			percepciones: Number(percepciones),
			deducciones: Number(deducciones),
			notas: notas.trim() || undefined,
		});
	};

	return (
		<form
			onSubmit={enviar}
			className="grid grid-cols-1 gap-3 sm:grid-cols-2"
			noValidate
		>
			<Select
				label="Empleado"
				required
				value={empleadoId}
				onChange={(e) =>
					setEmpleadoId(e.target.value ? Number(e.target.value) : "")
				}
				className="sm:col-span-2"
			>
				<option value="">Selecciona…</option>
				{empleados?.map((emp) => (
					<option key={emp.empleadoId} value={emp.empleadoId}>
						{emp.apellidoPaterno} {emp.apellidoMaterno ?? ""} {emp.nombre}
					</option>
				))}
			</Select>
			<Input
				label="Periodo inicial"
				required
				type="date"
				value={periodoIni}
				onChange={(e) => setPeriodoIni(e.target.value)}
			/>
			<Input
				label="Periodo final"
				required
				type="date"
				value={periodoFin}
				onChange={(e) => setPeriodoFin(e.target.value)}
				error={
					intento && periodoInvalido
						? "El periodo final debe ser igual o posterior al inicial."
						: undefined
				}
			/>
			<Input
				label="Días pagados"
				required
				type="number"
				inputMode="decimal"
				min="1"
				step="0.5"
				value={diasPagados}
				onChange={(e) => setDiasPagados(e.target.value)}
			/>
			<Input
				label="Percepciones"
				required
				type="number"
				inputMode="decimal"
				min="0"
				step="0.01"
				value={percepciones}
				onChange={(e) => setPercepciones(e.target.value)}
			/>
			<Input
				label="Deducciones"
				required
				type="number"
				inputMode="decimal"
				min="0"
				step="0.01"
				value={deducciones}
				onChange={(e) => setDeducciones(e.target.value)}
			/>
			<Input
				label="Notas (opcional)"
				value={notas}
				onChange={(e) => setNotas(e.target.value)}
				className="sm:col-span-2"
			/>
			{intento && invalido && (
				<p className="text-xs text-red-600 sm:col-span-2">
					Completa los campos obligatorios y verifica los valores.
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

const ESTADOS = [
	{ valor: "", nombre: "Todas" },
	{ valor: "PENDIENTE", nombre: "Pendiente" },
	{ valor: "PAGADA", nombre: "Pagada" },
	{ valor: "CANCELADA", nombre: "Cancelada" },
];

export default function NominaPage() {
	useDocumentTitle("Nómina");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();

	const [page, setPage] = useState(0);
	const [estado, setEstado] = useState("");
	const [rango, setRango] = useState<RangoFechas | null>(null);
	const [dialogoAbierto, setDialogoAbierto] = useState(false);
	const [pagarConfirmacion, setPagarConfirmacion] = useState<Nomina | null>(
		null,
	);
	const [cancelarConfirmacion, setCancelarConfirmacion] =
		useState<Nomina | null>(null);

	const empleados = useQuery({
		queryKey: ["empleados", 0],
		queryFn: () => apiEmpleados(0, 100),
	});

	const { data, isLoading, error, isFetching, isPlaceholderData } = useQuery({
		queryKey: ["nomina", page, estado, rango?.inicio, rango?.fin],
		queryFn: () =>
			apiNomina({
				estado: estado || undefined,
				desde: rango?.inicio,
				hasta: rango?.fin,
				page,
				size: 15,
			}),
		placeholderData: (prev) => prev,
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const invalidar = () =>
		queryClient.invalidateQueries({ queryKey: ["nomina"] });

	const crear = useMutation({
		mutationFn: (body: NominaRequest) => apiCrearNomina(body),
		onSuccess: () => {
			mostrarExito("Nómina creada.");
			setDialogoAbierto(false);
			invalidar();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const pagar = useMutation({
		mutationFn: (id: number) => apiPagarNomina(id),
		onSuccess: () => {
			mostrarExito("Nómina pagada.");
			setPagarConfirmacion(null);
			invalidar();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const cancelar = useMutation({
		mutationFn: (id: number) => apiCancelarNomina(id),
		onSuccess: () => {
			mostrarExito("Nómina cancelada.");
			setCancelarConfirmacion(null);
			invalidar();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const columnas: Columna<Nomina>[] = [
		{
			key: "emp",
			header: "Empleado",
			render: (v) => <span className="font-medium text-ink">{v.empleado}</span>,
		},
		{
			key: "per",
			header: "Periodo",
			render: (v) => (
				<span className="tabular-nums">
					{formatoFecha(v.periodoIni)} al {formatoFecha(v.periodoFin)}
				</span>
			),
		},
		{
			key: "dias",
			header: "Días",
			render: (v) => <span className="tabular-nums">{v.diasPagados}</span>,
		},
		{
			key: "percep",
			header: "Percepciones",
			align: "right",
			render: (v) => (
				<span className="tabular-nums">{formatoMoneda(v.percepciones)}</span>
			),
		},
		{
			key: "ded",
			header: "Deducciones",
			align: "right",
			render: (v) => (
				<span className="tabular-nums">{formatoMoneda(v.deducciones)}</span>
			),
		},
		{
			key: "neto",
			header: "Neto a pagar",
			align: "right",
			render: (v) => (
				<span className="font-medium tabular-nums">
					{formatoMoneda(v.netoPagar)}
				</span>
			),
		},
		{
			key: "est",
			header: "Estado",
			render: (v) => <EstadoBadge estado={v.estado} />,
		},
		{
			key: "fpago",
			header: "Fecha de pago",
			render: (v) =>
				v.fechaPago ? (
					<span className="tabular-nums">{formatoFecha(v.fechaPago)}</span>
				) : (
					"—"
				),
		},
		{
			key: "acc",
			header: "Acciones",
			align: "right",
			render: (v) =>
				v.estado === "PENDIENTE" && (
					<div className="flex justify-end gap-1">
						<button
							type="button"
							title="Pagar"
							aria-label={`Pagar nómina de ${v.empleado}`}
							className="rounded p-1.5 text-muted hover:bg-green-50 hover:text-green-600"
							onClick={() => setPagarConfirmacion(v)}
						>
							<Banknote className="h-4 w-4" />
						</button>
						<button
							type="button"
							title="Cancelar"
							aria-label={`Cancelar nómina de ${v.empleado}`}
							className="rounded p-1.5 text-muted hover:bg-red-50 hover:text-red-600"
							onClick={() => setCancelarConfirmacion(v)}
						>
							<XCircle className="h-4 w-4" />
						</button>
					</div>
				),
		},
	];

	return (
		<div className="space-y-4">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">Nómina</h1>
					<p className="text-sm text-muted">
						Liquidaciones por empleado y periodo. Solo administrador.
					</p>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					<RangoFiltro
						valor={rango}
						onChange={(siguiente) => {
							setRango(siguiente);
							setPage(0);
						}}
					/>
					<Select
						aria-label="Filtrar por estado"
						value={estado}
						onChange={(e) => {
							setEstado(e.target.value);
							setPage(0);
						}}
					>
						{ESTADOS.map((e) => (
							<option key={e.valor} value={e.valor}>
								{e.nombre}
							</option>
						))}
					</Select>
					<Button onClick={() => setDialogoAbierto(true)}>
						<CalendarPlus className="h-4 w-4" /> Nueva nómina
					</Button>
				</div>
			</header>

			{(isLoading || (isFetching && !data)) && <Spinner />}
			{data && (
				<Card titulo={`Nómina (${data.meta.totalElements})`}>
					<DataTable
						columnas={columnas}
						items={isPlaceholderData ? undefined : data.data}
						rowKey={(v) => v.nominaId}
						loading={isFetching}
						emptyTitle="Sin nóminas"
						emptyDescripcion="No hay registros para mostrar."
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}

			<Dialog
				open={dialogoAbierto}
				onClose={() => !crear.isPending && setDialogoAbierto(false)}
				title="Nueva nómina"
				width="max-w-lg"
			>
				<NominaForm
					empleados={empleados.data?.data}
					guardando={crear.isPending}
					onGuardar={(body) => crear.mutate(body)}
					onClose={() => setDialogoAbierto(false)}
				/>
			</Dialog>

			<Dialog
				open={pagarConfirmacion !== null}
				onClose={() => !pagar.isPending && setPagarConfirmacion(null)}
				title="Confirmar pago de nómina"
				width="max-w-md"
				footer={
					<>
						<Button
							variant="ghost"
							disabled={pagar.isPending}
							onClick={() => setPagarConfirmacion(null)}
						>
							Cancelar
						</Button>
						<Button
							disabled={pagar.isPending}
							onClick={() =>
								pagarConfirmacion && pagar.mutate(pagarConfirmacion.nominaId)
							}
						>
							{pagar.isPending ? "Pagando…" : "Sí, pagar"}
						</Button>
					</>
				}
			>
				{pagarConfirmacion && (
					<p className="text-sm text-ink">
						¿Pagar la nómina de{" "}
						<span className="font-semibold">{pagarConfirmacion.empleado}</span>{" "}
						por{" "}
						<span className="font-semibold tabular-nums">
							{formatoMoneda(pagarConfirmacion.netoPagar)}
						</span>
						?
					</p>
				)}
			</Dialog>

			<ConfirmDialog
				open={cancelarConfirmacion !== null}
				title="Confirmar cancelación"
				confirmLabel="Sí, cancelar"
				busy={cancelar.isPending}
				onCancel={() => setCancelarConfirmacion(null)}
				onConfirm={() =>
					cancelarConfirmacion && cancelar.mutate(cancelarConfirmacion.nominaId)
				}
			>
				<p className="text-sm text-ink">
					¿Cancelar la nómina pendiente de{" "}
					<span className="font-semibold">
						{cancelarConfirmacion?.empleado}
					</span>
					? La liquidación no se pagará.
				</p>
			</ConfirmDialog>
		</div>
	);
}
