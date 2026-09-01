/**
 * El React Router se crea aquí; el archivo exporta además del router un helper
 * de autorización por rol, por lo que Fast Refresh no aplica (se desactiva).
 */
/* eslint-disable react-refresh/only-export-components */
import { lazy, Suspense } from "react";
import { createBrowserRouter, Navigate } from "react-router-dom";

import type { ReactNode } from "react";
import { spinners } from "../components/router-utils";
import { ErrorPagina, NotFound } from "@/components/errors/PageStates";
import { AppShell } from "@/components/layout/AppShell";
import { RedirigirSiAutenticado, RequiereAuth, RequiereRol } from "./guards";
import { ToastProvider } from "@/components/ui/Toast";

const Login = lazy(() => import("@/features/auth/Login"));
const DashboardPage = lazy(() => import("@/features/dashboard/DashboardPage"));
const HorasPicoPage = lazy(() => import("@/features/reportes/HorasPicoPage"));
const VentasTotalesPage = lazy(
  () => import("@/features/reportes/VentasTotalesPage"),
);
const TopProductosPage = lazy(
  () => import("@/features/reportes/TopProductosPage"),
);
const MejoresDiasPage = lazy(
  () => import("@/features/reportes/MejoresDiasPage"),
);
const MejoresClientesPage = lazy(
  () => import("@/features/reportes/MejoresClientesPage"),
);
const MejoresVendedoresPage = lazy(
  () => import("@/features/reportes/MejoresVendedoresPage"),
);
const CierreDiarioPage = lazy(
  () => import("@/features/reportes/CierreDiarioPage"),
);
const ReportesIndexPage = lazy(
  () => import("@/features/reportes/ReportesIndexPage"),
);
const MejoresCategoriasPage = lazy(
  () => import("@/features/reportes/MejoresCategoriasPage"),
);

const ProductosSinMovimientoPage = lazy(
  () => import("@/features/reportes/ProductosSinMovimientoPage"),
);

const MovimientosPage = lazy(
  () => import("@/features/inventario/MovimientosPage"),
);
const StockPage = lazy(() => import("@/features/inventario/StockPage"));
const ProductosPage = lazy(() => import("@/features/catalogo/ProductosPage"));
const ClientesPage = lazy(() => import("@/features/catalogo/ClientesPage"));
const PromocionesPage = lazy(
  () => import("@/features/catalogo/PromocionesPage"),
);
const PosPage = lazy(() => import("@/features/pos/PosPage"));
const CajaPage = lazy(() => import("@/features/caja/CajaPage"));
const ComprasPage = lazy(() => import("@/features/compras/ComprasPage"));
const CuentasPagarPage = lazy(
  () => import("@/features/compras/CuentasPagarPage"),
);
const EmpleadosPage = lazy(() => import("@/features/rrhh/EmpleadosPage"));
const UsuariosPage = lazy(() => import("@/features/seguridad/UsuariosPage"));
const CobranzaPage = lazy(() => import("@/features/ventas/CobranzaPage"));
const VentasPage = lazy(() => import("@/features/ventas/VentasPage"));
const GastosPage = lazy(() => import("@/features/caja/GastosPage"));
const ProveedoresPage = lazy(
  () => import("@/features/catalogo/ProveedoresPage"),
);
const CotizacionesPage = lazy(
  () => import("@/features/ventas/CotizacionesPage"),
);
const DevolucionesPage = lazy(
  () => import("@/features/ventas/DevolucionesPage"),
);
const RentasPage = lazy(() => import("@/features/ventas/RentasPage"));
const TrasladosPage = lazy(() => import("@/features/inventario/TrasladosPage"));
const ConteosPage = lazy(() => import("@/features/inventario/ConteosPage"));
const NominaPage = lazy(() => import("@/features/rrhh/NominaPage"));
const RolesPage = lazy(() => import("@/features/seguridad/RolesPage"));
const AuditoriaPage = lazy(() => import("@/features/seguridad/AuditoriaPage"));
const FacturasPage = lazy(() => import("@/features/fiscal/FacturasPage"));

const pos = (roles: string[], el: ReactNode) => (
  <RequiereRol roles={roles}>{el}</RequiereRol>
);

export const router = createBrowserRouter([
  {
    path: "/login",
    element: (
      <RedirigirSiAutenticado>
        <Suspense fallback={spinners.full}>
          <Login />
        </Suspense>
      </RedirigirSiAutenticado>
    ),
  },
  {
    element: (
      <ToastProvider>
        <RequiereAuth />
      </ToastProvider>
    ),
    errorElement: <ErrorPagina />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          {
            path: "dashboard",
            element: (
              <Suspense fallback={spinners.full}>
                <DashboardPage />
              </Suspense>
            ),
          },
          {
            path: "pos",
            element: pos(
              ["VENDEDOR", "ENCARGADO_CAJA", "GERENTE", "ADMINISTRADOR"],
              <Suspense fallback={spinners.full}>
                <PosPage />
              </Suspense>,
            ),
          },
          {
            path: "catalogo",
            children: [
              {
                path: "productos",
                element: (
                  <Suspense fallback={spinners.full}>
                    <ProductosPage />
                  </Suspense>
                ),
              },
              {
                path: "clientes",
                element: (
                  <Suspense fallback={spinners.full}>
                    <ClientesPage />
                  </Suspense>
                ),
              },
              {
                path: "promociones",
                element: pos(
                  ["ADMINISTRADOR", "GERENTE"],
                  <Suspense fallback={spinners.full}>
                    <PromocionesPage />
                  </Suspense>,
                ),
              },
            ],
          },
          {
            path: "inventario",
            children: [
              {
                path: "stock",
                element: (
                  <Suspense fallback={spinners.full}>
                    <StockPage />
                  </Suspense>
                ),
              },
              {
                path: "movimientos",
                element: (
                  <Suspense fallback={spinners.full}>
                    <MovimientosPage />
                  </Suspense>
                ),
              },
              {
                path: "traslados",
                element: (
                  <Suspense fallback={spinners.full}>
                    <TrasladosPage />
                  </Suspense>
                ),
              },
              {
                path: "conteos",
                element: (
                  <Suspense fallback={spinners.full}>
                    <ConteosPage />
                  </Suspense>
                ),
              },
            ],
          },
          {
            path: "compras",
            children: [
              {
                path: "proveedores",
                element: (
                  <Suspense fallback={spinners.full}>
                    <ProveedoresPage />
                  </Suspense>
                ),
              },
              {
                path: "compras",
                element: (
                  <Suspense fallback={spinners.full}>
                    <ComprasPage />
                  </Suspense>
                ),
              },
              {
                path: "cuentas-pagar",
                element: (
                  <Suspense fallback={spinners.full}>
                    <CuentasPagarPage />
                  </Suspense>
                ),
              },
            ],
          },
          {
            path: "ventas",
            children: [
              {
                index: true,
                element: <Navigate to="/ventas/historial" replace />,
              },
              {
                path: "historial",
                element: (
                  <Suspense fallback={spinners.full}>
                    <VentasPage />
                  </Suspense>
                ),
              },
              {
                path: "cobranza",
                element: (
                  <Suspense fallback={spinners.full}>
                    <CobranzaPage />
                  </Suspense>
                ),
              },
              {
                path: "cotizaciones",
                element: (
                  <Suspense fallback={spinners.full}>
                    <CotizacionesPage />
                  </Suspense>
                ),
              },
              {
                path: "rentas",
                element: (
                  <Suspense fallback={spinners.full}>
                    <RentasPage />
                  </Suspense>
                ),
              },
              {
                path: "devoluciones",
                element: (
                  <Suspense fallback={spinners.full}>
                    <DevolucionesPage />
                  </Suspense>
                ),
              },
            ],
          },
          {
            path: "caja",
            children: [
              {
                path: "cajas",
                element: (
                  <Suspense fallback={spinners.full}>
                    <CajaPage />
                  </Suspense>
                ),
              },
              {
                path: "gastos",
                element: (
                  <Suspense fallback={spinners.full}>
                    <GastosPage />
                  </Suspense>
                ),
              },
              {
                path: "ingresos",
                element: (
                  <Suspense fallback={spinners.full}>
                    <GastosPage />
                  </Suspense>
                ),
              },
              {
                path: "cortes",
                element: (
                  <Suspense fallback={spinners.full}>
                    <CajaPage />
                  </Suspense>
                ),
              },
            ],
          },
          {
            path: "rrhh",
            children: [
              {
                path: "empleados",
                element: pos(
                  ["ADMINISTRADOR"],
                  <Suspense fallback={spinners.full}>
                    <EmpleadosPage />
                  </Suspense>,
                ),
              },
              {
                path: "nomina",
                element: pos(
                  ["ADMINISTRADOR"],
                  <Suspense fallback={spinners.full}>
                    <NominaPage />
                  </Suspense>,
                ),
              },
            ],
          },
          {
            path: "seguridad",
            children: [
              {
                path: "usuarios",
                element: pos(
                  ["ADMINISTRADOR"],
                  <Suspense fallback={spinners.full}>
                    <UsuariosPage />
                  </Suspense>,
                ),
              },
              {
                path: "roles",
                element: pos(
                  ["ADMINISTRADOR"],
                  <Suspense fallback={spinners.full}>
                    <RolesPage />
                  </Suspense>,
                ),
              },
              {
                path: "auditoria",
                element: pos(
                  ["ADMINISTRADOR", "AUDITOR"],
                  <Suspense fallback={spinners.full}>
                    <AuditoriaPage />
                  </Suspense>,
                ),
              },
            ],
          },
          {
            path: "fiscal",
            children: [
              {
                path: "facturas",
                element: (
                  <Suspense fallback={spinners.full}>
                    <FacturasPage />
                  </Suspense>
                ),
              },
            ],
          },
          {
            path: "reportes",
            children: [
              {
                index: true,
                element: (
                  <Suspense fallback={spinners.full}>
                    <ReportesIndexPage />
                  </Suspense>
                ),
              },
              {
                path: "ventas-totales",
                element: (
                  <Suspense fallback={spinners.full}>
                    <VentasTotalesPage />
                  </Suspense>
                ),
              },
              {
                path: "horas-pico",
                element: (
                  <Suspense fallback={spinners.full}>
                    <HorasPicoPage />
                  </Suspense>
                ),
              },
              {
                path: "mejores-dias",
                element: (
                  <Suspense fallback={spinners.full}>
                    <MejoresDiasPage />
                  </Suspense>
                ),
              },
              {
                path: "top-productos",
                element: (
                  <Suspense fallback={spinners.full}>
                    <TopProductosPage />
                  </Suspense>
                ),
              },
              {
                path: "mejores-clientes",
                element: (
                  <Suspense fallback={spinners.full}>
                    <MejoresClientesPage />
                  </Suspense>
                ),
              },
              {
                path: "mejores-vendedores",
                element: (
                  <Suspense fallback={spinners.full}>
                    <MejoresVendedoresPage />
                  </Suspense>
                ),
              },
              {
                path: "productos-sin-movimiento",
                element: (
                  <Suspense fallback={spinners.full}>
                    <ProductosSinMovimientoPage />
                  </Suspense>
                ),
              },
              {
                path: "cierre-diario",
                element: (
                  <Suspense fallback={spinners.full}>
                    <CierreDiarioPage />
                  </Suspense>
                ),
              },
              {
                path: "mejores-categorias",
                element: (
                  <Suspense fallback={spinners.full}>
                    <MejoresCategoriasPage />
                  </Suspense>
                ),
              },
              // Removed empty route object
            ],
          },
          { path: "*", element: <NotFound /> },
        ],
      },
    ],
  },
]);
