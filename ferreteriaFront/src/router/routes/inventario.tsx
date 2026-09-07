

import { withFallback } from "@/router/builder";

const MovimientosPage = withFallback(() => import("@/features/inventario/MovimientosPage"));
const AlmacenesAdminPage = withFallback(() => import("@/features/inventario/AlmacenesAdminPage"));
const StockPage = withFallback(() => import("@/features/inventario/StockPage"));
const TrasladosPage = withFallback(() => import("@/features/inventario/TrasladosPage"));
const ConteosPage = withFallback(() => import("@/features/inventario/ConteosPage"));

export const inventarioChildren = [
  { path: "stock", element: <StockPage /> },
  { path: "movimientos", element: <MovimientosPage /> },
  { path: "traslados", element: <TrasladosPage /> },
  { path: "conteos", element: <ConteosPage /> },
  { path: "almacenes", element: <AlmacenesAdminPage /> },
];

export const inventarioRoutes = [{ path: "inventario", children: inventarioChildren }];