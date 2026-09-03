import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	ArrowDownCircle,
	ArrowUpCircle,
	Banknote,
	BarChart3,
	Lock,
	Settings2,
	Unlock,
} from "lucide-react";
import {
	Bar,
	BarChart,
	CartesianGrid,
	Legend,
	ResponsiveContainer,
	Tooltip,
	XAxis,
	YAxis,
} from "recharts";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useNavigate } from "react-router-dom";
import { useTieneRol } from "@/store/auth";
import { esApiError } from "@/lib/api/client";
import {
	apiAbrirTurno,
	apiCajas,
	apiCerrarTurno,
	apiCortes,
	apiEsperadoTurno,
	apiMovimientosTurno,
	apiRegistrarMovimiento,
	apiTurnos,
} from "@/lib/api/caja";
import type {
	CorteCaja,
	CorteRequest,
	MovimientoCajaRequest,
	TurnoCaja,
} from "@/lib/api/types";
import { formatoFechaHora, formatoMoneda, formatoNumero } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

function EstadoTurno({ estado }: { estado: string }) {
	if (estado === "ABIERTO") return <Badge tone="success">Abierto</Badge>;
	if (estado === "CERRADO") return <Badge tone="info">Cerrado</Badge>;
	return <Badge>{estado}</Badge>;
}

const CONCEPTOS_MANUALES: Record<string, string[]> = {
	ENTRADA: ["OTRO_INGRESO", "COBRANZA_CREDITO", "DEPOSITO_GARANTIA_RENTA"],
	SALIDA: [
		"GASTO_OPERATIVO",
		"NOMINA",
		"RETIRO_EFECTIVO",
		"DEVOLUCION_CLIENTE",
		"DEVOLUCION_DEPOSITO_RENTA",
	],
};

const ETIQUETA_CONCEPTO: Record<string, string> = {
	OTRO_INGRESO: "Otro ingreso",
	COBRANZA_CREDITO: "Cobranza de crédito",
	DEPOSITO_GARANTIA_RENTA: "Depósito de garantía (renta)",
	GASTO_OPERATIVO: "Gasto operativo",
	NOMINA: "Nómina",
	RETIRO_EFECTIVO: "Retiro de efectivo",
	DEVOLUCION_CLIENTE: "Devolución a cliente",
	DEVOLUCION_DEPOSITO_RENTA: "Devolución de depósito (renta)",
};

function ResumenCorte({ corte }: { corte: CorteCaja }) {
	const desglose = (raw: string): [string, number][] => {
		if (!raw || raw === "") return [];
		try {
			return Object.entries(JSON.parse(raw)) as [string, number][];
		} catch {
			return [];
		}
	};
	const salidas = desglose(corte.desgloseSalidas);
	const formas = desglose(corte.desgloseFormasPago);
	const filas: [string, string][] = [
		["Total vendido", formatoMoneda(corte.totalVendido)],
		["Utilidad bruta", formatoMoneda(corte.utilidadBruta)],
		["Margen", `${formatoNumero(corte.margenPct)}%`],
		["Fondo de apertura", formatoMoneda(corte.fondoApertura)],
		["Entradas efectivo", formatoMoneda(corte.entradasEfectivo)],
		["Salidas efectivo", formatoMoneda(corte.salidasEfectivo)],
		["Egresos no efectivo", formatoMoneda(corte.egresosNoEfectivo)],
		["Pérdidas inventario", formatoMoneda(corte.perdidasInventario)],
		["Esperado", formatoMoneda(corte.dineroEsperado)],
		["Contado", formatoMoneda(corte.dineroContado)],
	];
	return (
		<div className="mt-3 rounded-md border border-line p-3">
			<div className="mb-1 flex items-center justify-between">
				<span className="text-sm font-semibold text-ink">
					Corte #{corte.corteId} · {corte.cajaNombre}
				</span>
				<Badge tone={corte.diferencia === 0 ? "success" : "warning"}>
					Diferencia {formatoMoneda(corte.diferencia)}
				</Badge>
			</div>
			<dl className="grid grid-cols-2 gap-x-4 gap-y-0.5 text-sm sm:grid-cols-4">
				{filas.map(([k, v]) => (
					<div key={k} className="flex justify-between gap-2">
						<dt className="text-muted">{k}</dt>
						<dd className="font-medium tabular-nums text-ink">{v}</dd>
					</div>
				))}
			</dl>
			{(salidas.length > 0 || formas.length > 0) && (
				<div className="mt-2 space-y-1 rounded-md bg-canvas px-2 py-1.5 text-xs">
					{salidas.length > 0 && (
						<div className="flex flex-wrap gap-x-3">
							<span className="text-muted">Salidas:</span>
							{salidas.map(([k, v]) => (
								<span key={k} className="font-medium text-red-700">
									{k} −{formatoMoneda(Math.abs(v))}
								</span>
							))}
						</div>
					)}
					{formas.length > 0 && (
						<div className="flex flex-wrap gap-x-3">
							<span className="text-muted">Formas de pago:</span>
							{formas.map(([k, v]) => (
								<span key={k} className="font-medium text-ink">
									{k} {formatoMoneda(Math.abs(v))}
								</span>
							))}
						</div>
					)}
				</div>
			)}
			{corte.observaciones && (
				<p className="mt-2 text-xs text-muted">{corte.observaciones}</p>
			)}
		</div>
	);
}

function GraficaCortes({ cortes }: { cortes: CorteCaja[] }) {
	const [tab, setTab] = useState<"ventas" | "efectivo" | "formas">("ventas");

	if (cortes.length === 0) {
		return (
			<p className="py-8 text-center text-sm text-muted">
				Sin cortes para el rango seleccionado.
			</p>
		);
	}
	const base = cortes.slice().sort((a, b) => a.corteId - b.corteId);

	const parseJson = (raw: string): Record<string, number> => {
		if (!raw) return {};
		try {
			const obj = JSON.parse(raw) as Record<string, number>;
			const merged: Record<string, number> = {};
			for (const [k, v] of Object.entries(obj)) {
				const norm = k.trim().toLowerCase();
				// normaliza "efectivo"/"EFECTIVO" -> "Efectivo"
				const label = norm === "efectivo" ? "Efectivo" : k;
				merged[label] = (merged[label] ?? 0) + Number(v);
			}
			return merged;
		} catch {
			return {};
		}
	};

	// --- datos ventas ---
	const dataVentas = base.map((c) => ({
		label: `#${c.corteId} ${c.cajaNombre}`,
		corteId: c.corteId,
		totalVendido: c.totalVendido,
		utilidadBruta: c.utilidadBruta,
		numVentas: c.numVentas,
		fecha: c.fecha,
		margenPct: c.margenPct,
		costoVentas: c.costoVentas,
	}));

	// --- datos efectivo ---
	const dataEfectivo = base.map((c) => ({
		label: `#${c.corteId}`,
		corteId: c.corteId,
		fondoApertura: c.fondoApertura,
		entradasEfectivo: c.entradasEfectivo,
		salidasEfectivo: c.salidasEfectivo,
		dineroEsperado: c.dineroEsperado,
		dineroContado: c.dineroContado,
		diferencia: c.diferencia,
		egresosNoEfectivo: c.egresosNoEfectivo,
	}));

	// --- datos formas de pago (stacked)
	const formasKeys = Array.from(
		new Set(base.flatMap((c) => Object.keys(parseJson(c.desgloseFormasPago)))),
	).sort();
	const coloresFormas: Record<string, string> = {
		Efectivo: "#16a34a",
		"Transferencia SPEI": "#2563eb",
		"Tarjeta de crédito": "#ea580c",
		"Tarjeta de débito": "#9333ea",
		EFECTIVO: "#16a34a",
	};
	const fallbackColors = ["#0ea5e9", "#f59e0b", "#84cc16", "#e11d48", "#14b8a6", "#a855f7"];
	const dataFormas = base.map((c) => {
		const parsed = parseJson(c.desgloseFormasPago);
		const row: Record<string, string | number> = { label: `#${c.corteId}`, corteId: c.corteId };
		for (const k of formasKeys) row[k] = parsed[k] ?? 0;
		return row as { label: string; corteId: number } & Record<string, number>;
	});

	const tabClass = (active: boolean) =>
		`rounded-md px-3 py-1.5 text-xs font-medium ${active ? "bg-primary text-white" : "bg-canvas text-muted hover:text-ink"}`;

	return (
		<div className="space-y-4">
			<div className="flex flex-wrap gap-1.5 rounded-lg bg-canvas p-1">
				<button type="button" className={tabClass(tab === "ventas")} onClick={() => setTab("ventas")}>
					Ventas
				</button>
				<button type="button" className={tabClass(tab === "efectivo")} onClick={() => setTab("efectivo")}>
					Flujo efectivo
				</button>
				<button type="button" className={tabClass(tab === "formas")} onClick={() => setTab("formas")}>
					Formas de pago
				</button>
			</div>
			<p className="text-xs text-muted">
				{tab === "ventas" && "Total vendido vs utilidad por corte. Margen 100% indica costo_ventas=0 (sin costo registrado)."}
				{tab === "efectivo" && "Fondo + entradas - salidas = esperado. Diferencia 0 = cuadrado. Incluye egresos no efectivo (transferencias/pagos proveedor)."}
				{tab === "formas" && "Distribución por forma de pago (desgloseFormasPago JSON). Efectivo vs digital."}
			</p>

			{tab === "ventas" && (
				<>
					<ResponsiveContainer width="100%" height={320}>
						<BarChart data={dataVentas} margin={{ left: 8, right: 8, top: 8 }}>
							<CartesianGrid strokeDasharray="3 3" stroke="#e7e5e4" />
							<XAxis dataKey="label" stroke="#57534e" fontSize={12} interval={0} angle={-15} textAnchor="end" height={60} />
							<YAxis stroke="#57534e" fontSize={12} width={90} tickFormatter={(v: number) => formatoMoneda(v)} />
							<Tooltip formatter={(value, name) => [formatoMoneda(Number(value)), String(name)]} />
							<Legend />
							<Bar dataKey="totalVendido" name="Total vendido" fill="#ea580c" radius={[4, 4, 0, 0]} />
							<Bar dataKey="utilidadBruta" name="Utilidad" fill="#16a34a" radius={[4, 4, 0, 0]} />
						</BarChart>
					</ResponsiveContainer>
					<div className="overflow-x-auto rounded-md border border-line">
						<table className="w-full text-sm">
							<thead className="bg-canvas text-xs uppercase tracking-wide text-muted">
								<tr>
									<th scope="col" className="px-2 py-1 text-left">Corte</th>
									<th scope="col" className="px-2 py-1 text-left">Fecha</th>
									<th scope="col" className="px-2 py-1 text-right">Ventas</th>
									<th scope="col" className="px-2 py-1 text-right">Total</th>
									<th scope="col" className="px-2 py-1 text-right">Utilidad</th>
									<th scope="col" className="px-2 py-1 text-right">Margen</th>
								</tr>
							</thead>
							<tbody className="divide-y divide-line">
								{dataVentas.map((d) => (
									<tr key={d.corteId}>
										<td className="px-2 py-1.5 font-medium">{d.label}</td>
										<td className="px-2 py-1.5 tabular-nums">{d.fecha}</td>
										<td className="px-2 py-1.5 text-right tabular-nums">{d.numVentas}</td>
										<td className="px-2 py-1.5 text-right font-medium tabular-nums">{formatoMoneda(d.totalVendido)}</td>
										<td className="px-2 py-1.5 text-right tabular-nums text-emerald-700">{formatoMoneda(d.utilidadBruta)}</td>
										<td className="px-2 py-1.5 text-right tabular-nums">{formatoNumero(d.margenPct)}%</td>
									</tr>
								))}
							</tbody>
							<tfoot className="bg-canvas font-semibold">
								<tr>
									<td colSpan={2} className="px-2 py-1.5 text-right">Total periodo</td>
									<td className="px-2 py-1.5 text-right tabular-nums">{formatoNumero(dataVentas.reduce((a, c) => a + c.numVentas, 0))}</td>
									<td className="px-2 py-1.5 text-right tabular-nums">{formatoMoneda(dataVentas.reduce((a, c) => a + c.totalVendido, 0))}</td>
									<td className="px-2 py-1.5 text-right tabular-nums">{formatoMoneda(dataVentas.reduce((a, c) => a + c.utilidadBruta, 0))}</td>
									<td className="px-2 py-1.5 text-right tabular-nums">—</td>
								</tr>
							</tfoot>
						</table>
					</div>
					{base.some((c) => c.costoVentas === 0) && (
						<p className="rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">
							Nota: {base.filter((c) => c.costoVentas === 0).length} de {base.length} cortes tienen costo_ventas=0 ⇒ margen 100%. Para utilidad real, asegurar costo_actual en productos.
						</p>
					)}
				</>
			)}

			{tab === "efectivo" && (
				<>
					<ResponsiveContainer width="100%" height={320}>
						<BarChart data={dataEfectivo} margin={{ left: 8, right: 8, top: 8 }}>
							<CartesianGrid strokeDasharray="3 3" stroke="#e7e5e4" />
							<XAxis dataKey="label" stroke="#57534e" fontSize={12} />
							<YAxis stroke="#57534e" fontSize={12} width={90} tickFormatter={(v: number) => formatoMoneda(v)} />
							<Tooltip formatter={(value) => formatoMoneda(Number(value))} />
							<Legend />
							<Bar dataKey="fondoApertura" name="Fondo" fill="#a3a3a3" radius={[4, 4, 0, 0]} />
							<Bar dataKey="entradasEfectivo" name="Entradas ef." fill="#16a34a" radius={[4, 4, 0, 0]} />
							<Bar dataKey="salidasEfectivo" name="Salidas ef." fill="#dc2626" radius={[4, 4, 0, 0]} />
							<Bar dataKey="dineroEsperado" name="Esperado" fill="#0ea5e9" radius={[4, 4, 0, 0]} />
							<Bar dataKey="egresosNoEfectivo" name="Egresos no ef." fill="#f59e0b" radius={[4, 4, 0, 0]} />
						</BarChart>
					</ResponsiveContainer>
					<div className="overflow-x-auto rounded-md border border-line">
						<table className="w-full text-sm">
							<thead className="bg-canvas text-xs uppercase tracking-wide text-muted">
								<tr>
									<th scope="col" className="px-2 py-1 text-left">Corte</th>
									<th scope="col" className="px-2 py-1 text-right">Fondo</th>
									<th scope="col" className="px-2 py-1 text-right">Entradas</th>
									<th scope="col" className="px-2 py-1 text-right">Salidas</th>
									<th scope="col" className="px-2 py-1 text-right">Esperado</th>
									<th scope="col" className="px-2 py-1 text-right">Contado</th>
									<th scope="col" className="px-2 py-1 text-right">Dif.</th>
								</tr>
							</thead>
							<tbody className="divide-y divide-line">
								{dataEfectivo.map((d) => (
									<tr key={d.corteId}>
										<td className="px-2 py-1.5 font-medium">{d.label}</td>
										<td className="px-2 py-1.5 text-right tabular-nums">{formatoMoneda(d.fondoApertura)}</td>
										<td className="px-2 py-1.5 text-right tabular-nums text-emerald-700">{formatoMoneda(d.entradasEfectivo)}</td>
										<td className="px-2 py-1.5 text-right tabular-nums text-red-700">{formatoMoneda(d.salidasEfectivo)}</td>
										<td className="px-2 py-1.5 text-right font-medium tabular-nums">{formatoMoneda(d.dineroEsperado)}</td>
										<td className="px-2 py-1.5 text-right tabular-nums">{formatoMoneda(d.dineroContado)}</td>
										<td className={`px-2 py-1.5 text-right tabular-nums ${d.diferencia === 0 ? "text-emerald-700" : "text-red-700"}`}>{formatoMoneda(d.diferencia)}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</>
			)}

			{tab === "formas" && (
				<>
					{formasKeys.length === 0 ? (
						<p className="py-4 text-center text-sm text-muted">Sin desglose de formas de pago.</p>
					) : (
						<>
							<ResponsiveContainer width="100%" height={320}>
								<BarChart data={dataFormas} margin={{ left: 8, right: 8, top: 8 }}>
									<CartesianGrid strokeDasharray="3 3" stroke="#e7e5e4" />
									<XAxis dataKey="label" stroke="#57534e" fontSize={12} />
									<YAxis stroke="#57534e" fontSize={12} width={90} tickFormatter={(v: number) => formatoMoneda(v)} />
									<Tooltip formatter={(value) => formatoMoneda(Number(value))} />
									<Legend />
									{formasKeys.map((k, i) => (
										<Bar key={k} dataKey={k} stackId="pago" fill={coloresFormas[k] ?? fallbackColors[i % fallbackColors.length]} radius={[4, 4, 0, 0]} />
									))}
								</BarChart>
							</ResponsiveContainer>
							<p className="text-xs text-muted">Claves normalizadas (Efectivo/EFECTIVO unificadas). Útil para ver proporción efectivo vs digital (Transferencia/Tarjeta).</p>
						</>
					)}
				</>
			)}
		</div>
	);
}

export default function CajaPage() {
	useDocumentTitle("Caja y cortes");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();
	const navigate = useNavigate();
	const puedeAdministrar = useTieneRol(["ADMINISTRADOR"]);

	const hoyLocal = () => {
		const d = new Date();
		const mes = String(d.getMonth() + 1).padStart(2, "0");
		const dia = String(d.getDate()).padStart(2, "0");
		return `${d.getFullYear()}-${mes}-${dia}`;
	};

	const [cajaId, setCajaId] = useState<number | "">("");
	const [pageTurnos, setPageTurnos] = useState(0);
	const [pageCortes, setPageCortes] = useState(0);
	const [desde, setDesde] = useState(hoyLocal);
	const [hasta, setHasta] = useState(hoyLocal);
	const [turnoSeleccionado, setTurnoSeleccionado] = useState<TurnoCaja | null>(
		null,
	);
	const [abrirAbierto, setAbrirAbierto] = useState(false);
	const [montoApertura, setMontoApertura] = useState("0");
	const [cerrarAbierto, setCerrarAbierto] = useState(false);
	const [montoContado, setMontoContado] = useState("");
	const [observaciones, setObservaciones] = useState("");
	const [graficaAbierta, setGraficaAbierta] = useState(false);
	const [mov, setMov] = useState({
		tipo: "SALIDA",
		concepto: "GASTO_OPERATIVO",
		monto: "",
	});

	const cajas = useQuery({ queryKey: ["cajas"], queryFn: apiCajas });
	const turnos = useQuery({
		queryKey: ["turnos", cajaId, pageTurnos],
		queryFn: () => apiTurnos(Number(cajaId), pageTurnos),
		enabled: cajaId !== "",
	});
	const cortes = useQuery({
		queryKey: ["cortes", pageCortes, desde, hasta],
		queryFn: () =>
			apiCortes(pageCortes, 10, desde || undefined, hasta || undefined),
	});
	const movimientos = useQuery({
		queryKey: ["movimientos-turno", turnoSeleccionado?.turnoCajaId],
		queryFn: () =>
			apiMovimientosTurno(
				turnoSeleccionado!.cajaId,
				turnoSeleccionado!.turnoCajaId,
			),
		enabled: turnoSeleccionado !== null,
	});
	const esperado = useQuery({
		queryKey: ["esperado-turno", turnoSeleccionado?.turnoCajaId],
		queryFn: () =>
			apiEsperadoTurno(
				turnoSeleccionado!.cajaId,
				turnoSeleccionado!.turnoCajaId,
			),
		enabled: cerrarAbierto && turnoSeleccionado !== null,
	});

	useEffect(() => {
		if (cajas.error)
			mostrarError(
				esApiError(cajas.error)
					? cajas.error.mensajeParaUsuario()
					: String(cajas.error),
			);
	}, [cajas.error, mostrarError]);
	useEffect(() => {
		if (turnos.error)
			mostrarError(
				esApiError(turnos.error)
					? turnos.error.mensajeParaUsuario()
					: String(turnos.error),
			);
	}, [turnos.error, mostrarError]);
	useEffect(() => {
		if (cortes.error)
			mostrarError(
				esApiError(cortes.error)
					? cortes.error.mensajeParaUsuario()
					: String(cortes.error),
			);
	}, [cortes.error, mostrarError]);
	useEffect(() => {
		if (movimientos.error)
			mostrarError(
				esApiError(movimientos.error)
					? movimientos.error.mensajeParaUsuario()
					: String(movimientos.error),
			);
	}, [movimientos.error, mostrarError]);
	useEffect(() => {
		if (esperado.error)
			mostrarError(
				esApiError(esperado.error)
					? esperado.error.mensajeParaUsuario()
					: String(esperado.error),
			);
	}, [esperado.error, mostrarError]);

	const invalidarTurnos = () => {
		queryClient.invalidateQueries({ queryKey: ["turnos"] });
		queryClient.invalidateQueries({ queryKey: ["movimientos-turno"] });
		queryClient.invalidateQueries({ queryKey: ["cortes"] });
	};

	const abrir = useMutation({
		mutationFn: (monto: number) => apiAbrirTurno(Number(cajaId), monto),
		onSuccess: () => {
			mostrarExito("Turno abierto.");
			setAbrirAbierto(false);
			invalidarTurnos();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const registrar = useMutation({
		mutationFn: (body: MovimientoCajaRequest) =>
			apiRegistrarMovimiento(
				turnoSeleccionado!.cajaId,
				turnoSeleccionado!.turnoCajaId,
				body,
			),
		onSuccess: () => {
			mostrarExito("Movimiento registrado.");
			setMov({ tipo: "SALIDA", concepto: "GASTO_OPERATIVO", monto: "" });
			invalidarTurnos();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const cerrar = useMutation({
		mutationFn: (body: CorteRequest) =>
			apiCerrarTurno(
				turnoSeleccionado!.cajaId,
				turnoSeleccionado!.turnoCajaId,
				body,
			),
		onSuccess: () => {
			mostrarExito("Corte de caja realizado.");
			setCerrarAbierto(false);
			setTurnoSeleccionado(null);
			invalidarTurnos();
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	return (
		<div className="space-y-4">
			<header className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h1 className="text-xl font-bold text-ink">Caja</h1>
					<p className="text-sm text-muted">
						Abre y cierra turnos de caja, registra entradas/salidas y revisa
						cortes.
					</p>
				</div>
				{puedeAdministrar && (
					<Button
						variant="secondary"
						onClick={() => navigate("/caja/cajas/admin")}
					>
						<Settings2 className="h-4 w-4" /> Administrar cajas
					</Button>
				)}
			</header>

			<Card>
				<div className="flex flex-wrap items-end gap-3">
					<Select
						label="Caja"
						value={cajaId}
						onChange={(e) => {
							setCajaId(e.target.value ? Number(e.target.value) : "");
							setPageTurnos(0);
						}}
						className="w-64"
					>
						<option value="">Selecciona…</option>
						{cajas.data?.map((c) => (
							<option key={c.cajaId} value={c.cajaId}>
								{c.nombre} · {c.almacenNombre}
							</option>
						))}
					</Select>
					<Button
						variant="secondary"
						disabled={cajaId === "" || abrir.isPending}
						onClick={() => setAbrirAbierto(true)}
					>
						<Unlock className="h-4 w-4" /> Abrir turno
					</Button>
				</div>
			</Card>

			<div className="grid gap-4 lg:grid-cols-3">
				<div className="lg:col-span-2">
					<Card titulo="Turnos">
						{cajaId === "" ? (
							<p className="py-6 text-center text-sm text-muted">
								Elige una caja para ver sus turnos.
							</p>
						) : turnos.isLoading ? (
							<Spinner />
						) : (
							turnos.data && (
								<div className="space-y-2">
									{turnos.data.data.length === 0 && (
										<p className="py-4 text-center text-sm text-muted">
											Aún no hay turnos para esta caja.
										</p>
									)}
									{turnos.data.data.map((t) => (
										<div
											key={t.turnoCajaId}
											className="rounded-md border border-line p-3"
										>
											<div className="flex flex-wrap items-center justify-between gap-2">
												<div className="text-sm">
													<span className="font-medium text-ink">
														Turno #{t.turnoCajaId}
													</span>{" "}
													<EstadoTurno estado={t.estado} />
													<p className="mt-0.5 text-xs text-muted">
														Apertura {formatoFechaHora(t.aperturaEn)} · Fondo{" "}
														{formatoMoneda(t.montoApertura)}
													</p>
												</div>
												<div className="flex gap-2">
													<Button
														size="sm"
														variant="secondary"
														onClick={() =>
															setTurnoSeleccionado(
																t === turnoSeleccionado ? null : t,
															)
														}
													>
														{t === turnoSeleccionado
															? "Ocultar"
															: "Movimientos"}
													</Button>
													{t.estado === "ABIERTO" && (
														<Button
															size="sm"
															variant="danger"
															onClick={() => {
																setTurnoSeleccionado(t);
																setCerrarAbierto(true);
															}}
														>
															<Lock className="h-4 w-4" /> Cerrar
														</Button>
													)}
												</div>
											</div>

											{turnoSeleccionado?.turnoCajaId === t.turnoCajaId && (
												<div className="mt-3 border-t border-line pt-3">
													{movimientos.isLoading && <Spinner />}
													{movimientos.data && (
														<ul className="space-y-1 text-sm">
															{movimientos.data.length === 0 && (
																<li className="text-xs text-muted">
																	Sin movimientos registrados.
																</li>
															)}
															{movimientos.data.map((m) => (
																<li
																	key={m.movimientoId}
																	className="flex items-center justify-between gap-2"
																>
																	<span className="flex min-w-0 items-center gap-1.5">
																		{m.tipo === "ENTRADA" ? (
																			<ArrowUpCircle className="h-4 w-4 shrink-0 text-green-600" />
																		) : (
																			<ArrowDownCircle className="h-4 w-4 shrink-0 text-red-600" />
																		)}
																		<span className="truncate">
																			{m.concepto}
																			{m.refDescripcion && (
																				<span className="text-xs text-muted">
																					{" "}
																					· {m.refDescripcion}
																				</span>
																			)}
																			{m.formaPagoNombre && (
																				<span className="text-xs text-muted">
																					{" "}
																					· {m.formaPagoNombre}
																				</span>
																			)}
																		</span>
																	</span>
																	<span
																		className={`shrink-0 font-medium tabular-nums ${m.tipo === "ENTRADA" ? "text-green-700" : "text-red-700"}`}
																	>
																		{m.tipo === "ENTRADA" ? "+" : "−"}{" "}
																		{formatoMoneda(m.monto)}
																	</span>
																</li>
															))}
														</ul>
													)}

													{t.estado === "ABIERTO" && (
														<div className="mt-3 grid grid-cols-1 gap-2 rounded-md bg-canvas p-3 sm:grid-cols-4">
															<Select
																label="Tipo"
																value={mov.tipo}
																onChange={(e) =>
																	setMov((m) => ({
																		...m,
																		tipo: e.target.value,
																		concepto:
																			CONCEPTOS_MANUALES[e.target.value][0],
																	}))
																}
															>
																<option value="SALIDA">Salida</option>
																<option value="ENTRADA">Entrada</option>
															</Select>
															<Select
																label="Concepto"
																value={mov.concepto}
																onChange={(e) =>
																	setMov((m) => ({
																		...m,
																		concepto: e.target.value,
																	}))
																}
															>
																{CONCEPTOS_MANUALES[mov.tipo]?.map((c) => (
																	<option key={c} value={c}>
																		{ETIQUETA_CONCEPTO[c]}
																	</option>
																))}
															</Select>
															<Input
																label="Monto"
																type="number"
																inputMode="decimal"
																step="0.01"
																value={mov.monto}
																onChange={(e) =>
																	setMov((m) => ({
																		...m,
																		monto: e.target.value,
																	}))
																}
															/>
															<div className="flex items-end">
																<Button
																	variant="secondary"
																	disabled={
																		registrar.isPending ||
																		mov.concepto.trim() === "" ||
																		Number(mov.monto) <= 0
																	}
																	onClick={() =>
																		registrar.mutate({
																			tipo: mov.tipo,
																			concepto: mov.concepto.trim(),
																			monto: Number(mov.monto),
																		})
																	}
																>
																	Registrar
																</Button>
															</div>
														</div>
													)}
												</div>
											)}
										</div>
									))}
									<Pagination meta={turnos.data.meta} onPage={setPageTurnos} />
								</div>
							)
						)}
					</Card>
				</div>

				<div>
					<Card titulo="Cortes de caja">
						<div className="mb-3 grid grid-cols-1 gap-2 sm:grid-cols-[1fr_1fr_auto]">
							<Input
								label="Desde"
								type="date"
								value={desde}
								onChange={(e) => {
									setDesde(e.target.value);
									setPageCortes(0);
								}}
							/>
							<Input
								label="Hasta"
								type="date"
								value={hasta}
								onChange={(e) => {
									setHasta(e.target.value);
									setPageCortes(0);
								}}
							/>
							<div className="flex items-end gap-2">
								<Button
									variant="secondary"
									size="sm"
									onClick={() => {
										setDesde(hoyLocal());
										setHasta(hoyLocal());
										setPageCortes(0);
									}}
								>
									Hoy
								</Button>
								<Button
									variant="secondary"
									size="sm"
									disabled={!cortes.data || cortes.data.data.length === 0}
									onClick={() => setGraficaAbierta(true)}
									aria-label="Ver gráfica de ventas por corte"
									title="Ver gráfica de ventas por corte"
								>
									<BarChart3 className="h-4 w-4" /> Gráfica
								</Button>
							</div>
						</div>
						{cortes.isLoading ? (
							<Spinner />
						) : (
							cortes.data && (
								<div className="space-y-3">
									{cortes.data.data.length === 0 && (
										<p className="py-4 text-center text-sm text-muted">
											Sin cortes para el rango seleccionado.
										</p>
									)}
									{cortes.data.data.map((c) => (
										<ResumenCorte key={c.corteId} corte={c} />
									))}
									<Pagination meta={cortes.data.meta} onPage={setPageCortes} />
								</div>
							)
						)}
					</Card>
					<Card titulo="Resumen de caja" className="mt-4">
						<p className="text-sm text-muted">
							Puedes ver el detalle financiero completo de cada corte en la
							sección de cortes recientes.
						</p>
					</Card>
				</div>
			</div>

			<Dialog
				open={abrirAbierto}
				onClose={() => setAbrirAbierto(false)}
				title="Abrir turno de caja"
			>
				<div className="space-y-3">
					<Input
						label="Fondo de apertura"
						type="number"
						inputMode="decimal"
						step="0.01"
						min={0}
						value={montoApertura}
						onChange={(e) => setMontoApertura(e.target.value)}
					/>
					<div className="flex justify-end gap-2">
						<Button variant="ghost" onClick={() => setAbrirAbierto(false)}>
							Cancelar
						</Button>
						<Button
							disabled={Number(montoApertura) < 0 || abrir.isPending}
							onClick={() => abrir.mutate(Number(montoApertura) || 0)}
						>
							<Banknote className="h-4 w-4" /> Abrir
						</Button>
					</div>
				</div>
			</Dialog>

			<Dialog
				open={cerrarAbierto}
				onClose={() => setCerrarAbierto(false)}
				title="Cerrar turno y generar corte"
			>
				<div className="space-y-3">
					<div>
						<div className="mb-1 flex items-center justify-between">
							<span className="text-sm font-medium text-ink">
								Monto esperado en caja
							</span>
							{esperado.isLoading && <Spinner />}
							{esperado.data && (
								<span className="font-semibold tabular-nums text-ink">
									{formatoMoneda(esperado.data.esperado)}
								</span>
							)}
						</div>
						{esperado.data && (
							<div className="flex flex-col gap-0.5 rounded-md bg-canvas px-2 py-1.5 text-xs text-muted">
								<span>
									Fondo de apertura:{" "}
									{formatoMoneda(esperado.data.montoApertura)}
								</span>
								<span>
									Entradas en efectivo: +
									{formatoMoneda(esperado.data.entradasEfectivo)}
								</span>
								<span>
									Salidas en efectivo: −
									{formatoMoneda(esperado.data.salidasEfectivo)}
								</span>
							</div>
						)}
						<p className="mt-1 text-xs text-muted">
							Valida que tu conteo físico coincida con este monto antes de
							registrar.
						</p>
					</div>
					<Input
						label="Dinero contado en caja"
						type="number"
						inputMode="decimal"
						step="0.01"
						min={0}
						value={montoContado}
						onChange={(e) => setMontoContado(e.target.value)}
					/>
					<Input
						label="Observaciones (opcional)"
						value={observaciones}
						onChange={(e) => setObservaciones(e.target.value)}
					/>
					<div className="flex justify-end gap-2">
						<Button variant="ghost" onClick={() => setCerrarAbierto(false)}>
							Cancelar
						</Button>
						<Button
							variant="danger"
							disabled={
								montoContado === "" ||
								Number(montoContado) < 0 ||
								cerrar.isPending
							}
							onClick={() =>
								cerrar.mutate({
									montoContado: Number(montoContado),
									observaciones: observaciones.trim() || undefined,
								})
							}
						>
							<Lock className="h-4 w-4" /> Cerrar y cortar
						</Button>
					</div>
				</div>
			</Dialog>

			<Dialog
				open={graficaAbierta}
				onClose={() => setGraficaAbierta(false)}
				title="Ventas por cierre de caja"
				width="max-w-3xl"
			>
				{cortes.data && <GraficaCortes cortes={cortes.data.data} />}
			</Dialog>
		</div>
	);
}
