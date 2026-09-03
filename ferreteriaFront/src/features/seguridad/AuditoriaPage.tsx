import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronRight, History, Search } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useT } from "@/i18n";
import {
	apiAuditoria,
	apiTablasAuditoria,
	type AuditoriaFiltros,
} from "@/lib/api/auditoria";
import { esApiError } from "@/lib/api/client";
import type { Auditoria, AuditoriaTabla } from "@/lib/api/types";
import { formatoFechaHora } from "@/lib/format";
import { rangoFechas, type RangoFechas } from "@/lib/rango";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { DateRangePicker } from "@/components/ui/DateRangePicker";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { useToast } from "@/components/ui/Toast";

const ACCIONES = ["INSERT", "UPDATE", "DELETE"] as const;

const accionTone = (
	a: Auditoria["accion"],
): "success" | "warning" | "danger" => {
	if (a === "INSERT") return "success";
	if (a === "UPDATE") return "warning";
	return "danger";
};

export default function AuditoriaPage() {
	const t = useT();
	useDocumentTitle(t("seguridad.auditoria.titulo"));
	const { error: mostrarError } = useToast();
	const [filtros, setFiltros] = useState<AuditoriaFiltros>({
		page: 0,
		size: 20,
	});
	const [rango, setRango] = useState<RangoFechas>(rangoFechas());

	const tablas = useQuery({
		queryKey: ["auditoria-tablas"],
		queryFn: apiTablasAuditoria,
	});

	const lista = useQuery({
		queryKey: ["auditoria", filtros, rango],
		queryFn: () => {
			const params: AuditoriaFiltros = {
				...filtros,
				fechaInicio: rango.inicio,
				fechaFin: rango.fin,
			};
			return apiAuditoria(params);
		},
	});

	if (lista.error) {
		const e = lista.error;
		mostrarError(esApiError(e) ? e.mensajeParaUsuario() : String(e));
	}

	const columnas: Columna<Auditoria>[] = [
		{
			key: "cuando",
			header: t("seguridad.auditoria.columnas.fecha"),
			render: (v) => (
				<span className="tabular-nums">{formatoFechaHora(v.creadoEn)}</span>
			),
		},
		{
			key: "usuario",
			header: t("seguridad.auditoria.columnas.usuario"),
			render: (v) =>
				v.usuario ? (
					<span className="font-medium">{v.usuario}</span>
				) : (
					<span className="text-muted">—</span>
				),
		},
		{
			key: "esquema",
			header: t("seguridad.auditoria.columnas.origen"),
			render: (v) => (
				<span className="text-xs">
					<span className="text-muted">{v.esquema}.</span>
					<span className="font-medium">{v.tabla}</span>
					<span className="text-muted"> · #{v.registroId}</span>
				</span>
			),
		},
		{
			key: "accion",
			header: t("seguridad.auditoria.columnas.accion"),
			render: (v) => <Badge tone={accionTone(v.accion)}>{v.accion}</Badge>,
		},
		{
			key: "cambios",
			header: t("seguridad.auditoria.columnas.cambios"),
			render: (v) => <DetalleCambios a={v} t={t} />,
		},
	];

	const limpiar = () => {
		setFiltros({ page: 0, size: filtros.size ?? 20 });
		setRango(rangoFechas());
	};

	return (
		<div className="space-y-4">
			<div>
				<h1 className="flex items-center gap-2 text-2xl font-semibold text-ink">
					<History className="h-6 w-6 text-primary" />{" "}
					{t("seguridad.auditoria.titulo")}
				</h1>
				<p className="text-sm text-muted">
					{t("seguridad.auditoria.subtitulo")}
				</p>
			</div>

			<Card>
				<div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
					<Select
						label={t("seguridad.auditoria.filtros.esquema")}
						value={filtros.esquema ?? ""}
						onChange={(e) =>
							setFiltros((f) => ({
								...f,
								esquema: e.target.value || undefined,
								page: 0,
							}))
						}
					>
						<option value="">{t("seguridad.auditoria.filtros.todos")}</option>
						{tablas.data
							? Array.from(new Set(tablas.data.map((tt) => tt.esquema))).map(
									(s) => (
										<option key={s} value={s}>
											{s}
										</option>
									),
								)
							: null}
					</Select>
					<Select
						label={t("seguridad.auditoria.filtros.tabla")}
						value={filtros.tabla ?? ""}
						onChange={(e) =>
							setFiltros((f) => ({
								...f,
								tabla: e.target.value || undefined,
								page: 0,
							}))
						}
					>
						<option value="">{t("seguridad.auditoria.filtros.todas")}</option>
						{(tablas.data ?? [])
							.filter(
								(tt: AuditoriaTabla) =>
									!filtros.esquema || tt.esquema === filtros.esquema,
							)
							.map((tt) => (
								<option key={`${tt.esquema}.${tt.tabla}`} value={tt.tabla}>
									{tt.tabla}
								</option>
							))}
					</Select>
					<Select
						label={t("seguridad.auditoria.filtros.accion")}
						value={filtros.accion ?? ""}
						onChange={(e) =>
							setFiltros((f) => ({
								...f,
								accion: e.target.value || undefined,
								page: 0,
							}))
						}
					>
						<option value="">
							{t("seguridad.auditoria.filtros.todasAcciones")}
						</option>
						{ACCIONES.map((a) => (
							<option key={a} value={a}>
								{a}
							</option>
						))}
					</Select>

					<Input
						label={t("seguridad.auditoria.filtros.usuario")}
						placeholder={t("seguridad.auditoria.filtros.placeholderUsuario")}
						value={filtros.usuario ?? ""}
						onChange={(e) =>
							setFiltros((f) => ({ ...f, usuario: e.target.value, page: 0 }))
						}
					/>
					<Input
						label={t("seguridad.auditoria.filtros.registro")}
						type="number"
						value={filtros.registroId ?? ""}
						onChange={(e) =>
							setFiltros((f) => ({
								...f,
								registroId:
									e.target.value === "" ? undefined : Number(e.target.value),
								page: 0,
							}))
						}
					/>
					<Input
						label={t("seguridad.auditoria.filtros.texto")}
						placeholder={t("seguridad.auditoria.filtros.placeholderTexto")}
						value={filtros.texto ?? ""}
						onChange={(e) =>
							setFiltros((f) => ({ ...f, texto: e.target.value, page: 0 }))
						}
					/>

					<div className="sm:col-span-2">
						<span className="text-xs font-medium text-muted">
							{t("seguridad.auditoria.filtros.rango")}
						</span>
						<div className="mt-1">
							<DateRangePicker valor={rango} onChange={setRango} />
						</div>
					</div>

					<div className="flex items-end">
						<Button variant="ghost" onClick={limpiar}>
							{t("seguridad.auditoria.filtros.limpiar")}
						</Button>
					</div>
				</div>
			</Card>

			<DataTable
				columnas={columnas}
				items={lista.data?.data}
				loading={lista.isLoading}
				rowKey={(v) => v.auditoriaId}
				emptyTitle={t("seguridad.auditoria.sinResultados")}
				emptyDescripcion={t("seguridad.auditoria.sinResultadosDesc")}
			/>

			{lista.data && (
				<Pagination
					meta={lista.data.meta}
					onPage={(p) => setFiltros((f) => ({ ...f, page: p }))}
				/>
			)}
		</div>
	);
}

function DetalleCambios({
	a,
	t,
}: {
	a: Auditoria;
	t: ReturnType<typeof useT>;
}) {
	const [abierto, setAbierto] = useState(false);
	return (
		<div className="text-xs">
			<button
				type="button"
				onClick={() => setAbierto((b) => !b)}
				className="inline-flex items-center gap-1 rounded border border-line px-2 py-0.5 text-muted hover:bg-primary-50 hover:text-primary"
			>
				{abierto ? (
					<ChevronDown className="h-3 w-3" />
				) : (
					<ChevronRight className="h-3 w-3" />
				)}
				<Search className="h-3 w-3" />{" "}
				{abierto
					? t("seguridad.auditoria.botones.ocultar")
					: t("seguridad.auditoria.botones.ver")}
			</button>
			{abierto && (
				<div className="mt-2 max-w-md space-y-2">
					<div>
						<div className="text-[10px] uppercase text-muted">
							{t("seguridad.auditoria.campos.anterior")}
						</div>
						<pre className="overflow-x-auto rounded bg-slate-900 px-2 py-1 text-[11px] text-slate-100">
							{a.datosAnteriores ?? "—"}
						</pre>
					</div>
					<div>
						<div className="text-[10px] uppercase text-muted">
							{t("seguridad.auditoria.campos.nuevo")}
						</div>
						<pre className="overflow-x-auto rounded bg-slate-900 px-2 py-1 text-[11px] text-slate-100">
							{a.datosNuevos ?? "—"}
						</pre>
					</div>
				</div>
			)}
		</div>
	);
}
