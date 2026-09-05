import type { RouteObject } from "react-router-dom";

import { authRoutes } from "./routes/auth";
import { dashboardRoutes } from "./routes/dashboard";
import { reportesRoutes } from "./routes/reportes";
import { catalogoRoutes } from "./routes/catalogo";
import { inventarioRoutes } from "./routes/inventario";
import { featureRoutes } from "./routes/features";

/**
 * Registry central de rutas por feature. Cada feature expone su propio array
 * de RouteObject; el ensamblador (router.tsx) los combina dentro del AppShell.
 *
 * Agregar una ruta nueva:
 *   1. Crear src/router/routes/<feature>.tsx con lazy + withFallback
 *   2. Importar y aadir al array en este archivo
 * No tocar router.tsx (salvo agregar guard o layout nuevo).
 */
export const publicRoutes: RouteObject[] = [...authRoutes];

export const privateRoutes: RouteObject[] = [
  ...dashboardRoutes,
  ...reportesRoutes,
  ...catalogoRoutes,
  ...inventarioRoutes,
  ...featureRoutes,
];