

import { withFallback } from "@/router/builder";

const PosPage = withFallback(() => import("@/features/pos/PosPage"));
const CajaPage = withFallback(() => import("@/features/caja/CajaPage"));
const CajasAdminPage = withFallback(() => import("@/features/caja/CajasAdminPage"));
const GastosPage = withFallback(() => import("@/features/caja/GastosPage"));
const ComprasPage = withFallback(() => import("@/features/compras/ComprasPage"));
const CuentasPagarPage = withFallback(() => import("@/features/compras/CuentasPagarPage"));
const EmpleadosPage = withFallback(() => import("@/features/rrhh/EmpleadosPage"));
const UsuariosPage = withFallback(() => import("@/features/seguridad/UsuariosPage"));
const CobranzaPage = withFallback(() => import("@/features/ventas/CobranzaPage"));
const VentasPage = withFallback(() => import("@/features/ventas/VentasPage"));

export const featureRoutes = [
  { path: "pos", element: <PosPage /> },
  { path: "caja", element: <CajaPage /> },
  { path: "cajas", element: <CajasAdminPage /> },
  { path: "gastos", element: <GastosPage /> },
  { path: "compras", element: <ComprasPage /> },
  { path: "cuentas-pagar", element: <CuentasPagarPage /> },
  { path: "rrhh", element: <EmpleadosPage /> },
  { path: "usuarios", element: <UsuariosPage /> },
  { path: "cobranza", element: <CobranzaPage /> },
  { path: "ventas", element: <VentasPage /> },
];