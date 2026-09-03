// Tipos Request/Response por catálogo individual. Reflejan los records DTO del
// backend (CatalogoDtos). El CRUD genérico traduce con catalogoEndpointMap.ts
// entre el descriptor (snake_case) y estos DTOs (camelCase).

// ── cat.estados ────────────────────────────────────────────────
export interface EstadoRequest {
	claveInegi: string;
	nombre: string;
}
export interface EstadoResponse {
	estadoId: number;
	claveInegi: string;
	nombre: string;
}

// ── cat.ciudades ───────────────────────────────────────────────
export interface CiudadRequest {
	estadoId: number;
	nombre: string;
}
export interface CiudadResponse {
	ciudadId: number;
	estadoId: number;
	estadoNombre: string;
	nombre: string;
}

// ── cat.puestos ────────────────────────────────────────────────
export interface PuestoRequest {
	nombre: string;
	sueldoBase?: number;
}
export interface PuestoResponse {
	puestoId: number;
	nombre: string;
	sueldoBase: number;
	activo: boolean;
}

// ── cat.motivos_movimiento ─────────────────────────────────────
export interface MotivoMovimientoRequest {
	clave: string;
	nombre: string;
	tipoDefault: "ENTRADA" | "SALIDA";
}
export interface MotivoMovimientoResponse {
	motivoId: number;
	clave: string;
	nombre: string;
	tipoDefault: string;
	activo: boolean;
}

// ── cat.tipos_gasto ────────────────────────────────────────────
export interface TipoGastoRequest {
	clave: string;
	nombre: string;
	esFijo?: boolean;
}
export interface TipoGastoResponse {
	tipoGastoId: number;
	clave: string;
	nombre: string;
	esFijo: boolean;
	activo: boolean;
}

// ── cat.formas_pago ────────────────────────────────────────────
export interface FormaPagoRequest {
	clave: string;
	nombre: string;
	esEfectivo?: boolean;
	requiereReferencia?: boolean;
	afectaCaja?: boolean;
	formaPagoSatClave?: string;
	comisionPct?: number;
}
export interface FormaPagoResponse {
	formaPagoId: number;
	clave: string;
	nombre: string;
	esEfectivo: boolean;
	requiereReferencia: boolean;
	afectaCaja: boolean;
	formaPagoSatClave?: string;
	comisionPct: number;
	activo: boolean;
}

// ── fis.impuestos ──────────────────────────────────────────────
export interface ImpuestoRequest {
	claveSat: string;
	nombre: string;
	tipo: "TRASLADADO" | "RETENIDO" | "LOCAL";
}
export interface ImpuestoResponse {
	impuestoId: number;
	claveSat: string;
	nombre: string;
	tipo: string;
	activo: boolean;
}

// ── fis.tasas_impuesto ─────────────────────────────────────────
export interface TasaImpuestoRequest {
	impuestoId: number;
	tasa: number;
	factor: "TASA" | "CUOTA" | "EXENTO";
	ambito: "VENTA" | "COMPRA" | "NOMINA";
	zonaFrontera?: boolean;
	vigenteDesde?: string;
	vigenteHasta?: string;
}
export interface TasaImpuestoResponse {
	tasaId: number;
	impuestoId: number;
	impuestoNombre: string;
	tasa: number;
	factor: string;
	ambito: string;
	zonaFrontera: boolean;
	vigenteDesde?: string;
	vigenteHasta?: string;
	activo: boolean;
}

// ── fis.regimenes_fiscales ─────────────────────────────────────
export interface RegimenFiscalRequest {
	claveSat: string;
	descripcion: string;
	personaFisica?: boolean;
	personaMoral?: boolean;
}
export interface RegimenFiscalResponse {
	claveSat: string;
	descripcion: string;
	personaFisica: boolean;
	personaMoral: boolean;
	activo: boolean;
}

// ── fis.usos_cfdi ──────────────────────────────────────────────
export interface UsoCfdiRequest {
	clave: string;
	descripcion: string;
	aplicaFisica?: boolean;
	aplicaMoral?: boolean;
}
export interface UsoCfdiResponse {
	clave: string;
	descripcion: string;
	aplicaFisica: boolean;
	aplicaMoral: boolean;
	activo: boolean;
}

// ── fis.formas_pago_sat ────────────────────────────────────────
export interface FormaPagoSatRequest {
	clave: string;
	descripcion: string;
}
export interface FormaPagoSatResponse {
	clave: string;
	descripcion: string;
	activo: boolean;
}

// ── fis.metodos_pago_sat ───────────────────────────────────────
export interface MetodoPagoSatRequest {
	clave: string;
	descripcion: string;
}
export interface MetodoPagoSatResponse {
	clave: string;
	descripcion: string;
	activo: boolean;
}

// ── fis.unidades_sat ───────────────────────────────────────────
export interface UnidadSatRequest {
	clave: string;
	descripcion: string;
}
export interface UnidadSatResponse {
	clave: string;
	descripcion: string;
	activo: boolean;
}

// ── fis.claves_prod_serv ───────────────────────────────────────
export interface ClaveProdServRequest {
	clave: string;
	descripcion: string;
	incluyeIva?: boolean;
	ejemplo?: boolean;
}
export interface ClaveProdServResponse {
	clave: string;
	descripcion: string;
	incluyeIva?: boolean;
	ejemplo: boolean;
}

// ── cfg.configuracion ──────────────────────────────────────────
export interface ConfiguracionRequest {
	clave: string;
	valor: string;
	descripcion?: string;
}
export interface ConfiguracionResponse {
	clave: string;
	valor: string;
	descripcion?: string;
}

// ── cfg.folios ─────────────────────────────────────────────────
export interface FolioRequest {
	tipo: string;
	prefijo: string;
	consecutivo?: number;
}
export interface FolioResponse {
	tipo: string;
	prefijo: string;
	consecutivo: number;
}
