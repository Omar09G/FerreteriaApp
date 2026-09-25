import { describe, expect, it } from "vitest";

import {
	catalogoEndpoint,
	catalogoEndpointDe,
	clavePayloadADTO,
	dtoFilaAClave,
} from "@/features/catalogo/catalogoEndpointMap";

describe("catalogoEndpoint", () => {
	it("resuelve el endpoint de un catálogo conocido", () => {
		expect(catalogoEndpoint("estados")?.path).toBe("/estados");
		expect(catalogoEndpoint("motivos_movimiento")?.path).toBe(
			"/motivos-movimiento",
		);
		expect(catalogoEndpoint("tasas_impuesto")?.pkDTO).toBe("tasaId");
	});

	it("devuelve undefined con clave desconocida", () => {
		expect(catalogoEndpoint("no_existe")).toBeUndefined();
	});

	it("acceso tipado devuelve el mismo descriptor", () => {
		expect(catalogoEndpointDe("ciudades").pkDTO).toBe("ciudadId");
		expect(catalogoEndpointDe("folios").path).toBe("/folios");
	});
});

describe("dtoFilaAClave", () => {
	it("traduce DTO camelCase a claves descriptor + __pk (estados)", () => {
		const fila = dtoFilaAClave("estados", {
			estadoId: 7,
			claveInegi: "14",
			nombre: "Jalisco",
			ignorado: "x",
		});
		expect(fila).toMatchObject({
			__pk: 7,
			estado_id: 7,
			clave_inegi: "14",
			nombre: "Jalisco",
		});
		expect(fila).not.toHaveProperty("ignorado");
	});

	it("mapea campos extra del DTO (ciudades trae estadoNombre)", () => {
		const fila = dtoFilaAClave("ciudades", {
			ciudadId: 3,
			estadoId: 14,
			estadoNombre: "Jalisco",
			nombre: "Guadalajara",
		});
		expect(fila).toMatchObject({
			__pk: 3,
			ciudad_id: 3,
			estado_id: 14,
			estado_nombre: "Jalisco",
			nombre: "Guadalajara",
		});
	});

	it("soporta PK string (regímenes, usos cfdi, folios, configuración)", () => {
		expect(
			dtoFilaAClave("regimenes_fiscales", {
				claveSat: "601",
				descripcion: "General de Ley",
				personaFisica: true,
				personaMoral: true,
				activo: true,
			}).__pk,
		).toBe("601");
		expect(
			dtoFilaAClave("usos_cfdi", {
				clave: "G03",
				descripcion: "Gastos",
				aplicaFisica: true,
				aplicaMoral: false,
				activo: true,
			}).__pk,
		).toBe("G03");
		expect(
			dtoFilaAClave("folios", {
				tipo: "VENTA",
				prefijo: "V-",
				consecutivo: 42,
			}).__pk,
		).toBe("VENTA");
		expect(
			dtoFilaAClave("configuracion", {
				clave: "IVA",
				valor: "0.16",
				descripcion: "Tasa",
			}).__pk,
		).toBe("IVA");
	});

	it("mapea booleanos y decimales (formas_pago, tasas, puestos)", () => {
		const fp = dtoFilaAClave("formas_pago", {
			formaPagoId: 1,
			clave: "EFECTIVO",
			nombre: "Efectivo",
			esEfectivo: true,
			requiereReferencia: false,
			afectaCaja: true,
			formaPagoSatClave: "01",
			comisionPct: 0,
			activo: true,
		});
		expect(fp).toMatchObject({
			__pk: 1,
			es_efectivo: true,
			requiere_referencia: false,
			forma_pago_sat: "01",
			comision_pct: 0,
		});
		const tasa = dtoFilaAClave("tasas_impuesto", {
			tasaId: 5,
			impuestoId: 1,
			tasa: 0.16,
			factor: "TASA",
			ambito: "VENTA",
			zonaFrontera: false,
			vigenteDesde: "2026-01-01",
			vigenteHasta: null,
			activo: true,
		});
		expect(tasa).toMatchObject({
			__pk: 5,
			tasa_id: 5,
			zona_frontera: false,
			vigente_desde: "2026-01-01",
		});
		const puesto = dtoFilaAClave("puestos", {
			puestoId: 2,
			nombre: "Vendedor",
			sueldoBase: 350,
			activo: true,
		});
		expect(puesto).toMatchObject({ __pk: 2, sueldo_base: 350 });
	});

	it("mapea catálogos SAT simples y claves_prod_serv", () => {
		expect(
			dtoFilaAClave("formas_pago_sat", {
				clave: "01",
				descripcion: "Efectivo",
				activo: true,
			}),
		).toMatchObject({ __pk: "01", clave: "01" });
		expect(
			dtoFilaAClave("metodos_pago_sat", {
				clave: "PUE",
				descripcion: "Pago en una exhibición",
				activo: true,
			}),
		).toMatchObject({ __pk: "PUE" });
		expect(
			dtoFilaAClave("unidades_sat", {
				clave: "H87",
				descripcion: "Pieza",
				activo: true,
			}),
		).toMatchObject({ __pk: "H87" });
		expect(
			dtoFilaAClave("claves_prod_serv", {
				clave: "01010101",
				descripcion: "No existe",
				incluyeIva: true,
				ejemplo: false,
			}),
		).toMatchObject({ __pk: "01010101", incluye_iva: true });
		expect(
			dtoFilaAClave("impuestos", {
				impuestoId: 1,
				claveSat: "002",
				nombre: "IVA",
				tipo: "TRASLADADO",
				activo: true,
			}),
		).toMatchObject({ __pk: 1, clave_sat: "002" });
		expect(
			dtoFilaAClave("motivos_movimiento", {
				motivoId: 1,
				clave: "AJUSTE",
				nombre: "Ajuste",
				tipoDefault: "ENTRADA",
				activo: true,
			}),
		).toMatchObject({ __pk: 1, tipo_default: "ENTRADA" });
		expect(
			dtoFilaAClave("tipos_gasto", {
				tipoGastoId: 1,
				clave: "LUZ",
				nombre: "Luz",
				esFijo: true,
				activo: true,
			}),
		).toMatchObject({ __pk: 1, es_fijo: true });
	});
});

describe("clavePayloadADTO", () => {
	it("traduce claves descriptor a DTO (estados)", () => {
		expect(
			clavePayloadADTO("estados", {
				estado_id: 1,
				clave_inegi: "14",
				nombre: "Jalisco",
			}),
		).toEqual({ estadoId: 1, claveInegi: "14", nombre: "Jalisco" });
	});

	it("ignora claves sin mapeo", () => {
		expect(
			clavePayloadADTO("puestos", {
				nombre: "Cajero",
				sueldo_base: 300,
				campo_fantasma: "x",
			}),
		).toEqual({ nombre: "Cajero", sueldoBase: 300 });
	});

	it("traduce booleanos y fechas (tasas_impuesto)", () => {
		expect(
			clavePayloadADTO("tasas_impuesto", {
				impuesto_id: 1,
				tasa: 0.16,
				factor: "TASA",
				ambito: "VENTA",
				zona_frontera: true,
				vigente_desde: "2026-01-01",
				vigente_hasta: "",
				activo: true,
			}),
		).toEqual({
			impuestoId: 1,
			tasa: 0.16,
			factor: "TASA",
			ambito: "VENTA",
			zonaFrontera: true,
			vigenteDesde: "2026-01-01",
			vigenteHasta: "",
			activo: true,
		});
	});
});
