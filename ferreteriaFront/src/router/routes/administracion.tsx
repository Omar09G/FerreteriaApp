import { withFallback } from "@/router/builder";

const ConfiguracionPage = withFallback(() => import("@/features/administracion/ConfiguracionPage"));

export const administracionRoutes = [{ path: "administracion/configuracion", element: <ConfiguracionPage /> }];
