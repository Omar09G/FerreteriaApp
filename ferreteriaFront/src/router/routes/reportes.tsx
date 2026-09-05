

import { withFallback } from "@/router/builder";

const HorasPicoPage = withFallback(() => import("@/features/reportes/HorasPicoPage"));
const VentasTotalesPage = withFallback(() => import("@/features/reportes/VentasTotalesPage"));
const TopProductosPage = withFallback(() => import("@/features/reportes/TopProductosPage"));
const MejoresDiasPage = withFallback(() => import("@/features/reportes/MejoresDiasPage"));
const MejoresClientesPage = withFallback(() => import("@/features/reportes/MejoresClientesPage"));
const MejoresVendedoresPage = withFallback(() => import("@/features/reportes/MejoresVendedoresPage"));
const CierreDiarioPage = withFallback(() => import("@/features/reportes/CierreDiarioPage"));
const ReportesIndexPage = withFallback(() => import("@/features/reportes/ReportesIndexPage"));
const MejoresCategoriasPage = withFallback(() => import("@/features/reportes/MejoresCategoriasPage"));
const ProductosSinMovimientoPage = withFallback(() => import("@/features/reportes/ProductosSinMovimientoPage"));

export const reportesChildren = [
  { index: true, element: <ReportesIndexPage /> },
  { path: "horas-pico", element: <HorasPicoPage /> },
  { path: "ventas-totales", element: <VentasTotalesPage /> },
  { path: "top-productos", element: <TopProductosPage /> },
  { path: "mejores-dias", element: <MejoresDiasPage /> },
  { path: "mejores-clientes", element: <MejoresClientesPage /> },
  { path: "mejores-vendedores", element: <MejoresVendedoresPage /> },
  { path: "cierre-diario", element: <CierreDiarioPage /> },
  { path: "mejores-categorias", element: <MejoresCategoriasPage /> },
  { path: "productos-sin-movimiento", element: <ProductosSinMovimientoPage /> },
];

export const reportesRoutes = [
  {
    path: "reportes",
    children: reportesChildren,
  },
];