import { Navigate } from "react-router-dom";

import { withFallback } from "@/router/builder";

const PosPage = withFallback(() => import("@/features/pos/PosPage"));
const CajasAdminPage = withFallback(
  () => import("@/features/caja/CajasAdminPage"),
);
const GastosPage = withFallback(() => import("@/features/caja/GastosPage"));
const ComprasPage = withFallback(
  () => import("@/features/compras/ComprasPage"),
);
const CuentasPagarPage = withFallback(
  () => import("@/features/compras/CuentasPagarPage"),
);
const EmpleadosPage = withFallback(
  () => import("@/features/rrhh/EmpleadosPage"),
);
const NominaPage = withFallback(() => import("@/features/rrhh/NominaPage"));
const UsuariosPage = withFallback(
  () => import("@/features/seguridad/UsuariosPage"),
);
const RolesPage = withFallback(() => import("@/features/seguridad/RolesPage"));
const AuditoriaPage = withFallback(
  () => import("@/features/seguridad/AuditoriaPage"),
);
const CobranzaPage = withFallback(
  () => import("@/features/ventas/CobranzaPage"),
);
const VentasPage = withFallback(() => import("@/features/ventas/VentasPage"));
const CotizacionesPage = withFallback(
  () => import("@/features/ventas/CotizacionesPage"),
);
const RentasPage = withFallback(() => import("@/features/ventas/RentasPage"));
const DevolucionesPage = withFallback(
  () => import("@/features/ventas/DevolucionesPage"),
);
const FacturasPage = withFallback(
  () => import("@/features/fiscal/FacturasPage"),
);

// Rutas que AppShell espera (ver src/components/layout/AppShell.tsx GRUPOS)
export const featureRoutes = [
  { path: "pos", element: <PosPage /> },
  // ventas/*
  {
    path: "ventas",
    children: [
      { path: "historial", element: <VentasPage /> },
      { path: "cobranza", element: <CobranzaPage /> },
      { path: "cotizaciones", element: <CotizacionesPage /> },
      { path: "rentas", element: <RentasPage /> },
      { path: "devoluciones", element: <DevolucionesPage /> },
      { index: true, element: <Navigate to="historial" replace /> },
    ],
  },
  // compat: rutas planas legacy
  { path: "cobranza", element: <Navigate to="/ventas/cobranza" replace /> },
  // compras/*
  {
    path: "compras",
    children: [
      { path: "compras", element: <ComprasPage /> },
      { path: "cuentas-pagar", element: <CuentasPagarPage /> },
      { index: true, element: <Navigate to="compras" replace /> },
    ],
  },
  {
    path: "cuentas-pagar",
    element: <Navigate to="/compras/cuentas-pagar" replace />,
  },
  // caja/*
  {
    path: "caja",
    children: [
      { path: "cajas", element: <CajasAdminPage /> },
      { path: "cajas/admin", element: <CajasAdminPage /> },
      { path: "gastos", element: <GastosPage /> },
      { path: "ingresos", element: <GastosPage /> }, // ingresos usa misma page que gastos por ahora
      { index: true, element: <Navigate to="cajas" replace /> },
    ],
  },
  { path: "cajas", element: <Navigate to="/caja/cajas" replace /> },
  { path: "gastos", element: <Navigate to="/caja/gastos" replace /> },
  // rrhh/*
  {
    path: "rrhh",
    children: [
      { path: "empleados", element: <EmpleadosPage /> },
      { path: "nomina", element: <NominaPage /> },
      { index: true, element: <Navigate to="empleados" replace /> },
    ],
  },
  // seguridad/*
  {
    path: "seguridad",
    children: [
      { path: "usuarios", element: <UsuariosPage /> },
      { path: "roles", element: <RolesPage /> },
      { path: "auditoria", element: <AuditoriaPage /> },
      { index: true, element: <Navigate to="usuarios" replace /> },
    ],
  },
  { path: "usuarios", element: <Navigate to="/seguridad/usuarios" replace /> },
  // fiscal
  { path: "fiscal/facturas", element: <FacturasPage /> },
];
