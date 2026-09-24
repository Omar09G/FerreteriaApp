import { describe, expect, it } from "vitest";
import type { RouteObject } from "react-router-dom";

import { privateRoutes, publicRoutes } from "@/router/registry";

/** Aplana rutas a paths absolutos (los hijos relativos se unen al padre). */
export function aplanarRutas(routes: RouteObject[], base = ""): string[] {
	const out: string[] = [];
	for (const r of routes) {
		if (typeof r.index !== "undefined" && r.index) {
			out.push(base || "/");
			continue;
		}
		const p = r.path ?? "";
		const full = p.startsWith("/") ? p : `${base}/${p}`.replace(/\/+/g, "/");
		out.push(full === "" ? "/" : full);
		if (r.children) out.push(...aplanarRutas(r.children, full));
	}
	return out;
}

describe("registry", () => {
	it("expone rutas públicas con /login y redirect /", () => {
		const paths = aplanarRutas(publicRoutes);
		expect(paths).toContain("/login");
		expect(paths).toContain("/");
	});

	it("expone las rutas privadas principales", () => {
		const paths = aplanarRutas(privateRoutes);
		for (const esperada of [
			"/dashboard",
			"/catalogo/productos",
			"/catalogo/clientes",
			"/catalogo/promociones",
			"/catalogo/proveedores",
			"/catalogos",
			"/inventario/stock",
			"/inventario/movimientos",
			"/inventario/traslados",
			"/inventario/conteos",
			"/inventario/almacenes",
			"/reportes",
			"/reportes/horas-pico",
			"/reportes/ventas-totales",
			"/reportes/top-productos",
			"/reportes/cierre-diario",
			"/pos",
			"/ventas/historial",
			"/ventas/cobranza",
			"/ventas/cotizaciones",
			"/ventas/rentas",
			"/ventas/devoluciones",
			"/compras/compras",
			"/compras/cuentas-pagar",
			"/caja/cajas",
			"/caja/gastos",
			"/rrhh/empleados",
			"/rrhh/nomina",
			"/seguridad/usuarios",
			"/seguridad/roles",
			"/seguridad/auditoria",
			"/fiscal/facturas",
			"/administracion/configuracion",
		]) {
			expect(paths, esperada).toContain(esperada);
		}
	});

	it("conserva los redirects legacy", () => {
		const paths = aplanarRutas(privateRoutes);
		for (const legacy of [
			"/compras/proveedores",
			"/cobranza",
			"/cuentas-pagar",
			"/cajas",
			"/gastos",
			"/usuarios",
		]) {
			expect(paths, legacy).toContain(legacy);
		}
	});

	it("todas las rutas tienen path o index y elemento", () => {
		const revisar = (routes: RouteObject[]) => {
			for (const r of routes) {
				expect(r.path !== undefined || r.index === true).toBe(true);
				if (r.children) revisar(r.children);
			}
		};
		revisar(publicRoutes);
		revisar(privateRoutes);
	});
});
