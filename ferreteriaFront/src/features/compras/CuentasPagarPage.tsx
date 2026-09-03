import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, FileWarning, HandCoins } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { apiCajas, apiTurnoActual } from "@/lib/api/caja";
import {
	apiAbonarCuentaPagar,
	apiCuentasPagar,
	apiFacturasPendientes,
	apiFacturasVencidas,
} from "@/lib/api/compras";
import {
	FORMAS_PAGO,
	type AbonoProveedorRequest,
	type CuentasPagar,
	type FacturaPendiente,
	type FacturaVencida,
} from "@/lib/api/types";
import { formatoFecha, formatoMoneda } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

function estadoAbono(c: CuentasPagar) {
	if (c.estado === "LIQUIDADA") return <Badge tone="success">Pagada</Badge>;
	if (c.estado === "CANCELADA") return <Badge tone="default">Cancelada</Badge>;
	if (c.diasVencido > 0)
		return <Badge tone="danger">Vencida {c.diasVencido}d</Badge>;
	if (c.estado === "PARCIAL") return <Badge tone="info">Parcial</Badge>;
	return <Badge tone="warning">Pendiente</Badge>;
}

function AbonoDialog({
	cuenta,
	guardando,
	onGuardar,
	onClose,
}: {
	cuenta: CuentasPagar;
	guardando: boolean;
	onGuardar: (payload: AbonoProveedorRequest) => void;
	onClose: () => void;
}) {
	const cajas = useQuery({ queryKey: ["cajas"], queryFn: apiCajas });
	const [monto, setMonto] = useState<number>(cuenta.saldo);
	const [formaPagoId, setFormaPagoId] = useState<number>(1);
	const [cajaId, setCajaId] = useState<number | "">("");
	const [referencia, setReferencia] = useState("");
	const [intento, setIntento] = useState(false);

	const forma = FORMAS_PAGO.find((f) => f.id === formaPagoId);
	const requiereReferencia = forma?.requiereReferencia ?? false;
	const turno = useQuery({
		queryKey: ["turnoActual", cajaId],
		queryFn: () => apiTurnoActual(Number(cajaId)),
		enabled: cajaId !== "",
		retry: false,
	});
	const cajaConTurno = cajaId !== "" && turno.isSuccess;
	const cajaSinTurno = cajaId !== "" && turno.isError;

	const invalido =
		Number.isNaN(monto) ||
		monto <= 0 ||
		monto > cuenta.saldo ||
		(requiereReferencia && referencia.trim() === "") ||
		cajaId === "" ||
		cajaSinTurno;

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		setIntento(true);
		if (invalido) return;
		onGuardar({
			monto: Number(monto),
			formaPagoId,
			cajaId: Number(cajaId),
			referencia: referencia.trim() || undefined,
		});
	};

	return (
		<form onSubmit={enviar} className="space-y-3" noValidate>
			<div className="rounded-md border border-line bg-warmbg px-3 py-2 text-sm">
				<span className="font-medium text-ink">{cuenta.proveedor}</span>
				<span className="text-muted"> · {cuenta.compraFolio}</span>
				<div className="flex justify-between text-xs text-muted">
					<span>Vence {formatoFecha(cuenta.fechaVencimiento)}</span>
					<span>
						Saldo restante{" "}
						<span className="font-semibold text-ink">
							{formatoMoneda(cuenta.saldo)}
						</span>
					</span>
				</div>
			</div>

			<div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
				<Input
					label="Monto del abono"
					type="number"
					inputMode="decimal"
					required
					min={0.01}
					max={cuenta.saldo}
					step="0.01"
					value={monto}
					onChange={(e) => setMonto(Number(e.target.value))}
				/>
				<Select
					label="Forma de pago"
					value={formaPagoId}
					onChange={(e) => setFormaPagoId(Number(e.target.value))}
				>
					{FORMAS_PAGO.filter((f) => f.id !== 6).map((f) => (
						<option key={f.id} value={f.id}>
							{f.nombre}
						</option>
					))}
				</Select>
				<Select
					label="Caja"
					required
					value={cajaId}
					onChange={(e) =>
						setCajaId(e.target.value ? Number(e.target.value) : "")
					}
				>
					<option value="">Selecciona…</option>
					{cajas.data?.map((c) => (
						<option key={c.cajaId} value={c.cajaId}>
							{c.nombre}
						</option>
					))}
				</Select>
				{requiereReferencia && (
					<Input
						label="Referencia (folio/últimos dígitos)"
						required
						value={referencia}
						onChange={(e) => setReferencia(e.target.value)}
						placeholder="Ej. SPEI-841223"
					/>
				)}
			</div>

			{cajaId !== "" && turno.isLoading && (
				<p className="text-xs text-muted">Verificando turno abierto…</p>
			)}
			{cajaConTurno && (
				<p className="text-xs text-emerald-600">
					Turno abierto: la salida se registrará en la caja para el cuadre.
				</p>
			)}
			{cajaSinTurno && (
				<p className="text-xs text-red-600">
					Esta caja no tiene un turno abierto. Ábrelo en el POS para registrar
					el abono.
				</p>
			)}

			{intento && invalido && (
				<p className="text-xs text-red-600">
					Revisa el monto (mayor a 0 y hasta el saldo), la caja y la referencia.
				</p>
			)}
			<div className="flex justify-end gap-2">
				<Button type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button type="submit" disabled={guardando}>
					{guardando ? "Guardando…" : "Registrar abono"}
				</Button>
			</div>
		</form>
	);
}

export default function CuentasPagarPage() {
	useDocumentTitle("Cuentas por pagar");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();
	const [tab, setTab] = useState<"todas" | "pendientes" | "vencidas">("todas");
	const [cuentaAbonar, setCuentaAbonar] = useState<CuentasPagar | null>(null);

	const cuentas = useQuery({
		queryKey: ["cuentas-pagar"],
		queryFn: apiCuentasPagar,
	});
	const pendientes = useQuery({
		queryKey: ["facturas-pendientes"],
		queryFn: apiFacturasPendientes,
	});
	const vencidas = useQuery({
		queryKey: ["facturas-vencidas"],
		queryFn: apiFacturasVencidas,
	});

	useEffect(() => {
		if (cuentas.error)
			mostrarError(
				esApiError(cuentas.error)
					? cuentas.error.mensajeParaUsuario()
					: String(cuentas.error),
			);
	}, [cuentas.error, mostrarError]);
	useEffect(() => {
		if (pendientes.error)
			mostrarError(
				esApiError(pendientes.error)
					? pendientes.error.mensajeParaUsuario()
					: String(pendientes.error),
			);
	}, [pendientes.error, mostrarError]);
	useEffect(() => {
		if (vencidas.error)
			mostrarError(
				esApiError(vencidas.error)
					? vencidas.error.mensajeParaUsuario()
					: String(vencidas.error),
			);
	}, [vencidas.error, mostrarError]);

	const abonar = useMutation({
		mutationFn: (body: AbonoProveedorRequest) =>
			apiAbonarCuentaPagar(cuentaAbonar!.cuentaPagarId, body),
		onSuccess: (resp) => {
			mostrarExito(
				resp.estado === "LIQUIDADA"
					? `${resp.compraFolio} liquidada. Salida registrada en caja.`
					: `Abono en ${resp.compraFolio}: saldo ${formatoMoneda(resp.saldo)}.`,
			);
			setCuentaAbonar(null);
			queryClient.invalidateQueries({ queryKey: ["cuentas-pagar"] });
			queryClient.invalidateQueries({ queryKey: ["facturas-pendientes"] });
			queryClient.invalidateQueries({ queryKey: ["facturas-vencidas"] });
			queryClient.invalidateQueries({ queryKey: ["cortes"] });
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const columnas: Columna<CuentasPagar>[] = [
		{
			key: "c",
			header: "Compra",
			render: (v) => (
				<span className="font-medium text-ink">{v.compraFolio}</span>
			),
		},
		{ key: "p", header: "Proveedor", render: (v) => v.proveedor },
		{
			key: "tot",
			header: "Total",
			align: "right",
			render: (v) => (
				<span className="tabular-nums">{formatoMoneda(v.montoTotal)}</span>
			),
		},
		{
			key: "pag",
			header: "Pagado",
			align: "right",
			render: (v) => (
				<span className="tabular-nums text-muted">
					{formatoMoneda(v.montoPagado)}
				</span>
			),
		},
		{
			key: "sal",
			header: "Saldo",
			align: "right",
			render: (v) => (
				<span className="font-medium tabular-nums">
					{formatoMoneda(v.saldo)}
				</span>
			),
		},
		{
			key: "vto",
			header: "Vence",
			render: (v) => (
				<span className="whitespace-nowrap tabular-nums">
					{formatoFecha(v.fechaVencimiento)}
				</span>
			),
		},
		{ key: "est", header: "Estado", render: estadoAbono },
		{
			key: "acc",
			header: "",
			render: (v) =>
				v.estado === "LIQUIDADA" || v.estado === "CANCELADA" ? (
					<span className="text-xs text-muted">Cerrada</span>
				) : (
					<Button
						variant="secondary"
						size="sm"
						onClick={() => setCuentaAbonar(v)}
					>
						<HandCoins className="h-4 w-4" /> Abonar
					</Button>
				),
		},
	];

	const resumenPendiente = (v: FacturaPendiente) => (
		<div
			key={v.cuentaPagarId}
			className="flex items-center justify-between gap-3 rounded-md border border-line px-3 py-2 text-sm"
		>
			<span className="min-w-0">
				<span className="block truncate font-medium text-ink">
					{v.proveedor}
				</span>
				<span className="text-xs text-muted">
					{v.compraFolio} · vence {formatoFecha(v.fechaVencimiento)} ·{" "}
					{v.alerta}
				</span>
			</span>
			<span className="shrink-0 font-semibold tabular-nums">
				{formatoMoneda(v.saldo)}
			</span>
		</div>
	);

	const resumenVencida = (v: FacturaVencida) => (
		<div
			key={v.cuentaPagarId}
			className="flex items-center justify-between gap-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm"
		>
			<span className="min-w-0">
				<span className="block truncate font-medium text-red-900">
					{v.proveedor}
				</span>
				<span className="text-xs text-red-700">
					{v.compraFolio} · {v.diasVencido}d de retraso · {v.antiguedad}
				</span>
			</span>
			<span className="shrink-0 font-semibold tabular-nums text-red-900">
				{formatoMoneda(v.saldo)}
			</span>
		</div>
	);

	const pendientesAbonables =
		cuentas.data?.filter(
			(c) => c.estado !== "LIQUIDADA" && c.estado !== "CANCELADA",
		) ?? [];

	return (
		<div className="space-y-4">
			<header>
				<h1 className="text-xl font-bold text-ink">Cuentas por pagar</h1>
				<p className="text-sm text-muted">
					Compromisos con proveedores: pueden cerrarse registrando un abono
					(salida en caja).
				</p>
			</header>

			<div className="grid gap-4 lg:grid-cols-2">
				<Card titulo="Próximas a vencer">
					{pendientes.isLoading ? (
						<Spinner />
					) : pendientes.data && pendientes.data.length > 0 ? (
						<div className="space-y-2">
							<p className="text-xs text-muted">
								<CalendarClock className="mr-1 inline h-3.5 w-3.5" />
								{pendientes.data.length} factura(s) con saldo y fecha de
								vencimiento futura o reciente.
							</p>
							{pendientes.data.map(resumenPendiente)}
						</div>
					) : (
						<p className="py-4 text-center text-sm text-muted">
							Sin facturas por vencer.
						</p>
					)}
				</Card>
				<Card titulo="Vencidas — prioridad de pago">
					{vencidas.isLoading ? (
						<Spinner />
					) : vencidas.data && vencidas.data.length > 0 ? (
						<div className="space-y-2">
							<p className="text-xs text-muted">
								<FileWarning className="mr-1 inline h-3.5 w-3.5" />
								{vencidas.data.length} factura(s) con retraso. Registra el abono
								para liquidarlas.
							</p>
							{vencidas.data.map(resumenVencida)}
						</div>
					) : (
						<p className="py-4 text-center text-sm text-muted">
							Sin facturas vencidas.
						</p>
					)}
				</Card>
			</div>

			<div className="flex gap-2">
				{(["todas", "pendientes", "vencidas"] as const).map((t) => (
					<button
						key={t}
						type="button"
						onClick={() => setTab(t)}
						className={`rounded-md px-3 py-1.5 text-sm font-medium ${tab === t ? "bg-primary text-white" : "border border-line bg-surface text-ink hover:bg-warmbg"}`}
					>
						{t === "todas"
							? "Todas"
							: t === "pendientes"
								? "Pendientes"
								: "Vencidas"}
					</button>
				))}
			</div>

			{cuentas.isLoading ? (
				<Spinner />
			) : (
				cuentas.data && (
					<Card
						titulo={`Cuentas (${cuentas.data.length}) · ${pendientesAbonables.length} abonable(s)`}
					>
						<DataTable
							columnas={columnas}
							items={
								tab === "todas"
									? cuentas.data
									: tab === "pendientes"
										? cuentas.data.filter(
												(c) => c.estado !== "LIQUIDADA" && c.diasVencido <= 0,
											)
										: cuentas.data.filter((c) => c.diasVencido > 0)
							}
							rowKey={(v) => v.cuentaPagarId}
						/>
					</Card>
				)
			)}

			<Dialog
				open={cuentaAbonar !== null}
				onClose={() => !abonar.isPending && setCuentaAbonar(null)}
				title={`Abonar ${cuentaAbonar?.compraFolio ?? ""}`}
				width="max-w-lg"
			>
				{cuentaAbonar && (
					<AbonoDialog
						cuenta={cuentaAbonar}
						guardando={abonar.isPending}
						onGuardar={(body) => abonar.mutate(body)}
						onClose={() => setCuentaAbonar(null)}
					/>
				)}
			</Dialog>
		</div>
	);
}
