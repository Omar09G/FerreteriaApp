import http from "./client";
import type {
	CierreDiario,
	Envelope,
	Inventario,
	MejorCliente,
	MejorDiaVenta,
	MejoresCategorias,
	MejorVendedor,
	MovimientoInventario,
	PageEnvelope,
	ProductosSinMovimiento,
	ResumenDashboard,
	TopProducto,
	VentaPorHora,
	VentaTotal,
} from "./types";

function rango(inicio: string, fin: string): Record<string, string> {
	return { fechaInicio: inicio, fechaFin: fin };
}

export async function apiDashboard(
	inicio: string,
	fin: string,
): Promise<ResumenDashboard> {
	const { data } = await http.get<Envelope<ResumenDashboard>>(
		"/reportes/dashboard",
		{ params: rango(inicio, fin) },
	);
	return data.data;
}

export async function apiVentasTotales(
	inicio: string,
	fin: string,
): Promise<VentaTotal[]> {
	const { data } = await http.get<Envelope<VentaTotal[]>>(
		"/reportes/ventas-totales",
		{ params: rango(inicio, fin) },
	);
	return data.data;
}

export async function apiTopProductos(
	inicio: string,
	fin: string,
): Promise<TopProducto[]> {
	const { data } = await http.get<Envelope<TopProducto[]>>(
		"/reportes/top-productos",
		{ params: rango(inicio, fin) },
	);
	return data.data;
}

export async function apiHorasPico(
	inicio: string,
	fin: string,
): Promise<VentaPorHora[]> {
	const { data } = await http.get<Envelope<VentaPorHora[]>>(
		"/reportes/horas-pico",
		{ params: rango(inicio, fin) },
	);
	return data.data;
}

export async function apiMejoresDias(
	inicio: string,
	fin: string,
): Promise<MejorDiaVenta[]> {
	const { data } = await http.get<Envelope<MejorDiaVenta[]>>(
		"/reportes/mejores-dias",
		{ params: rango(inicio, fin) },
	);
	return data.data;
}

export async function apiMejoresClientes(
	inicio: string,
	fin: string,
): Promise<MejorCliente[]> {
	const { data } = await http.get<Envelope<MejorCliente[]>>(
		"/reportes/mejores-clientes",
		{ params: rango(inicio, fin) },
	);
	return data.data;
}

export async function apiMejoresVendedores(
	inicio: string,
	fin: string,
): Promise<MejorVendedor[]> {
	const { data } = await http.get<Envelope<MejorVendedor[]>>(
		"/reportes/mejores-vendedores",
		{ params: rango(inicio, fin) },
	);
	return data.data;
}

export async function apiCierreDiario(
	inicio: string,
	fin: string,
): Promise<CierreDiario[]> {
	const { data } = await http.get<Envelope<CierreDiario[]>>(
		"/reportes/cierre-diario",
		{ params: rango(inicio, fin) },
	);
	return data.data;
}

export async function apiMejoresCategorias(): Promise<MejoresCategorias[]> {
	const { data } = await http.get<Envelope<MejoresCategorias[]>>(
		"/reportes/mejores-categorias",
	);
	return data.data;
}

export async function apiProductosSinMovimiento(): Promise<
	ProductosSinMovimiento[]
> {
	const { data } = await http.get<Envelope<ProductosSinMovimiento[]>>(
		"/reportes/productos-sin-movimiento",
	);
	return data.data;
}

export async function apiMovimientos(p: {
	inicio: string;
	fin: string;
	productoId?: number;
	almacenId?: number;
	page: number;
	size: number;
	sort?: string;
}): Promise<PageEnvelope<MovimientoInventario>> {
	const params: Record<string, string | number> = {
		fechaInicio: p.inicio,
		fechaFin: p.fin,
		page: p.page,
		size: p.size,
	};
	if (p.productoId) params.productoId = p.productoId;
	if (p.almacenId) params.almacenId = p.almacenId;
	if (p.sort) params.sort = p.sort;
	const { data } = await http.get<PageEnvelope<MovimientoInventario>>(
		"/movimientos",
		{ params },
	);
	return data;
}

export async function apiStock(p: {
	almacenId?: number;
	soloBajoStock?: boolean;
	page: number;
	size: number;
}): Promise<PageEnvelope<Inventario>> {
	const params: Record<string, string | number | boolean> = {
		page: p.page,
		size: p.size,
	};
	if (p.almacenId) params.almacenId = p.almacenId;
	if (p.soloBajoStock) params.soloBajoStock = p.soloBajoStock;
	const { data } = await http.get<PageEnvelope<Inventario>>("/inventario", {
		params,
	});
	return data;
}
