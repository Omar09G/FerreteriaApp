import http from "./client";
import type {
	AbonoProveedorRequest,
	AbonoProveedorResponse,
	Compra,
	CompraRequest,
	CuentasPagar,
	CuentasPagarInformeEnvio,
	CuentasPagarInformeEstado,
	Envelope,
	FacturaPendiente,
	FacturaVencida,
	PageEnvelope,
} from "./types";

export async function apiCompras(p: {
	almacenId?: number;
	proveedorId?: number;
	desde?: string;
	hasta?: string;
	page: number;
	size: number;
}): Promise<PageEnvelope<Compra>> {
	const params: Record<string, string | number> = {
		page: p.page,
		size: p.size,
	};
	if (p.almacenId) params.almacenId = p.almacenId;
	if (p.proveedorId) params.proveedorId = p.proveedorId;
	if (p.desde) params.desde = p.desde;
	if (p.hasta) params.hasta = p.hasta;
	const { data } = await http.get<PageEnvelope<Compra>>("/compras", { params });
	return data;
}

export async function apiCrearCompra(body: CompraRequest): Promise<Compra> {
	const { data } = await http.post<Envelope<Compra>>("/compras", body);
	return data.data;
}

export async function apiCuentasPagar(): Promise<CuentasPagar[]> {
	const { data } = await http.get<Envelope<CuentasPagar[]>>("/cuentas-pagar");
	return data.data;
}

export async function apiFacturasPendientes(): Promise<FacturaPendiente[]> {
	const { data } = await http.get<Envelope<FacturaPendiente[]>>(
		"/reportes/facturas-pendientes",
	);
	return data.data;
}

export async function apiFacturasVencidas(): Promise<FacturaVencida[]> {
	const { data } = await http.get<Envelope<FacturaVencida[]>>(
		"/reportes/facturas-vencidas",
	);
	return data.data;
}

export async function apiAbonarCuentaPagar(
	cuentaPagarId: number,
	body: AbonoProveedorRequest,
): Promise<AbonoProveedorResponse> {
	const { data } = await http.post<Envelope<AbonoProveedorResponse>>(
		`/cuentas-pagar/${cuentaPagarId}/abonos`,
		body,
	);
	return data.data;
}

/** Recordatorio manual de cuentas por pagar (mismo contenido del JOB 09:00). */
export async function apiEnviarCuentasPagarInforme(): Promise<CuentasPagarInformeEnvio> {
	const { data } = await http.post<Envelope<CuentasPagarInformeEnvio>>(
		"/reportes/cuentas-pagar/informe",
		{},
	);
	return data.data;
}

/** Estado del recordatorio de hoy (avisa si ya se envió). */
export async function apiEstadoCuentasPagarInforme(): Promise<CuentasPagarInformeEstado> {
	const { data } = await http.get<Envelope<CuentasPagarInformeEstado>>(
		"/reportes/cuentas-pagar/informe/estado",
	);
	return data.data;
}
