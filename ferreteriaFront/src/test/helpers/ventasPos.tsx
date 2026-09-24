import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";

import { ToastProvider } from "@/components/ui/Toast";
import type {
	Almacen,
	Caja,
	Cliente,
	CuentaCobrar,
	Cotizacion,
	PageEnvelope,
	Producto,
	Renta,
	TurnoCaja,
	Venta,
} from "@/lib/api/types";

/** Envuelve una página con lo que todas necesitan: router, React Query y Toast. */
export function renderPagina(ui: ReactNode) {
	localStorage.clear();
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false } },
	});
	return render(
		<MemoryRouter>
			<QueryClientProvider client={client}>
				<ToastProvider>{ui}</ToastProvider>
			</QueryClientProvider>
		</MemoryRouter>,
	);
}

export function pageOf<T>(data: T[]): PageEnvelope<T> {
	return {
		success: true,
		data,
		meta: { page: 0, size: 20, totalElements: data.length, totalPages: 1 },
	};
}

export const ALMACEN: Almacen = {
	almacenId: 1,
	nombre: "Matriz",
	direccion: null,
	telefono: null,
	esPuntoVenta: true,
	activo: true,
};

export const CAJA: Caja = {
	cajaId: 1,
	nombre: "Caja 1",
	almacenId: 1,
	almacenNombre: "Matriz",
	activa: true,
};

export const TURNO: TurnoCaja = {
	turnoCajaId: 5,
	cajaId: 1,
	cajaNombre: "Caja 1",
	usuarioId: 1,
	aperturaEn: "2026-01-10T08:00:00",
	montoApertura: 500,
	cierreEn: null,
	montoEsperado: null,
	montoContado: null,
	diferencia: null,
	estado: "ABIERTO",
	observaciones: null,
};

export const CLIENTE: Cliente = {
	clienteId: 1,
	tipoPersona: "FISICA",
	razonSocial: "Juan Pérez",
	nombreComercial: null,
	rfc: null,
	curp: null,
	regimenFiscal: null,
	telefono: null,
	whatsapp: null,
	email: null,
	calle: null,
	colonia: null,
	ciudadId: null,
	ciudadNombre: null,
	cp: null,
	limiteCredito: null,
	diasCredito: null,
	esMayorista: false,
	activo: true,
};

export const PRODUCTO: Producto = {
	productoId: 10,
	codigo: "MAR-001",
	tipo: "PRODUCTO",
	nombre: "Martillo",
	descripcion: null,
	categoriaId: 1,
	categoriaNombre: "Herramientas",
	marcaId: null,
	marcaNombre: null,
	unidadMedidaId: 1,
	unidadMedidaClave: "PZA",
	costoActual: 30,
	precioMenudeo: 50,
	precioMayoreo: null,
	aplicaIva: true,
	stockActual: 100,
	imagenUrl: null,
	codigosBarras: [],
};

export const VENTA: Venta = {
	ventaId: 1,
	folio: "V-0001",
	clienteId: null,
	clienteNombre: "Juan Pérez",
	cliente: null,
	almacenId: 1,
	almacenNombre: "Matriz",
	fecha: "2026-01-10T12:00:00",
	fechaLocal: "2026-01-10",
	formaPagoId: 1,
	formaPagoNombre: "Efectivo",
	ivaTasa: 0.16,
	ivaIncluido: true,
	subtotal: 100,
	iva: 16,
	descuentoTotal: 0,
	total: 116,
	estado: "COMPLETADA",
	usuarioId: 1,
	turnoCajaId: 1,
	notas: null,
	detalles: [
		{
			ventaDetalleId: 1,
			productoId: 10,
			productoNombre: "Martillo",
			cantidad: 2,
			precioUnitario: 50,
			costoUnitario: 30,
			descuentoLinea: 0,
			totalLinea: 100,
			promocionId: null,
		},
	],
	pagos: [
		{
			pagoClienteId: 1,
			formaPagoId: 1,
			referencia: null,
			monto: 116,
			fecha: "2026-01-10",
		},
	],
};

export const CUENTA: CuentaCobrar = {
	cuentaCobrarId: 1,
	ventaId: 1,
	ventaFolio: "V-0001",
	clienteId: 1,
	clienteNombre: "Juan Pérez",
	montoTotal: 116,
	montoPagado: 0,
	saldo: 116,
	fechaVencimiento: "2099-01-10",
	estado: "VIGENTE",
	creadoEn: "2026-01-10T12:00:00",
	pagos: [],
};

export const COTIZACION: Cotizacion = {
	cotizacionId: 1,
	folio: "COT-001",
	clienteId: null,
	clienteNombre: null,
	fecha: "2026-01-10T12:00:00",
	vigenciaHasta: "2026-01-20",
	subtotal: 100,
	iva: 16,
	total: 116,
	estado: "VIGENTE",
	ventaGeneradaId: null,
	usuarioId: 1,
	detalles: [
		{
			productoId: 10,
			productoNombre: "Martillo",
			cantidad: 2,
			precioUnitario: 50,
			importeLinea: 100,
		},
	],
};

export const RENTA: Renta = {
	rentaId: 7,
	folio: "R-001",
	clienteId: 1,
	clienteNombre: "Juan Pérez",
	almacenId: 1,
	almacenNombre: "Matriz",
	fechaRenta: "2026-01-10T12:00:00",
	fechaDevEsperada: "2026-01-13",
	fechaDevReal: null,
	deposito: 200,
	costoTotal: 150,
	formaPagoId: 1,
	turnoCajaId: 1,
	estado: "ABIERTA",
	usuarioId: 1,
	detalles: [
		{
			productoId: 10,
			productoNombre: "Martillo",
			cantidad: 1,
			costoDia: 150,
			diasCobrados: 0,
			subtotal: 150,
		},
	],
};
