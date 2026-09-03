import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, FileCode2 } from "lucide-react";

import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { esApiError } from "@/lib/api/client";
import { apiCrearFactura, apiFacturaXml, apiFacturas } from "@/lib/api/fis";
import type { FacturaFis, FacturaFisRequest } from "@/lib/api/types";
import { formatoFechaHora, formatoMoneda } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DataTable, type Columna } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";

const truncarUuid = (uuid: string, limite = 24) =>
	uuid.length <= limite ? uuid : `${uuid.slice(0, limite)}…`;

function NuevaFacturaForm({
	guardando,
	onGuardar,
	onClose,
}: {
	guardando: boolean;
	onGuardar: (body: FacturaFisRequest) => void;
	onClose: () => void;
}) {
	const [tipo, setTipo] = useState("EMITIDA");
	const [folio, setFolio] = useState("");
	const [serie, setSerie] = useState("");
	const [uuid, setUuid] = useState("");
	const [emisorRfc, setEmisorRfc] = useState("");
	const [receptorRfc, setReceptorRfc] = useState("");
	const [subtotal, setSubtotal] = useState("");
	const [iva, setIva] = useState("");

	const enviar = (e: { preventDefault: () => void }) => {
		e.preventDefault();
		onGuardar({
			tipo,
			folio: folio.trim(),
			serie: serie.trim() || undefined,
			uuid: uuid.trim() || undefined,
			emisorRfc: emisorRfc.trim().toUpperCase(),
			receptorRfc: receptorRfc.trim().toUpperCase(),
			subtotal: Number(subtotal),
			iva: Number(iva),
		});
	};

	return (
		<form onSubmit={enviar} className="space-y-3" noValidate>
			<div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
				<Select
					label="Tipo"
					required
					value={tipo}
					onChange={(e) => setTipo(e.target.value)}
				>
					<option value="EMITIDA">EMITIDA</option>
					<option value="RECIBIDA">RECIBIDA</option>
				</Select>
				<Input
					label="Folio"
					required
					type="text"
					value={folio}
					onChange={(e) => setFolio(e.target.value)}
				/>
				<Input
					label="Serie"
					type="text"
					value={serie}
					onChange={(e) => setSerie(e.target.value)}
				/>
				<Input
					label="UUID"
					type="text"
					value={uuid}
					onChange={(e) => setUuid(e.target.value)}
				/>
				<Input
					label="RFC emisor"
					required
					type="text"
					value={emisorRfc}
					onChange={(e) => setEmisorRfc(e.target.value)}
					placeholder="XAXX010101000"
				/>
				<Input
					label="RFC receptor"
					required
					type="text"
					value={receptorRfc}
					onChange={(e) => setReceptorRfc(e.target.value)}
					placeholder="XAXX010101000"
				/>
				<Input
					label="Subtotal"
					required
					type="number"
					inputMode="decimal"
					min="0"
					step="0.01"
					value={subtotal}
					onChange={(e) => setSubtotal(e.target.value)}
				/>
				<Input
					label="IVA"
					required
					type="number"
					inputMode="decimal"
					min="0"
					step="0.01"
					value={iva}
					onChange={(e) => setIva(e.target.value)}
				/>
			</div>
			<div className="flex justify-end gap-2">
				<Button type="button" variant="ghost" onClick={onClose}>
					Cancelar
				</Button>
				<Button type="submit" disabled={guardando}>
					{guardando ? "Guardando…" : "Crear factura"}
				</Button>
			</div>
		</form>
	);
}

export default function FacturasPage() {
	useDocumentTitle("Facturas CFDI");
	const { error: mostrarError, success: mostrarExito } = useToast();
	const queryClient = useQueryClient();

	const [tipo, setTipo] = useState("");
	const [page, setPage] = useState(0);
	const [creando, setCreando] = useState(false);
	const [verXml, setVerXml] = useState<FacturaFis | null>(null);

	const { data, isLoading, error, isFetching } = useQuery({
		queryKey: ["facturas", tipo, page],
		queryFn: () =>
			apiFacturas({
				tipo: tipo || undefined,
				page,
				size: 15,
			}),
	});

	const { data: xmlData, isFetching: cargandoXml } = useQuery({
		queryKey: ["facturas-xml", verXml?.facturaId],
		queryFn: () => apiFacturaXml(verXml!.facturaId),
		enabled: verXml !== null,
	});

	useEffect(() => {
		if (error)
			mostrarError(
				esApiError(error) ? error.mensajeParaUsuario() : String(error),
			);
	}, [error, mostrarError]);

	const crear = useMutation({
		mutationFn: (body: FacturaFisRequest) => apiCrearFactura(body),
		onSuccess: () => {
			mostrarExito("Factura creada correctamente.");
			setCreando(false);
			setPage(0);
			queryClient.invalidateQueries({ queryKey: ["facturas"] });
		},
		onError: (err) =>
			mostrarError(esApiError(err) ? err.mensajeParaUsuario() : String(err)),
	});

	const columnas: Columna<FacturaFis>[] = [
		{
			key: "tipo",
			header: "Tipo",
			render: (v) =>
				v.tipo === "EMITIDA" ? (
					<Badge tone="success">EMITIDA</Badge>
				) : (
					<Badge tone="info">RECIBIDA</Badge>
				),
		},
		{
			key: "folio",
			header: "Folio",
			render: (v) => <span className="font-medium text-ink">{v.folio}</span>,
		},
		{ key: "serie", header: "Serie", render: (v) => v.serie ?? "—" },
		{
			key: "uuid",
			header: "UUID",
			render: (v) =>
				v.uuid ? (
					<span className="text-muted" title={v.uuid}>
						{truncarUuid(v.uuid)}
					</span>
				) : (
					"—"
				),
		},
		{ key: "emisor", header: "RFC emisor", render: (v) => v.emisorRfc },
		{ key: "receptor", header: "RFC receptor", render: (v) => v.receptorRfc },
		{
			key: "subtotal",
			header: "Subtotal",
			align: "right",
			render: (v) => (
				<span className="tabular-nums">{formatoMoneda(v.subtotal)}</span>
			),
		},
		{
			key: "iva",
			header: "IVA",
			align: "right",
			render: (v) => (
				<span className="tabular-nums">{formatoMoneda(v.iva)}</span>
			),
		},
		{
			key: "total",
			header: "Total",
			align: "right",
			render: (v) => (
				<span className="font-medium tabular-nums">
					{formatoMoneda(v.total)}
				</span>
			),
		},
		{
			key: "fecha",
			header: "Fecha timbrado",
			render: (v) => (
				<span className="whitespace-nowrap tabular-nums">
					{formatoFechaHora(v.fechaTimbrado)}
				</span>
			),
		},
		{
			key: "estado",
			header: "Estado",
			render: (v) => (
				<Badge
					tone={
						v.estado.toUpperCase() === "CANCELADA"
							? "danger"
							: v.estado.toUpperCase() === "TIMBRADA"
								? "success"
								: "default"
					}
				>
					{v.estado}
				</Badge>
			),
		},
		{
			key: "acc",
			header: "Acciones",
			align: "right",
			render: (v) => (
				<div className="flex justify-end gap-1">
					<button
						type="button"
						aria-label="Ver XML"
						title="Ver XML"
						className="rounded p-1.5 text-muted hover:bg-primary-50 hover:text-primary"
						onClick={() => setVerXml(v)}
					>
						<FileCode2 className="h-4 w-4" />
					</button>
				</div>
			),
		},
	];

	return (
		<div className="space-y-4">
			<header className="flex items-center justify-between">
				<div>
					<h1 className="text-xl font-bold text-ink">Facturas CFDI</h1>
					<p className="text-sm text-muted">
						Facturas emitidas y recibidas con timbrado fiscal.
					</p>
				</div>
				<Button onClick={() => setCreando(true)}>
					<Plus className="h-4 w-4" /> Nueva factura
				</Button>
			</header>

			<Card>
				<div className="flex flex-wrap items-end gap-2">
					<Select
						label="Tipo"
						value={tipo}
						onChange={(e) => {
							setTipo(e.target.value);
							setPage(0);
						}}
						className="w-48"
					>
						<option value="">Todas</option>
						<option value="EMITIDA">EMITIDA</option>
						<option value="RECIBIDA">RECIBIDA</option>
					</Select>
					{tipo !== "" && (
						<Button
							variant="ghost"
							onClick={() => {
								setTipo("");
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
				<Card titulo={`Facturas (${data.meta.totalElements})`}>
					<DataTable
						columnas={columnas}
						items={data.data}
						rowKey={(v) => v.facturaId}
						loading={isFetching}
					/>
					<Pagination meta={data.meta} onPage={setPage} />
				</Card>
			)}

			<Dialog
				open={creando}
				onClose={() => !crear.isPending && setCreando(false)}
				title="Nueva factura"
				width="max-w-lg"
			>
				<NuevaFacturaForm
					guardando={crear.isPending}
					onGuardar={(body) => crear.mutate(body)}
					onClose={() => setCreando(false)}
				/>
			</Dialog>

			<Dialog
				open={verXml !== null}
				onClose={() => setVerXml(null)}
				title={verXml ? `XML · ${verXml.folio}` : ""}
				width="max-w-3xl"
			>
				{cargandoXml ? (
					<Spinner />
				) : xmlData?.cfdiXml ? (
					<pre className="max-h-96 overflow-auto whitespace-pre-wrap text-xs text-ink">
						{xmlData.cfdiXml}
					</pre>
				) : (
					<p className="py-4 text-center text-sm text-muted">
						Sin XML almacenado
					</p>
				)}
			</Dialog>
		</div>
	);
}
