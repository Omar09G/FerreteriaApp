

import { withFallback } from "@/router/builder";

const MovimientosPage = withFallback(() => import("@/features/inventario/MovimientosPage"));
const AlmacenesAdminPage = withFallback(() => import("@/features/inventario/AlmacenesAdminPage"));
const StockPage = withFallback(() => import("@/features/inventario/StockPage"));

export const inventarioChildren = [
  { path: "stock", element: <StockPage /> },
  { path: "movimientos", element: <MovimientosPage /> },
  { path: "almacenes", element: <AlmacenesAdminPage /> },
];

export const inventarioRoutes = [{ path: "inventario", children: inventarioChildren }];