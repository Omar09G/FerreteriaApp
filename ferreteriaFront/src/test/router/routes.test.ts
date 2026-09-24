import { describe, expect, it } from "vitest";

import { authRoutes } from "@/router/routes/auth";
import { dashboardRoutes } from "@/router/routes/dashboard";
import {
	catalogoChildren,
	catalogoRoutes,
	catalogosChildren,
} from "@/router/routes/catalogo";
import { inventarioChildren, inventarioRoutes } from "@/router/routes/inventario";
import { reportesChildren, reportesRoutes } from "@/router/routes/reportes";
import { featureRoutes } from "@/router/routes/features";
import { administracionRoutes } from "@/router/routes/administracion";

describe("routes/auth", () => {
	it("registra /login y redirect /", () => {
		expect(authRoutes.map((r) => r.path)).toContain("/login");
		expect(authRoutes.map((r) => r.path)).toContain("/");
	});
});

describe("routes/dashboard", () => {
	it("registra dashboard", () => {
		expect(dashboardRoutes.map((r) => r.path)).toContain("dashboard");
	});
});

describe("routes/catalogo", () => {
	it("registra hijos de catalogo y catalogos", () => {
		expect(catalogoChildren.map((r) => r.path)).toEqual(
			expect.arrayContaining(["productos", "clientes", "promociones", "proveedores"]),
		);
		expect(catalogosChildren.some((r) => "index" in r && r.index)).toBe(true);
		expect(catalogosChildren.map((r) => ("path" in r ? r.path : undefined))).toContain(":clave");
		expect(catalogoRoutes.map((r) => r.path)).toContain("catalogo");
		expect(catalogoRoutes.map((r) => r.path)).toContain("compras/proveedores");
	});
});

describe("routes/inventario", () => {
	it("registra los 5 hijos de inventario", () => {
		expect(inventarioChildren.map((r) => r.path)).toEqual(
			expect.arrayContaining(["stock", "movimientos", "traslados", "conteos", "almacenes"]),
		);
		expect(inventarioRoutes[0].path).toBe("inventario");
	});
});

describe("routes/reportes", () => {
	it("registra index más 9 reportes", () => {
		expect(reportesChildren.some((r) => "index" in r && r.index)).toBe(true);
		expect(reportesChildren.map((r) => ("path" in r ? r.path : undefined))).toEqual(
			expect.arrayContaining([
				"horas-pico",
				"ventas-totales",
				"top-productos",
				"cierre-diario",
				"productos-sin-movimiento",
			]),
		);
		expect(reportesRoutes[0].path).toBe("reportes");
	});
});

describe("routes/features", () => {
	it("registra pos, ventas, compras, caja, rrhh, seguridad y fiscal", () => {
		const paths = featureRoutes.map((r) => r.path);
		for (const p of [
			"pos",
			"ventas",
			"cobranza",
			"compras",
			"cuentas-pagar",
			"caja",
			"cajas",
			"gastos",
			"rrhh",
			"seguridad",
			"usuarios",
			"fiscal/facturas",
		]) {
			expect(paths, p).toContain(p);
		}
	});
});

describe("routes/administracion", () => {
	it("registra configuracion", () => {
		expect(administracionRoutes[0].path).toBe("administracion/configuracion");
		expect(administracionRoutes[0].element).toBeDefined();
	});
});
